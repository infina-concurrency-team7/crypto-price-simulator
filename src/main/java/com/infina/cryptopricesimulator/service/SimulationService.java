package com.infina.cryptopricesimulator.service;

import com.infina.cryptopricesimulator.api.exception.SimulationAlreadyRunningException;
import com.infina.cryptopricesimulator.api.exception.SimulationNotFoundException;
import com.infina.cryptopricesimulator.counter.Counter;
import com.infina.cryptopricesimulator.counter.SafeCounter;
import com.infina.cryptopricesimulator.counter.UnsafeCounter;
import com.infina.cryptopricesimulator.dto.CoinStatResponse;
import com.infina.cryptopricesimulator.dto.SafeCoinResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.engine.WorkerPool;
import com.infina.cryptopricesimulator.metrics.InvariantChecker;
import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import com.infina.cryptopricesimulator.queue.ExpectedCoinCalculatedResult;
import com.infina.cryptopricesimulator.queue.ExpectedResultCalculator;
import com.infina.cryptopricesimulator.queue.PriceUpdateTask;
import com.infina.cryptopricesimulator.queue.TaskProducer;
import com.infina.cryptopricesimulator.state.CoinState;
import com.infina.cryptopricesimulator.state.SafeCoinState;
import com.infina.cryptopricesimulator.state.UnSafeCoinState;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class SimulationService {

    // ─── Queue & shutdown ─────────────────────────────────────────────
    private static final int QUEUE_CAPACITY = 1000;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    // ─── Poison pill ────────────────────────────────────────────────
    private static final long POISON_PILL_SEQUENCE = -1;
    private static final long POISON_PILL_DELTA = 0;

    // ─── Timing ─────────────────────────────────────────────────────
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MILLIS_PER_SECOND = 1000L;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SimulationResultResponse> lastResult = new AtomicReference<>();

    public SimulationResultResponse runSimulation(int workers, int updates, long seed) {

        if (!running.compareAndSet(false, true)) {
            throw new SimulationAlreadyRunningException();
        }

        try {
            // Generate deterministic task list (single-threaded)
            List<PriceUpdateTask> tasks = TaskProducer.createStaticTasks(updates, seed);
            log.info("Generated {} tasks with seed {}", updates, seed);

            // Calculate expected results as single-threaded reference point
            Map<Coin, ExpectedCoinCalculatedResult> expectedResults =
                    ExpectedResultCalculator.calculateExpectedResults(tasks);

            // Run unsafe simulation (no synchronization — race conditions expected)
            Map<Coin, CoinState> unsafeStates = createCoinStates(false);
            Counter unsafeCounter = new UnsafeCounter();
            long unsafeElapsedMs = runSingleSimulation(tasks, unsafeStates, unsafeCounter, workers);

            // Run safe simulation (ReentrantLock-protected)
            Map<Coin, CoinState> safeStates = createCoinStates(true);
            Counter safeCounter = new SafeCounter();
            long safeElapsedMs = runSingleSimulation(tasks, safeStates, safeCounter, workers);

            // Verify invariant: safe results must match expected
            boolean invariantPassed = InvariantChecker.verifyPrices(safeStates, expectedResults);

            SimulationResultResponse response = new SimulationResultResponse(
                    seed, updates,
                    unsafeCounter.count(), safeCounter.count(),
                    workers,
                    unsafeElapsedMs, safeElapsedMs,
                    calculateThroughput(updates, unsafeElapsedMs),
                    calculateThroughput(updates, safeElapsedMs),
                    invariantPassed,
                    buildCoinStats(unsafeStates, safeStates, expectedResults));

            lastResult.set(response);
            return response;

        } finally {
            running.set(false);
        }
    }

    // ─── Public accessors (GET /stats, GET /coins) ───────────────────

    public SimulationResultResponse getLastResult() {
        SimulationResultResponse result = lastResult.get();
        if (result == null) {
            throw new SimulationNotFoundException();
        }
        return result;
    }

    public List<SafeCoinResponse> getLastSafeCoins() {
        SimulationResultResponse result = getLastResult();
        return result.coins().stream()
                .map(c -> new SafeCoinResponse(
                        c.coin().name(),
                        c.initial(),
                        c.safe(),
                        c.safeUpdateCount(),
                        c.safeLastDelta(),
                        c.safeLastUpdatedBy()))
                .toList();
    }

    // ─── Private helper methods ─────────────────────────────────────────

    private Map<Coin, CoinState> createCoinStates(boolean safe) {
        Map<Coin, CoinState> states = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            states.put(coin, safe ? new SafeCoinState(coin) : new UnSafeCoinState(coin));
        }
        return states;
    }

    private long runSingleSimulation(List<PriceUpdateTask> tasks,
                                     Map<Coin, CoinState> states,
                                     Counter counter,
                                     int workers) {

        PriceUpdateTask poisonPill = new PriceUpdateTask(POISON_PILL_SEQUENCE, Coin.BTC, POISON_PILL_DELTA);
        BlockingQueue<PriceUpdateTask> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        WorkerPool<PriceUpdateTask> pool = new WorkerPool<>(workers, poisonPill);

        pool.start(queue, task -> {
            states.get(task.coin()).applyDelta(task.delta());
            counter.increment();
        });

        long startNanos = System.nanoTime();

        enqueueTasks(queue, tasks);
        pool.signalNoMoreTasks();

        try {
            pool.getLatch().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for workers");
        }

        long elapsedMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;

        // Executor cleanup — workers already finished (latch guarantees), just release resources
        pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS);

        log.info("Simulation completed in {}ms ({} tasks, {} workers)",
                elapsedMs, tasks.size(), workers);

        return elapsedMs;
    }

    private List<CoinStatResponse> buildCoinStats(
            Map<Coin, CoinState> unsafeStates,
            Map<Coin, CoinState> safeStates,
            Map<Coin, ExpectedCoinCalculatedResult> expected) {

        List<CoinStatResponse> stats = new ArrayList<>(Coin.values().length);
        for (Coin coin : Coin.values()) {
            Snapshot unsafeSnap = unsafeStates.get(coin).snapshot();
            Snapshot safeSnap = safeStates.get(coin).snapshot();
            ExpectedCoinCalculatedResult exp = expected.get(coin);

            stats.add(new CoinStatResponse(
                    coin,
                    coin.getInitialPrice(),
                    exp.expectedPrice(),
                    unsafeSnap.currentPrice(),
                    safeSnap.currentPrice(),
                    exp.expectedUpdateCount(),
                    unsafeSnap.updateCount(),
                    safeSnap.updateCount(),
                    safeSnap.lastDelta(),
                    safeSnap.lastUpdatedBy()
            ));
        }
        return stats;
    }

    private static void enqueueTasks(BlockingQueue<PriceUpdateTask> queue,
                                      List<PriceUpdateTask> tasks) {
        try {
            for (PriceUpdateTask task : tasks) {
                queue.put(task);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while enqueueing tasks");
        }
    }

    // Returns 0 when elapsedMs is 0 to avoid ArithmeticException (division by zero)
    private static long calculateThroughput(int updates, long elapsedMs) {
        return elapsedMs > 0 ? (updates * MILLIS_PER_SECOND) / elapsedMs : 0;
    }
}

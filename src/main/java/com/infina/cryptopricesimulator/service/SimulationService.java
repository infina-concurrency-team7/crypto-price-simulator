package com.infina.cryptopricesimulator.service;

import com.infina.cryptopricesimulator.api.exception.SimulationAlreadyRunningException;
import com.infina.cryptopricesimulator.counter.Counter;
import com.infina.cryptopricesimulator.counter.SafeCounter;
import com.infina.cryptopricesimulator.counter.UnsafeCounter;
import com.infina.cryptopricesimulator.dto.CoinStatResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.engine.WorkerPool;
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

    /**
     * Simülasyonu çalıştırır: expected (tek thread), unsafe (multi-thread), safe (multi-thread).
     * Aynı anda sadece bir simülasyon çalışabilir — ikinci istek 409 döner.
     */
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
            boolean invariantPassed = verifyInvariant(safeStates, expectedResults);

            return new SimulationResultResponse(
                    seed, updates,
                    unsafeCounter.count(), safeCounter.count(),
                    workers,
                    unsafeElapsedMs, safeElapsedMs,
                    calculateThroughput(updates, unsafeElapsedMs),
                    calculateThroughput(updates, safeElapsedMs),
                    invariantPassed,
                    buildCoinStats(unsafeStates, safeStates, expectedResults));

        } finally {
            running.set(false);
        }
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

        try {
            for (PriceUpdateTask task : tasks) {
                queue.put(task);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while enqueueing tasks");
        }

        pool.signalNoMoreTasks();
        pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS);

        long elapsedMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
        log.info("Simulation completed in {}ms ({} tasks, {} workers)",
                elapsedMs, tasks.size(), workers);

        return elapsedMs;
    }

    private boolean verifyInvariant(Map<Coin, CoinState> safeStates,
                                    Map<Coin, ExpectedCoinCalculatedResult> expected) {
        for (Coin coin : Coin.values()) {
            Snapshot snapshot = safeStates.get(coin).snapshot();
            ExpectedCoinCalculatedResult exp = expected.get(coin);
            if (snapshot.currentPrice() != exp.expectedPrice()) {
                log.error("INVARIANT FAILED for {}: expected={}, actual={}",
                        coin, exp.expectedPrice(), snapshot.currentPrice());
                return false;
            }
        }
        log.info("Invariant check PASSED: all safe prices match expected");
        return true;
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
                    safeSnap.updateCount()
            ));
        }
        return stats;
    }

    // Returns 0 when elapsedMs is 0 to avoid ArithmeticException (division by zero)
    private long calculateThroughput(int updates, long elapsedMs) {
        return elapsedMs > 0 ? (updates * MILLIS_PER_SECOND) / elapsedMs : 0;
    }
}

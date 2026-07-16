package com.infina.cryptopricesimulator.service;

import com.infina.cryptopricesimulator.api.exception.SimulationAlreadyRunningException;
import com.infina.cryptopricesimulator.dto.CoinStatResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.model.Coin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService();
    }

    // ─── Happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("runSimulation_whenValidParams_shouldReturnCompleteResult")
    void runSimulation_whenValidParams_shouldReturnCompleteResult() {
        SimulationResultResponse result = simulationService.runSimulation(4, 1000, 42L);

        assertNotNull(result);
        assertEquals(42L, result.seed());
        assertEquals(1000, result.submittedUpdates());
        assertEquals(4, result.workers());
        assertTrue(result.safeInvariantPassed(), "Safe invariant must pass");
        assertTrue(result.safeElapsedMs() >= 0, "Elapsed must be non-negative");
        assertTrue(result.safeThroughputPerSec() >= 0, "Throughput must be non-negative");
    }

    @Test
    @DisplayName("runSimulation_whenValidParams_shouldProcessAllTasksSafely")
    void runSimulation_whenValidParams_shouldProcessAllTasksSafely() {
        int updates = 5000;
        SimulationResultResponse result = simulationService.runSimulation(4, updates, 42L);

        assertEquals(updates, result.safeProcessedUpdates(),
                "Safe counter must equal submitted updates");
    }

    @Test
    @DisplayName("runSimulation_whenValidParams_shouldReturnCorrectCoinStats")
    void runSimulation_whenValidParams_shouldReturnCorrectCoinStats() {
        SimulationResultResponse result = simulationService.runSimulation(4, 1000, 42L);
        List<CoinStatResponse> coins = result.coins();

        assertEquals(Coin.values().length, coins.size(), "Must contain all coins");

        for (CoinStatResponse coin : coins) {
            assertEquals(coin.coin().getInitialPrice(), coin.initial(),
                    "Initial price must match enum for " + coin.coin());
            assertEquals(coin.expected(), coin.safe(),
                    "Safe price must equal expected for " + coin.coin());
        }
    }

    // ─── Deterministic seed ─────────────────────────────────────────

    @Test
    @DisplayName("runSimulation_whenSameSeed_shouldReturnSameSafeResults")
    void runSimulation_whenSameSeed_shouldReturnSameSafeResults() {
        SimulationResultResponse first = simulationService.runSimulation(4, 1000, 99L);
        SimulationResultResponse second = simulationService.runSimulation(4, 1000, 99L);

        for (Coin coin : Coin.values()) {
            CoinStatResponse f = findCoin(first.coins(), coin);
            CoinStatResponse s = findCoin(second.coins(), coin);
            assertEquals(f.expected(), s.expected(), "Same seed → same expected for " + coin);
            assertEquals(f.safe(), s.safe(), "Same seed → same safe for " + coin);
        }
    }

    @Test
    @DisplayName("runSimulation_whenDifferentSeed_shouldReturnDifferentExpectedResults")
    void runSimulation_whenDifferentSeed_shouldReturnDifferentExpectedResults() {
        SimulationResultResponse r1 = simulationService.runSimulation(4, 5000, 1L);
        SimulationResultResponse r2 = simulationService.runSimulation(4, 5000, 999L);

        boolean anyDifferent = false;
        for (Coin coin : Coin.values()) {
            if (findCoin(r1.coins(), coin).expected() != findCoin(r2.coins(), coin).expected()) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Different seeds should produce different results");
    }

    // ─── Worker count variations ────────────────────────────────────

    @Test
    @DisplayName("runSimulation_whenSingleWorker_shouldPassInvariant")
    void runSimulation_whenSingleWorker_shouldPassInvariant() {
        SimulationResultResponse result = simulationService.runSimulation(1, 1000, 42L);
        assertTrue(result.safeInvariantPassed());
    }

    @Test
    @DisplayName("runSimulation_whenManyWorkers_shouldPassInvariant")
    void runSimulation_whenManyWorkers_shouldPassInvariant() {
        SimulationResultResponse result = simulationService.runSimulation(8, 5000, 42L);
        assertTrue(result.safeInvariantPassed());
    }

    // ─── AtomicBoolean guard ────────────────────────────────────────

    @Test
    @DisplayName("runSimulation_whenCalledConcurrently_shouldRejectSecondCall")
    void runSimulation_whenCalledConcurrently_shouldRejectSecondCall() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<SimulationAlreadyRunningException> caught = new AtomicReference<>();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> first = executor.submit(() -> {
            started.countDown();
            simulationService.runSimulation(4, 50000, 42L);
        });

        started.await();
        Thread.sleep(50);

        try {
            simulationService.runSimulation(4, 1000, 99L);
        } catch (SimulationAlreadyRunningException e) {
            caught.set(e);
        }

        first.get();
        executor.shutdown();

        assertNotNull(caught.get(), "Second concurrent call must throw SimulationAlreadyRunningException");
    }

    @Test
    @DisplayName("runSimulation_whenPreviousCompleted_shouldAllowNextCall")
    void runSimulation_whenPreviousCompleted_shouldAllowNextCall() {
        assertNotNull(simulationService.runSimulation(2, 500, 42L));
        assertNotNull(simulationService.runSimulation(2, 500, 99L));
    }

    // ─── Edge case ──────────────────────────────────────────────────

    @Test
    @DisplayName("runSimulation_whenMinimalUpdates_shouldCompleteSuccessfully")
    void runSimulation_whenMinimalUpdates_shouldCompleteSuccessfully() {
        SimulationResultResponse result = simulationService.runSimulation(1, 1, 42L);

        assertNotNull(result);
        assertEquals(1, result.submittedUpdates());
        assertTrue(result.safeInvariantPassed());
    }


    private CoinStatResponse findCoin(List<CoinStatResponse> coins, Coin target) {
        return coins.stream()
                .filter(c -> c.coin() == target)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Coin not found: " + target));
    }

    private static class AssertionError extends RuntimeException {
        AssertionError(String message) {
            super(message);
        }
    }
}

package com.infina.cryptopricesimulator;

import com.infina.cryptopricesimulator.metrics.InvariantChecker;
import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.queue.ExpectedCoinCalculatedResult;
import com.infina.cryptopricesimulator.state.CoinState;
import com.infina.cryptopricesimulator.state.SafeCoinState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

class CoinStateInvariantTest {

    @Test
    void whenSafePricesMatchExpected_thenInvariantShouldPass() {
        // GIVEN: Safe coin states with known deltas applied
        Map<Coin, CoinState> safeStates = new EnumMap<>(Coin.class);
        for (Coin coin : Coin.values()) {
            safeStates.put(coin, new SafeCoinState(coin));
        }
        // Apply a delta to BTC
        safeStates.get(Coin.BTC).applyDelta(120L);

        // GIVEN: Expected results matching the applied deltas
        Map<Coin, ExpectedCoinCalculatedResult> expected = new EnumMap<>(Coin.class);
        expected.put(Coin.BTC, new ExpectedCoinCalculatedResult(
                Coin.BTC.getInitialPrice() + 120L, 1));
        expected.put(Coin.ETH, new ExpectedCoinCalculatedResult(
                Coin.ETH.getInitialPrice(), 0));
        expected.put(Coin.SOL, new ExpectedCoinCalculatedResult(
                Coin.SOL.getInitialPrice(), 0));

        // THEN: Invariant check should pass
        Assertions.assertTrue(InvariantChecker.verifyPrices(safeStates, expected),
                "Safe coin prices must match expected results");
    }
}
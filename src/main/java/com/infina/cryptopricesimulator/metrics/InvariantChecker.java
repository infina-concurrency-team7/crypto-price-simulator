package com.infina.cryptopricesimulator.metrics;

import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import com.infina.cryptopricesimulator.queue.ExpectedCoinCalculatedResult;
import com.infina.cryptopricesimulator.state.CoinState;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public final class InvariantChecker {

    private InvariantChecker() {}

    /**
     * Güvenli fiyatların beklenen değerlerle birebir eşleştiğini doğrular.
     * safePrice == initialPrice + sum(all deltas for coin)
     */
    public static boolean verifyPrices(Map<Coin, CoinState> safeStates,
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
}

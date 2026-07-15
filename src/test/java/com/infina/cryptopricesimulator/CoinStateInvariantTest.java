package com.infina.cryptopricesimulator;

import com.infina.cryptopricesimulator.metrics.InvariantChecker;
import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

public class CoinStateInvariantTest {

    private final InvariantChecker checker = new InvariantChecker();

    @Test
    void whenSafePricesMatchExpected_thenInvariantShouldPass() {
        // GIVEN: Beklenen (Expected) fiyat listesi
        Map<String, Long> expectedPrices = Map.of(
                "BTC", 60120L,
                "ETH", 2975L
        );

        // WHEN: Arkadaşının Snapshot yapısına uygun sahte veriler üretiyoruz
        List<Snapshot> safeCoins = List.of(
                new Snapshot(Coin.BTC, 60120L, 120L, 5L, "worker-1"),
                new Snapshot(Coin.ETH, 2975L, -25L, 2L, "worker-2")
        );

        // THEN: Invariant kontrolü başarılı (true) dönmelidir
        boolean isPassed = checker.checkPriceInvariant(safeCoins, expectedPrices);
        Assertions.assertTrue(isPassed, "Güvenli coin fiyatları beklenen sonuçla birebir eşleşmelidir!");
    }

    @Test
    void whenProcessedCountMatchesSubmitted_thenCountInvariantShouldPass() {
        long submitted = 10000L;
        long processed = 10000L;

        Assertions.assertTrue(checker.checkCountInvariant(processed, submitted));
    }
}
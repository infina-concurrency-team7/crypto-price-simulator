package com.infina.cryptopricesimulator;

import com.infina.cryptopricesimulator.metrics.InvariantChecker;
import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

class CoinStateInvariantTest { // <-- "public" kaldırıldı!

    private final InvariantChecker checker = new InvariantChecker();

    @Test
    void whenSafePricesMatchExpected_thenInvariantShouldPass() { // <-- "public" yok!
        // GIVEN: Beklenen (Expected) fiyat listesi artık Coin enum ile tutuluyor
        Map<Coin, Long> expectedPrices = Map.of(
                Coin.BTC, 60120L,
                Coin.ETH, 2975L
        );

        // WHEN: Worker'ların işlediği güvenli (Safe) coin son durumları
        List<Snapshot> safeCoins = List.of(
                new Snapshot(Coin.BTC, 60120L, 120L, 5L, "worker-1"),
                new Snapshot(Coin.ETH, 2975L, -25L, 2L, "worker-2")
        );

        // THEN: Invariant kontrolü başarılı (true) dönmelidir
        boolean isPassed = checker.checkPriceInvariant(safeCoins, expectedPrices);
        Assertions.assertTrue(isPassed, "Güvenli coin fiyatları beklenen sonuçla birebir eşleşmelidir!");
    }

    @Test
    void whenProcessedCountMatchesSubmitted_thenCountInvariantShouldPass() { // <-- "public" yok!
        long submitted = 10000L;
        long processed = 10000L;

        Assertions.assertTrue(checker.checkCountInvariant(processed, submitted));
    }
}
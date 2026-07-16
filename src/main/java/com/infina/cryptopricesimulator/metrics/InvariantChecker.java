package com.infina.cryptopricesimulator.metrics;

import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import java.util.List;
import java.util.Map;

public class InvariantChecker {

    // 1. Kural: Güvenli Fiyat == Başlangıç Fiyatı + Bütün Deltaların Toplamı
    public boolean checkPriceInvariant(List<Snapshot> safeCoins, Map<Coin, Long> expectedPrices) {
        for (Snapshot snapshot : safeCoins) {
            // Artık hem snapshot.coin() hem de Map key değeri doğrudan Coin enum tipinde
            Long expected = expectedPrices.get(snapshot.coin());
            if (expected == null || snapshot.currentPrice() != expected) {
                return false; // Sapma var, invariant bozuldu!
            }
        }
        return true;
    }

    // 2. Kural: İşlenen Güvenli Görev Sayısı == Üretilen Toplam Görev Sayısı
    public boolean checkCountInvariant(long safeProcessed, long submitted) {
        return safeProcessed == submitted;
    }
}
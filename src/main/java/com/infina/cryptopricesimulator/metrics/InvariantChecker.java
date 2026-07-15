package com.infina.cryptopricesimulator.metrics;

import com.infina.cryptopricesimulator.model.Snapshot;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class InvariantChecker {

    // 1. Kural: Güvenli Fiyat == Başlangıç Fiyatı + Bütün Deltaların Toplamı
    public boolean checkPriceInvariant(List<Snapshot> safeCoins, Map<String, Long> expectedPrices) {
        for (Snapshot snapshot : safeCoins) {
            // coin().name() bize "BTC", "ETH" gibi symbol/adı verir
            Long expected = expectedPrices.get(snapshot.coin().name());
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
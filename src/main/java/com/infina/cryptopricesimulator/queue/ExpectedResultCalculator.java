package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.entities.CoinType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Görev listesini tek bir thread üzerinden işleyerek "beklenen son fiyatları" hesaplar.
 * Simülasyonun doğruluğunu kanıtlayacak olan invariant kontrolü için referans noktasıdır
 */
public class ExpectedResultCalculator {

    /**
     * @param tasks Simülasyon başında üretilen immutable görev listesi
     * @return Her coin için beklenen fiyat ve güncelleme sayısını içeren harita
     */
    public static Map<CoinType, ExpectedCoinCalculatedResult> calculateExpectedResults(List<PriceUpdateTask> tasks) {
        Map<CoinType, Long> finalPrices = new HashMap<>();
        Map<CoinType, Long> updateCounts = new HashMap<>();

        //  Başlangıç fiyatlarını yükle (Initial Price)
        for (CoinType type : CoinType.values()) {
            finalPrices.put(type, type.getInitialPrice());
            updateCounts.put(type, 0L);
        }

        // Tüm deltaları tek tek ekle (Deterministik hesaplama)
        // Bu işlem tek thread'de yapıldığı için race condition oluşmaz
        for (PriceUpdateTask task : tasks) {
            CoinType coin = task.coin();
            long currentPrice = finalPrices.get(coin);

            finalPrices.put(coin, currentPrice + task.delta());
            updateCounts.put(coin, updateCounts.get(coin) + 1);
        }

        //  Sonuçları DTO (record) yapısına dönüştürerek paketle
        Map<CoinType, ExpectedCoinCalculatedResult> resultMap = new HashMap<>();
        for (CoinType type : CoinType.values()) {
            resultMap.put(type, new ExpectedCoinCalculatedResult(
                    finalPrices.get(type),
                    updateCounts.get(type)
            ));
        }

        return resultMap;
    }
}

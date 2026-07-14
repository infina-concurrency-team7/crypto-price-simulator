package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;

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
    public static Map<Coin, ExpectedCoinCalculatedResult> calculateExpectedResults(List<PriceUpdateTask> tasks) {
        Map<Coin, Long> finalPrices = new HashMap<>();
        Map<Coin, Long> updateCounts = new HashMap<>();

        //  Başlangıç fiyatlarını yükle (Initial Price)
        for (Coin type : Coin.values()) {
            finalPrices.put(type, type.getInitialPrice());
            updateCounts.put(type, 0L);
        }

        // Tüm deltaları tek tek ekle (Deterministik hesaplama)
        // Bu işlem tek thread'de yapıldığı için race condition oluşmaz
        for (PriceUpdateTask task : tasks) {
            Coin coin = task.coin();
            long currentPrice = finalPrices.get(coin);

            finalPrices.put(coin, currentPrice + task.delta());
            updateCounts.put(coin, updateCounts.get(coin) + 1);
        }

        //  Sonuçları DTO (record) yapısına dönüştürerek paketle
        Map<Coin, ExpectedCoinCalculatedResult> resultMap = new HashMap<>();
        for (Coin type : Coin.values()) {
            resultMap.put(type, new ExpectedCoinCalculatedResult(
                    finalPrices.get(type),
                    updateCounts.get(type)
            ));
        }

        return resultMap;
    }
}

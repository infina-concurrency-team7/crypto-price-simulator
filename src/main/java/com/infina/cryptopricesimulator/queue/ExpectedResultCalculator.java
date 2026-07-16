package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.queue.ExpectedCoinCalculatedResult;
import com.infina.cryptopricesimulator.queue.PriceUpdateTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Görev listesini tek bir thread üzerinden işleyerek "beklenen son fiyatları" hesaplar.
 * Simülasyonun doğruluğunu kanıtlayacak olan invariant kontrolü için referans noktasıdır [3, 4].
 */
public class ExpectedResultCalculator {

    /**
     * @param tasks Simülasyon başında üretilen immutable görev listesi [3, 5]
     * @return Her coin için beklenen fiyat ve güncelleme sayısını içeren harita
     */
    public static Map<Coin, ExpectedCoinCalculatedResult> calculateExpectedResults(List<PriceUpdateTask> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("Görev listesi null olamaz!");
        }

        Map<Coin, Long> finalPrices = new HashMap<>();
        Map<Coin, Long> updateCounts = new HashMap<>();

        // Başlangıç durumunu hazırla
        initializeInitialStates(finalPrices, updateCounts);

        // Tüm deltaları tek thread üzerinde deterministik olarak uygula
        applyDeltas(tasks, finalPrices, updateCounts);

        //Sonuçları DTO yapısına dönüştürerek paketle
        return packageResults(finalPrices, updateCounts);
    }

    /**
     * Coin'lerin başlangıç fiyatlarını ve sayaçlarını yükler.
     */
    private static void initializeInitialStates(Map<Coin, Long> finalPrices, Map<Coin, Long> updateCounts) {
        for (Coin coin : Coin.values()) {
            finalPrices.put(coin, coin.getInitialPrice());
            updateCounts.put(coin, 0L);
        }
    }

    /**
     * Görev listesini sırayla işleyerek fiyat ve sayaç güncellemelerini yapar.
     * Bu işlem tek thread'de yapıldığı için race condition oluşmaz [3].
     */
    private static void applyDeltas(List<PriceUpdateTask> tasks, Map<Coin, Long> finalPrices, Map<Coin, Long> updateCounts) {
        for (PriceUpdateTask task : tasks) {
            Coin coin = task.coin();
            long newPrice = finalPrices.get(coin) + task.delta();

            finalPrices.put(coin, newPrice);
            updateCounts.put(coin, updateCounts.get(coin) + 1);
        }
    }

    /**
     * Hesaplanan ham verileri ExpectedCoinCalculatedResult record'larına dönüştürür.
     */
    private static Map<Coin, ExpectedCoinCalculatedResult> packageResults(Map<Coin, Long> finalPrices, Map<Coin, Long> updateCounts) {
        Map<Coin, ExpectedCoinCalculatedResult> resultMap = new HashMap<>();
        for (Coin coin : Coin.values()) {
            resultMap.put(coin, new ExpectedCoinCalculatedResult(
                    finalPrices.get(coin),
                    updateCounts.get(coin)
            ));
        }
        return resultMap;
    }
}
package com.infina.cryptopricesimulator.queue;

/**
 * Bir coin için simülasyon öncesinde hesaplanan "mutlak doğru" referans sonuçlarını tutar.
 * Bu değerler, güvenli (safe) simülasyonun doğruluğunu kanıtlamak için kullanılır .
 *
 * @param expectedPrice Başlangıç fiyatı + o coin'e ait tüm deltaların toplamı .
 * @param expectedUpdateCount O coin için üretilen toplam görev sayısı .
 */
public record ExpectedCoinCalculatedResult(
        long expectedPrice,
        long expectedUpdateCount
) {}
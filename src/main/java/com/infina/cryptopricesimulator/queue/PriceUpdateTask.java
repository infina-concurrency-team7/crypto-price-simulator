package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;

/*Kuyrukta taşınan ve işlenmeyi bekleyen her bir "fiyat değişim emri" paketidir.
 @param sequence Görevin sıra numarası (Loglama ve Poison Pill için kritik)
Record sınıfı ,Sadece veri taşımak (DTO/POJO) amacıyla üretilmiş özel bir sınıf türüdür.
Java, arka planda bizim için getter, equals, hashCode ve toString metotlarını otomatik olarak yazar.
Sınıfın içindeki tüm alanlar varsayılan olarak private final (yani salt okunur/değiştirilemez) olur.
 */

public record PriceUpdateTask(
        //worker loglarında takibi sağlamak amacıyla eklenmiştir
        long sequence,
        Coin coin,
        //eklenecek veya çıkarılacak değişim miktarını gösterir
        long delta
) {}
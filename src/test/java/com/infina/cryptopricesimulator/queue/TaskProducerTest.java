package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class TaskProducerTest {


    @Test
    @DisplayName("Aynı seed ile başlatılan iki farklı producer aynı görev sırasını üretmeli (Deterministik Test)")
    void testProducerIsDeterministicWithSameSeed() throws InterruptedException {

        long sameSeed = 999L;
        int totalUpdates = 5;

        // Kapasiteli kuyruklar kullanarak backpressure desteği sağlıyoruz
        BlockingQueue<PriceUpdateTask> queue1 = new LinkedBlockingQueue<>(10);
        BlockingQueue<PriceUpdateTask> queue2 = new LinkedBlockingQueue<>(10);

        // Önce aynı seed ile iki ayrı görev listesi üretiyoruz
        List<PriceUpdateTask> tasks1 = TaskProducer.createStaticTasks(totalUpdates, sameSeed);
        List<PriceUpdateTask> tasks2 = TaskProducer.createStaticTasks(totalUpdates, sameSeed);

        // Yapıcı metoda doğru parametreleri (Queue, List) geçiyoruz
        TaskProducer producer1 = new TaskProducer(queue1, tasks1);
        TaskProducer producer2 = new TaskProducer(queue2, tasks2);


        Thread t1 = new Thread(producer1);
        Thread t2 = new Thread(producer2);

        t1.start();
        t2.start();

        // Üreticilerin işini bitirdiğinden emin olmak için join kullanıyoruz
        t1.join();
        t2.join();


        assertEquals(totalUpdates, queue1.size(), "Kuyruk 1 beklenen sayıda görev içermiyor");
        assertEquals(queue1.size(), queue2.size(), "Farklı producerlar farklı sayıda görev üretti");

        // Görevleri sırayla karşılaştır
        while (!queue1.isEmpty()) {
            PriceUpdateTask task1 = queue1.poll();
            PriceUpdateTask task2 = queue2.poll();

            assertNotNull(task1);
            assertNotNull(task2);

            assertEquals(task1.sequence(), task2.sequence(), "Sıra numaraları (sequence) eşleşmeli");
            assertEquals(task1.coin(), task2.coin(), "Coin türleri aynı seed için eşleşmeli");
            assertEquals(task1.delta(), task2.delta(), "Delta değerleri aynı seed için eşleşmeli");
        }
    }


    @Test
    @DisplayName("Fiyat Hesaplama Testi: ExpectedResultCalculator doğru matematiksel sonucu döndürmeli")
    void testExpectedResultCalculatorLogic() {
        //  Manuel bir görev listesi oluşturuyoruz
        List<PriceUpdateTask> manualTasks = new ArrayList<>();
        manualTasks.add(new PriceUpdateTask(1L, Coin.BTC, 100L));
        manualTasks.add(new PriceUpdateTask(2L, Coin.BTC, -50L));

        //  Hesaplayıcıyı çağırıyoruz
        Map<Coin, ExpectedCoinCalculatedResult> results =
                ExpectedResultCalculator.calculateExpectedResults(manualTasks);

        //  Then: Invariant Kontrolü
        long expectedBtcPrice = Coin.BTC.getInitialPrice() + 100L - 50L;

        // BTC sonuçlarını doğrula
        assertNotNull(results.get(Coin.BTC), "BTC sonucu null olamaz!");
        assertEquals(expectedBtcPrice, results.get(Coin.BTC).expectedPrice(),
                "BTC için hesaplanan beklenen fiyat hatalı!");
        assertEquals(2, results.get(Coin.BTC).expectedUpdateCount(),
                "BTC için beklenen güncelleme sayısı hatalı!");

        //  İşlem görmeyen coinlerin kontrolü
        assertNotNull(results.get(Coin.ETH), "ETH sonucu null olamaz!");
        assertEquals(Coin.ETH.getInitialPrice(), results.get(Coin.ETH).expectedPrice(),
                "İşlem görmeyen ETH başlangıç fiyatında kalmalıydı!");
        assertEquals(0, results.get(Coin.ETH).expectedUpdateCount(),
                "İşlem görmeyen ETH'nin güncelleme sayısı 0 olmalıydı!");
    }
}
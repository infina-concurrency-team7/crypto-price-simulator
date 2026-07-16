package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Collections;
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
        // Given: Manuel bir immutable görev listesi oluşturuyoruz [3, 4]
        List<PriceUpdateTask> manualTasks = new ArrayList<>();
        // BTC için 100 artış ve 50 düşüş emirleri ekleniyor
        manualTasks.add(new PriceUpdateTask(1L, Coin.BTC, 100L));
        manualTasks.add(new PriceUpdateTask(2L, Coin.BTC, -50L));

        // When: Hesaplayıcıyı tek thread üzerinden çağırıyoruz [5]
        Map<Coin, ExpectedCoinCalculatedResult> results =
                ExpectedResultCalculator.calculateExpectedResults(manualTasks);

        // Then: Invariant Kontrolü - Matematiksel Doğrulama [6, 7]
        // Kural: Son fiyat = Başlangıç fiyatı + Deltaların toplamı
        long expectedBtcPrice = Coin.BTC.getInitialPrice() + 100L - 50L;

        // 1. BTC sonuçlarını doğrula
        ExpectedCoinCalculatedResult btcResult = results.get(Coin.BTC);
        assertNotNull(btcResult, "BTC sonucu null olamaz!");
        assertEquals(expectedBtcPrice, btcResult.expectedPrice(),
                "BTC için hesaplanan beklenen fiyat hatalı!");
        assertEquals(2, btcResult.expectedUpdateCount(),
                "BTC için beklenen güncelleme sayısı hatalı!");

        // 2. İşlem görmeyen coinlerin kontrolü (Side Effect kontrolü)
        // Başlangıç fiyatları korunmalı: ETH = 3000, SOL = 150 [8]
        ExpectedCoinCalculatedResult ethResult = results.get(Coin.ETH);
        assertNotNull(ethResult, "ETH sonucu null olamaz!");
        assertEquals(Coin.ETH.getInitialPrice(), ethResult.expectedPrice(),
                "İşlem görmeyen ETH başlangıç fiyatında kalmalıydı!");
        assertEquals(0, ethResult.expectedUpdateCount(),
                "İşlem görmeyen ETH'nin güncelleme sayısı 0 olmalıydı!");
    }
    @Nested
    @DisplayName("Uç Durum ve Sıra Dışı Senaryolar")
    class EdgeCaseAndFailureTests {

        @Test
        @DisplayName("Hesaplayıcıya 'null' görev listesi gönderildiğinde hata fırlatmalı veya güvenli dönmeli")
        void testCalculatorWithNullTaskList() {
            // İpucu: Tasarımına göre null geldiğinde boş map dönebilir veya hata fırlatabilir.
            // Biz burada savunmacı programlama (defensive programming) gereği IllegalArgumentException bekliyoruz.
            assertThrows(IllegalArgumentException.class, () -> {
                ExpectedResultCalculator.calculateExpectedResults(null);
            }, "Null liste gönderildiğinde IllegalArgumentException fırlatılmalıydı!");
        }

        @Test
        @DisplayName("Hesaplayıcıya 'boş' görev listesi gönderildiğinde tüm coinlerin fiyatı başlangıç değerinde kalmalı")
        void testCalculatorWithEmptyTaskList() {
            List<PriceUpdateTask> emptyTasks = Collections.emptyList();

            Map<Coin, ExpectedCoinCalculatedResult> results =
                    ExpectedResultCalculator.calculateExpectedResults(emptyTasks);

            assertNotNull(results, "Sonuç map'i null olmamalı!");

            // Tüm tanımlı coinlerin başlangıç durumunda kalıp kalmadığını doğrula
            for (Coin coin : Coin.values()) {
                ExpectedCoinCalculatedResult result = results.get(coin);
                assertNotNull(result, coin.name() + " için sonuç haritada bulunamadı!");
                assertEquals(coin.getInitialPrice(), result.expectedPrice(),
                        coin.name() + " başlangıç fiyatını korumalıydı!");
                assertEquals(0, result.expectedUpdateCount(),
                        coin.name() + " güncelleme sayısı sıfır olmalıydı!");
            }
        }

        @Test
        @DisplayName("TaskProducer sıfır görev sayısı istendiğinde boş liste döndürmeli")
        void testProducerWithZeroTaskCount() {
            List<PriceUpdateTask> tasks = TaskProducer.createStaticTasks(0, 12345L);
            assertNotNull(tasks);
            assertTrue(tasks.isEmpty(), "Görev sayısı 0 iken liste boş olmalı!");
        }

        @Test
        @DisplayName("TaskProducer negatif görev sayısı istendiğinde hata fırlatmalı")
        void testProducerWithNegativeTaskCount() {
            assertThrows(IllegalArgumentException.class, () -> {
                TaskProducer.createStaticTasks(-5, 12345L);
            }, "Negatif görev adedi istendiğinde hata fırlatılmalı!");
        }

        @Test
        @DisplayName("Fiyat taşma sınırları zorlandığında veri güvenliği korunmalı")
        void testCalculatorDeltaOverflowProtection() {
            List<PriceUpdateTask> extremeTasks = new ArrayList<>();
            // Sabit 65000L yerine dinamik olarak Coin.BTC başlangıç fiyatını çıkartıyoruz
            long btcInitialPrice = Coin.BTC.getInitialPrice();
            extremeTasks.add(new PriceUpdateTask(1L, Coin.BTC, Long.MAX_VALUE - btcInitialPrice));

            Map<Coin, ExpectedCoinCalculatedResult> results =
                    ExpectedResultCalculator.calculateExpectedResults(extremeTasks);

            assertEquals(Long.MAX_VALUE, results.get(Coin.BTC).expectedPrice(),
                    "Fiyat güvenle Long.MAX_VALUE sınırına ulaşabilmeli.");
        }

        @Test
        @DisplayName("TaskProducer çalışırken kesintiye (interrupt) uğrarsa durumunu temiz yönetmeli")
        void testProducerThreadInterruption() throws InterruptedException {
            // Sadece 1 elemanlık kapasiteye sahip dar bir kuyruk
            BlockingQueue<PriceUpdateTask> tightQueue = new LinkedBlockingQueue<>(1);

            // Kuyruğu dolduracak 5 adet görev hazırlıyoruz
            List<PriceUpdateTask> tasks = TaskProducer.createStaticTasks(5, 123L);
            TaskProducer producer = new TaskProducer(tightQueue, tasks);

            Thread producerThread = new Thread(producer);
            producerThread.start();

            // Producer'ın ilk elemanı kuyruğa eklemesini ve ikincide bloke olmasını bekle
            await().atMost(1, TimeUnit.SECONDS).until(() -> tightQueue.size() == 1);

            // Bloke durumundaki thread'i dışarıdan bölüyoruz (interrupt)
            producerThread.interrupt();

            // Thread'in sonlanmasını bekle
            producerThread.join(2000);

            // Thread'in gerçekten öldüğünü ve interrupt bayrağını yutmadığını kontrol et
            assertFalse(producerThread.isAlive(), "Thread interrupt edildikten sonra sonlanmalıydı!");
        }
    }
}
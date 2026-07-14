package com.infina.cryptopricesimulator.queue;

import com.infina.cryptopricesimulator.model.Coin;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class TaskProducer implements Runnable {
    //görevlerin worker thread'lere (tüketicilere) güvenli bir şekilde dağıtılmasını sağlar
    //bir worker kuyruktan görev alırken, üretici aynı anda yeni bir görevi kuyruğa ekleyebilir
    private final BlockingQueue<PriceUpdateTask> taskQueue;
    private final List<PriceUpdateTask> preGeneratedTasks;
    public TaskProducer(BlockingQueue<PriceUpdateTask> taskQueue, List<PriceUpdateTask> tasks) {
        this.taskQueue = taskQueue;
        this.preGeneratedTasks = tasks;
    }


    //Görevleri simülasyon başlamadan önce bir kez üretin
    //Güvenli ve güvensiz simülasyonların sonuçlarını karşılaştırabilmek için birebir aynı görev listesini kullanıyoruz
    //Simülasyon başlamadan önce bu listeyi tek bir thread üzerinden işleyerek "mutlak doğru" sonucu (beklenen fiyatı) hesapları
    public static List<PriceUpdateTask> createStaticTasks(int count, long seed) {
        //aynı seed değeri verildiği sürece metot her çalıştığında birebir aynı "rastgele" sayı dizisini üretir
        Random random = new Random(seed);
        //Üretilecek görev nesnelerini (PriceUpdateTask) içinde toplamak amacıyla boş bir dinamik liste (ArrayList) oluşturur.
        List<PriceUpdateTask> tasks = new ArrayList<>();
        //Coin adındaki Enum (sabitler listesi) yapısının içinde tanımlı tüm kripto para veya coin türlerini bir dizi (Array) olarak çeker.
        Coin[] coins = Coin.values();

        for (int i = 1; i <= count; i++) {
            Coin randomCoin = coins[random.nextInt(coins.length)];
            //Fiyattaki değişimi (delta) belirlemek için -100 ile +100 arasında rastgele bir tam sayı üretir.
            // -100 ile +100 arası
            long randomDelta = random.nextInt(201) - 100;

            // sequence alanını 'i' (döngü sayacı) olarak atıyoruz
            tasks.add(new PriceUpdateTask(i, randomCoin, randomDelta));
        }
        return tasks;
    }


    @Override
    public void run() {
        try {
            // Önceden üretilmiş aynı listeyi kuyruğa boşaltın
            for (PriceUpdateTask task : preGeneratedTasks) {
                taskQueue.put(task); // Kuyruk doluysa backpressure sağlar
            }

            //  Worker'lara işin bittiğini bildirmek için Poison Pill eklenecek
            // taskQueue.put(new PriceUpdateTask(-1, null, 0));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Interrupt durumunu yutma
        }
    }
}
package com.infina.cryptopricesimulator.engine;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Thread dump alma yardımcı demosu (yönerge §14). Üretim kodu DEĞİLDİR; yalnızca elle
 * çalıştırılıp çalışırken dump alınması içindir.
 *
 * <p>Çalıştır: main'i başlat, PID'i al (konsol çıktısı veya {@code jps}), sonra
 * {@code jstack <pid>}. Birkaç görev hızlıca işlenir; ardından açık bir pencerede worker'lar boş
 * kalıp {@code BlockingQueue.take()} içinde WAITING görünür — kuyruğun gerçekten kullanıldığının
 * kanıtı. 10.000 thread OLMAMALI; yalnızca worker-1..4 görünmeli.
 */
public final class ThreadDumpDemo {

    /** Demo görevi (domain modelinden bağımsız). */
    private record Task(long seq) { }

    public static void main(String[] args) throws Exception {
        int workers = 4;
        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(64);
        WorkerPool<Task> pool = new WorkerPool<>(workers, new Task(-1));
        pool.start(queue, task -> { /* hızlı işleme */ });

        System.out.println("PID=" + ProcessHandle.current().pid());
        System.out.flush();

        // Birkaç görev üret; hepsi anında işlenir, sonra worker'lar take() üzerinde beklemeye geçer.
        for (int i = 1; i <= 5; i++) {
            queue.put(new Task(i));
        }

        // Dump alma penceresi: bu süre boyunca 4 worker da queue.take() içinde WAITING.
        long windowMs = args.length > 0 ? Long.parseLong(args[0]) : 8_000L;
        Thread.sleep(windowMs);

        pool.signalNoMoreTasks();
        pool.awaitCompletion(30);
        System.out.println("done");
    }
}

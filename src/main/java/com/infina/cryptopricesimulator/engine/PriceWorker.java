package com.infina.cryptopricesimulator.engine;

import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer–Consumer yapısının tüketici (consumer) tarafı. Bir {@link BlockingQueue}'dan
 * görevleri alır ve {@link TaskProcessor}'a devreder; poison pill görene kadar döner (#5).
 *
 * <p>Her worker ayrı bir thread değildir: {@link WorkerPool} sabit bir thread havuzunda
 * {@code workers} adet {@code PriceWorker} çalıştırır. Böylece 10.000 görev için 10.000 thread
 * açılmaz; sınırlı sayıda worker görevleri sırayla tüketir (yönerge §3-§4).
 *
 * <p><b>Durma koşulu (poison pill):</b> Kuyruğa gerçek görevlerin ardından her worker için bir
 * sentinel görev konur. Worker sentinel'i referans kimliğiyle ({@code ==}) tanır ve döngüden
 * çıkar. FIFO kuyrukta sentinel'ler gerçek görevlerden sonra sıralandığı için worker çıkmadan
 * önce tüm gerçek görevler işlenmiş olur.
 *
 * @param <T> işlenen görev tipi; poison pill de aynı tiptedir
 */
public final class PriceWorker<T> implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PriceWorker.class);

    private final BlockingQueue<T> queue;
    private final TaskProcessor<T> processor;
    private final T poisonPill;

    /**
     * @param queue      görevlerin alınacağı paylaşılan kuyruk
     * @param processor  görevi işleyen strateji (safe/unsafe)
     * @param poisonPill döngüyü sonlandıran sentinel; {@link WorkerPool} tarafından sağlanır
     */
    public PriceWorker(BlockingQueue<T> queue, TaskProcessor<T> processor, T poisonPill) {
        this.queue = queue;
        this.processor = processor;
        this.poisonPill = poisonPill;
    }

    @Override
    public void run() {
        try {
            while (true) {
                T task = queue.take();
                // Referans kimliği: sadece WorkerPool'un koyduğu tekil sentinel eşleşir.
                if (task == poisonPill) {
                    break;
                }
                // Görev bazlı log DEBUG seviyesinde; performans testlerinde kapatılır (§13).
                if (log.isDebugEnabled()) {
                    log.debug("[{}] processing {}", Thread.currentThread().getName(), task);
                }
                processor.process(task);
            }
        } catch (InterruptedException e) {
            // shutdownNow() sonrası: interrupt flag'ini geri koyup temiz çıkış yap.
            Thread.currentThread().interrupt();
        }
    }
}

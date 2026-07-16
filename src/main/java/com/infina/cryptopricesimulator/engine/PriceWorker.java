package com.infina.cryptopricesimulator.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * <p><b>Tamamlanma (CountDownLatch):</b> Worker döngüden çıkarken (poison pill, interrupt veya
 * beklenmedik durum — {@code finally}) {@link #latch}'i bir azaltır. Böylece çağıran katman
 * {@code latch.await()} ile tüm worker'ların bittiğini graceful shutdown'dan önce bekleyebilir.
 *
 * <p><b>Hata dayanıklılığı:</b> {@code process} bir {@link RuntimeException} fırlatırsa worker
 * ölmez; hata loglanır ve sıradaki göreve geçilir. Bir worker ölseydi kalan pill'lerden birini
 * kimse tüketemez, sınırlı kuyrukta {@code signalNoMoreTasks()} put'u sonsuza bloklanabilirdi.
 *
 * @param <T> işlenen görev tipi; poison pill de aynı tiptedir
 */
@Slf4j
@RequiredArgsConstructor
public final class PriceWorker<T> implements Runnable {

    private final BlockingQueue<T> queue;
    private final TaskProcessor<T> processor;
    private final T poisonPill;
    private final CountDownLatch latch;

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                T task = queue.take();
                // Referans kimliği: sadece WorkerPool'un koyduğu tekil sentinel eşleşir.
                if (task == poisonPill) {
                    break;
                }
                // Görev bazlı log DEBUG seviyesinde; performans testlerinde kapatılır (§13).
                if (log.isDebugEnabled()) {
                    log.debug("[{}] processing {}", Thread.currentThread().getName(), task);
                }
                // Tek bir görevin hatası worker'ı öldürmemeli: yakala, logla, devam et.
                try {
                    processor.process(task);
                } catch (RuntimeException ex) {
                    log.error("[{}] task failed, skipping: {}",
                            Thread.currentThread().getName(), task, ex);
                }
            }
        } catch (InterruptedException e) {
            // shutdownNow() sonrası normal akış: interrupt flag'ini geri koyup temiz çıkış yap.
            log.debug("[{}] interrupted, stopping", Thread.currentThread().getName());
            Thread.currentThread().interrupt();
        } finally {
            // Worker hangi yolla çıkarsa çıksın latch mutlaka azaltılır (aksi hâlde await asılı kalır).
            latch.countDown();
        }
    }
}

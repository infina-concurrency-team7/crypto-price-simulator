package com.infina.cryptopricesimulator.engine;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sabit boyutlu worker havuzunun yaşam döngüsünü yönetir (#6, #7).
 *
 * <p><b>Tek kullanımlıktır:</b> bir {@code WorkerPool} örneği tam olarak BİR simülasyon
 * koşusuna karşılık gelir. {@link #awaitCompletion} executor'ı kapatır; kapanan bir havuz
 * tekrar {@link #start} edilemez. Yönergenin "önce unsafe, sonra safe" akışında çağıran
 * katman İKİ ayrı örnek yaratmalıdır (biri unsafe processor, biri safe processor ile).
 *
 * <p>Sorumlulukları:
 * <ul>
 *   <li><b>#6</b> — {@code workers} adet worker'ı {@link Executors#newFixedThreadPool} ile
 *       çalıştırır; her görev için yeni thread AÇMAZ. Özel {@link ThreadFactory} thread'lere
 *       {@code worker-1..N} anlamlı isimlerini verir (thread dump okunabilirliği, §14).</li>
 *   <li><b>Tamamlanma</b> — iki tamamlayıcı mekanizma: (1) poison pill:
 *       {@link #signalNoMoreTasks()} kuyruğa her worker için bir sentinel koyar; worker'lar bunu
 *       görünce çıkar. (2) {@link CountDownLatch}: her worker çıkarken latch'i azaltır, böylece
 *       {@link #getLatch()}.{@code await()} ile worker'ların bitişi shutdown'dan önce beklenebilir.</li>
 *   <li><b>#7</b> — {@link #awaitCompletion(long)} graceful shutdown: shutdown +
 *       awaitTermination, timeout'ta shutdownNow.</li>
 * </ul>
 *
 * <p>Görev tipinden bağımsızdır ({@code <T>}); domain modeline (ör. {@code queue.PriceUpdateTask})
 * bağlanmaz. Poison pill, çağıran katman tarafından tekil bir sentinel örnek olarak sağlanır ve
 * worker'lar onu referans kimliğiyle ({@code ==}) tanır.
 *
 * <p>Tipik kullanım:
 * <pre>{@code
 * PriceUpdateTask pill = new PriceUpdateTask(-1, CoinType.BTC, 0); // tekil sentinel
 * WorkerPool<PriceUpdateTask> pool = new WorkerPool<>(workers, pill);
 * pool.start(queue, processor);      // worker'lar kuyruğu tüketmeye başlar
 * producer.produceInto(queue);       // gerçek görevler kuyruğa
 * pool.signalNoMoreTasks();          // her worker için 1 poison pill
 * pool.getLatch().await();           // tüm worker'ların görevlerini tamamlamasını bekle
 * pool.awaitCompletion(30);          // executor'ı güvenle kapat
 * }</pre>
 *
 * @param <T> görev tipi
 */
@RequiredArgsConstructor
@Getter
@Slf4j
public final class WorkerPool<T> {


    private final int workers;
    private final ExecutorService executor;
    private final T poisonPill;
    private final CountDownLatch latch;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile BlockingQueue<T> queue;

    /**
     * @param workers    worker sayısı (1..16 aralığı çağıran katmanda doğrulanır)
     * @param poisonPill döngüyü sonlandıran tekil sentinel; hiçbir gerçek görevle referans
     *                   olarak aynı olmamalıdır
     */
    public WorkerPool(int workers, T poisonPill) {
        if (workers < 1) {
            throw new IllegalArgumentException("workers must be >= 1, was " + workers);
        }
        if (poisonPill == null) {
            throw new IllegalArgumentException("poisonPill must not be null");
        }
        this.workers = workers;
        this.poisonPill = poisonPill;
        this.executor = Executors.newFixedThreadPool(workers, new NamedWorkerThreadFactory());
        this.latch = new CountDownLatch(workers);
    }

    /** Poison pill sentinel'i — producer/servis kuyruğu bu referansla sonlandırmalıdır. */
    public T poisonPill() {
        return poisonPill;
    }

    /** Havuzdaki worker sayısı. */
    public int getWorkerCount() {
        return workers;
    }

    /**
     * Worker'ların tamamlanmasını beklemek için kullanılan CountDownLatch'i döndürür. Her worker
     * çıkarken latch'i bir azaltır; {@code getLatch().await()} tüm worker'lar bitince döner.
     *
     * @return WorkerPool tarafından yönetilen CountDownLatch nesnesi
     */


    /**
     * Worker'ları başlatır; her biri {@code queue}'yu tüketmeye başlar. Görevler bu çağrıdan
     * sonra kuyruğa eklenebilir (producer eşzamanlı üretebilir).
     *
     * @throws IllegalStateException havuz zaten başlatılmışsa (tek kullanımlıktır — yeni bir
     *                               koşu için yeni bir {@code WorkerPool} yaratın)
     */
    public void start(BlockingQueue<T> queue, TaskProcessor<T> processor) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "WorkerPool tek kullanımlıktır; zaten başlatılmış. Yeni koşu için yeni örnek yaratın.");
        }
        this.queue = queue;
        for (int i = 0; i < workers; i++) {
            // submit değil execute: dönen Future'ı kullanmıyoruz. submit ile kaçan bir istisna
            // Future'da saklanıp sessizce yutulurdu; execute ile thread'in
            // UncaughtExceptionHandler'ına düşüp görünür olur (fire-and-forget).
            executor.execute(new PriceWorker<>(queue, processor, poisonPill, latch));
        }
    }

    /**
     * Kuyruğa her worker için bir poison pill koyar. TÜM gerçek görevler kuyruğa eklendikten
     * sonra çağrılmalıdır; aksi hâlde bir worker gerçek görevlerden önce pill alıp erken çıkabilir.
     *
     * @throws IllegalStateException {@link #start} çağrılmadıysa
     */
    public void signalNoMoreTasks() {
        BlockingQueue<T> q = this.queue;
        if (q == null) {
            throw new IllegalStateException("start() must be called before signalNoMoreTasks()");
        }
        try {
            for (int i = 0; i < workers; i++) {
                q.put(poisonPill);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueuing poison pills", e);
        }
    }

    /**
     * Graceful shutdown (#7): yeni görev kabulünü durdurur ve mevcut worker'ların bitmesini
     * bekler. Süre içinde bitmezlerse zorla kapatır. Yalnızca {@code shutdown()} çağırmak
     * görevlerin bittiğini garanti etmez (yönerge §12).
     *
     * @param timeoutSeconds worker'ların normal bitişi için tanınan süre
     * @return worker'lar süre içinde temiz bittiyse {@code true}; zorla kapatıldıysa {@code false}
     */
    public boolean awaitCompletion(long timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Workers did not finish within {}s; forcing shutdownNow()", timeoutSeconds);
                executor.shutdownNow();
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Worker thread'lerine {@code worker-1}, {@code worker-2}, ... isimlerini veren fabrika.
     * Anlamlı isimler thread dump ve log okunabilirliği için gereklidir (§13-§14).
     */
    private static final class NamedWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "worker-" + counter.incrementAndGet());
            t.setDaemon(false);
            return t;
        }
    }
}

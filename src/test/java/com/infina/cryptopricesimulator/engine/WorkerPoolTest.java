package com.infina.cryptopricesimulator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Worker pool katmanının bağımsız kanıtı: sabit havuz tüm görevleri kaybetmeden işler,
 * thread'ler doğru isimlendirilir ve graceful shutdown zamanında döner.
 *
 * <p>Domain modelinden bağımsız olduğunu göstermek için basit bir {@link Task} kaydı kullanılır.
 * Thread-safe {@link AtomicLong} kullanılır çünkü burada test edilen şey işlemenin güvenliği
 * değil, hiçbir görevin atlanmadığıdır.
 */
class WorkerPoolTest {

    private static final int WORKERS = 4;
    /** Sınırlı kuyruk kapasitesi; worker'ların producer'a yetişmesini de test eder. */
    private static final int QUEUE_CAPACITY = 1_000;
    /**
     * Graceful shutdown için üst sınır (saniye). Bir sınır olarak seçildi; testler doğru
     * çalışıyorsa worker'lar poison pill'den hemen sonra biteceği için buna asla dayanmaz.
     * Yavaş CI ortamlarında yanlış-negatif olmaması için cömert tutuldu.
     */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final Pattern WORKER_NAME = Pattern.compile("worker-\\d+");
    /** Paylaşılan tekil poison pill; gerçek görevler {@code seq >= 1} olduğundan çakışmaz. */
    private static final Task POISON = new Task(-1);

    /** Test görevi (domain modelinden bağımsız). */
    private record Task(long seq) { }

    @Test
    @DisplayName("Tüm görevler tam olarak bir kez işlenir; hiç görev kaybı yok")
    void processesEveryTaskExactlyOnce() {
        int updates = 50_000;

        AtomicLong processed = new AtomicLong();
        TaskProcessor<Task> processor = task -> processed.incrementAndGet();

        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        WorkerPool<Task> pool = new WorkerPool<>(WORKERS, POISON);
        pool.start(queue, processor);

        produce(queue, updates);
        pool.signalNoMoreTasks();
        boolean clean = pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS);

        assertTrue(clean, "Worker'lar süre içinde temiz bitmeliydi (graceful shutdown)");
        assertEquals(updates, processed.get(), "İşlenen görev sayısı gönderilenle eşleşmeli");
    }

    @Test
    @DisplayName("Thread'ler worker-1..N olarak isimlendirilir; N worker'dan fazla thread yok")
    void namesThreadsWorker1ToN() {
        int updates = 5_000;

        Set<String> threadNames = ConcurrentHashMap.newKeySet();
        TaskProcessor<Task> processor = task -> threadNames.add(Thread.currentThread().getName());

        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        WorkerPool<Task> pool = new WorkerPool<>(WORKERS, POISON);
        pool.start(queue, processor);

        produce(queue, updates);
        pool.signalNoMoreTasks();
        pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS);

        assertTrue(threadNames.size() <= WORKERS,
                "Sabit havuz: en fazla " + WORKERS + " thread iş yapmalı, görülen: " + threadNames);
        for (String name : threadNames) {
            assertTrue(WORKER_NAME.matcher(name).matches(),
                    "Beklenen worker-N formatı, görülen: " + name);
        }
    }

    @Test
    @DisplayName("Boş kuyrukta sinyal sonrası graceful shutdown hızlıca döner")
    void gracefulShutdownReturnsPromptly() {
        WorkerPool<Task> pool = new WorkerPool<>(WORKERS, POISON);
        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        pool.start(queue, task -> { /* no-op */ });

        pool.signalNoMoreTasks();
        assertTrue(pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS),
                "Boş iş yükünde graceful shutdown hızlıca dönmeli");
    }

    @Test
    @DisplayName("Geçersiz worker sayısı IllegalArgumentException fırlatır")
    void rejectsInvalidWorkerCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerPool<>(0, POISON));
    }

    @Test
    @DisplayName("null poison pill IllegalArgumentException fırlatır")
    void rejectsNullPoisonPill() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkerPool<Task>(WORKERS, null));
    }

    @Test
    @DisplayName("start() öncesi signalNoMoreTasks() IllegalStateException fırlatır")
    void signalBeforeStartFails() {
        WorkerPool<Task> pool = new WorkerPool<>(WORKERS, POISON);
        assertThrows(IllegalStateException.class, pool::signalNoMoreTasks);
    }

    @Test
    @DisplayName("İkinci start() IllegalStateException fırlatır (tek kullanımlık)")
    void secondStartFails() {
        WorkerPool<Task> pool = new WorkerPool<>(WORKERS, POISON);
        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        pool.start(queue, task -> { });
        assertThrows(IllegalStateException.class,
                () -> pool.start(queue, task -> { }));
        pool.signalNoMoreTasks();
        pool.awaitCompletion(SHUTDOWN_TIMEOUT_SECONDS); // temizlik
    }

    /** {@code count} adet gerçek görev üretip kuyruğa koyar. */
    private static void produce(BlockingQueue<Task> queue, int count) {
        try {
            for (int i = 1; i <= count; i++) {
                queue.put(new Task(i));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while producing tasks", e);
        }
    }
}

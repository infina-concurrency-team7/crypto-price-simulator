package com.infina.cryptopricesimulator.engine;

/**
 * Worker katmanı ile domain katmanı arasındaki sözleşme (decoupling seam).
 *
 * <p>{@link PriceWorker} yalnızca "bir görevi kuyruktan al ve işlet" akışını bilir; görevin
 * <em>nasıl</em> işlendiği (coin state güncelleme + işlenen görev sayacı) bu arayüzü
 * implemente eden tarafın sorumluluğudur. Böylece worker pool, hangi görev tipiyle ve hangi
 * (safe/unsafe) stratejiyle çalıştığından bağımsız kalır: aynı worker'lar farklı bir
 * {@code TaskProcessor} ile hem güvenli hem güvensiz simülasyonu çalıştırabilir.
 *
 * <p><b>Thread-safety:</b> {@code process} birden çok worker thread'i tarafından eşzamanlı
 * çağrılır. Implementasyon kendi thread-safety'sinden sorumludur; worker herhangi bir kilit
 * tutmaz.
 *
 * @param <T> işlenen görev tipi (ör. {@code queue.PriceUpdateTask})
 */
@FunctionalInterface
public interface TaskProcessor<T> {

    /**
     * Tek bir görevi işler. Birden çok worker tarafından eşzamanlı çağrılabilir.
     *
     * @param task işlenecek görev (asla poison pill değildir; onu worker ayıklar)
     */
    void process(T task);
}

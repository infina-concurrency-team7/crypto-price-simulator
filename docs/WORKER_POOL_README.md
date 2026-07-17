# Worker Pool — README Katkıları

> Bu dosya, ana `README.md` dosyasına yapıştırılmaya hazır bölümleri içerir.
> İlgili başlıkların altına taşınabilir. Ham thread dump kanıtı:
> [`thread-dump-4workers.txt`](thread-dump-4workers.txt).

---

## Worker Katmanı Tasarımı

Worker katmanı `com.infina.cryptopricesimulator.engine` paketinde bulunur ve domain
modelinden bağımsız olacak şekilde generic `<T>` yapısıyla tasarlanmıştır.

- **`TaskProcessor<T>`** — worker katmanı ile domain katmanı arasındaki sözleşmedir.
  Worker, bir görevin nasıl işleneceğini bilmez. Coin state ve sayaç güncellemesi,
  bu arayüzü implemente eden simülasyon veya domain katmanı tarafından gerçekleştirilir.
  Aynı worker yapısı farklı `TaskProcessor` implementasyonlarıyla hem güvenli hem de
  güvensiz simülasyonlarda kullanılabilir.

- **`PriceWorker<T>`** — `BlockingQueue<T>` üzerinden görev tüketen `Runnable`
  implementasyonudur. Kuyruktan görev alır, `TaskProcessor` üzerinden işler ve poison
  pill gördüğünde çalışma döngüsünü sonlandırır.

- **`WorkerPool<T>`** — sabit boyutlu thread havuzunu, worker thread isimlendirmesini,
  poison pill tabanlı tamamlanma mekanizmasını ve graceful shutdown sürecini yönetir.

Çalışma zamanında `T`, `queue.PriceUpdateTask` tipiyle bağlanır. Görev modeli içerisinde
`entities.CoinType coin` alanı bulunur.

Poison pill, worker döngüsünü sonlandırmak için kullanılan tekil bir sentinel örnektir:

```java
new PriceUpdateTask(-1, CoinType.BTC, 0)
```

Worker, poison pill nesnesini referans kimliğiyle (`==`) kontrol eder. Böylece normal bir
görevin yanlışlıkla poison pill olarak değerlendirilmesi engellenir.

---

## Tasarım Kararları — Worker Pool

| Karar noktası | Kararımız | Neden? ve alternatif değerlendirmesi |
|---|---|---|
| Worker havuzu | `Executors.newFixedThreadPool(workers)` ve özel `ThreadFactory` | Sabit sayıda thread yeniden kullanılır; her görev için `new Thread()` açılmaz. 10.000 görev için 10.000 thread oluşturulması bellek maliyeti, aşırı context switching ve kontrolsüz kaynak tüketimi oluşturabilir. Sabit havuz worker sayısını `workers` parametresiyle sınırlar. |
| Thread isimlendirme | `worker-1..N` — özel `ThreadFactory` ve `AtomicInteger` | Varsayılan `pool-1-thread-3` gibi isimler yerine anlamlı isimler kullanılır. Böylece thread dump ve log çıktıları daha kolay okunur. |
| Worker–domain bağı | Generic `TaskProcessor<T>` arayüzü | Worker yalnızca kuyruk, thread ve durma döngüsünü bilir. Görev tipinden ve safe/unsafe stratejisinden bağımsız kalır. Model ve domain katmanlarına doğrudan bağlanmaz. |
| İşlerin tamamlanması | Her worker için bir poison pill | Producer tamamlandıktan sonra kuyruğa `workers` adet sentinel eklenir. FIFO kuyrukta gerçek görevlerden sonra sıralandıkları için worker'lar çıkmadan önce bütün gerçek görevler işlenir. `CountDownLatch` alternatifine göre worker döngüsüne daha doğal şekilde entegre olur. |
| Graceful shutdown | `shutdown()` → `awaitTermination(timeout)` → gerekirse `shutdownNow()` | Yalnızca `shutdown()` çağrılması yeni görev kabulünü durdurur; çalışan görevlerin tamamlandığını tek başına garanti etmez. `awaitTermination()` worker'ların bitmesini bekler. Timeout durumunda `shutdownNow()` çağrılır ve interrupt durumu korunur. |

---

## Neden Thread Pool Kullanıyoruz?

10.000 görev için 10.000 adet `new Thread()` oluşturmuyoruz.

### Thread oluşturma ve bellek maliyeti

Her thread'in kendine ait stack alanı ve işletim sistemi seviyesinde yönetim kaydı vardır.
Binlerce thread oluşturulması yüksek bellek tüketimine, thread oluşturma maliyetine ve
işletim sistemi kaynaklarının gereksiz kullanımına neden olabilir.

### Context switching maliyeti

Aktif thread sayısı işlemci çekirdeği sayısından çok fazla olduğunda CPU, asıl görevi
çalıştırmak yerine thread'ler arasında geçiş yapmak için fazla zaman harcayabilir.

Bir thread durdurulup başka bir thread çalıştırıldığında mevcut thread'in durumu kaydedilir,
yeni thread'in durumu yüklenir. Bu işleme context switch denir ve belirli bir işlem maliyeti
vardır.

### Kaynakların kontrol edilmesi

Fixed thread pool, aynı anda çalışabilecek thread sayısını `workers` parametresiyle sınırlar.
Thread'ler yeniden kullanılır ve sistemin kontrolsüz biçimde aşırı yüklenmesi engellenir.

Görev ve thread birbirinden farklı kavramlardır:

```text
Task   = yapılacak iş
Thread = görevi çalıştıran worker
```

Bu projede çok sayıdaki `PriceUpdateTask`, sınırlı sayıdaki worker thread tarafından sırayla
işlenir.

---

## Thread Dump İncelemesi

Worker pool, 4 worker ve `ArrayBlockingQueue` kullanılarak çalıştırılmıştır. Uygulama
çalışırken `jstack <pid>` komutuyla gerçek bir thread dump alınmıştır.

Thread dump işlemini tekrar etmek için `ThreadDumpDemo` sınıfı çalıştırılır, konsoldaki
PID değeri alınır ve aşağıdaki komut uygulanır:

```bash
jstack <pid>
```

Ham thread dump çıktısı:

[`thread-dump-4workers.txt`](thread-dump-4workers.txt)

Örnek thread dump kesiti:

```text
"worker-1" #25 prio=5 os_prio=31 ... waiting on condition
   java.lang.Thread.State: WAITING (parking)
    at jdk.internal.misc.Unsafe.park(...)
    - parking to wait for <0x...>
      (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
    at java.util.concurrent.ArrayBlockingQueue.take(ArrayBlockingQueue.java:421)
    at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:45)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:619)
    at java.lang.Thread.run(...)

"worker-2" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
"worker-3" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
"worker-4" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
```

### Worker sayısı

Thread dump çıktısında tam olarak dört worker görülmüştür:

```text
worker-1
worker-2
worker-3
worker-4
```

Bu sayı `workers=4` parametresiyle birebir uyumludur.

Görev sayısı binlerce olmasına rağmen her görev için ayrı thread oluşturulmamıştır.
Dump çıktısında 10.000 ayrı worker thread bulunmaması, sabit boyutlu bir thread pool
kullanıldığını doğrulamaktadır.

### Thread durumları

Dört worker da `WAITING (parking)` durumundadır ve
`ArrayBlockingQueue.take()` çağrısı üzerinde beklemektedir.

Bu durum, kuyruk boşken worker'ların sürekli kontrol yaparak CPU tüketmediğini gösterir.
`BlockingQueue.take()` metodu, yeni görev gelene kadar worker thread'i verimli şekilde
bekletir.

Producer kuyruğa yeni bir görev eklediğinde bekleyen worker'lardan biri uyanır ve görevi
işlemeye başlar.

İş yükü sırasında worker'lar `RUNNABLE`, kuyruk boşaldığında ise `WAITING` durumunda
görülebilir.

### Producer–Consumer yapısının doğrulanması

Stack trace içerisinde aşağıdaki satırın bulunması worker'ın gerçekten ayrı bir
`BlockingQueue` üzerinden görev beklediğini gösterir:

```text
java.util.concurrent.ArrayBlockingQueue.take(...)
```

Bu durum, worker'ların executor'un iç kuyruğuna doğrudan gönderilen fiyat güncelleme
görevlerini değil, uygulama tarafından oluşturulan Producer–Consumer kuyruğunu tükettiğini
doğrular.

### Thread pool kullanımının doğrulanması

Stack trace içerisinde aşağıdaki çağrının görülmesi worker'ların `ExecutorService`
tarafından yönetildiğini doğrular:

```text
ThreadPoolExecutor$Worker.run
```

Bu yapı, her görev için yeni bir thread oluşturulmadığını ve sabit sayıdaki worker
thread'in tekrar kullanıldığını gösterir.

### Lock çekişmesi ve deadlock

İncelenen thread dump çıktısında worker thread'ler `BLOCKED` durumunda değildir.

JVM tarafından aşağıdaki gibi bir deadlock raporu üretilmemiştir:

```text
Found one Java-level deadlock:
```

Bu nedenle worker katmanında deadlock gözlemlenmemiştir.

Worker katmanı kendi içinde coin kilidi tutmaz. Coin başına kullanılan kilitler güvenli
coin state implementasyonu tarafından yönetilir. Bu nedenle coin lock contention durumu
domain katmanında ayrıca gözlemlenebilir.

---

## Merge Conflict Deneyimi

### Conflict özeti

Merge conflict, `com.infina.cryptopricesimulator.engine` paketi altında bulunan
`WorkerPool<T>` sınıfının aşağıdaki metodunda meydana gelmiştir:

```java
awaitCompletion(long timeoutSeconds)
```

Her iki branch de sınıfın temel yapısını, multi-threading çalışma mantığını, graceful
shutdown akışını, Lombok anotasyonlarını ve güvenlik mekanizmalarını korumuştur.

Conflict'in nedeni, graceful shutdown süresi aşıldığında yazdırılan warning log mesajının
iki branch'te farklı biçimde yazılmasıdır.

### Branch'lerdeki farklılıklar

İlk branch'teki kod:

```java
if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
    log.warn(
        "Workers still running after {}s timeout; forcing shutdownNow()",
        timeoutSeconds
    );
    executor.shutdownNow();
    return false;
}
```

Diğer branch'teki kod:

```java
if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
    log.warn(
        "Workers did not finish within {}s; forcing shutdownNow()",
        timeoutSeconds
    );
    executor.shutdownNow();
    return false;
}
```

Her iki kod da işlevsel olarak aynı davranışı uygular:

1. Executor'ın belirlenen süre içinde kapanması beklenir.
2. Worker'lar süre içinde tamamlanmazsa warning log yazılır.
3. `shutdownNow()` çağrılarak worker thread'lere interrupt sinyali gönderilir.
4. Metot `false` döndürür.

### Conflict çözümü

Nihai sürümde aşağıdaki log mesajı korunmuştur:

```java
log.warn(
    "Workers did not finish within {}s; forcing shutdownNow()",
    timeoutSeconds
);
```

Bu mesaj, worker'ların kendilerine ayrılan süre içinde tamamlanamadığını daha açık ve
doğrudan ifade etmektedir. Bu nedenle logların okunabilirliği ve production ortamındaki
hata analizleri açısından daha uygun bulunmuştur.

### Korunan diğer yapılar

Conflict çözümü sırasında yalnızca log mesajı değerlendirilmemiş, dosyanın genel yapısı
da kontrol edilmiştir.

- `shutdown()` ve `awaitTermination()` tabanlı graceful shutdown akışı korunmuştur.
- Timeout durumunda `shutdownNow()` çağrılması korunmuştur.
- `InterruptedException` durumunda interrupt bilgisinin kaybolmaması sağlanmıştır.
- `@Getter`, `@RequiredArgsConstructor` ve `@Slf4j` Lombok anotasyonları korunmuştur.
- Logger oluşturmak için manuel `Logger` ve `LoggerFactory` kullanımı yerine proje
  genelindeki `@Slf4j` yaklaşımı devam ettirilmiştir.
- Kullanılmayan veya tekrarlanan importlar temizlenmiştir.
- Lombok tarafından üretilen erişim metotlarıyla ilgili mevcut Javadoc yapısı korunmuştur.

### Conflict çözüm adımları

Güncel ana branch, conflict'in çözüleceği feature branch'ine alınmıştır:

```bash
git switch feature/worker-pool
git fetch origin
git merge origin/main
```

Git tarafından oluşturulan conflict işaretleri incelenmiştir:

```text
<<<<<<< HEAD
İlk branch'teki log mesajı
=======
Diğer branch'teki log mesajı
>>>>>>> origin/main
```

İki tarafın değişiklikleri karşılaştırıldıktan sonra daha açıklayıcı log mesajı korunmuş,
conflict işaretleri kaldırılmış ve dosya yeniden derlenebilir hâle getirilmiştir.

Çözüm sonrasında:

```bash
git add src/main/java/com/infina/cryptopricesimulator/engine/WorkerPool.java
git commit -m "Resolve WorkerPool graceful shutdown conflict"
git push
```

### Conflict sonucu

Birleştirme sonucunda ortaya çıkan `WorkerPool.java` dosyası:

- Sözdizimi açısından doğrulanmıştır.
- Graceful shutdown davranışını korumaktadır.
- Worker pool'un multi-threading mantığını değiştirmemektedir.
- Timeout durumundaki log mesajını daha anlaşılır hâle getirmektedir.
- Gereksiz importlardan arındırılmıştır.
- Projedeki Lombok kullanım biçimiyle uyumludur.

Conflict çözümünde iki branch'ten birinin tamamı doğrudan seçilmemiştir. Her iki değişiklik
incelenmiş, işlevsel davranışı koruyan ve log okunabilirliğini artıran ortak bir nihai sürüm
oluşturulmuştur.

- **Conflict çıkan dosya:** `WorkerPool.java`
- **Conflict çıkan metot:** `awaitCompletion(long timeoutSeconds)`
- **Conflict konusu:** Graceful shutdown timeout warning mesajı
- **Çözüm yöntemi:** Manuel içerik karşılaştırması ve birleştirme
- **Korunan mesaj:** `Workers did not finish within {}s; forcing shutdownNow()`

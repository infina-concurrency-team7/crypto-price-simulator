# Worker Pool — README Katkıları (Kişi 2)

> Bu dosya, ana `README.md`'ye **yapıştırılmaya hazır** bölümleri içerir. Kişi 6 README'yi
> birleştirirken ilgili başlıkların altına taşıyabilir. Ham thread dump kanıtı:
> [`thread-dump-4workers.txt`](thread-dump-4workers.txt).

---

## Worker katmanı tasarımı (özet)

Worker katmanı `com.infina.cryptopricesimulator.engine` paketindedir ve **domain modelinden
bağımsızdır** (generic `<T>`):

- **`TaskProcessor<T>`** — worker ile domain arasındaki sözleşme (decoupling seam). Worker "task
  işlenince ne olur"u bilmez; coin state + sayaç güncellemesini bu arayüzü implemente eden
  (Kişi 3/6) sağlar. Aynı worker'lar farklı bir `TaskProcessor` ile hem güvenli hem güvensiz
  simülasyonu çalıştırabilir.
- **`PriceWorker<T>`** — `BlockingQueue<T>`'den görev tüketen `Runnable`; poison pill görene
  kadar döner.
- **`WorkerPool<T>`** — sabit thread havuzunu (`newFixedThreadPool`), thread isimlendirmeyi
  (`worker-1..N`), poison-pill tamamlanmasını ve graceful shutdown'ı yönetir.

Çalışma zamanında `T`, Kişi 1'in `queue.PriceUpdateTask` (`entities.CoinType coin` ile) tipiyle
bağlanır; poison pill tekil bir sentinel örnektir (ör. `new PriceUpdateTask(-1, CoinType.BTC, 0)`).

## Tasarım Kararları (worker-pool ile ilgili satırlar)

| Karar noktası | Kararımız | Neden? (+alternatif) |
|---|---|---|
| Worker havuzu | `Executors.newFixedThreadPool(workers)` + özel `ThreadFactory` | Sabit sayıda thread yeniden kullanılır; her görev için `new Thread()` açılmaz. 10.000 görev için 10.000 thread → bellek maliyeti, aşırı context switch, kontrolsüz kaynak tüketimi. Sabit havuz worker sayısını `workers` parametresiyle sınırlar. |
| Thread isimlendirme | `worker-1..N` (custom `ThreadFactory`, `AtomicInteger`) | Varsayılan `pool-1-thread-3` yerine anlamlı isim; thread dump ve loglar okunabilir olur (§14). |
| Worker–domain bağı | Generic `TaskProcessor<T>` arayüzü | Worker yalnızca kuyruk+thread+durma döngüsünü bilir; görev tipinden ve safe/unsafe stratejisinden bağımsız kalır. Model layer'a (Kişi 1/6) bağlanmaz. |
| İşlerin tamamlanması | Poison pill (her worker için 1 sentinel) | Producer bitince kuyruğa `workers` adet sentinel konur; FIFO'da gerçek görevlerden sonra sıralandığı için worker çıkmadan tüm gerçek görevler işlenir. `CountDownLatch` alternatifine göre worker döngüsüne daha doğal; ayrıca latch bunu sarabilir. |
| Graceful shutdown | `shutdown()` → `awaitTermination(timeout)` → gerekirse `shutdownNow()` | Yalnızca `shutdown()` yeni görev kabulünü durdurur ama mevcut görevlerin bittiğini garanti etmez. `awaitTermination` bitişi bekler; timeout'ta `shutdownNow()` + interrupt flag geri konur (§12). |

## Neden Thread Pool? (yönerge §4 cevabı)

10.000 görev için 10.000 `new Thread()` açmıyoruz çünkü:

- **Oluşturma maliyeti + bellek:** Her thread'in kendi stack'i (≈ MB'lar) ve OS-seviyesi kaydı
  vardır; binlerce thread bellek ve kurulum maliyetiyle sistemi boğar.
- **Context switching:** CPU çekirdeği sayısından çok daha fazla thread olduğunda, işlemci asıl
  işi yapmak yerine thread'ler arası geçişle zaman harcar.
- **Kontrol:** Sabit havuz worker sayısını sınırlar, thread'leri yeniden kullanır ve sistemin
  aşırı yüklenmesini engeller. Görevler (task) ile onları çalıştıran worker'lar (thread) ayrıdır:
  az sayıda worker, çok sayıda görevi sırayla tüketir.

---

## Thread Dump İncelemesi

Simülasyon çalışırken (4 worker, `ArrayBlockingQueue`) `jstack <pid>` ile alınan gerçek kesit.
Tekrar üretmek için: `ThreadDumpDemo`'yu çalıştır, konsoldaki `PID`'i al, `jstack <pid>`.

```
"worker-1" #25 prio=5 os_prio=31 ... waiting on condition
   java.lang.Thread.State: WAITING (parking)
	at jdk.internal.misc.Unsafe.park(...)
	- parking to wait for <0x...> (a ...AbstractQueuedSynchronizer$ConditionObject)
	at java.util.concurrent.ArrayBlockingQueue.take(ArrayBlockingQueue.java:421)
	at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:45)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:619)
	at java.lang.Thread.run(...)
"worker-2" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
"worker-3" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
"worker-4" ... WAITING (parking) ... at ArrayBlockingQueue.take(...)
```

**Yorum:**

- **Kaç worker var?** Tam olarak **4** thread (`worker-1 … worker-4`) — `workers=4` parametresiyle
  birebir uyumlu. Dump'ta **10.000 thread YOK**; bu, sabit boyutlu bir havuz kullandığımızın
  doğrudan kanıtıdır.
- **Hangi state'teler?** Dört worker da **WAITING (parking)**, `ArrayBlockingQueue.take()`
  içinde. Yani kuyruk boşken worker'lar CPU harcamadan yeni görev bekliyor — Producer–Consumer
  yapısının ve `BlockingQueue`'nun gerçekten kullanıldığının kanıtı. Stack'in en altında
  `ThreadPoolExecutor$Worker.run` görünmesi, worker'ların bizim havuzumuz tarafından
  çalıştırıldığını gösterir. (İş yükü sürekli akarken worker'lar `RUNNABLE`, kuyruk boşaldığında
  `take()` üzerinde `WAITING` olur.)
- **Lock çekişmesi / deadlock var mı?** **Hayır.** Hiçbir worker `BLOCKED` değil ve JVM
  "Found one Java-level deadlock" raporu üretmedi. Worker katmanı kilit tutmaz; coin başına
  kilit çekişmesi domain katmanında (safe coin state) gözlemlenir.

---

## Merge Conflict Deneyimi (taslak — #37/#38 sonrası doldurulacak)

- **Branch isimleri:** `feature/task-queue` (Kişi 1) ↔ `feature/worker-pool` (Kişi 2)
- **Conflict çıkması beklenen dosya:** paylaşılan `README.md` (aynı bölüm) gibi ortak bir dosya.
  Not: worker katmanı generic olduğu için model dosyalarıyla (Kişi 1'in `queue.PriceUpdateTask`'ı)
  artık çakışmıyor.
- **Çözüm adımları:** Kişi 1 #37 ile bilinçli conflict oluşturur → Kişi 2 #38 ile çözer:
  `git merge` → çakışan `<<<<<<< / ======= / >>>>>>>` bölümleri IntelliJ merge tool ile
  birleştirilir → `git add` + commit → PR tamamlanır.
- Bu bölüm gerçek conflict çözüldükten sonra kesin bilgilerle (commit/PR linki, korunan içerik)
  doldurulacak.

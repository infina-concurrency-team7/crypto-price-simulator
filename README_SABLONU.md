# Eşzamanlı Kripto Fiyat Simülatörü

> Bu bir **şablondur**. `<...>` yerlerini doldurun, açıklama satırlarını (bu blok dahil) silin.
> Yönergedeki başlıkların tümü bulunmalıdır. En kritik bölümler:
> **Tasarım Kararları**, **Race Condition Gözlemi**, **Invariant**, **Thread Dump İncelemesi**.

## Proje Hakkında

<2-3 cümle. Bellek içinde çalışan sade bir uygulama; N görev bir kuyruğa düşer, sabit sayıda
worker işler. Ortak sayaç ve coin state üzerinde race condition gösterilir ve güvenle çözülür.>

## Kullanılan Teknolojiler

Java 17+ (bonus için 21), Spring Boot, Maven, Git/GitHub, Swagger/OpenAPI, JUnit 5.

## Uygulamayı Çalıştırma

1. `git clone <repo-linki>`
2. IntelliJ ile açın (Open → `pom.xml`).
3. `PriceSimApplication` çalıştırın (Shift+F10) **veya** `mvn spring-boot:run`
4. `http://localhost:8080` üzerinde ayağa kalkar.

## Swagger Adresi

- http://localhost:8080/swagger-ui/index.html
- Endpoint'ler Swagger üzerinden test edilebilir.

## Endpoint'ler

| Endpoint | Ne yapar? |
|---|---|
| `POST /simulate?updates=10000&workers=4&seed=42` | Görevleri üretir, kuyruğa koyar, havuzla işler, biter. 409: aynı anda ikinci istek. 400: geçersiz parametre. |
| `GET /coins` | Son simülasyondaki güvenli coin durumları. |
| `GET /stats` | Son simülasyon sonucu (beklenen/güvensiz/güvenli, süre, throughput, invariant). 404: henüz simülasyon yok. |

## Mimari Akış

```
Task Producer → BlockingQueue<PriceUpdateTask> → Sabit Worker Pool (N) → Coin State + Sayaçlar
```

<Hangi sınıf ne yapıyor, 2-3 cümle.>

## ⭐ Tasarım Kararları

> Yönergedeki 9 karar noktasının her biri için aracınızı ve **kısa "neden"ini** yazın.

| Karar noktası | Kararımız | Neden? (+alternatif karşılaştırması) |
|---|---|---|
| Görev kuyruğu | <örn. ArrayBlockingQueue(10000)> | <neden sınırlı/kapasite> |
| Worker havuzu | <örn. FixedThreadPool(workers)> | <neden yeni thread yok> |
| Güvenli sayaç | <örn. AtomicLong> | <neden yeterli> |
| Coin kilidi | <synchronized / ReentrantLock / CAS> | <neden> |
| Lock kapsamı | <coin başına / global> | <neden> |
| İşlerin tamamlanması | <CountDownLatch / Future / poison pill> | <neden> |
| Graceful shutdown | <shutdown + awaitTermination> | <timeout durumunda ne yapılıyor> |
| Sonucun paylaşılması | <volatile / AtomicReference / immutable snapshot> | <controller'a nasıl güvenli sunuluyor> |
| İkinci simülasyon isteği | <AtomicBoolean> | <finally ile serbest bırakma> |

## Race Condition Gözlemi

<İki race noktasını açıklayın: (1) sayaç `count++` oku-artır-yaz; (2) coin state çok alanlı
tutarsızlık. Neden güncelleme kayboluyor?>

```
BTC   beklenen: 61.240 | güvenli: 61.240 ✓ | güvensiz: 60.890 ✗
Sayaç beklenen: 10.000 | güvenli: 10.000 ✓ | güvensiz: 9.784  ✗
```

> Gözlem: <kaç çalıştırmanın kaçında güvensiz bozuldu? worker/görev sayısını artırınca ne oldu?>

## Güvenli Çözüm

<Coin state'i neyle koruduğunuzu ve neden tek başına AtomicLong'un yetmediğini açıklayın.>

```java
lock.lock();
try {
    currentPrice += delta;
    updateCount++;
    lastDelta = delta;
    lastUpdatedBy = Thread.currentThread().getName();
} finally {
    lock.unlock();
}
```

## Invariant ve Doğruluk Kanıtı

```
safePrice       == initialPrice + sum(all deltas)   ->  <geçti/geçmedi>
safeUpdateCount == o coin için üretilen görev sayısı ->  <geçti/geçmedi>
```

## Performans Sonuçları

| Updates | Workers | Süre | Throughput | Invariant |
|---:|---:|---:|---:|---|
| 50.000 | 1 | <...> | <...> | Başarılı |
| 50.000 | 2 | <...> | <...> | Başarılı |
| 50.000 | 4 | <...> | <...> | Başarılı |
| 50.000 | 8 | <...> | <...> | Başarılı |

<Yorum: worker artınca ne oldu? Bir noktadan sonra neden hızlanmadı (lock contention / context switch)?>

## ReentrantLock ve synchronized Karşılaştırması

<Nerede hangisini kullandınız? ReentrantLock'un sağladığı ekstralar (tryLock, adalet, kesintiye
uğrayabilir kilitleme) sizin için gerekli miydi? Global lock vs coin başına lock farkı.>

## Thread Dump İncelemesi

<Simülasyon çalışırken bir thread dump alın (IntelliJ "Capture Thread Dump" / jstack / jcmd).
Kısa bir kesit yapıştırın ve yorumlayın.>

```
"worker-1" ... RUNNABLE ...
"worker-2" ... WAITING (parking) ... at ...BlockingQueue.take(...)
...
```

- **Kaç worker var?** <workers parametresiyle uyumlu mu — 10.000 thread YOK>
- **Hangi state'teler?** <boş worker'lar take() üzerinde WAITING mi>
- **Lock çekişmesi/deadlock var mı?** <BLOCKED thread'ler / "Found one Java-level deadlock" var mı>

## Merge Conflict Deneyimi

- **Branch isimleri:** <...>
- **Conflict çıkan dosya / bölüm:** <...>
- **İki branch'in farklı değişikliği:** <...>
- **Hangi içerik korundu:** <...>
- **IntelliJ mi terminal mi:** <...>
- **Çözüm commit / PR linki:** <...>
- **Ne öğrendik:** <...>

## Testler

<Hangi testler var? Nasıl çalıştırılır (`mvn test`)? Kısa liste.>

- Seed tekrarlanabilirlik testi
- Beklenen fiyat hesabı testi
- Güvenli sayaç testi
- Coin invariant testi
- Parametre validation (HTTP 400) testi
- Controller integration testi

## Grup Üyeleri ve Katkıları

| Üye | Sorumluluk | Branch | Pull Request | Review |
|---|---|---|---|---|
| <Ad> | Coin & State | `feature/coin-state` | PR #1 | PR #3 |
| <Ad> | Worker Pool | `feature/task-queue` | PR #2 | PR #1 |
| <Ad> | Invariant | `feature/stats` | PR #3 | PR #4 |
| <Ad> | API | `feature/api` | PR #4 | PR #5 |
| <Ad> | Test & Metrik | `feature/tests` | PR #5 | PR #2 |

## Bonus Çalışmalar

<Yaptıysanız: Java 21 Virtual Threads karşılaştırması / CompletableFuture / Deadlock + lock
ordering. Neyi nasıl yaptığınızı ve sonucu kısaca anlatın. Yapmadıysanız "Yok" yazın.>

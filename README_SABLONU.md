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

| Updates | Workers | Süre (ms) | Throughput (ops/s) | Invariant |
|---:|---:|---:|---:|---|
| 50.000 | 1 | 84 | 595.238 | Başarılı |
| 50.000 | 2 | 138 | 362.318 | Başarılı |
| 50.000 | 4 | 142 | 352.112 | Başarılı |
| 50.000 | 8 | 189 | 264.550 | Başarılı |

**Yorum:**

- Worker sayısı arttıkça performans beklenenin aksine iyileşmemiş, belirli bir noktadan sonra düşmüştür.
- Bu senaryoda tüm worker'lar aynı `ReentrantLock` üzerinden senkronizasyon sağladığı için **lock contention** oluşmuş ve thread'ler kilidi almak için beklemek zorunda kalmıştır.
- Worker sayısının artması **context switching** maliyetini de artırmış ve ek performans yükü oluşturmuştur.
- Her görevin yalnızca tek bir `applyDelta()` işlemi içermesi nedeniyle, lock alma ve bırakma maliyeti yapılan işten daha baskın hale gelmiştir.
- Sonuç olarak, bu deney daha fazla thread kullanmanın her zaman daha yüksek performans sağlamadığını göstermektedir.
- Özellikle ince taneli kilitleme (fine-grained locking) ve küçük iş birimlerinde ek thread'ler performansı artırmak yerine düşürebilir.

## ReentrantLock ve synchronized Karşılaştırması

Java'da paylaşılan mutable state üzerinde thread güvenliği sağlamak için
`synchronized` veya `ReentrantLock` kullanılabilir.

### synchronized

`synchronized`, JVM tarafından sağlanan dahili bir kilitleme mekanizmasıdır.
Bir metoda veya kod bloğuna aynı anda yalnızca bir thread'in erişmesine izin verir.

Avantajları:
- Kullanımı kolaydır.
- Lock yönetimi JVM tarafından otomatik gerçekleştirilir.

Dezavantajları:
- Lock yönetimi üzerinde daha az kontrol sağlar.
- `tryLock()` veya fair locking gibi gelişmiş özellikleri desteklemez.


### ReentrantLock

`ReentrantLock`, Java Concurrency API içerisinde bulunan daha gelişmiş bir
kilitleme mekanizmasıdır.

Avantajları:
- Manuel lock kontrolü sağlar.
- `tryLock()` ile lock alınabilirliği beklemeden kontrol edilebilir.
- Fair locking gibi ek özellikler sunar.
- Daha esnek concurrency yönetimi sağlar.

Dezavantajları:
- `lock()` ve `unlock()` yönetimi geliştirici sorumluluğundadır.
- `unlock()` unutulması durumunda kilitlenme problemleri oluşabilir.


## Project Usage

Bu projede `CoinState` üzerinde birden fazla worker thread tarafından yapılan
fiyat güncellemelerinde thread güvenliği sağlamak amacıyla `ReentrantLock`
kullanılmıştır.

`SafeCoinState` sınıfında:

- `applyDelta()` metodu
- `snapshot()` metodu

lock ile korunmaktadır.

Böylece aynı coin state üzerinde eşzamanlı güncellemeler sırasında oluşabilecek
race condition problemleri engellenir.

Bu projede `synchronized` yerine `ReentrantLock` tercih edilmiştir çünkü lock
yönetimi üzerinde daha fazla kontrol sağlamak hedeflenmiştir.

Sayaç işlemlerinde ise farklı bir thread-safe yaklaşım olarak `AtomicLong`
kullanılmıştır. `SafeCounter` sınıfında atomic operasyonlar sayesinde ek bir
lock mekanizmasına ihtiyaç duyulmadan güvenli sayaç yönetimi sağlanmıştır.

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

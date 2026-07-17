# Eşzamanlı Kripto Fiyat Simülatörü


## Proje Hakkında
Bu proje, Java 17 ve Spring Boot kullanılarak geliştirilmiş, bellek içinde çalışan (in-memory) eşzamanlı bir kripto fiyat simülasyon uygulamasıdır. Sistem, çoklu iş parçacığı (multi-threading) ortamında paylaşılan veriler üzerinde oluşabilecek **yarış durumlarını (race condition)** ve **kayıp güncellemeleri (lost update)** gözlemlemeyi hedefler. Üretilen N adet fiyat güncelleme görevi thread-safe bir kuyrukta toplanır, sabit sayıdaki worker thread tarafından işlenir ve kilit mekanizmalarıyla (`ReentrantLock`, `AtomicLong`) güvenli hale getirilerek sonucun doğruluğu matematiksel olarak kanıtlanır.

## Kullanılan Teknolojiler
- **Dil & Framework:** Java 17, Spring Boot 3.5.x
- **Concurrency API:** `ExecutorService`, `LinkedBlockingQueue`, `ReentrantLock`, `AtomicLong`, `CountDownLatch`, `volatile`
- **Araçlar & Dokümantasyon:** Maven, Git/GitHub, Lombok, Slf4j, Swagger/OpenAPI (Springdoc)
- **Test:** JUnit 5 (Jupiter), Spring Boot Test

# Uygulamayı Çalıştırma

### Ön Gereksinimler
* **Java SDK:** OpenJDK 17 veya üzeri
* **Derleme Aracı:** Maven 3.6+

### Uygulamanın Başlatılması

Projenin yerel ortamınızda ayağa kaldırılması ve test edilmesi için aşağıdaki adımları sırasıyla takip edebilirsiniz:

```bash
# 1. Projeyi GitHub üzerinden klonlayın
git clone [https://github.com/infina-concurrency-team7/crypto-price-simulator.git](https://github.com/infina-concurrency-team7/crypto-price-simulator.git)

# 2. Projenin kök dizinine geçiş yapın
cd crypto-price-simulator

# 3. Maven araç zincirini kullanarak Spring Boot uygulamasını ayağa kaldırın
mvn spring-boot:run
```

---

# Endpoint'ler

Sistem üzerindeki tüm simülasyon süreçleri, metrik takipleri ve bellek durumları (In-Memory State) REST API endpoint'leri üzerinden yönetilmektedir. `SimulationService` içindeki `AtomicReference` yapısı sayesinde en son tamamlanan simülasyon sonuçları bellekte güvenli bir şekilde saklanır.

### API Kullanım Tablosu

| HTTP Metodu | Endpoint | Parametreler | Başarılı Dönüş (HTTP 200) | Açıklama |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/simulate` | `updates` (int)<br>`workers` (int)<br>`seed` (long) | `SimulationResultResponse` (JSON) | Simülasyonu başlatır. Aynı görev listesiyle beklenen, güvensiz ve güvenli motorları uçtan uca koşturur. |
| **GET** | `/api/v1/stats` | Yok | `SimulationResultResponse` (JSON) | En son başarıyla tamamlanan simülasyonun özet, throughput, süre ve invariant doğruluk metriklerini döner. |
| **GET** | `/api/v1/coins` | Yok | `List<SafeCoinResponse>` (JSON) | Son tamamlanan simülasyon sonrasındaki güncel, güvenli ve tutarlı coin fiyat listesini döner. |

### API Güvenlik ve Doğrulama (Validation) Kuralları

* **Parametre Sınır Kontrolleri (HTTP 400 Bad Request):** Giriş parametreleri `SimulationApi` arayüzü üzerinde Spring Validation (`@Min` / `@Max`) altyapısı ile denetlenir. `updates` parametresi **1 ile 100.000** arasında, `workers` parametresi ise **1 ile 16** arasında olmak zorundadır. Sınır dışı istekler doğrudan engellenir.
* **Eşzamanlılık Koruması (HTTP 409 Conflict):** Sistem aynı anda yalnızca tek bir simülasyonun çalışmasına izin verir. Bir simülasyon süreci aktifken gelen ikinci bir istek, `AtomicBoolean` tabanlı `compareAndSet` kontrolü sayesinde engellenir ve `SimulationAlreadyRunningException` fırlatılır.
* **Bulunamadı Hatası (HTTP 404 Not Found):** Sistem ayağa kalktıktan sonra henüz hiçbir simülasyon çalıştırılmadan `/api/v1/stats` veya `/api/v1/coins` endpoint'leri tetiklenirse sistem `SimulationNotFoundException` fırlatır.

---

# Swagger Adresi

Uygulama çalışırken API uç noktalarını tarayıcı üzerinden interaktif olarak test etmek, veri şemalarını incelemek ve istekler simüle etmek için Springdoc OpenAPI (Swagger UI) entegrasyonu projeye dahil edilmiştir.

* **Swagger UI Adresi:** [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **Açıklama:** Projede `SimulationApi` interface yapısı kullanılarak controller katmanı temiz tutulmuş ve Swagger dökümantasyonu bu arayüz üzerinden soyutlanmıştır. Başarılı/hatalı HTTP durum kodları (200, 400, 404, 409) ve örnek şemalar Swagger üzerinde dökümante edilmiştir.

---


##  Mimari Akış ve Kuyruk Seçimi Tasarım Kararları

Bu projede, üretilen yoğun fiyat güncelleme görevlerini sınırlı sistem kaynaklarıyla (CPU/Thread) ezmeden, güvenli ve performanslı bir şekilde işleyebilmek amacıyla **Producer-Consumer (Üretici-Tüketici)** tasarım deseni tercih edilmiştir.

Aşağıda sistemin çalışma mimarisi, veri akışı ve bu akışta kullanılan kuyruk (Queue) yapısının teknik seçim gerekçeleri detaylandırılmıştır.

---

##  Mimari Akış Şeması

Sistemimiz üç temel katmandan oluşmaktadır: **Üretim (Producer)**, **Ara Bellek / Tampon (Kuyruk)** ve **Tüketim (Consumer/Worker)**.



### Veri ve İşlem Akışı Adımları:

1. **Statik Görev Hazırlığı:** Uygulama veya test çalışmaya başlamadan önce, deterministik (tekrarlanabilir) test yeteneğini korumak adına `TaskProducer.createStaticTasks(count, seed)` metodu ile belirtilen `seed` değerine göre immutable `PriceUpdateTask` listesi bellekte bir kez oluşturulur.
2. **Kuyruğa Besleme (Ingestion):** `TaskProducer` (Runnable), bu statik listeyi sırayla döngüye alarak paylaşılan, kapasitesi sınırlandırılmış (Bounded) `BlockingQueue` içerisine yazar (`put` metodu ile).
3. **Eşzamanlı İşleme (Concurrent Processing):** Arka planda çalışan $N$ adet `Worker` thread'i (Tüketiciler), kuyruktan sürekli olarak görev çeker (`take` metodu ile).
4. **Fiyat Güncellemesi:** Çekilen her bir görev, ilgili coinin durumunu günceller. Güvenli (Safe) modda `ReentrantLock` veya `synchronized` gibi concurrency mekanizmaları devreye girerken, güvensiz (Unsafe) modda race condition'a izin verilir.
5. **Sonlandırma (Poison Pill & Graceful Shutdown):** Tüm görevler kuyruğa eklendikten sonra, üretici kuyruğa bir sonlandırma işareti (**Poison Pill**) bırakır. Bu özel görevi (`sequence = -1`) alan worker'lar çalışmayı güvenli bir şekilde sonlandırır.

---

##  Kuyruk (Queue) Seçimi Karar Analizi

Grup çalışması kapsamında eşzamanlı veri aktarımını sağlamak için Java'nın `java.util.concurrent` paketindeki yapılar incelenmiş ve **`LinkedBlockingQueue`** üzerinde karar kılınmıştır. Karar alma sürecinde değerlendirilen alternatifler ve teknik gerekçeler şu şekildedir:

### Karşılaştırma Tablosu

| Özellik | `ArrayBlockingQueue` | `LinkedBlockingQueue` | `ConcurrentLinkedQueue` |
| :--- | :--- | :--- | :--- |
| **Kapasite Sınırı** | Zorunlu (Bounded) | İsteğe Bağlı (Bounded/Unbounded) | Sınırsız (Unbounded) |
| **Kilitleme Modeli** | **Tek Lock (Single-Lock):** Hem `put` hem `take` aynı lock'ı yarışarak kullanır. | **Çift Lock (Two-Queue Lock):** `put` ve `take` işlemleri farklı kilitler kullanır. | Kilit barındırmaz (Lock-free / CAS tabanlı). |
| **Backpressure** | Var (Kuyruk dolduğunda üreticiyi bloklar). | Var (Kapasite sınırı verildiğinde bloklar). | Yok (Hafıza dolana kadar büyür, OOM riski). |
| **Performans** | Düşük/Orta (Kilit çekişmesi yüksektir). | **Yüksek** (Okuma ve yazma thread'leri birbirini bloklamaz). | Çok Yüksek (Bloklama istenmeyen senaryolar için). |

### Neden `LinkedBlockingQueue` Seçildi?

* **İki Kilitli Yapı (Two-Lock Queue Algorithm):** `LinkedBlockingQueue`, baş (head) ve kuyruk (tail) düğümleri için ayrı kilitler (`ReentrantLock putLock` ve `ReentrantLock takeLock`) kullanır. Bu sayede, üreticinin kuyruğa yeni bir görev eklemesiyle (`put`), tüketicinin kuyruktan görev çekmesi (`take`) **aynı anda** gerçekleşebilir. Bu durum yüksek eşzamanlılıkta kilit çekişmesini (lock contention) minimize eder.

* **Backpressure (Geri Basınç) Desteği:** Sınırsız bir kuyruk (`ConcurrentLinkedQueue` gibi) kullanılsaydı, üretici milyonlarca görevi saniyeler içinde belleğe yığıp JVM'in *OutOfMemoryError* (OOM) almasına sebep olabilirdi. `LinkedBlockingQueue(capacity)` şeklinde sınırlandırılmış (bounded) bir kapasite belirlenerek, kuyruk dolduğunda üretici thread'in `put()` çağrısında doğal olarak bloklanması ve worker'ların kuyruğu boşaltmasını beklemesi sağlanmıştır.

* **Thread-Safe ve Bloklama Desteği:** `take()` metodu, kuyruk boş olduğunda worker thread'lerini CPU harcamadan (`waiting` durumunda) bekletir. Yeni eleman eklendiğinde ise thread'ler anında sinyallenerek uyandırılır.

---

##  Tasarımın İnvariant (Doğruluk) Temeli

* **Determinizm:** `ExpectedResultCalculator` sınıfı, simülasyon başlamadan önce statik üretilen immutable listeyi tek bir thread üzerinde sırayla işleyerek kayıpsız ve kesin sonuçları (beklenen son fiyat ve güncelleme sayıları) hesaplar.
* **Doğrulama:** Simülasyon bittiğinde, çoklu thread ortamında güncellenen nihai coin durumları ile bu referans hesaplama karşılaştırılarak veri kaybı (data loss) veya yarış durumu (race condition) sapmaları matematiksel olarak ispatlanır.

## ⭐ Tasarım Kararları

| Karar Noktası | Kararımız | Neden? (+ Alternatif Karşılaştırması) |
| --- | --- | --- |
| **Görev Kuyruğu** | `LinkedBlockingQueue(1000)` | Bellek yığılmasını (Memory Leak/OOM) önlemek için kapasite sınırı (Bounded) zorunluluğu olan `LinkedBlockingQueue` tercih edilerek sabit ve kontrollü bellek kullanımı hedeflendi. |
| **Worker Havuzu** | `Executors.newFixedThreadPool(workers)` | N adet görev için N adet yeni thread açmak ciddi context-switch ve bellek maliyeti yaratacağından, sabit sayıda thread yeniden kullanıldı (Thread Reuse). |
| **Güvenli Sayaç** | `AtomicLong` | Tek değişkenli basit artırma işlemleri (`count++`) için kilit (lock) maliyetine girmeden donanım seviyesinde atomiklik sağlandığı için tercih edildi. |
| **Coin Kilidi** | `ReentrantLock` | Fiyat, güncelleme sayısı, son delta ve güncelleyen thread bilgisi (4 alan) **birlikte ve tutarlı** değişmeliydi. Bu kritik bölümü korumak ve `finally` bloğunda güvenle kilidi bırakmak için seçildi. |
| **Lock Kapsamı** | Coin Başına Lock (Fine-Grained) | Tek bir global lock kullanılsaydı BTC güncellenirken ETH ve SOL boş yere beklerdi. Coin başına ayrı kilit kullanılarak darboğaz engellendi ve eşzamanlılık artırıldı. |
| **İşlerin Tamamlanması** | Poison Pill (-1) + `CountDownLatch.await()` | Worker'ların döngü kapanışını bildirmesi için kuyruğa Poison Pill gönderildi. Süre (elapsedMs) kesiminin kusursuz ölçülebilmesi için ise, ana thread `CountDownLatch` ile tüm worker'ların bitişini bekledi. |
| **Graceful Shutdown** | `shutdown()` + `awaitTermination()` | İşlem süreleri sayıldıktan sonra Executor üzerinde son temizlik ve worker tasfiyesi (kilit beklemeleri vs.) executor kapatma metoduyla güvenceye alındı. |
| **Sonucun Paylaşılması** | DTO (`CoinStatResponse`) | İçerideki mutable state doğrudan dışarı açılmadı. Simülasyon biter bitmez içerik güvenli veriler olan DTO listelerine (`CoinStatResponse` & `SafeCoinResponse`) dönüştürülerek Controller'a thread-safe sunuldu. |
| **İkinci Simülasyon İsteği** | `AtomicBoolean` (`compareAndSet`) | Aynı anda atılan 2. isteğin state'leri bozmasını engellemek (HTTP 409 dönmek) için CAS (Compare-And-Swap) mantığı kullanıldı. İşlem bittiğinde `finally` bloğunda bayrak serbest bırakıldı. |

## Race Condition Gözlemi

Bu simülatörde iki race condition noktası bulunur:

1. **Sayaç (`count++`):** Oku-artır-yaz (read-modify-write) işlemi atomik değildir.
   İki thread aynı anda `count=100` okur, ikisi de `101` yazar → bir güncelleme kaybolur.

2. **Coin state (çok alanlı tutarsızlık):** `currentPrice`, `updateCount`, `lastDelta` ve
   `lastUpdatedBy` alanları birlikte güncellenmesi gereken bir bütündür. Lock olmadan
   bir thread fiyatı günceller ama sayacı artıramadan başka thread araya girer →
   alanlar birbirleriyle tutarsız kalır.

```
Çalıştırma 1 (seed=42, workers=4, updates=50.000):
BTC   beklenen: 59.387 | güvenli: 59.387 ✓ | güvensiz: 59.370 ✗ (17 sapma)
ETH   beklenen: -3.122 | güvenli: -3.122 ✓ | güvensiz: -3.253 ✗ (131 sapma)
SOL   beklenen: -2.445 | güvenli: -2.445 ✓ | güvensiz: -2.502 ✗ (57 sapma)
Sayaç beklenen: 50.000 | güvenli: 50.000 ✓ | güvensiz: 49.991 ✗ (9 kayıp)

Çalıştırma 2 (seed=42, workers=4, updates=50.000):
BTC   beklenen: 59.387 | güvenli: 59.387 ✓ | güvensiz: 59.281 ✗ (106 sapma)
ETH   beklenen: -3.122 | güvenli: -3.122 ✓ | güvensiz: -3.228 ✗ (106 sapma)
SOL   beklenen: -2.445 | güvenli: -2.445 ✓ | güvensiz: -2.300 ✗ (145 sapma)
Sayaç beklenen: 50.000 | güvenli: 50.000 ✓ | güvensiz: 49.987 ✗ (13 kayıp)
```

> **Gözlem:** 2/2 çalıştırmada güvensiz sonuçlar bozuldu ve her seferinde farklı değerler üretti. Race condition non-deterministic'tir — aynı input ile farklı output verir. Worker/görev sayısı artınca sapma büyür çünkü çakışma olasılığı artar.

## Güvenli Çözüm

Coin state `ReentrantLock` ile korunur. Tek başına `AtomicLong` yetmez çünkü `currentPrice`, `updateCount`, `lastDelta` ve `lastUpdatedBy` alanlarının hepsi tek bir atomik işlem içinde güncellenmeli — aksi halde alanlar arası tutarsızlık oluşur.

`ReentrantLock` bu 4 alanı tek kritik bölgede gruplar:

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

Sayaç için ise tek bir `long` değer güncellendiğinden `AtomicLong` (CAS) yeterlidir — lock overhead'ine gerek yoktur.

## Invariant ve Doğruluk Kanıtı

```
safePrice       == initialPrice + sum(all deltas for coin)   →  GEÇTİ ✓
safeUpdateCount == o coin için üretilen görev sayısı         →  GEÇTİ ✓
```

Her iki çalıştırmada da `safeInvariantPassed: true` döndü. Safe fiyatlar beklenen değerlerle birebir eşleşti — `ReentrantLock` hiçbir güncellemenin kaybolmamasını garanti etti.


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

Simülasyon çalışırken `jstack` ile alınan thread dump kesiti (workers=4, updates=100.000):

```
"worker-1" #70 prio=5 WAITING (parking)
    at java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
    at ch.qos.logback.core.OutputStreamAppender.writeBytes(OutputStreamAppender.java:211)
    at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:51)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)

"worker-2" #71 prio=5 RUNNABLE
    at java.io.FileOutputStream.writeBytes(Native Method)
    at ch.qos.logback.core.OutputStreamAppender.writeBytes(OutputStreamAppender.java:217)
    at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:51)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)

"worker-3" #72 prio=5 WAITING (parking)
    at java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
    at ch.qos.logback.core.OutputStreamAppender.writeBytes(OutputStreamAppender.java:211)
    at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:51)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)

"worker-4" #73 prio=5 WAITING (parking)
    at java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:322)
    at ch.qos.logback.core.OutputStreamAppender.writeBytes(OutputStreamAppender.java:211)
    at com.infina.cryptopricesimulator.engine.PriceWorker.run(PriceWorker.java:51)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)

"http-nio-8080-exec-4" #39 prio=5 WAITING (parking)
    at java.util.concurrent.ArrayBlockingQueue.put(ArrayBlockingQueue.java:370)
    at com.infina.cryptopricesimulator.service.SimulationService.enqueueTasks(SimulationService.java:201)
    at com.infina.cryptopricesimulator.service.SimulationService.runSingleSimulation(SimulationService.java:149)
```

**Analiz:**

- **Kaç worker var?** Tam olarak 4 adet (`worker-1` .. `worker-4`). `workers=4` parametresiyle uyumlu — 100.000 görev için 100.000 thread açılmamış, sabit havuz kullanılmış.
- **Hangi state'teler?** `worker-2` RUNNABLE (aktif olarak konsola log yazıyor), diğer 3'ü WAITING — logback'in `ReentrantLock`'unda sıra bekliyorlar. Bu beklenen davranış: konsol çıktısı serialized olduğundan aynı anda sadece 1 thread yazabilir.
- **Lock çekişmesi var mı?** Evet — logback appender'ındaki lock üzerinde contention görünüyor (3/4 worker beklemede). Bu iş mantığındaki lock değil, log yazma lock'u. Coin state lock'unda çekişme bu anlık kesitte görünmüyor.
- **Deadlock var mı?** Hayır — `"Found one Java-level deadlock"` mesajı yok. Tüm beklemeler normal lock sıralaması.
- **Producer (HTTP thread):** `http-nio-8080-exec-4` WAITING durumunda — `ArrayBlockingQueue.put()` üzerinde bloklanmış. Kuyruk dolu olduğu için backpressure çalışıyor: producer worker'ların kuyruğu boşaltmasını bekliyor.

## Merge Conflict Deneyimi
### Çakışmanın Yaşandığı Dallar (Branches):
Ana Dal: main (Ekip arkadaşlarının güncel model katmanını içeren dal)
.
Özellik Dalı: feature/issue-1-price-update-task (Geçici sınıfları içeren çalışma dalı).
### Çakışma Çıkan Dosyalar ve Bölümler:
src/main/java/com/infina/cryptopricesimulator/queue/TaskProducer.java
src/main/java/com/infina/cryptopricesimulator/queue/ExpectedResultCalculator.java
Çakışma Sebebi: main dalında com.infina.cryptopricesimulator.model.Coin kullanılırken, özellik dalında geçici olarak oluşturulan com.infina.cryptopricesimulator.entities.CoinType sınıfına referans verilmesi.
### Farklı Değişikliklerin Detayları:
Ekip Üyeleri: model ve state paketleri altında standart isimlendirmelerle (Coin, CoinState) kalıcı sınıfları oluşturdu
.
Geliştirici (Siz): Henüz bu sınıflar main dalında olmadığı için geliştirme sürecini aksatmamak adına entities paketi altında geçici sınıflar oluşturarak kodun mantığını tamamladınız.
### Çözüm Süreci ve Uygulanan Yöntem:
Yöntem: Özellik dalı, güncel main dalı üzerine git rebase komutuyla taşındı.
Manuel Müdahale: Çakışma (conflict) anında özellik dalındaki geçici entities paketi ve sınıfları tamamen silindi. Kod içerisindeki tüm referanslar, ekibin ortaklaştığı model.Coin ve state.CoinState paketlerine yönlendirilerek çakışma manuel olarak çözüldü
.
Araç: Çözüm için IntelliJ IDEA'nın Merge Tool aracı kullanıldı


## Testler

Toplam **7 test sınıfı**, **30 test metodu**. Çalıştırmak için: `mvn test`

| Test Sınıfı | Test Sayısı | Kapsam |
|---|---:|---|
| `SimulationServiceTest` | 10 | Happy path, deterministic seed, worker varyasyonları, AtomicBoolean guard, edge case |
| `TaskProducerTest` | 8 | Görev üretimi, seed tekrarlanabilirliği, sınır değerler |
| `WorkerPoolTest` | 7 | Görev işleme, thread isimlendirme, graceful shutdown, hata durumları |
| `SimulationControllerIntegrationTest` | 2 | Uçtan uca simülasyon akışı, validation (HTTP 400) |
| `CoinStateInvariantTest` | 1 | Safe fiyat invariant doğrulaması |
| `SafeCounterStressTest` | 1 | AtomicLong thread-safety stres testi |
| `CryptoPriceSimulatorApplicationTests` | 1 | Spring Boot context yükleme |

## Grup Üyeleri ve Katkıları

| Üye | Sorumluluk | Branch | Pull Request | Review |
| --- | --- | --- | --- | --- |
| **Miray Tepe** | Task Queue & Producer | `feature/issue-1-price-update-task`, `feature/conflict-demo-b` | PR #20 | Ali Rıza Kayğusuz |
| **Mehmet Çavdar** | Worker Pool & Engine & Latch | `feature/worker-pool` , `feature/conflict-demo-a` | PR #19 / #22 / #53 / #54 | Ali Rıza Kayğusuz|
| **Ali Rıza Kayğusuz** | Simulation Service & Race | `feature/model-layer`, `feature/simulation-service`, `feature/metrics`, `feature/api-layer`, `feature/api-versioning`, `feature/latch-await`, `fix/integration-test-urls`, `fix/application-properties-encoding`, `refactor/simulation-service-cleanup`  | PR #51 / #55 / #56 / #58 / #59 / #60 / #61 | Miray Tepe |
| **Ece Nisa Uğur** | API (v1), Validation & Swagger | `feature/api-layer` , `feature/api-versioning` | PR #21 / #52 | Miray Tepe |
| **Zehra Buse Tüfekçi** | Metrics, Benchmark & Tests | `feature/metrics`, `docs/performance` | PR #41 | Ömer Onur Çamlı |
| **Ömer Onur Çamlı** | DTO, InvariantChecker & Docs | `feature/dto-docs`, `docs/update-architecture-and-diagrams` | PR #33 | Zehra Buse Tüfekçi / Ece Nisa Uğur / Mehmet Çavdar |

## Bonus Çalışmalar
* Bu projede ek bonus madde (Virtual Threads veya Deadlock Simülasyonu) implemente edilmemiştir.

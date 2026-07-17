# Eşzamanlı Kripto Fiyat Simülatörü - Güncel Mimari Dokümanı

## 1. Proje Hakkında

Bu proje, Java 17 ve Spring Boot kullanılarak geliştirilmiş eşzamanlı bir kripto fiyat simülasyon uygulamasıdır.  
Sistem, üretilen fiyat güncelleme görevlerini bir `LinkedBlockingQueue` üzerinde toplar ve sabit sayıdaki worker thread tarafından işler.

Uygulamanın amacı, çoklu thread ortamında oluşabilecek **race condition** problemlerini göstermek ve thread-safe veri yönetimi (Fine-Grained lock, Atomic yapıları) ile güvenli çözüm sağlamaktır.

---

# 2. Genel Mimari Akış

```text
Controller (SimulationController)
│
▼
SimulationService
│
├──► ExpectedResultCalculator (Referans Sonuçlar)
│
▼
queue.TaskProducer ───────── engine.WorkerPool (CountDownLatch ile Bekler)
│                             │
▼                             ▼
LinkedBlockingQueue            engine.PriceWorker (Worker Threads)
│                             │
▼                             ▼
Worker Threads (Tüketim)      state.CoinState & counter.Counter (Durum Güncellemeleri)
│
▼
SimulationService (Sonuç Birleştirme & Doğrulama)
```

## Akış Açıklaması

1. Kullanıcı `/api/v1/simulate` endpoint'i üzerinden simülasyonu başlatır.
2. `SimulationController`, isteği `SimulationService` katmanına iletir.
3. `SimulationService` kuyruk (LinkedBlockingQueue) ve worker pool oluşturur, `ExpectedResultCalculator` ile beklenen matematiksel referans (golden source) sonuçları hesaplar.
4. `TaskProducer`, fiyat güncelleme görevlerini üretir.
5. Üretilen görevler `LinkedBlockingQueue` içerisine eklenir.
6. `PriceWorker` thread'leri kuyruktan görevleri alarak işler.
7. Görevler hem güvenli (`SafeCoinState`) hem de güvensiz (`UnSafeCoinState`) statelere enjekte edilir. Aynı zamanda worker üzerinden update sayacının artması (SafeCounter/UnsafeCounter) tetiklenir.
8. `SimulationService` simülasyonun bittiğini `CountDownLatch` ile bekler, süre hesaplamasını yapar ve veri tutarlılığını/kuralların bozulup bozulmadığını kontrol eder.
9. Sonuçlar dışa güvenli bir şekilde `SimulationResultResponse` DTO'su (içinde detaylı `CoinStatResponse` listesi ile) döndürülür. İstenildiği an `/api/v1/stats` ve `/api/v1/coins` den geriye dönük veriler de okunabilir.

---

# 3. Paket Yapısı

```text
src/main/java/com/infina/cryptopricesimulator

├── api
│     ├── controller (SimulationController)
│     │      └── docs (SimulationApi Swagger Interface)
│     ├── dto (ErrorResponse)
│     ├── exception (Özel exception sınıfları)
│     └── GlobalExceptionHandler
│
├── config
│     └── OpenApiConfig
│
├── service
│     └── SimulationService
│
├── engine
│     ├── PriceWorker
│     ├── TaskProcessor
│     └── WorkerPool
│
├── queue
│     ├── ExpectedCoinCalculatedResult
│     ├── ExpectedResultCalculator
│     ├── PriceUpdateTask
│     └── TaskProducer
│
├── state
│     ├── CoinState
│     ├── SafeCoinState
│     └── UnSafeCoinState
│
├── counter
│     ├── Counter
│     ├── SafeCounter
│     └── UnsafeCounter
│
├── metrics
│     └── InvariantChecker
│
├── model
│     ├── Coin (Enum)
│     └── Snapshot
│
├── dto
│     ├── CoinStatResponse
│     ├── SafeCoinResponse
│     └── SimulationResultResponse
│
└── CryptoPriceSimulatorApplication
```

---

## Tüm Paket ve Sınıfların Veri-Akış Açıklamaları

### `api`
- REST endpoint'leri ve gelen HTTP isteklerinde oluşabilecek olan hataları yönetir.
- **controller:** İş mantığını doğrudan yapmaz, controller katmanı (SimulationController) olarak servise bağlar.
- **docs:** API dokümantasyonu için oluşturulan swagger (SimulationApi) arayüzlerini içerir.
- İçerisinde özel durum/kural hataları (`SimulationAlreadyRunningException` vb.), hata formatını veren bir `ErrorResponse` ve hataları global seviyede sarmalayan `GlobalExceptionHandler` yer alır.

### `config`
- **OpenApiConfig:** Swagger/OpenAPI gibi standart API arayüz konfigürasyonlarını devreye alır.

### `service`
- Uygulamanın ana iş mantığının ve koreografinin yürütüldüğü katmandır.
- **SimulationService:** Simülasyon sürecinin tek komuta merkezidir. Task üretimi öncesi seed ile görev inşasını sağlar, engine üzerindeki worker havuzunu başlatır, görevlerin bir ArrayBlockingQueue ile atılmasını tetikler, `CountDownLatch` ile işin bitmesini isabetli ölçecek şekilde bekler ve Invariant kontrollerinden geçirip nihai DTO yanıtını oluşturur. Ayrıca `/coins` ve `/stats` endpoint'lerine son duruma dair veriler (state) tutar.

### `engine`
- Worker thread'leri ile producer arasındaki eşzamanlı motor yapısını idare eder.
- **WorkerPool:** Thread havuzunu yaratır (`Executors.newFixedThreadPool`). Bünyesinde bulunan `CountDownLatch` ile beklemeleri yapar ve işlerin tamamlanmasından itibaren executor timeout operasyonlarını ayarlar (graceful shutdown).
- **PriceWorker:** Queue üzerinden görevleri alan tüketici thread yapısıdır (`Runnable`). Görevleri alıp `TaskProcessor` adlı fonksiyonel arayüz vasıtasıyla işlenmesini tetikler ve sonunda Latch'i azaltır.

### `queue`
- Mimaride görev bloklarının oluşumu, kuyruk iletilme süreci ve görevlerle alakalı referans veri üretimini sağlar.
- **PriceUpdateTask:** `record` nesnesi olup, güncellenecek coin ID'si, fiyat değişim (delta) bilgisi ve sıra numarasını immutable taşır.
- **TaskProducer:** Statik liste üretimini (createStaticTasks) yapıp, kendi `run` metodunda bunu Producer mantığı ile BlockingQueue'ya enjekte eder.
- **ExpectedResultCalculator / Result Models:** Fiyat güncellemelerinin hiçbir yarış durumu olmadan arka arkaya serice yapıldığında, varması gereken "tek ve şaşmaz" matematiksel beklenti verilerini simüle eder (Golden Source).

### `state`
- Sistemdeki her coinin, worker'lar tarafından üzerine yazılacak olan hafızadaki güncel durumlarını yönetir.
- **CoinState:** Fiyat, update sayısı ve son delta güncellemesini tutan soyut temel sınıftır.
- **SafeCoinState:** İçerisinde `ReentrantLock` barındıran ve "Fine-Grained" mantıkla race-condition'ı önleyip Thread-Safe modifikasyonu sağlar.
- **UnSafeCoinState:** Kayıp update (lost update) senaryosunu oluşturup sergilemek adına yarış durumuna (race condition) kasıtlı açık bırakılmıştır.

### `counter`
- Toplam operasyonun güncelleme döngü sayılarını tutan işlem sayaçları katmanıdır.
- **SafeCounter:** `AtomicLong.incrementAndGet` ile yarış durumu önlenmiş paralel sayıcı.
- **UnsafeCounter:** Saf logik `count++` işlemi kullanan kilitlenmemiş bozuk sayaç simülatörü.

### `metrics`
- İstendiği zaman simülasyon sonu tespit yapılması için elzem yapıları barındırır.
- **InvariantChecker:** Safe veri havuzundan alınan verileri (güncel limit) ve `ExpectedResultCalculator`'dan gelen verileri yan yana koyup, hiçbir fiyat şaşması var mı veya update eksiği var mı diye kıyaslamasını test eden yardımcı algoritmadır. (Metodu `SimulationService` içindeki Invariant doğrulamasında iş bitiminde kullanılır veya legacy mantığı için aracıdır).

### `model`
- Mimarideki model ve enum dosyalarını içerir.
- **Coin:** Simüle edilen (BTC, ETH, vb.) destekli varlıkların başlangıç değerleri.
- **Snapshot:** Modifiye edilebilen bir state objesinin belli andaki "fotoğrafını", Immutable bir Record nesnesi ile alıp dışarıdaki nesneden verisinin tutarlı okunması amacıyla oluşturulmuştur.

### `dto`
- Dış dünyaya yani HTTP / JSON response akışlarına çıkartılacak olan nesnelerin yer aldığı Data-Transfer-Object klasörüdür.
- **SimulationResultResponse:** Gecikmeler, seed, update sayıları ve Throughput bilgileriyle dolu `/simulate` ve `/stats` uçlarından dönülen ana gövdedir.
- **CoinStatResponse:** O simülasyondaki (BTC, ETH) birimler bazında Beklenen vs. Safe vs. Unsafe bilgisini taşır.
- **SafeCoinResponse:** `/coins` endpoint'i üzerinden doğrudan "Sadece safe/güvenli" son paraların duruk hallerini dönen sarmaldır.

### `CryptoPriceSimulatorApplication`
- Spring Boot uygulamamızın başlatıcısıdır. Container'ını ve context yüklemesini idare eder.

---

## Tasarım Kararlarında Güncel Değişimler
| Karar Noktası | Kararımız | Neden? (+ Alternatif Karşılaştırması) |
|---|---|---|
| **Görev Kuyruğu** | `ArrayBlockingQueue(1000)` | Bellek yığılmasını önlemek (OOM engellemek) için sınırlı-Bounded kuyruk seçildi. |
| **Worker Havuzu** | `Executors.newFixedThreadPool(workers)` | Her görev için thread kurma context-switch yükü yaratır; thread havuzu ile reuse edildi. |
| **Coin Kilidi & Kapsamı** | `ReentrantLock` & Fine-Grained | Bütün coin'leri bir kilide sokmak (Global) yerine her coine kendi özel lock'u verilerek darboğaz engellendi. |
| **İşlerin Tamamlanması (Performans Senkronizasyonu)** | Poison Pill (-1) + `CountDownLatch.await()` | Worker'ların döngü kapanışını bildirmesi Poison Pill ile, süre (elapsedMs) kesiminin kusursuz ölçülebilmesi ise `CountDownLatch`'in son worker'da sıfırlanması ile sağlandı. |
| **Graceful Shutdown** | `awaitCompletion()` -> `shutdown()` | İşlem süreleri sayıldıktan sonra Executor üzerinde son temizlik (kilit beklemeleri vs.) kapatma metoduyla güvenceye alındı. |
| **İkinci Simülasyon Koruması** | `AtomicBoolean` (`compareAndSet`) | Çalışırken üzerine 2. tetiklemenin getireceği state kirliliğini Thread-Safe olarak engellemek (HTTP 409) için CAS (Compare-And-Swap) kullanıldı. |

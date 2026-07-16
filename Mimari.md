# Eşzamanlı Kripto Fiyat Simülatörü - Mimari Dokümanı

## 1. Proje Hakkında

Bu proje, Java 17 ve Spring Boot kullanılarak geliştirilmiş eşzamanlı bir kripto fiyat simülasyon uygulamasıdır.  
Sistem, üretilen fiyat güncelleme görevlerini bir `BlockingQueue` üzerinde toplar ve sabit sayıdaki worker thread tarafından işler.

Uygulamanın amacı, çoklu thread ortamında oluşabilecek **race condition** problemlerini göstermek ve thread-safe veri yönetimi ile güvenli çözüm sağlamaktır.

---

# 2. Genel Mimari Akış


```

Controller
│
▼
SimulationService
│
├──► ExpectedResultCalculator (Referans Sonuçlar)
│
▼
TaskProducer ────── WorkerService
│                   │
│            ───────
▼           ▼
BlockingQueue
│
▼
Worker Threads
│
▼
CoinStateManager
│
▼
CoinState
│
▼
InvariantChecker (Doğruluk Kontrolü)

```

## Akış Açıklaması

1. Kullanıcı `/simulate` endpoint'i üzerinden simülasyonu başlatır.
2. `SimulationController`, isteği `SimulationService` katmanına iletir.
3. `SimulationService` queue, worker pool oluşturur ve `ExpectedResultCalculator` ile beklenen matematiksel referans (golden source) sonuçları hesaplar.
4. `TaskProducer`, fiyat güncelleme görevlerini üretir.
5. Üretilen görevler `BlockingQueue` içerisine eklenir.
6. Worker thread'leri kuyruktan görevleri alarak işler.
7. `CoinStateManager`, coin durumlarının güvenli (safe) ve güvensiz (unsafe) güncellenmesini sağlar.
8. `InvariantChecker`, simülasyon sonunda veri tutarlılığını ve thread-safe kuralların bozulup bozulmadığını kontrol eder.
9. Sonuçlar (`SimulationResultResponse`) kullanıcıya döndürülür.

---

# 3. Paket Yapısı


```

src/main/java/com/infina/cryptopricesimulator

├── controller
│     └── SimulationController
│
├── service
│     ├── SimulationService
│     └── WorkerService
│
├── queue
│     ├── TaskProducer
│     └── PriceUpdateTask
│
├── worker
│     └── Worker
│
├── state
│     ├── CoinState
│     ├── SafeCoinState
│     ├── UnsafeCoinState
│     └── CoinStateManager
│
├── metrics
│     ├── ExpectedResultCalculator
│     └── InvariantChecker
│
├── model (DTO & Enums)
│     ├── CoinEnum
│     ├── SimulationResultResponse
│     └── CoinStatResponse
│
├── config
│     └── SwaggerConfig
│
├── exception
│     └── GlobalExceptionHandler
│
└── CryptoPriceSimulatorApplication

```

---

controller
- REST API endpoint'lerini içerir.
- Kullanıcıdan gelen simülasyon başlatma, coin durumu ve istatistik isteklerini yönetir.
- İş mantığını doğrudan yapmaz, service katmanına yönlendirir.

service
- Uygulamanın ana iş mantığının bulunduğu katmandır.
- SimulationService simülasyon sürecini yönetir; queue, producer, worker ve doğrulama akışını koordine eder.
- WorkerService thread pool oluşturma, worker başlatma ve sonlandırma işlemlerinden sorumludur.

queue
- Worker thread'leri ile producer arasındaki görev iletişimini sağlar.
- PriceUpdateTask güncellenecek coin, fiyat değişim bilgisi ve sıra numarasını taşır.
- TaskProducer belirlenen sayı ve seed değerine göre görevleri üretip kuyruğa ekler.

worker
- Queue içerisindeki görevleri tüketen thread yapısını içerir.
- Her worker aldığı PriceUpdateTask'i işler ve ilgili coin state güncellemesini gerçekleştirir.

state
- Coin'lerin güncel durumlarını ve thread-safe güncelleme mekanizmasını yönetir.
- CoinState fiyat, update sayısı ve son güncelleme bilgilerini tutan soyut yapıdır.
- SafeCoinState (Lock kullanan) ve UnsafeCoinState yarış durumu (race condition) farklarını ortaya koyar.

metrics
- Simülasyon sonuçlarının doğruluğunu kontrol eden ve metrikleri hesaplayan katmandır.
- ExpectedResultCalculator matematiksel olarak beklenen kesin sonuçları (golden source) hesaplar.
- InvariantChecker thread-safe ve thread-unsafe durumların tutarlılığını doğrular.

model
- Uygulama içerisinde kullanılan veri modellerini, enum'ları ve DTO (Data Transfer Object) yapılarını içerir.
- CoinEnum, ana cevap modeli olan SimulationResultResponse ve alt coin detaylarını tutan CoinStatResponse burada yer alır.

config
- Uygulama konfigürasyonlarını içerir.
- Swagger/OpenAPI gibi uygulama ayarları burada tanımlanır.

exception
- Uygulama genelindeki hata yönetimini içerir.
- Validation hataları ve özel exception durumları merkezi olarak yönetilir.

CryptoPriceSimulatorApplication
- Spring Boot uygulamasının başlangıç sınıfıdır.
- Uygulamayı ayağa kaldıran main metodunu içerir.

---

## Sınıf Açıklamaları

### controller

**SimulationController**
- REST API isteklerini karşılayan controller sınıfıdır.
- Kullanıcının simülasyon başlatma (`/simulate`), coin durumlarını görüntüleme (`/coins`) ve istatistik alma (`/stats`) isteklerini yönetir.
- İş mantığını içermez, ilgili işlemler için service katmanını çağırır.

### service

**SimulationService**
- Simülasyon sürecinin ana yönetici sınıfıdır.
- Task üretimi, worker başlatılması, görevlerin tamamlanmasının beklenmesi ve sonuçların oluşturulması işlemlerini koordine eder.
- BlockingQueue, ExecutorService ve CountDownLatch gibi concurrency yapılarını yönetir.

**WorkerService**
- Worker thread'lerinin yaşam döngüsünü yönetir.
- Belirlenen worker sayısına göre thread pool oluşturur, worker'ları başlatır ve güvenli şekilde sonlandırır.

### queue

**PriceUpdateTask**
- Kuyruk içerisinde taşınan görev nesnesidir (`record`).
- Her görev sıra numarası (`sequence`), coin ID (`coinId`) ve fiyat değişimi (`delta`) bilgisini taşır.
- Producer tarafından oluşturulur, Worker tarafından tüketilir.

Örnek:
`sequence: 1, coinId: "BTC", delta: +5`

**TaskProducer**
- Simülasyon için fiyat güncelleme görevlerini üretir.
- Belirlenen update sayısı ve seed değeri ile tekrar üretilebilir rastgele görevler oluşturur.
- Oluşturduğu görevleri BlockingQueue içerisine ekler.

### worker

**Worker**
- Queue üzerinden görev alan çalışan thread yapısıdır (`Runnable`).
- Aldığı PriceUpdateTask'i işler ve hem safe hem de unsafe coin state güncellemelerini gerçekleştirir.
- Birden fazla worker aynı anda çalışarak eşzamanlı işlem yapılmasını sağlar.

### state

**CoinState & Alt Sınıfları**
- **CoinState:** Tek bir coin'in anlık durumunu temsil eden soyut/temel yapıdır. Fiyat, güncelleme sayısı, son değişim ve güncelleyen thread bilgilerini tutar.
- **SafeCoinState:** `ReentrantLock` kullanarak thread-safe (race condition olmadan) fiyat güncellemesi yapan sınıftır.
- **UnsafeCoinState:** Herhangi bir senkronizasyon mekanizması kullanmayan, yarış durumuna (race condition) açık sınıftır.
- **CoinStateManager:** Tüm coin state nesnelerini yönetir ve merkezi güncelleme noktası sunar.

### metrics

**ExpectedResultCalculator**
- Fiyat güncellemelerinin hiçbir race condition (yarış durumu) olmadan, tek thread çalışıyormuş gibi matematiksel olarak ulaşması gereken "beklenen" (`expected`) nihai sonuçları hesaplar.
- Simülasyon bitiminde, safe ve unsafe worker'ların ürettiği sonuçların doğruluğunu kıyaslamak için referans (golden source) oluşturur.

**InvariantChecker**
- Simülasyon kurallarının (invariant) bozulup bozulmadığını kontrol eder.
- `checkPriceInvariant`: Safe coin durumlarının, beklenen referans sonuçlarla birebir uyuşup uyuşmadığını doğrular (`safeInvariantPassed`).
- `checkCountInvariant`: İşlenen toplam güvenli ve güvensiz güncelleme sayılarının, gönderilen toplam görev sayısına eşit olup olmadığını kontrol eder.

### model (DTO & Enums)

**CoinEnum**
- Sistemde desteklenen kripto para türlerini ve başlangıç fiyatlarını temsil eden enum sınıfıdır.

Örnek Değerler:
- `BTC` (Başlangıç Fiyatı: 60.000)
- `ETH` (Başlangıç Fiyatı: 3.000)
- `SOL` (Başlangıç Fiyatı: 150)

**SimulationResultResponse**
- `/simulate` ve `/stats` endpoint'lerinden dönen ana DTO cevap modelidir.
- Simülasyon seed değeri, toplam işlenen update sayıları (submitted, safe, unsafe), geçen süreler (elapsedMs), saniye başına throughput değerleri, `safeInvariantPassed` doğruluk bayrağı ve her bir coin için detaylı sonuçları içeren `CoinStatResponse` listesini tutar.

**CoinStatResponse**
- Tek bir coin için simülasyon sonu detaylı istatistiklerini taşıyan DTO modelidir.
- Coin ID'si, başlangıç fiyatı (`initial`), beklenen fiyat (`expected`), güvenli/güvensiz nihai fiyatlar (`safe` / `unsafe`) ve bunların ilgili güncelleme sayılarını (`update count`) içerir.

### config

**SwaggerConfig**
- Swagger/OpenAPI dokümantasyon ayarlarını içerir.
- API endpoint'lerinin kullanıcı tarafından arayüz üzerinden test edilmesini sağlar.

### exception

**GlobalExceptionHandler**
- Uygulamadaki hataları merkezi olarak yönetir.
- Validation hataları, geçersiz parametreler ve özel exception durumları için uygun HTTP cevapları döner.

### CryptoPriceSimulatorApplication

**CryptoPriceSimulatorApplication**
- Spring Boot uygulamasının başlangıç noktasıdır.
- Main metodu içerisinde Spring konteynerini başlatır ve uygulamayı ayağa kaldırır.

```
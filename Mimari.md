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
      ▼
TaskProducer ────── WorkerService
      │                  │
      ▼                  ▼
BlockingQueue<PriceUpdateTask>
              │
              ▼
          Worker Threads
              │
              ▼
      CoinStateManager
              │
              ▼
          CoinState
```

## Akış Açıklaması

1. Kullanıcı `/simulate` endpoint'i üzerinden simülasyonu başlatır.
2. `SimulationController`, isteği `SimulationService` katmanına iletir.
3. `SimulationService` queue ve worker pool oluşturur.
4. `TaskProducer`, fiyat güncelleme görevlerini üretir.
5. Üretilen görevler `BlockingQueue` içerisine eklenir.
6. Worker thread'leri kuyruktan görevleri alarak işler.
7. `CoinStateManager`, coin durumlarının güvenli güncellenmesini sağlar.
8. Sonuçlar kullanıcıya döndürülür.

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
│     └── CoinStateManager
│
├── model
│     ├── Coin
│     ├── StatsResponse
│     └── SimulationResult
│
├── config
│     └── SwaggerConfig
│
├── exception
│     └── GlobalExceptionHandler
│
└── PriceSimApplication
```

---

controller
- REST API endpoint'lerini içerir.
- Kullanıcıdan gelen simülasyon başlatma, coin durumu ve istatistik isteklerini yönetir.
- İş mantığını doğrudan yapmaz, service katmanına yönlendirir.


service
- Uygulamanın ana iş mantığının bulunduğu katmandır.
- SimulationService simülasyon sürecini yönetir; queue, producer ve worker akışını koordine eder.
- WorkerService thread pool oluşturma, worker başlatma ve sonlandırma işlemlerinden sorumludur.


queue
- Worker thread'leri ile producer arasındaki görev iletişimini sağlar.
- PriceUpdateTask güncellenecek coin ve fiyat değişim bilgisini taşır.
- TaskProducer belirlenen sayı ve seed değerine göre görevleri üretip kuyruğa ekler.


worker
- Queue içerisindeki görevleri tüketen thread yapısını içerir.
- Her worker aldığı PriceUpdateTask'i işler ve ilgili coin state güncellemesini gerçekleştirir.


state
- Coin'lerin güncel durumlarını ve thread-safe güncelleme mekanizmasını yönetir.
- CoinState fiyat, update sayısı ve son güncelleme bilgilerini tutar.
- CoinStateManager lock kullanarak race condition oluşmasını engeller.


model
- Uygulama içerisinde kullanılan veri modellerini içerir.
- Coin, simülasyon sonucu ve istatistik cevap modelleri burada tutulur.


config
- Uygulama konfigürasyonlarını içerir.
- Swagger/OpenAPI gibi uygulama ayarları burada tanımlanır.


exception
- Uygulama genelindeki hata yönetimini içerir.
- Validation hataları ve özel exception durumları merkezi olarak yönetilir.


repository
- Veri erişim katmanı için kullanılır.
- Bu proje tamamen in-memory çalıştığı için repository bulunmamaktadır.


PriceSimApplication
- Spring Boot uygulamasının başlangıç sınıfıdır.
- Uygulamayı ayağa kaldıran main metodunu içerir.


## Sınıf Açıklamaları

### controller

**SimulationController**
- REST API isteklerini karşılayan controller sınıfıdır.
- Kullanıcının simülasyon başlatma, coin durumlarını görüntüleme ve istatistik alma isteklerini yönetir.
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
- Kuyruk içerisinde taşınan görev nesnesidir.
- Hangi coin'in ne kadar fiyat değişimine uğrayacağını tutar.
- Producer tarafından oluşturulur, Worker tarafından tüketilir.

Örnek:
BTC +5.2 fiyat güncellemesi


**TaskProducer**
- Simülasyon için fiyat güncelleme görevlerini üretir.
- Belirlenen update sayısı ve seed değeri ile tekrar üretilebilir rastgele görevler oluşturur.
- Oluşturduğu görevleri BlockingQueue içerisine ekler.


### worker

**Worker**
- Queue üzerinden görev alan çalışan thread yapısıdır.
- Aldığı PriceUpdateTask'i işler ve ilgili coin state güncellemesini gerçekleştirir.
- Birden fazla worker aynı anda çalışarak eşzamanlı işlem yapılmasını sağlar.


### state

**CoinState**
- Tek bir coin'in anlık durumunu temsil eder.
- Coin fiyatı, güncelleme sayısı, son değişim ve güncelleyen thread bilgilerini tutar.
- Thread güvenliği için lock mekanizması içerir.

Örnek:
BTC
- Current Price: 65000
- Update Count: 2500
- Last Delta: +15


**CoinStateManager**
- Tüm coin state nesnelerini yönetir.
- Coin güncellemelerini merkezi olarak gerçekleştirir.
- Race condition oluşmasını önlemek için güvenli güncelleme mekanizması sağlar.


### model

**Coin**
- Sistemde desteklenen kripto para türlerini temsil eden enum veya model sınıfıdır.

Örnek:
- BTC
- ETH
- SOL


**StatsResponse**
- `/stats` endpoint'inden dönen cevap modelidir.
- Simülasyon süresi, throughput, beklenen/güvenli/güvensiz sonuçlar gibi bilgileri içerir.


**SimulationResult**
- Bir simülasyon çalışmasının sonucunu temsil eder.
- Tamamlanan görev sayısı, coin sonuçları ve doğruluk kontrollerini içerir.


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

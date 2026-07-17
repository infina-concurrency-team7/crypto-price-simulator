# Teslim Raporu — Eşzamanlı Kripto Fiyat Simülatörü

## 1. Grup Bilgileri
| Alan | Bilgi                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Grup adı | Concurrent Minds                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Grup üyeleri | Ali Rıza Kaygusuz, Ece Nisa Uğur, Mehmet Çavdar, Miray Tepe, Ömer Onur Çamlı, Zehra Buse Tüfekçi                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| GitHub repo linki | https://github.com/infina-concurrency-team7/crypto-price-simulator                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Pull Request linkleri | - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/51 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/55 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/56 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/58 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/59 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/60 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/61 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/19 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/22 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/53 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/54 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/20 → https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/33 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/57 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/41 → https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/21 <br> - https://github.com/infina-concurrency-team7/crypto-price-simulator/pull/52 |
| Conflict çözülen dosya | WorkerPool.java , CoinState.java                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Conflict çözüm commit / PR | https://github.com/infina-concurrency-team7/crypto-price-simulator/commit/8021f280e91f5acbb5f0c03d21130a35b79d3f19  -  https://github.com/infina-concurrency-team7/crypto-price-simulator/commit/1bcd738e4d26cd11f42822368c047f542fc19cb7                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| Yapılan bonus (varsa) | Yok                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |

## 2. Kısa Açıklama

Bu proje, Java 17 ve Spring Boot kullanılarak geliştirilmiş, çoklu iş parçacığı (multi-threading) tabanlı bir kripto fiyat simülasyon uygulamasıdır. Sistem, eşzamanlı veri güncellemelerinde oluşabilecek race condition ve lost update problemlerini inceleyerek, farklı kilitleme mekanizmalarıyla (ReentrantLock, AtomicLong) thread-safe çalışma sağlamayı amaçlamaktadır.

## 3. Çalıştırma (özet)

```
git clone https://github.com/infina-concurrency-team7/crypto-price-simulator.git
cd crypto-price-simulator
mvn spring-boot:run
Swagger: http://localhost:8080/swagger-ui/index.html

```

## 4. Tasarım Kararları (Özet)

| Karar Noktası | Kararımız | Neden? |
|---|---|---|
| Görev Kuyruğu | ArrayBlockingQueue(1000) | Bounded yapı sayesinde bellek kullanımını kontrol altında tutmak ve aşırı görev birikmesini önlemek için tercih edildi. |
| Worker Havuzu | Executors.newFixedThreadPool(workers) | Thread oluşturma maliyetini azaltmak ve thread reuse sağlamak için sabit boyutlu worker havuzu kullanıldı. |
| Coin Kilidi ve Lock Kapsamı | Coin başına ReentrantLock (Fine-Grained Locking) | Her coin'in bağımsız güncellenebilmesi için global lock yerine coin bazlı kilitleme kullanılarak gereksiz beklemeler azaltıldı. |
| İşlerin Tamamlanması | Poison Pill + CountDownLatch | Worker yaşam döngüsünü kontrollü sonlandırmak ve tüm görevlerin tamamlandığından emin olarak süre ölçümü yapmak için kullanıldı. |
| Graceful Shutdown | shutdown() + awaitTermination() | Worker thread'lerin güvenli şekilde kapatılması ve kaynakların düzgün temizlenmesi sağlandı. |

## 5. Race Condition Kanıtı

### Çalıştırma 1 (seed=42, workers=4, updates=50.000)

| Coin / Sayaç | Beklenen | Güvenli | Güvensiz | Sonuç |
|---|---:|---:|---:|---|
| BTC | 59.387 | 59.387 ✓ | 59.370 ✗ | 17 sapma |
| ETH | -3.122 | -3.122 ✓ | -3.253 ✗ | 131 sapma |
| SOL | -2.445 | -2.445 ✓ | -2.502 ✗ | 57 sapma |
| Sayaç | 50.000 | 50.000 ✓ | 49.991 ✗ | 9 kayıp |
| Invariant | Başarılı | ✓ | ✗ | Güvenli sonuç doğrulandı |

### Çalıştırma 2 (seed=42, workers=4, updates=50.000)

| Coin / Sayaç | Beklenen | Güvenli | Güvensiz | Sonuç |
|---|---:|---:|---:|---|
| BTC | 59.387 | 59.387 ✓ | 59.281 ✗ | 106 sapma |
| ETH | -3.122 | -3.122 ✓ | -3.228 ✗ | 106 sapma |
| SOL | -2.445 | -2.445 ✓ | -2.300 ✗ | 145 sapma |
| Sayaç | 50.000 | 50.000 ✓ | 49.987 ✗ | 13 kayıp |
| Invariant | Başarılı | ✓ | ✗ | Güvenli sonuç doğrulandı |


## 6. Metrik Özeti
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

## 7. Thread Dump Özeti

Simülasyon sırasında alınan thread dump incelendiğinde:

- Sistem, `workers=4` parametresine uygun olarak **4 adet worker thread** ile çalışmaktadır.
- Worker thread'lerden biri `RUNNABLE`, diğerleri `WAITING` durumundadır.
- Log yazma işlemi sırasında Logback'in kullandığı lock üzerinde **lock contention** gözlemlenmiştir.
- Herhangi bir **deadlock** durumu bulunmamaktadır; beklemeler normal thread senkronizasyonundan kaynaklanmaktadır.
- HTTP producer thread'i `LinkedBlockingQueue.put()` üzerinde bekleyerek queue doluluğunda **backpressure** mekanizmasının çalıştığını göstermektedir.

## 8. Zorunlu Özellikler — Öz Değerlendirme

- [x] /simulate, /coins, /stats çalışıyor
- [x] Geçersiz parametre → HTTP 400, ikinci eşzamanlı istek → HTTP 409
- [x] Aynı görev listesi (immutable, tek üretim) safe ve unsafe'de kullanılıyor
- [x] BlockingQueue + sabit thread pool (her görev için yeni thread yok)
- [x] Güvensiz sürüm hatayı gösteriyor; güvenli sürüm invariant'ı sağlıyor
- [x] En az bir yerde ReentrantLock kullanıldı
- [x] Graceful shutdown; işlerin bitmesi bekleniyor
- [x] Seed ile tekrarlanabilir görev üretimi
- [x] throughput/süre + 1/2/4/8 worker tablosu
- [x] Thread dump alındı ve README'de yorumlandı
- [x] Swagger çalışıyor, adres README'de
- [x] Unit + en az 1 integration test
- [x] En az 3 branch, 2 PR, 2 review, 1 çözülmüş conflict gereksinimi karşılanmıştır.
- 
  (- Toplam **17 branch** oluşturulmuştur.
  - Toplam **19 Pull Request** açılmıştır.
  - **22 code review** gerçekleştirilmiştir.
  - Toplam **4 merge conflict** çözülmüş, bunlardan **1 tanesi detaylı olarak belgelenmiştir**.)


## 9. Bireysel Katkı Tablosu

| Üye | Rol / Ne yaptı? | Branch | PR | Review |
|---|---|---|---|---|
| Miray Tepe | Task Queue & Producer geliştirmeleri | feature/issue-1-price-update-task, feature/conflict-demo-b | PR #20 | Ali Rıza Kaygusuz |
| Mehmet Çavdar | Worker Pool, Engine ve CountDownLatch entegrasyonu | feature/worker-pool, feature/conflict-demo-a | PR #19, #22, #53, #54 | Ali Rıza Kaygusuz |
| Ali Rıza Kaygusuz | Simulation Service, Race Condition çalışmaları ve Metrics entegrasyonu | feature/model-layer, feature/simulation-service, feature/metrics, feature/api-layer, feature/api-versioning, feature/latch-await, fix/integration-test-urls, fix/application-properties-encoding, refactor/simulation-service-cleanup | PR #51, #55, #56, #58, #59, #60, #61 | Miray Tepe |
| Ece Nisa Uğur | API v1, Validation ve Swagger geliştirmeleri | feature/api-layer, feature/api-versioning | PR #21, #52 | Miray Tepe |
| Zehra Buse Tüfekçi | Metrics, Benchmark sonuçları ve Test geliştirmeleri | feature/metrics, docs/performance | PR #41 | Ömer Onur Çamlı |
| Ömer Onur Çamlı | DTO, InvariantChecker ve Dokümantasyon çalışmaları | feature/dto-docs, docs/update-architecture-and-diagrams | PR #33 | Zehra Buse Tüfekçi, Ece Nisa Uğur, Mehmet Çavdar |

## 10. Notlar (opsiyonel)

Proje sürecinde en çok zorlandığımız noktalar model tasarımı ve görev dağılımı aşamaları oldu. Sistemin mimarisine ve sınıfların sorumluluklarına karar vermek zaman aldı. Görevleri ekip içerisinde bölüştürme sürecinde de ortak bir plan oluşturmak ve herkesin sorumluluklarını netleştirmek beklediğimizden uzun sürdü. Karar aşamalarında ekip olarak fikir alışverişi yaparak, farklı alternatifleri değerlendirip ortak çözümler ürettik. Gelecekte projeye başlanırken yalnızca API endpoint'lerinin verilmesi ve sistem tasarımının ekibe bırakılması daha verimli olabilirdi. Ayrıca ödev dokümantasyonunun daha kısa ve odaklı olması, geliştirme sürecine daha fazla zaman ayırmamızı sağlayabilirdi. 


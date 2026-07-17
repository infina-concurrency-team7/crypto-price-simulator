# Eşzamanlı Kripto Fiyat Simülatörü


## Proje Hakkında


## Kullanılan Teknolojiler


## Uygulamayı Çalıştırma


## Swagger Adresi


## Endpoint'ler


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


## Race Condition Gözlemi

## Güvenli Çözüm

## Invariant ve Doğruluk Kanıtı


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



## Merge Conflict Deneyimi



## Testler


## Grup Üyeleri ve Katkıları


## Bonus Çalışmalar
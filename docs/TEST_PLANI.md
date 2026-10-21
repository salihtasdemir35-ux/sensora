# Test Planı

## Otomatik testler

- `./gradlew testDebugUnitTest` — 116 birim testi (kılavuz sınır değerleri, eGFR, spirometri yorumu, odyogram, görme, üfleme dedektörü, kamera geometrisi ve ton sentezi dahil; (15'i astronomi: Meeus örnekleri, bilinen ay evreleri, gün uzunlukları, katalog bütünlüğü): açı matematiği, standart atmosfer, dBFS/Leq, birim dönüşümleri (tüm birim çiftlerinde gidiş-dönüş), GPS mesafe/titreşim filtresi, adım günlük ayrımı ve cihaz yeniden başlatma, kalite kuralları, CSV/JSON kaçış ve formül enjeksiyonu koruması.
- `./gradlew connectedDebugAndroidTest` — cihaz/emülatörde ana ekranın açıldığını doğrulayan duman testi.

## Gerçek cihaz senaryoları

| # | Senaryo | Beklenen |
|---|---|---|
| 1 | Tüm sensörleri olan telefon | Tüm araçlar veri gösterir, kalite rozetleri koşula göre değişir |
| 2 | Barometresiz telefon | Barometre aracı çökmez; açıklama + GPS irtifası |
| 3 | Işık sensörsüz telefon | Işık kartında "Bu cihazda kullanılamaz"; araç açıklama gösterir |
| 4 | Manyetometresiz cihaz | Pusula ve manyetik alan açıklama gösterir |
| 5 | Konum izni reddedildi | Pusula manyetik kuzeyle çalışır; GPS araçları izin kartı + "Ayarları aç" |
| 6 | Mikrofon izni reddedildi | Ses ölçer izin kartı gösterir, çökmez |
| 7 | İnternet kapalı | Aşama 2 araçlarının tamamı çalışır |
| 8 | Küçük ekran (≤ 5") | Kaydırma ile tüm içerik erişilebilir |
| 9 | Tablet / katlanabilir | Ana ekranda çok sütunlu kartlar, araçlarda ortalanmış içerik |
| 10 | Koyu tema + en büyük yazı boyutu | Okunaklı, taşma yok |
| 11 | Uygulama arka plana alınır | Sensör, mikrofon ve GPS dinleyicileri durur (Android "mikrofon kullanımda" göstergesi söner) |
| 12 | 30 dk ses ölçümü / GPS kaydı | Isınma ve pil tüketimi gözlenir |
| 13 | Telefon yeniden başlatılır | Adım sayısı kaybolmaz |
| 14 | Kaydet / sil / CSV / JSON | Dosyalar seçilen konuma yazılır, Excel'de doğru açılır |
| 15 | Android 8, 11, 13, 14/15 | Dil seçimi, izinler ve kenardan kenara ekran doğru çalışır |
| 16 | Arapça | Sağdan sola yerleşim doğru |
| 17 | Gökyüzü haritası, açık gökyüzü | Telefon parlak bir yıldıza (ör. Vega) doğrultulduğunda ekranın ortasına yakın görünür; sapma > 10° ise pusula kalibrasyonu |
| 18 | Gökyüzü haritası, sensörsüz cihaz / emülatör | Sürükleme ve yakınlaştırma çalışır, çökme yok |
| 19 | Bu gece, gündüz açılırsa | Bir sonraki gecenin karanlık penceresi gösterilir |
| 20 | Güneş ve Ay, yüksek enlem (ör. 69° K, Haziran) | "Güneş batmıyor" mesajı |
| 21 | Konum elle girildi, GPS izni yok | Astronomi araçları tam çalışır |
| 22 | Ayarlar → konumu sil | Astronomi araçları konum ister |
| 24 | AR ölçüm, ARCore destekli telefon | Bilinen 1 m'lik cetvel ±3 cm içinde ölçülür; masada alan makul |
| 25 | AR ölçüm, ARCore desteksiz telefon | "Desteklenmiyor" kartı ve alternatif araçlar; çökme yok |
| 26 | AR ölçüm, karanlık oda / hızlı hareket | Uygun takip uyarısı görünür |
| 27 | Açı ile yükseklik, bilinen bina katı | Sonuç gösterilen aralık içinde |
| 28 | Referansla boyut, banka kartı + A4 kağıt | A4 uzun kenarı ≈ 29,7 cm (±%5) |
| 29 | Düşük ışık, Gece eklentili/eklentisiz telefon | Doğru durum mesajı; fotoğraf Resimler/Sensora'ya kaydedilir |
| 30 | Ton üreteci, kulaklıkla sol/sağ testi | Ses doğru tarafa geçer; ekrandan çıkınca ses kesilir |
| 31 | Ton üreteci, 3 dk çalma | Otomatik durur; ses düzeyi değişimi tıklamasız |
| 32 | Üfleme testi, sessiz oda | 3 kabul edilebilir deneme sonrası oturum "iyi" olarak tamamlanır |
| 33 | Üfleme testi, gürültülü ortam | "Ortam çok gürültülü" uyarısı, ölçüm başlamaz |
| 34 | Üfleme testi, yavaş/kesik üfleme | İlgili sorun mesajı görünür |
| 35 | Sonuç yorumlayıcı, gerçek bir tahlil raporu | Rapor aralığı girilince rapordaki DÜŞÜK/YÜKSEK işaretleriyle uyumlu |
| 36 | Sonuç yorumlayıcı, K 6,5 mmol/L | Kırmızı "bugün başvurun" uyarısı |
| 37 | Kamera nabzı, parmak flaşı kapatıyor | 30 sn sonunda sonuç; nabız oksimetresiyle ±5 atım/dk |
| 38 | Kamera nabzı, parmak hareket ediyor | "Sinyal yeterince temiz değil", sayı yok |
| 39 | Son depremler, AFAD | Listedeki saat deprem.afad.gov.tr'deki saatle aynı |
| 40 | Son depremler, internet yok | Hata mesajı, çökme yok |
| 41 | Sismograf, telefon masada, masaya vurma | Tepe ivme yükselir, FFT değişir |
| 42 | Uyku testi, tam gece, ekran kapalı | Kayıt kesilmez, sabah rapor oluşur |
| 43 | Uyku testi, Android 14+ | Bildirim görünür, mikrofon göstergesi açık |
| 44 | İşitme taraması, kablolu kulaklık, sessiz oda | Sağ/sol sonuçlar birbirine yakın; tekrar 1 kHz farkı ≤10 dB |
| 45 | İşitme taraması, bir kulaklık teki çıkarılmış | O kulak için "yanıt yok" ve asimetri uyarısı |
| 46 | İşitme taraması, kulaklık yok | Başlat düğmesi devre dışı, açıklama görünür |
| 47 | Ekran kalibrasyonu, banka kartı | Kutu kartla aynı genişlikte; kaydedilir |
| 48 | Görme keskinliği 40 cm, gözlüklü | Sonuç optisyen/göz doktoru değeriyle ±0,1 logMAR |
| 49 | Renk testi, bilinen renk körü kullanıcı | Kırmızı–yeşil şüphesi bildirilir |
| 50 | Amsler, bir kutu işaretli | "Kısa sürede göz doktoru" uyarısı |
| 23 | Gece modu | Tüm harita kırmızı tonlarda (karanlığa uyum korunur) |

## Bilinen sınırlamalar

- Gökyüzü haritasının telefonla hizası pusula doğruluğuna bağlıdır (tipik ±2–5°); hesaplanan konumlar bundan çok daha doğrudur.
- AR doğruluğu yüzey dokusu ve ışığa çok bağlıdır; kalite rozeti bunu yansıtır.
- Ton üretecinin çıkış seviyesi kalibre değildir; bu yüzden işitme eşiği (dB HL) iddiası yoktur.
- Tutulma ve uydu geçişleri henüz hesaplanmıyor; uygulama bunu açıkça belirtiyor.

- Adım günlük ayrımı, uygulamanın sensörü okuduğu ana göre yapılır; arka plan işi Aşama 4'te eklenecek.
- Ses ölçerde A-ağırlıklandırma yoktur.
- Pusula telefon dik tutulduğunda arka kamera yönünü kullanır; yatay–dikey geçişte kısa bir sıçrama olabilir.

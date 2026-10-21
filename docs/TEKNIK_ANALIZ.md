# Sensora — Aşama 1: Teknik Analiz ve Mimari

## 1. İsim, kimlik ve dil

**Sensora** — "sensor" kökünden türetilmiş, Latin alfabesiyle her dilde aynı okunan kısa ve evrensel bir isim. Alt başlık her dilde çevrilir (TR: *Akıllı Ölçüm Asistanı*, EN: *Smart Measurement Assistant*). Uygulama kimliği: `com.sensora.app`.

**Simge:** ölçüm halkası (gösterge kadranı) + pusula iğnesi + halkayı kapatan tek "sensör noktası". Turkuaz–petrol mavisi degrade zemin, kehribar kuzey ucu. Android adaptive icon (vektör) ve Android 13+ tema uyumlu tek renkli simge olarak hazırlandı. Önizleme: `docs/sensora-icon.svg`.

**Diller:** English (varsayılan), Türkçe, Deutsch, Español, العربية (sağdan sola yazım destekli). Uygulama içi dil seçimi Ayarlar ekranındadır; Android 13+ sistem ayarlarındaki "Uygulama dili" listesinde de görünür. Yeni dil eklemek için `res/values-xx/strings.xml` ve `res/xml/locales_config.xml` yeterlidir.

## 2. Özellik analizi

Grup A: telefonun kendi donanımıyla doğrudan. Grup B: kamera, GPS, algoritma veya kalibrasyonla yaklaşık. Grup C: harici donanım, çevrimiçi veri veya profesyonel cihaz gerektirir.

| Özellik | Donanım | Android API / kütüphane | İnternet | İzin | Gerçekçilik | Doğruluk sınırı | Zorluk | Grup | Durum |
|---|---|---|---|---|---|---|---|---|---|
| Pusula | Manyetometre, ivmeölçer | ROTATION_VECTOR, GeomagneticField | Hayır | Konum (yalnız gerçek kuzey) | Gerçek | ±2–5°, parazite duyarlı | Orta | A | ✅ Aşama 2 |
| Su terazisi / çekül | İvmeölçer | TYPE_GRAVITY | Hayır | — | Gerçek | ~±0,1–0,3° kalibrasyonla | Düşük | A | ✅ |
| Eğim / açı ölçer | İvmeölçer | TYPE_GRAVITY | Hayır | — | Gerçek | ~±0,2° | Düşük | A | ✅ |
| Ortam ışığı | Işık sensörü | TYPE_LIGHT | Hayır | — | Gerçek | %10–20, doyma | Düşük | A | ✅ |
| Işık (kamera ile) | Kamera | CameraX pozlama | Hayır | Kamera | Yaklaşık | Kalibrasyon şart | Orta | B | Aşama 3 |
| Desibel ölçer | Mikrofon | AudioRecord (UNPROCESSED) | Hayır | Mikrofon | Göreli (dBFS) | dB SPL yalnız kullanıcı kalibrasyonuyla | Orta | B | ✅ |
| Barometre | Barometre | TYPE_PRESSURE | Hayır | — | Gerçek | ±0,1 hPa göreli | Düşük | A | ✅ |
| Barometrik irtifa | Barometre | Standart atmosfer | Hayır | — | Yaklaşık | Hava ile onlarca m kayma | Düşük | B | ✅ |
| GPS irtifası | GPS | Fused / LocationManager, MSL (API 34) | Hayır | Konum | Yaklaşık | ±10–30 m dikey | Düşük | B | ✅ |
| Adım sayacı | Adım sensörü / ivmeölçer | TYPE_STEP_COUNTER | Hayır | Fiziksel aktivite | Gerçek | ±%5–10 | Orta | A | ✅ |
| GPS hız/mesafe | GPS | Fused Location | Hayır | Konum | Gerçek | ±%2–5 açık alanda | Orta | A/B | ✅ |
| Manyetik alan | Manyetometre | TYPE_MAGNETIC_FIELD | Hayır | — | Gerçek | ±1–5 µT; metal dedektörü değil | Düşük | A | ✅ |
| Birim dönüştürücü | — | Saf Kotlin | Hayır | — | Kesin | Tanımlı katsayılar | Düşük | A | ✅ |
| Sensör merkezi | Tümü | SensorManager, PackageManager | Hayır | — | Gerçek | — | Düşük | A | ✅ |
| Ölçüm geçmişi | — | Room, SAF | Hayır | — | — | — | Orta | A | ✅ CSV/JSON |
| Gökyüzü haritası | Pusula, ivmeölçer, GPS | Efemeris + 5.044 yıldız, 88 takımyıldız, 121 derin uzay nesnesi | Hayır | Konum (veya elle) | Gerçek (hesaplama) | Gezegen ~1′, Ay ~2′; ekran eşleme pusula doğruluğuna bağlı (±2–5°) | Yüksek | A/B | ✅ Aşama 3 |
| Gezegen asistanı + "Bu gece" | GPS | Schlyter efemerisi, gece penceresi, görünürlük | Hayır | Konum (veya elle) | Gerçek | Hava/ışık kirliliği bilinmez | Orta | A | ✅ Aşama 3 |
| AR mesafe, uzunluk, alan, yükseklik farkı | Kamera, ARCore | ARCore hit-test (düzlem > derinlik > nokta) | Hayır | Kamera | Yaklaşık | ~cm/m, 3 m içinde; desteksiz cihazda kapalı | Yüksek | B | ✅ Aşama 3 |
| Referansla boyut | Kamera | CameraX + oran | Hayır | Kamera | Yaklaşık | Aynı düzlem şart; en iyi "Sınırlı" | Orta | B | ✅ Aşama 3 |
| Açı ile yükseklik ve mesafe | İvmeölçer (+kamera nişangâhı) | Trigonometri + hata yayılımı | Hayır | Kamera (isteğe bağlı) | Yaklaşık | ±0,5° → aralık gösterilir | Orta | B | ✅ Aşama 3 |
| Alan ve hacim hesaplayıcı | — | Saf Kotlin | Hayır | — | Kesin (girdiye göre) | Ölçülerin doğruluğuna bağlı | Düşük | A | ✅ Aşama 3 |
| Düşük ışık modu | Kamera | CameraX pozlama + Gece eklentisi + ekran kazancı | Hayır | Kamera | Görüntü iyileştirme | Termal/kızılötesi DEĞİL | Orta | B | ✅ Aşama 3 |
| Ton üreteci, tarama, gürültü, sol/sağ testi | Hoparlör/kulaklık | AudioTrack, gerçek zamanlı sentez | Hayır | — | Gerçek | Çıkış seviyesi kalibresiz (dBFS) | Düşük | A | ✅ Aşama 3 |
| Tiz ses öz denemesi | Hoparlör/kulaklık | Yükselen ton | Hayır | — | Eğlence amaçlı | Tıbbi değil, "Güvenilir değil" etiketli | Düşük | B | ✅ Aşama 3 |
| İşitme taraması | Kulaklık | AudioTrack | Hayır | — | Tarama | Kalibrasyonsuz dB HL yok | Orta | B | Aşama 4 |
| Görme taraması | Ekran | DisplayMetrics + ekran kalibrasyonu | Hayır | — | Tarama | Reçete üretmez | Orta | B | Aşama 4 |
| Kamera ile nabız (PPG) | Kamera + flaş | CameraX analiz | Hayır | Kamera | Deneysel | Tıbbi cihaz değil | Yüksek | B | Aşama 4 |
| Titreşim + FFT | İvmeölçer | SENSOR_DELAY_FASTEST, FFT | Hayır | — | Yaklaşık | Örnekleme ~100–500 Hz | Orta | B | Aşama 4 |
| Sıcaklık / nem | Nadiren sensör | TYPE_AMBIENT_TEMPERATURE, BLE | Seçenekli | Bluetooth | Çoğu telefonda yok | Pil sıcaklığı kullanılmaz | Orta | C | Aşama 4 |
| Rüzgâr hızı | — | Meteoroloji API, BLE anemometre | Evet | İnternet/Bluetooth | Telefon ölçemez | Kaynak + zaman damgası | Orta | C | Aşama 4 |
| Hava kalitesi | — | API, BLE PM2.5/CO2 | Evet | — | Telefon ölçemez | — | Orta | C | Aşama 4 |
| Gün doğumu/batımı, alacakaranlık, Ay evresi | — | Efemeris + kesişim araması | Hayır | Konum (veya elle) | Gerçek | ±1 dk; ufuk engelleri hesaba katılmaz | Düşük | A | ✅ Aşama 3 |
| Meteor yağmurları | — | IMO listesi (sabit tablo) | Hayır | — | Gerçek | Zirve ±1 gün | Düşük | A | ✅ Aşama 3 |
| Tutulmalar, uydu geçişleri | — | Besselian elemanları / TLE | Evet (TLE) | — | Gerçek | — | Yüksek | C | Aşama 4 |
| QR / barkod | Kamera | ML Kit (gömülü) | Hayır | Kamera | Gerçek | — | Düşük | A | Aşama 4 |
| NFC | NFC | NfcAdapter | Hayır | — | Gerçek | — | Düşük | A | Aşama 4 |

## 2b. Astronomi doğrulaması (Aşama 3)

Hesaplama motoru Jean Meeus'un *Astronomical Algorithms* kitabındaki çözümlü örneklere karşı test edildi:

| Test | Referans | Sensora hatası |
|---|---|---|
| Yıldız zamanı (örnek 12.a) | 197,693195° | < 0,00001° |
| Güneş konumu (örnek 25.a) | RA 198,38083°, Dec −7,78507° | 0,015′ / 0,012′ |
| Venüs konumu (örnek 33.a) | RA 316,17291°, Dec −18,88801° | 0,12′ / 0,03′ |
| Ay konumu (örnek 47.a) | RA 134,68847°, Dec 13,76837° | 1,35′ / 0,24′ |
| Presesyon (örnek 21.b) | θ Persei, 2028 | < 0,002° |
| Dolunay 23.04.2024 23:49 UTC, yeni ay 08.04.2024 18:21 UTC | Bilinen olaylar | < 3 saat (test toleransı) |
| Ekvator gün uzunluğu, Ankara gün dönümü, Tromsø kutup günü/gecesi | Bilinen değerler | Geçti |

Katalog verisi: d3-celestial (BSD-3), temelinde Hipparcos / Yale Parlak Yıldız Kataloğu (kamu malı) ve Messier kataloğu. Takımyıldız adları 5 dilde yerelleştirilmiş olarak gelir.

## 2c. Sağlık modülü: hastane standartlarıyla ilişki (Aşama 4a)

Telefon donanımı kalibre tıbbi cihaz değildir; bu yüzden uygulama iki farklı şekilde hastane standartlarına bağlanır:

**A. Hastane prosedürünü uygulayan ölçümler** — yöntem hastanedekiyle aynı, ölçüm aracı farklıdır:

| Test | Uygulanan standart | Telefonun ölçebildiği | Ölçemediği |
|---|---|---|---|
| Üfleme testi | ATS/ERS 2019 spirometri prosedürü: patlayıcı başlangıç, kesintisiz üfleme, ≥6 sn (çocuk ≥3 sn), ≥3 kabul edilebilir deneme, tekrarlanabilirlik, en fazla 8 deneme | Üfleme süresi, göreli güç, zirveye süre, kesinti, tekrarlanabilirlik | FEV1, FVC, PEF (litre) — tanı |

**B. Hastane sonuçlarını kılavuza göre yorumlayan modül** — kullanıcının kendi hastane/eczane sonuçları:

| Alan | Kılavuz / standart | Doğrulama |
|---|---|---|
| Açlık glukozu, HbA1c | ADA Standards of Care | Sınır değer testleri (100/126 mg/dL, 5,7/6,5 %) |
| Lipidler | NCEP ATP III | LDL kategori sınırları |
| Böbrek | KDIGO, CKD-EPI 2021 (ırk bağımsız) | NKF hesaplayıcısıyla birebir (91 ve 51 mL/dk) |
| D vitamini | Endocrine Society | — |
| Kritik değerler (K, Na, Hb, PLT, glukoz…) | Yaygın laboratuvar "panik değer" uygulaması | K 6,3 → acil, 5,6 → yakında |
| Tansiyon | ESH 2023 ofis kategorileri, ev ölçümü eşiği 135/85 | Kategori sınırları, izole sistolik |
| SpO₂ | BTS oksijen kılavuzu | ≤92 acil, 93–94 görüş |
| Spirometri raporu | ATS/ERS 2022 yorumlama (z-skoru/LLN), GOLD evreleri, bronkodilatör yanıtı (2022: >%10 beklenen; eski: ≥%12 ve ≥200 mL) | Patern, şiddet ve GOLD testleri |
| Peak flow | NHLBI astım eylem planı bölgeleri | %80 / %50 sınırları |
| Odyogram | WHO 2021 işitme dereceleri (4 frekans ortalaması, iyi kulak), asimetri, gürültü çentiği (Coles) | Derece sınırları, asimetri, çentik |
| Görme keskinliği | ICD-11 görme bozukluğu kategorileri (Snellen/ondalık/logMAR dönüşümü) | 6/12, 6/18, 6/60, 3/60 sınırları |
| Gözlük reçetesi | IMI miyopi tanımları (SE ≤ −0,50 D; yüksek ≤ −6,00 D) | Sferik eşdeğer |

Her sonuç dört aciliyet düzeyinden biriyle verilir: olağan / doktorla konuşun / birkaç gün içinde / bugün (acil). Belirtiler varsa değerlerden bağımsız olarak 112 uyarısı her ekranda bulunur.

**C. Yeni ölçümler (Aşama 4b)**

| Test | Yöntem / standart | Ölçebildiği | Ölçemediği |
|---|---|---|---|
| Nabız (kamera) | Parmak ucu fotopletismografisi (PPG), 30 sn; otokorelasyon + tepe tespiti çapraz doğrulama | Dinlenme nabzı, deneysel HRV (RMSSD); sinyal kötüyse sonuç yok | Ritim bozukluğu, SpO₂, tansiyon, kan değerleri |
| Uyku testi | Telefonla aktigrafi (dakikalık hareket, medyana uyarlanır eşik) + ses seviyesinden ritmik horlama tespiti; öneriler: NSF / AASM süreleri, uyku verimi eşiği %85 | Yatakta süre, uykuya dalma, tahmini uyku, verim, uyanmalar, horlama oranı | Uyku evreleri, oksijen, apne (polisomnografi gerekir) |
| Sismograf | İvmeölçer (en yüksek hız), tepe ivme, RMS, FFT; şiddet tahmini Wald ve ark. 1999 | Göreli sarsıntı/titreşim, baskın frekans | Resmi deprem büyüklüğü, erken uyarı |
| Son depremler | AFAD apiv2 (Türkiye, resmi), USGS GeoJSON akışı (dünya) | Resmi büyüklük, derinlik, yer, zaman | — |

**D. Tarama testleri (Aşama 4c)**

| Test | Hastane yöntemi | Telefonda ne anlamlı | Ne verilmez |
|---|---|---|---|
| İşitme taraması | Modifiye Hughson-Westlake (ASHA 2005, ISO 8253-1): kesikli tonlar, −10/+5 dB, 2 yükselen yanıtla eşik; 250 Hz–8 kHz; sessiz kontrol denemeleri; 1 kHz tekrar | Aynı kulaklık/ses düzeyinde kulaklar arası fark (≥15 dB iki komşu frekansta veya ≥20 dB tek frekansta), kişisel zaman takibi | dB HL eşikleri, WHO işitme derecesi |
| Görme keskinliği | ETDRS skorlaması (satır başı 5 optotip, 0,02 logMAR/optotip), yön değiştiren E, çerçeveli tek optotip; banka kartıyla ekran kalibrasyonu | logMAR, Snellen/ondalık; 3 m'de ICD-11 kategorisi | Gözlük numarası |
| Kontrast | Pelli–Robson yöntemi (0,15 log adımlar, üçlüler, ≥2/3 doğru) | log CS; normal ≥1,65 | Kalibre luminans |
| Renk görme | Renk bilimiyle üretilen pseudo-izokromatik plakalar (Smith–Pokorny eş-nokta karıştırma çizgileri, eşit ortalama luminans + gürültü); Ishihara kopyası değildir | Kırmızı–yeşil / mavi–sarı şüphesi, kontrol plakasıyla geçerlilik | Tip ve derece (anomaloskop) |
| Amsler ızgarası | 5 mm kareler (30 cm'de ≈1°), merkez nokta | Çarpıklık/eksik alan bildirimi | — |

AFAD zaman damgaları Türkiye saati (UTC+3) olarak yorumlanır; bu, gerçek cihazda AFAD sitesindeki saatle karşılaştırılarak doğrulanmalıdır (bkz. test planı 39).

**Mevzuat notu:** Tanı koyan veya tedavi öneren yazılım AB MDR ve TİTCK kapsamında tıbbi cihaz (SaMD) sayılır ve onay gerektirir. Sensora bu nedenle bilgilendirme ve tarama aracı olarak tasarlandı: tanı koymaz, reçete üretmez, her yorumda doktora yönlendirir. Mağazada "tıbbi cihaz" veya "hastane eşdeğeri" ifadeleri kullanılmamalıdır.

## 3. Mimari

```
app/src/main/java/com/sensora/app
├── core/                 ← Android'den bağımsız ölçüm motoru (JVM'de test edilir)
│   ├── model/            Quality (İyi / Sınırlı / Güvenilir değil)
│   ├── math/             OrientationMath, AtmosphereMath, AudioMath, GeoMath,
│   │                     TrackAccumulator, UnitConverter, Statistics, QualityRules
│   ├── steps/            StepDayTracker (gün ayrımı + yeniden başlatma), AccelStepDetector
│   ├── camera/           AR geometrisi (Newell alanı), açı yöntemi + hata yayılımı, referans oranı, şekiller
│   ├── audio/            Tıklamasız stereo ton/gürültü sentezleyici
│   ├── hearing/          Hughson-Westlake eşik arama, kulaklar arası asimetri, güvenilirlik
│   ├── vision/           Optotip geometrisi, ETDRS ve Pelli–Robson skorlama, renk bilimi, plaka üretici
│   ├── health/           Laboratuvar referansları, eGFR, ESH, BTS, ATS/ERS, GOLD, WHO, ICD-11, üfleme dedektörü
│   ├── astro/            Zaman, koordinatlar, presesyon, efemeris (Güneş/Ay/8 gezegen),
│   │                     doğuş-batış, alacakaranlık, Ay evresi, gece planlayıcı, katalog
│   └── sensor/           SensorReaders: sensör, konum, mikrofon için soğuk Flow'lar
├── data/                 Room (MeasurementRecord), AppPrefs, Exporters (CSV/JSON)
└── ui/
    ├── catalog/          Araç kataloğu, kategoriler, cihaz uyumluluğu
    ├── common/           Ortak bileşenler, izin kapısı, grafikler, kalite rozeti
    ├── home/             Ana ekran (arama, favoriler, son kullanılanlar, kategoriler)
    ├── tools/            Her ölçüm aracı için ekran
    ├── astro/            Gökyüzü haritası, Bu gece, Güneş ve Ay
    ├── health/           Üfleme testi, hastane sonuç yorumlayıcı
    ├── camera/           Kamera önizleme, açı ile ölçüm, referansla boyut, alan/hacim, düşük ışık
    └── ar/               ARCore oturumu, OpenGL kamera arka planı, ölçüm noktaları
    ├── sensors/          "Cihazım Neleri Ölçebilir?"
    ├── history/          Geçmiş, filtre, CSV/JSON
    ├── settings/         Dil, tema, gizlilik
    ├── theme/            Material 3 açık/koyu tema
    └── nav/              Navigation Compose
```

**Temel kararlar**

- **Sensör yaşam döngüsü:** Her sensör `callbackFlow` ile sarılır ve `collectAsStateWithLifecycle` ile toplanır; ekran görünmez olduğunda dinleyici otomatik olarak kaydı silinir. Bu, pil kuralını mimari düzeyde garanti eder.
- **Kalite kuralları tek yerde:** `QualityRules` her modül için açık eşikler içerir ve birim testlerle doğrulanır. Uydurma yüzde doğruluk gösterilmez.
- **Dürüstlük ilkesi kodda:** gerçek kuzey konum olmadan gösterilmez; kalibrasyonsuz mikrofon dB SPL değil dBFS gösterir ve hiçbir zaman "İyi" olamaz; pil sıcaklığı oda sıcaklığı olarak gösterilmez; ışık sensörü doyduğunda uyarı verir.
- **Minimum Android:** 8.0 (API 26) — aktif cihazların ~%97'si. Hedef API 35.
- **Google Play Hizmetleri olmayan cihazlar** (ör. Huawei) için konum, platformun GPS sağlayıcısına düşer.

## 4. Kabul kriterleri durumu (Aşama 2 sonunda)

| Kriter | Durum |
|---|---|
| Android Studio projesi açılabiliyor | ✅ Standart Gradle projesi + wrapper |
| Temel modüller derleniyor | ⚠️ Çekirdek motor derlendi ve test edildi; tam APK derlemesi GitHub Actions'ta doğrulanacak |
| Sensörler gerçek veri üretiyor | ⏳ Gerçek cihazda test edilmeli (bkz. TEST_PLANI.md) |
| Eksik donanımda çökmeden alternatif | ✅ Her araçta kontrol + açıklayıcı kart |
| Hesaplamalar doğru | ✅ 35 birim testi geçti |
| Ölçüm geçmişi | ✅ Room + CSV/JSON |
| Telefon/tablet uyumu | ✅ Uyarlanabilir ızgara, 720 dp içerik genişliği |
| İzin yönetimi | ✅ Yalnızca ilgili ekranda, reddedilince ayarlara yönlendirme |
| Çevrimdışı | ✅ Aşama 2 araçlarının hiçbiri internet kullanmaz |
| Yanıltıcı ölçüm yok | ✅ Yukarıdaki dürüstlük kuralları |

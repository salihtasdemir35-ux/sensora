# Sensora — Akıllı Ölçüm Asistanı

Telefonu veya tableti çok amaçlı bir ölçüm cihazına dönüştüren Android uygulaması. Kotlin + Jetpack Compose + Material 3. Her ölçüm; değerini, yöntemini, kalitesini (İyi / Sınırlı / Güvenilir değil) ve sınırlarını açıkça gösterir.

**Diller:** English · Türkçe · Deutsch · Español · العربية
**Minimum Android:** 8.0 (API 26) · **Hedef:** API 35

## Çalışan araçlar (Aşama 2 + 3)

İşitme taraması (Hughson-Westlake) · Görme taraması (ETDRS, Pelli–Robson, renk, Amsler) · Kamera ile nabız · Uyku testi (gece boyu) · Deprem ve titreşim (sismograf, FFT, AFAD/USGS) · Üfleme testi (ATS/ERS prosedürü) · Hastane sonuç yorumlayıcı (kan tahlili, tansiyon, SpO₂, spirometri, peak flow, odyogram, görme) · AR ile ölçüm (ARCore) · Açı ile yükseklik ve mesafe · Referansla boyut · Alan ve hacim hesaplayıcı · Düşük ışık kamerası · Ton üreteci ve ses testi · Canlı gökyüzü haritası · Bu gece ne görebilirim? (gezegenler, kümeler, bulutsular, galaksiler, meteor yağmurları) · Güneş ve Ay (alacakaranlık, altın/mavi saat, Ay evresi) · Pusula (gerçek kuzey dahil) · Su terazisi + çekül · Eğim/açı ölçer · Işık ölçer · Ses seviyesi ölçer · Barometre + irtifa + GPS irtifası · Adım sayar · GPS hız ve mesafe · Manyetik alan · Birim dönüştürücü · Cihazım Neleri Ölçebilir? · Ölçüm geçmişi (CSV/JSON)

Planlanan aşamalar ve teknik analiz için `docs/TEKNIK_ANALIZ.md`, testler için `docs/TEST_PLANI.md`.

## APK almanın en kolay yolu: GitHub Actions

1. GitHub'da yeni bir depo oluşturun (ör. `sensora`).
2. Bu klasördeki **tüm** dosyaları (gizli `.github` ve `.gitignore` dahil) depoya yükleyin.
3. **Actions** sekmesinde "Build Sensora APK" çalışır (veya *Run workflow* ile elle başlatın).
4. Bitince çalıştırmanın altındaki **Artifacts → sensora-apk** dosyasını indirin.
   - `app-debug.apk` → telefona doğrudan kurulur (Bilinmeyen kaynaklara izin verin).
   - `app-release-unsigned.apk` → imzasız; Play Store için aşağıdaki imzalama gerekir.

## Android Studio ile

1. *File → Open* ile bu klasörü açın, Gradle senkronizasyonunu bekleyin.
2. Telefonu USB hata ayıklama ile bağlayıp ▶ Run.
3. Komut satırı: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## Release imzalama (Google Play)

1. Bir kez anahtar oluşturun (anahtarı **asla** depoya koymayın, yedekleyin):
   `keytool -genkey -v -keystore sensora.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sensora`
2. GitHub → Settings → Secrets and variables → Actions içine ekleyin:
   `SENSORA_KEYSTORE_BASE64` (`base64 -w0 sensora.jks` çıktısı), `SENSORA_KEYSTORE_PASSWORD`, `SENSORA_KEY_ALIAS`, `SENSORA_KEY_PASSWORD`.
3. Sonraki çalıştırmada imzalı `app-release.apk` ve Play Store için `app-release.aab` üretilir.

**Sürüm yükseltme:** `app/build.gradle.kts` içinde her yayında `versionCode` değerini 1 artırın, `versionName`'i güncelleyin.
**Not:** Google Play, yeni uygulamalar için hedef API seviyesini her yıl yükseltiyor; yayından önce güncel şartı kontrol edip `targetSdk` değerini gerekirse artırın.

## İsim ve simge

İsim ve simge kolayca değiştirilebilir: `res/values*/strings.xml` içindeki `app_name`, simge için `res/drawable/ic_launcher_*.xml`. Mağazada yayınlamadan önce "Sensora" adının Play Store'da ve marka tescilinde boş olduğunu kontrol edin.

## Veri kaynakları

Yıldız, takımyıldız ve derin uzay verisi: [d3-celestial](https://github.com/ofrohn/d3-celestial) (BSD-3, © Olaf Frohn); ayrıntılar `app/src/main/assets/sky/LICENSE-DATA.txt`. Gezegen hesapları: Paul Schlyter yöntemi; doğrulama: J. Meeus, *Astronomical Algorithms*.

## Sağlık özellikleri ve mevzuat

Sağlık araçları bilgilendirme ve tarama amaçlıdır; tanı koymaz. Ayrıntılı standart eşleştirmesi ve mevzuat notu: `docs/TEKNIK_ANALIZ.md` bölüm 2c.

## Gizlilik

İnternet yalnızca deprem listesi için kullanılır (AFAD/USGS'nin herkese açık listesi indirilir; konum gönderilmez). Tüm diğer veriler cihazda kalır. Mikrofon sesi ve kamera görüntüsü kaydedilmez. Konum yalnızca ilgili ekran açıkken kullanılır. Reklam ve izleme yoktur.

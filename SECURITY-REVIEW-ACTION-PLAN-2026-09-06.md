# Deploy Öncesi Eylem Planı — Değiştirilecekler / Eksikler / Tamamlananlar

**Tarih:** 2026-09-06
**Amaç:** Üç denetim raporunun tek bir çalışma listesine indirgenmiş hali. Detaylı gerekçe ve kod örnekleri için kaynak raporlara bakın; burası **ne yapılacağının** listesi.

| Kaynak rapor | İçerik |
|---|---|
| [SECURITY-REVIEW-2026-09-06.md](SECURITY-REVIEW-2026-09-06.md) | Ana denetim — OWASP, iyzico, iş mantığı, altyapı |
| [SECURITY-REVIEW-ADDENDUM-iyzipay-java.md](SECURITY-REVIEW-ADDENDUM-iyzipay-java.md) | Resmî `iyzipay-java` ile satır satır karşılaştırma |
| [SECURITY-REVIEW-VERIFICATION-2026-09-06.md](SECURITY-REVIEW-VERIFICATION-2026-09-06.md) | Resmî dokümana karşı doğrulama — **çelişki halinde bu bağlayıcı** |

**Durum özeti:** 🔴 6 kritik · 🟠 7 yüksek · 🟡 12 orta · ⚪ 6 düşük · ✅ 19 alan temiz

---

# BÖLÜM 1 — DEĞİŞTİRMEM GEREKENLER

## 🔴 Katman 0 — Bugün başlatın (tedarik süresi var, kod değil)

| ✓ | Madde | Ne yapılacak | Bulgu |
|---|---|---|---|
| ☐ | iyzico panel | **Ayarlar → Üye İşyeri Ayarları → Üye İşyeri Bildirimleri** → webhook URL'ini production adresine ayarla | V-4 |
| ☐ | iyzico entegrasyon ekibi | `entegrasyon@iyzico.com`'a **webhook signature** özelliğinin aktivasyonu için talep gönder — **hem sandbox hem prod hesabı için** | V-4 |
| ☐ | Onay bekle | Özellik açılmadan `X-IYZ-SIGNATURE-V3` hiç gelmez; kod satır 337-339'da 401 döner ve aşağıdaki hiçbir düzeltmeyi **test edemezsiniz** | V-4 |

> Bu üç madde diğer her şeyin önkoşulu. E-posta trafiği gün alabilir — paralel olarak Katman 1'i yazmaya başlayın, ama sandbox doğrulaması buna bağlı.

---

## 🔴 Katman 1 — Webhook sözleşmesi (tek iş kalemi, parçalamayın)

Aşağıdaki altı maddenin her biri **tek başına** her webhook'u reddetmeye yeter. Birini düzeltip diğerini bırakmak hiçbir şey kazandırmaz.

**Dosya:** `backend/src/main/java/com/ykskocluk/demo/dto/IyzicoWebhookRequest.java`

| ✓ | Satır | Sorun | Yapılacak | Bulgu |
|---|---|---|---|---|
| ☐ | 8-9 | `@NotNull Long paymentId` — HPP payload'ında bu alan **yok** | Kaldır veya Direct formatı için `String paymentId` yap | K-1 |
| ☐ | 13-14 | `providerReference` — iyzico payload'ında **hiç yok** | Kaldır; yerine `iyziPaymentId` kullan | V-5 |
| ☐ | tümü | `token`, `iyziPaymentId`, `merchantId`, `iyziReferenceCode`, `iyziEventTime` alanları tanımlı değil | Ekle, hepsine `@JsonProperty` ver | İtiraz 2 |
| ☐ | 20-22 | 3 argümanlı kısa constructor | Testlerdeki çağıranları kontrol et: `grep -rn "new IyzicoWebhookRequest(" backend/src/test` | — |

**Dosya:** `backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java`

| ✓ | Satır | Sorun | Yapılacak | Bulgu |
|---|---|---|---|---|
| ☐ | 313 | `!"PAYMENT_API".equals(...)` — Checkout Form'da `CHECKOUT_FORM_AUTH` gelir | `HANDLED_EVENT_TYPES = Set.of("CHECKOUT_FORM_AUTH")`; eşleşmeyen → **200 + no-op** | V-1 |
| ☐ | 316-320 | `paymentId.toString().equals(paymentConversationId)` — `paymentId` yok, NPE riski | Kaldır; `paymentConversationId` doğrudan bizim `Payment.id`'miz | K-1 |
| ☐ | 321-325 | `PROVIDER_REFERENCE_MISSING` her SUCCESS'te tetiklenir | `iyziPaymentId != null` kontrolüne çevir | V-5 |
| ☐ | 347, 351 | İmza dizilimi Direct formatına ait, `token` eksik | HPP: `secretKey + iyziEventType + iyziPaymentId + token + paymentConversationId + status`; `iyziEventType`'a göre dallan | İtiraz 2, V-6 |
| ☐ | 367 | `findById(request.paymentId())` | `findById(request.localPaymentId())` — conversationId'den parse | K-1 |
| ☐ | 332-360 | Yorum eksik | Metoda "bu şema `ResponseSignatureGenerator`'dan **kasıtlı olarak** farklı, ayraçsız" notunu düş — aynı yanlış alarmın tekrarını önler | İtiraz 1 |

**Değiştirilmeyecek** (doğru çalışıyor): satır 351'deki **birleştirme biçimi** (ayraçsız), `calculateHmacSha256` (hex/küçük harf), satır 354-356 `MessageDigest.isEqual`.

---

## 🔴 Katman 2 — Sağlayıcı teyidi (K-3)

| ✓ | Dosya | Yapılacak | Bulgu |
|---|---|---|---|
| ☐ | `integration/IyzicoClient.java` | `CheckoutVerification verifyCheckout(String token)` metodunu arayüze ekle | K-3 |
| ☐ | `integration/RealIyzicoClient.java` | `CheckoutForm.retrieve()` + `form.verifySignature(secretKey)` implementasyonu | K-3, Ek §2.1 |
| ☐ | `integration/StubIyzicoClient.java` | Stub karşılığını ekle (aksi halde local/test derlenmez) | — |
| ☐ | `SubscriptionService` | `paidPrice` + `currency` beklenen tutarla karşılaştır → uyuşmazlıkta `PAYMENT_AMOUNT_MISMATCH` | K-3 |
| ☐ | `SubscriptionService` | `paymentStatus == "SUCCESS"` ayrıca kontrol et | Ek §2.2 |
| ☐ | `SubscriptionService` | `fraudStatus`: `< 0` → erişim açma, `== 0` → PENDING'de bekle, `> 0` → devam | Ek §2.2 |
| ☐ | `integration/RealIyzicoClient.java:116` | `CheckoutFormInitialize.create()` sonrası `verifySignature()` çağır | Ek §2.1 |

> **Kolay kazanç:** `token` webhook payload'ında geliyor → `payments.checkout_token` kolonu **gerekmez**. Ek rapordaki "zorunlu" ifadesi doğrulama sırasında opsiyonele düştü.

---

## 🔴 Katman 3 — Para alındı / hizmet verilmedi (V-3)

**Dosya:** `service/SubscriptionService.java`

| ✓ | Satır | Sorun | Yapılacak |
|---|---|---|---|
| ☐ | 271-275 | `COACH_FULL` istisnası `@Transactional` (satır 305) sınırından geçip **tüm webhook transaction'ını geri alıyor** → ödeme `PENDING` kalıyor → 03:00'te `FAILED` → para alındı, kayıt yok, iade yok | Rollback etme: `Payment`'ı `SUCCESS` kaydet, aboneliği `TERMINATED` + `COACH_FULL_AFTER_PAYMENT` yap, iade olayı yayınla, **200** dön |
| ☐ | ~196 | Checkout aşamasında kontenjan ön-kontrolü yok — pencereyi daraltmıyor | `coach.getActiveStudentCount() >= coach.getMaxStudentCapacity()` ön-kontrolü ekle (garanti değil, çoğu vakayı eler) |
| ☐ | yeni | `PaymentRequiresRefundEvent` + dinleyici | **Karar gerektirir:** otomatik iade mi, admin kuyruğu mu? (bkz. Bölüm 4) |

---

## 🟠 Katman 4 — Teslim edilebilirlik (V-2)

iyzico 15 dk arayla **3 kez** dener, sonra bildirim **kalıcı olarak kaybolur**. Toplam pencere ~45 dk.

**Dosya:** `service/SubscriptionService.java` + `exception/GlobalExceptionHandler.java`

| ✓ | Senaryo | Şu an | Olması gereken |
|---|---|---|---|
| ☐ | Ara durumlar (`INIT_THREEDS`, `CALLBACK_THREEDS`, `BKM_POS_SELECTED`, `INIT_APM`, `INIT_CONTACTLESS`, `INIT_BANK_TRANSFER`, `INIT_CREDIT`, `PENDING_CREDIT`) — satır 363-365 | 400 | **200 + no-op** |
| ☐ | Bizim akışımıza ait olmayan event türleri | 400 | **200 + no-op** |
| ☐ | `paymentConversationId` parse edilemiyor | 400 | **200 + no-op + WARN** |
| ☐ | `PAYMENT_NOT_FOUND` (satır 368) | 404 | **5xx** (retry istensin) + 3. denemede Sentry |
| ☐ | `WEBHOOK_PAYMENT_MISMATCH` (satır 371) | 409 | **200 + no-op + Sentry** |
| ☐ | İmza geçersiz (satır 358) | 401 | **401 (değiştirme)** + `log.error` + Sentry alarmı |
| ☐ | `IyzicoWebhookResponse` | — | `ignored(String)` fabrika metodu ekle |

---

## 🟠 Katman 5 — Yenileme fail-open (Y-1, Y-2)

| ✓ | Dosya:satır | Sorun | Yapılacak |
|---|---|---|---|
| ☐ | `SubscriptionBillingService.java:106` | `iyzicoClient.charge()` throw ediyor → tx2 hiç çalışmıyor → abonelik **süresiz ACTIVE** kalıyor → ödemesiz sınırsız erişim | Sağlayıcı istisnasını **başarısız tahsilat** say: `catch (PaymentProviderException) → ChargeResult.failed(...)` → `finalizeCharge` çalışsın → PAST_DUE/EXPIRED akışı işlesin |
| ☐ | `SubscriptionRenewalJob.java:69-73` | İstisnayı yutup `continue` diyor | Yukarıdaki düzeltmeyle birlikte istisna oraya ulaşmaz; catch bloğu son çare olarak kalsın |
| ☐ | yeni | Sessiz gelir kaybına karşı savunma yok | **Watchdog:** `status = ACTIVE AND end_at < now() - interval '2 days'` → boş değilse Sentry alarmı |
| ☐ | `SubscriptionService.java:~205` | `savedCardToken = "stub-card-token-" + UUID` — gerçek kart yok | **Karar gerektirir** (bkz. Bölüm 4) |

---

## 🟠 Katman 6 — Kullanıcı dönüş akışı (K-2)

| ✓ | Dosya | Yapılacak |
|---|---|---|
| ☐ | `controller/PaymentController.java` | Ayrı `POST /api/v1/payments/iyzico/callback`, `consumes = APPLICATION_FORM_URLENCODED_VALUE`, `@RequestParam("token")` |
| ☐ | `security/SecurityConfig.java:66-77` | Yeni callback path'ini `permitAll()` listesine ekle |
| ☐ | `config/IyzicoProperties` + `application.yml:263` | `IYZICO_CALLBACK_URL`'i webhook değil callback adresine yönlendir |
| ☐ | `docs/iyzico-sandbox-test-guide.md` | Satır 25, 42, 156 — callback ≠ webhook ayrımını düzelt |

> Doğrulama sonrası **önceliği düştü**: webhook `token` taşıdığı için para akışı bu olmadan da tamamlanır. Kalan etki kullanıcı deneyimi — ödeme sonrası tarayıcı JSON endpoint'ine form-POST atıp hata ekranı görüyor.

---

## 🟠 Katman 7 — Kütüphane kullanımı (Ek rapor)

| ✓ | Dosya:satır | Yapılacak | Bulgu |
|---|---|---|---|
| ☐ | `RealIyzicoClient.java:144-182` | `Refund.create` → **`Refund.createV2`** (`CreateRefundV2Request`, sadece `paymentId` ister) — Y-5'teki `paymentTransactionId` problemi tamamen kalkar | Y-5, Ek §4 |
| ☐ | `RealIyzicoClient.java:163` | `Refund.verifySignature(secretKey)` çağır | Ek §4 |
| ☐ | `RealIyzicoClient.java:154-160` | `RefundReason` + `description` ekle; `setIp("127.0.0.1")` → gerçek IP | Ek §4 |
| ☐ | `RealIyzicoClient.java:81-102` | Sahte `Buyer` ("John Doe", sabit TCKN/GSM/adres) → gerçek öğrenci verisi + `buyer.setIp()` + `setRegistrationDate()` | Ek §2.3 |
| ☐ | `RealIyzicoClient.java:64-71` | `setEnabledInstallments(List.of(1))` — abonelikte taksit kapalı | Ek §2.4 |
| ☐ | `RealIyzicoClient.java:64-71` | `setForceThreeDS(1)` | Ek §2.5 |
| ☐ | `RealIyzicoClient.java` (tüm çağrılar) | Sınırlı havuz + `Future.get(timeout)` sarmalayıcı — kütüphanenin **140 sn** sabit timeout'u değiştirilemiyor | Y-4 |
| ☐ | `frontend/src/subscriptionCheckout/CheckoutSection.tsx:64-70` | Exact-host allowlist → suffix kuralı (`*.iyzipay.com` / `*.iyzico.com` + https). Gerçek ödeme sayfası host'u (`cpp.iyzipay.com` ailesi) listede yok → checkout reddediliyor | Y-3 |
| ☐ | `backend/pom.xml:195-200` | Kullanılmayan 6 `javax.*` EE API'sini `<exclusions>` ile çıkar (`javax.persistence-api 2.2` dahil) | Ek §1 |

---

## 🟡 Katman 8 — Altyapı ve iş mantığı

| ✓ | Dosya:satır | Yapılacak | Bulgu |
|---|---|---|---|
| ☐ | `security/SecurityConfig.java:49-58` | HSTS'yi açıkça tanımla (`includeSubDomains`, `maxAge`) + `Permissions-Policy` + `Cross-Origin-Opener-Policy` ekle | O-2 |
| ☐ | `security/RefreshTokenCookieService.java:70-75` | `Origin` yokken `Sec-Fetch-Site` kontrolü ekle | O-4 |
| ☐ | `config/` (yeni) | `RateLimitProductionConfigurationValidator` — prod'da `store=REDIS` iken `REDIS_URL` localhost ise başlatma | O-3 |
| ☐ | `controller/RefundRequestController.java:21-26` | `actionRateLimit.checkRefundRequestCreate(studentId)` + `application.yml`'e limit tanımı | O-6 |
| ☐ | `service/RefundPolicy.java` | İfaya başlanmış hizmette pro-rata iade — **karar gerektirir** (bkz. Bölüm 4) | O-5 |
| ☐ | `service/SubscriptionService.java:300-303` | İmzasız `processWebhook(request)` overload'ını `package-private` yap | D-2 |
| ☐ | `frontend/package.json` | `npm audit fix` — `browserslist` (devDependency, high) | O-8 |

---

# BÖLÜM 2 — EKSİKLER

Kodda **hiç olmayan** şeyler. Bunlar "düzeltme" değil, "yokluk".

## 🔴 Ortam değişkenleri (prod deploy'da set edilmeli)

| ✓ | Değişken | Değer | Neden | Bulgu |
|---|---|---|---|---|
| ☐ | `SERVER_FORWARD_HEADERS_STRATEGY` | `framework` | Default `none` → `request.isSecure()` false → **HSTS header'ı hiç gönderilmez** + OAuth2 callback kırık | O-2 |
| ☐ | `RATE_LIMIT_TRUST_PROXY_HEADERS` | `true` | Default `false` → proxy arkasında tüm istekler tek IP → IP limitleri tek kovaya düşer | O-1 |
| ☐ | `RATE_LIMIT_TRUSTED_PROXY_CIDRS` | Railway çıkış CIDR'leri | CIDR listesi olmadan yukarıdakini **açmayın** — kod `X-Forwarded-For`'un soldaki (spoof edilebilir) girdisini alıyor | O-1 |
| ☐ | `REDIS_URL` | Gerçek Redis (`rediss://` tercih) | Unutulursa `IN_MEMORY_FALLBACK` sessizce devreye girer, limitler replica başına ayrışır | O-3 |
| ☐ | `IYZICO_ENABLED` / `IYZICO_MODE` / `IYZICO_BASE_URL` | `true` / `production` / `https://api.iyzipay.com` | — | — |
| ☐ | `IYZICO_API_KEY` / `IYZICO_SECRET_KEY` | Production değerleri | Sandbox anahtarıyla çıkılmadığını doğrula | — |
| ☐ | `IYZICO_CALLBACK_URL` | Public HTTPS callback (webhook değil) | Validator localhost/private IP'yi zaten reddediyor | K-2 |
| ☐ | `CORS_ALLOWED_ORIGINS` | Gerçek frontend origin'i | Wildcard reddediliyor ✅ | — |
| ☐ | `SWAGGER_ENABLED` | set etme veya `false` | Default `false` ✅ — yine de doğrula | — |
| ☐ | `ADMIN_PASSWORD_HASH` | Gerçek BCrypt hash | Validator zorunlu kılıyor | — |
| ☐ | `RESEND_FROM` / `FRONTEND_BASE_URL` / `OAUTH2_FRONTEND_REDIRECT_URI` | Prod değerleri | — | — |
| ☐ | `VITE_API_BASE_URL` (frontend build) | Prod API origin'i | Stub ödeme `import.meta.env.DEV` ile zaten ölü kod ✅ | — |

## 🔴 Secret rotasyonu

`backend/src/main/resources/application-local.yml` gerçek secret'ları düz metin taşıyor ve repo **iCloud senkronize `~/Desktop`** altında. Git geçmişi temiz ✅ ama disk kopyası Apple sunucularında.

| ✓ | Yapılacak |
|---|---|
| ☐ | `NEON_DATABASE_URL` / `NEON_DATABASE_PASSWORD` rotate |
| ☐ | `JWT_SECRET` rotate — ⚠️ tüm access token'ları geçersiz kılar, düşük trafikli saatte yap |
| ☐ | `RESEND_API_KEY` rotate |
| ☐ | `R2_ACCESS_KEY_ID` + `R2_SECRET_ACCESS_KEY` rotate |
| ☐ | `GOOGLE_CLIENT_SECRET` rotate |
| ☐ | Local secret dosyasını repo dışına taşı (`~/.config/yks/` + `SPRING_CONFIG_ADDITIONAL_LOCATION`) |
| ☐ | Repo'yu iCloud senkronizasyonu dışına taşımayı değerlendir (`~/dev/demo`) — denetim boyunca yaşanan dosya okuma yavaşlığını da çözer |

## 🟡 Frontend host güvenlik header'ları

Backend'in CSP'si mükemmele yakın ama **API yanıtlarına** iliştiriliyor. SPA ayrı host'tan servis ediliyorsa kullanıcının HTML yüklediği origin'de hiçbir CSP yok.

| ✓ | Yapılacak |
|---|---|
| ☐ | `vercel.json` / `netlify.toml` / Cloudflare Pages header'ları: CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` |

## 🟡 CI / süreç

| ✓ | Yapılacak | Bulgu |
|---|---|---|
| ☐ | OWASP Dependency-Check ekle (`-DfailBuildOnCVSS=7`) — backend hiç taranmıyor | O-8 |
| ☐ | `.github/dependabot.yml` (maven + npm + github-actions) | O-8 |
| ☐ | `gitleaks detect --source . --log-opts="--all" --redact` — dosya adı taraması temiz ✅ ama **içerik** taraması yapılmadı | Doğrulama |
| ☐ | GitHub secret scanning + push protection aç | — |
| ☐ | Webhook sözleşme testi: gerçek HPP payload'ı ile imza + event type + status matrisi | Yeni |

## 🟡 İzleme

| ✓ | Yapılacak |
|---|---|
| ☐ | Sentry DSN prod'da tanımlı |
| ☐ | Alarm: imzasız/geçersiz imzalı webhook (3 denemede kaybolur) |
| ☐ | Alarm: `PAYMENT_NOT_FOUND` 3. denemede |
| ☐ | Alarm: `COACH_FULL_AFTER_PAYMENT` (para alındı, hizmet açılmadı) |
| ☐ | Alarm: watchdog — `ACTIVE` ama `end_at` 2 günden eski |
| ☐ | Ödeme yolunda structured log: `paymentId`, `conversationId`, `iyziEventType` — kart/imza/token **yok** |
| ☐ | Neon yedek + point-in-time recovery doğrulandı |

## 🟡 Sandbox'ta elle doğrulanacaklar

Dokümandan kesin çıkaramadıklarım. Detaylı liste: [doğrulama raporu §4](SECURITY-REVIEW-VERIFICATION-2026-09-06.md).

| ✓ | Madde | Neden kritik |
|---|---|---|
| ☐ | **`iyziPaymentId` null geldiğinde imza nasıl hesaplanıyor?** | JS'te `undefined` → `"undefined"`, bizim `str()` → `""`. `FAILURE` bildirimlerinde fark ortaya çıkar |
| ☐ | `token` `FAILURE` bildirimlerinde dolu mu? | Aynı gerekçe |
| ☐ | Gerçek bir `X-IYZ-SIGNATURE-V3` header'ı yakala, hesapladığımızla karşılaştır | K-1'in tek gerçek kanıtı |
| ☐ | 3DS akışında `INIT_THREEDS` / `CALLBACK_THREEDS` gerçekten geliyor mu? | V-2'nin aciliyeti buna bağlı |
| ☐ | `CHECKOUT_FORM_AUTH` dışında hangi event türleri düşüyor? | `HANDLED_EVENT_TYPES` kalibrasyonu |
| ☐ | Callback ve webhook hangisi önce geliyor? | İki yolun idempotent kalması gerek |
| ☐ | `getPaymentPageUrl()` hangi host'u dönüyor? | Y-3 allowlist'i |
| ☐ | Java 21'de `verifySignature()` çalışıyor mu? (`DatatypeConverter`) | Patlarsa `jaxb-runtime` gerekir |
| ☐ | `Refund.createV2` yanıtında `currency` null iken imza doğrulaması | Ek §4 notu |
| ☐ | Uçtan uca: başarılı ödeme → ACTIVE, başarısız → PENDING, çift webhook → IDEMPOTENT, kısmi + tam iade | — |

---

# BÖLÜM 3 — TAMAMLANANLAR

Dokunmayın. Bunlar denetimde **temiz çıktı** veya **doğrulamada geri çekildi**.

## ✅ OWASP Top 10 — açık bulunamadı

| Alan | Durum |
|---|---|
| **SQL Injection** | Tüm sorgular JPA/JPQL veya parametreli native query. String birleştirme yok. İki native query de `:named` parametre kullanıyor |
| **XSS** | Frontend'de tek bir `dangerouslySetInnerHTML`, `innerHTML` veya `eval` yok |
| **Token depolama** | Access token **yalnızca bellekte**; refresh token `HttpOnly` + `Secure` + `SameSite=Lax` + `Path=/api/v1/auth`. Eski `localStorage` anahtarları aktif temizleniyor, testi var |
| **IDOR** | Her uçta sahiplik kontrolü: `MediaService.requireOwner`, `NOT_SUBSCRIPTION_OWNER`, `NOT_PAYMENT_OWNER`. Public media token'ı 256-bit `SecureRandom` |
| **Yetkilendirme** | Route (`/api/v1/admin/**` → `hasRole('ADMIN')`) + metot (`@PreAuthorize`) çift katman. `@PreAuthorize`'sız 10 controller'ın hepsi ya public ya sahiplik kontrollü |
| **CSRF** | Bearer API için `disable()` doğru; cookie'li iki uçta `SameSite=Lax` + `Path` daraltma + `Origin` allowlist |
| **SSRF** | `IyzicoProductionConfigurationValidator` provider host allowlist'i + private/loopback callback reddi |

## ✅ Ödeme iş mantığı — doğru kurulmuş

| Alan | Durum |
|---|---|
| **Fiyat manipülasyonu** | Tutar **hiçbir zaman** client'tan gelmiyor; `Package.price` DB'den. `SubscriptionCheckoutRequest`'te tutar alanı yok |
| **Negatif/aşırı tutar** | `INVALID_REFUND_AMOUNT` + `EXCEEDS_REFUNDABLE_AMOUNT` |
| **Idempotency** | `UNIQUE(idempotency_key)` + deterministik key (`checkout:{id}`, `charge:{id}:{date}`, `refund:{id}:{n}`). Webhook tekrarları `IDEMPOTENT` dönüyor |
| **Aşırı iade** | PENDING rezervasyon deseni — eşzamanlı iki tam iade birbirini görüyor |
| **Yarış koşulları** | `findByIdForUpdate`, `incrementActiveStudentCountIfRoom` atomik koşullu UPDATE, `Session.availability_id` UNIQUE, `OPTIMISTIC_FORCE_INCREMENT` |
| **Transaction sınırları** | Dış çağrılar iki commit arasında (tx1 rezerve → external → tx2 finalize), çökme sonrası resume mantığı var |
| **Komisyon** | İşlem anında snapshot'lanıyor |

## ✅ Doğrulamada geri çekilenler — kodunuz doğruymuş

| Bulgu | Kaynak | Sonuç |
|---|---|---|
| "Webhook imza kodumuz resmî `:` ayraçlı şemayla çelişiyor" | Ek rapor §3 | 🔴 **GERİ ÇEKİLDİ** — webhook imzası dokümantasyon gereği ayraçsız. Birleştirme biçiminiz, HMAC anahtarınız ve hex çıktınız **doğru** |
| "`:` ayracı iyzico'nun genel konvansiyonu" | Ek rapor §3 | 🔴 **GERİ ÇEKİLDİ** — genel konvansiyon yok, iki bağımsız şema var |
| "`checkout_token` kolonu **zorunlu**" | Ek rapor §3 | ⚠️ **OPSİYONEL** — token webhook'ta geliyor |
| "İmza şeması ve encoding doğrulanmalı" | Ana rapor K-1 notu | ✅ **ÇÖZÜLDÜ** — HMAC-SHA256, hex, küçük harf; artık açık soru değil |
| "iyzico çağrılarında timeout **yok**" | Ana rapor Y-4 (ilk hali) | ✅ **DÜZELTİLDİ** — timeout var ama 140 sn ve değiştirilemez. Fix'i de değişti: `sun.net.client.*` property'leri işe yaramaz |
| Sabit zamanlı imza karşılaştırması | `SubscriptionService.java:354-356` | ✅ **KÜTÜPHANEDEN İYİ** — `MessageDigest.isEqual` kullanıyorsunuz; iyzico'nun kendi `HashValidator`'ı `StringUtils.equals` (sabit zamanlı değil) |

## ✅ Bağımlılık ve secret hijyeni

| Alan | Durum |
|---|---|
| **iyzipay-java sürümü** | `2.0.142` = upstream `VERSION` = en son tag. **Güncel** |
| **Git geçmişi** | 188 commit / tüm ref'ler tarandı — secret adı desenine uyan yalnızca üç `.example` şablonu, hepsi placeholder'lı. Gerçek secret dosyası **hiç commit'lenmemiş** |
| **`.gitignore`** | `application-local*.yml` ve `frontend/.env` doğru kapsanmış |
| **Frontend bağımlılıkları** | 288 paket, 1 high (`browserslist`, devDependency, runtime maruziyeti yok) |
| **`commons-lang3 3.18.0`** | Güncel, CVE-2025-48924 düzeltmesini içeriyor |

## ✅ Altyapı ve süreç

| Alan | Durum |
|---|---|
| **Startup validator'ları** | `IyzicoProductionConfigurationValidator`, `AdminBootstrapSecurityValidator`, `FrontendUrlProductionConfigurationValidator`, `MediaConfigurationValidator` — fail-fast deseni örnek nitelikte |
| **Rate limiting** | 15 eylem için IP + identifier ikili limitleme, SHA-256 hash'li anahtarlar, Redis'e taşınabilir soyutlama |
| **WebSocket** | CONNECT'te JWT zorunlu, SUBSCRIBE/SEND destination allowlist'i, katılımcı kontrolü, admin read-only, bağlantı kotaları |
| **Hata yönetimi** | RFC 9457 `ProblemDetail`, catch-all generic 500 (stack trace sızıntısı yok) |
| **Sıralama/sayfalama** | Her listeleme ucunda sort whitelist'i, `max-page-size: 100` |
| **Loglama** | Token/şifre/kart/imza içeren log ifadesi yok |
| **CI** | Action'lar commit SHA'sına pinlenmiş, `permissions: contents: read`, `npm ci` lockfile'dan, prod bağımlılık audit'i |
| **Swagger / demo-seed** | Default `false` ✅ |
| **Checkout URL allowlist** | `validateCheckoutResult` — kütüphanede olmayan, sizin eklediğiniz iyi bir koruma |
| **Eski `AUDIT.md` bulguları** | #1, #2, #4, #5, #7, #11 kapatılmış. Açık kalan tek madde: **#6 (timeout)** → Katman 7 |

---

# BÖLÜM 4 — KARAR GEREKTİRENLER

Bunlar teknik yama değil; sizin (veya danışmanlarınızın) kararı olmadan kod yazılamaz.

| ✓ | Konu | Karar | Kime sorulacak |
|---|---|---|---|
| ☐ | **TCKN** | Sahte TCKN göndermeye devam mı, gerçek TCKN toplamaya başlamak mı? Kullanıcıların bir kısmı **reşit değil**; her iki yol da sonuç doğuruyor | iyzico üye işyeri temsilcisi + hukuk |
| ☐ | **Auto-renew** | (a) iyzico kart saklama ile implemente et, (b) üründen kaldır + `autoRenew` default `false`, (c) **iyzico yerleşik abonelik ürününe geç** | Ürün + iyzico temsilcisi (hesapta abonelik ürünü açık mı?) |
| ☐ | **İade politikası** | İfaya başlanmış hizmette tam iade mi, pro-rata mı? Şu an seans kanıtı toplanıyor ama karara **girmiyor** | Hukuk (Mesafeli Satış Yönetmeliği) |
| ☐ | **`COACH_FULL` sonrası** | Otomatik iade mi, admin kuyruğu mu? Para hareketi olduğu için bilinçli onay ister | Ürün |
| ☐ | **HSTS `preload`** | Alan adını kalıcı olarak HTTPS'e bağlamaya hazır mısınız? Geri alması zor | Siz |
| ☐ | **`gson` güncellemesi** | 2.8.9 bayat ama bilinen açık yok. `<dependencyManagement>` ile pin'lemek uçtan uca test gerektirir | Siz |

---

# Deploy Kararı

**Mevcut haliyle gerçek para ile canlıya çıkmayın.**

Minimum çıkış eşiği:

- ☐ Katman 0 (iyzico hesap yapılandırması) tamamlandı ve **onaylandı**
- ☐ Katman 1 (webhook sözleşmesi) tamamlandı ve **sandbox'ta gerçek bir webhook ile doğrulandı**
- ☐ Katman 2 (retrieve teyidi) tamamlandı — elle yazılmış imza kodundaki olası bir hata tek başına ücretsiz aboneliğe dönüşmesin
- ☐ Katman 3 (`COACH_FULL`) tamamlandı — para alınıp hizmet verilmeme yolu kapalı
- ☐ Katman 4 (retry semantiği) tamamlandı — bildirimler 45 dk penceresinde kaybolmasın
- ☐ Katman 5 (yenileme fail-closed + watchdog) tamamlandı — süresiz ücretsiz erişim kapalı
- ☐ Bölüm 2'deki env değişkenleri set edildi
- ☐ Secret'lar rotate edildi
- ☐ `cd backend && ./mvnw verify` yeşil
- ☐ CLAUDE.md'nin zorunlu kritik yolları geçiyor: auth/security, booking race, **payment idempotency**, weekly quota, message gate

Katman 6, 7, 8 ve Bölüm 4 kararları **çıkıştan sonra** ele alınabilir — ancak Y-3 (frontend host allowlist) çıkıştan önce düzeltilmezse checkout kullanıcı tarafında zaten çalışmaz, dolayısıyla o Katman 1 ile birlikte test edilmeli.

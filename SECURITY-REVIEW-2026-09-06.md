# Deploy Öncesi Güvenlik & İş Mantığı Denetimi

**Proje:** YKS Coaching Platform (`~/Desktop/dev/demo`) — Spring Boot 4.0.6 / Java 21 backend + React 19 / Vite frontend
**Tarih:** 2026-09-06
**Kapsam:** 578 Java + 159 TS/TSX kaynak dosyası, Flyway migration'ları, `application*.yml`, CI workflow, git geçmişi
**Denetim tipi:** Manuel kaynak kod incelemesi + statik desen taraması + bağımlılık taraması. Çalışan bir ortama karşı dinamik test (DAST/pentest) yapılmadı.

> 📎 **Ek rapor:** [SECURITY-REVIEW-ADDENDUM-iyzipay-java.md](SECURITY-REVIEW-ADDENDUM-iyzipay-java.md) — resmî `iyzipay-java` kaynağı ile satır satır karşılaştırma. K-3, Y-1, Y-2, Y-5'in somut çözümlerini ve Y-4'ün **düzeltilmiş** halini içerir.

---

## Yönetici Özeti

Kod tabanı **güvenlik açısından beklenenin oldukça üstünde**. OWASP Top 10'un klasik kalemlerinde (SQLi, XSS, IDOR, yetkilendirme, session yönetimi) somut bir açık bulunamadı — bunlar zaten bilinçli olarak kapatılmış. Repo'daki `AUDIT.md`'de listelenen eski bulguların çoğu (webhook imzası, rate limiting, constant-time karşılaştırma, transaction sınırları, N+1) kapatılmış durumda.

Ancak **iyzico entegrasyonu production'a hazır değil.** Bulguların ağırlığı burada toplanıyor ve bunlar "güvenlik açığı"ndan çok **"para akışı gerçek iyzico ile hiç çalışmayacak veya yanlış çalışacak"** kategorisinde:

| Seviye | Adet | Ana temalar |
|---|---|---|
| 🔴 Kritik | 3 | Webhook payload sözleşmesi gerçek iyzico ile uyuşmuyor; callback akışı yanlış kurgulanmış; sağlayıcı tarafı teyit (retrieve) hiç yapılmıyor |
| 🟠 Yüksek | 5 | Otomatik yenileme prod'da çalışmıyor → süresiz ücretsiz erişim; kart saklama yok; frontend host allowlist eksik; iyzico çağrılarında timeout yok; refund referansı şüpheli |
| 🟡 Orta | 9 | Proxy/IP güveni, HSTS, Redis fallback, CSRF derinliği, refund politikası, backend CVE taraması, secret hijyeni |
| ⚪ Düşük | 6 | JWT claim'leri, actuator, perf, dev-only CVE |

**Deploy kararı:** Mevcut haliyle **gerçek para ile canlıya alınmamalı**. Kritik + Yüksek bulgular kapatılmadan `IYZICO_ENABLED=true` ile prod'a çıkmak, en iyi ihtimalle "hiçbir ödeme tamamlanmıyor", en kötü ihtimalle "abonelikler ücretsiz süresiz uzuyor" sonucunu verir.

---

## 🔴 KRİTİK

### K-1 — Webhook DTO'su gerçek iyzico payload'ı ile uyuşmuyor; webhook production'da hiç işlenmez

**Risk:** Kritik
**Dosya/Endpoint:** `POST /api/v1/payments/iyzico/webhook`
`backend/.../dto/IyzicoWebhookRequest.java`
`backend/.../service/SubscriptionService.java` → `validateWebhookIdentity()`, `verifyWebhookSignature()`

Üç ayrı uyuşmazlık üst üste biniyor:

1. **Alan adı.** DTO `paymentId` bekliyor; iyzico webhook gövdesinde bu alanın adı **`iyziPaymentId`**. Spring Boot'ta `FAIL_ON_UNKNOWN_PROPERTIES=false` olduğu için fazladan alanlar sessizce yutulur ve `paymentId` **`null`** kalır → `@NotNull` devreye girer → her gerçek webhook **400** ile reddedilir.

2. **Kimlik invariantı.** `validateWebhookIdentity()` şunu şart koşuyor:
   ```java
   !request.paymentId().toString().equals(request.paymentConversationId())
   ```
   Gerçek hayatta `iyziPaymentId` **iyzico'nun** ödeme kimliği, `paymentConversationId` ise bizim `RealIyzicoClient.initializeCheckout()` içinde `conversationId` olarak gönderdiğimiz **kendi** `Payment.id`'miz. Bu ikisi hiçbir zaman eşit olmaz.

3. **İmza girdisi.** İmza `paymentIdStr` (bizim ID) ile hesaplanıyor; iyzico ise imzayı **kendi** `iyziPaymentId`'si ile hesaplar. Alan adları düzeltilse bile imza tutmaz.

Sonuç: ödeme başarılı olsa bile `Subscription` `PENDING_PAYMENT`'ta kalır, 30 dk sonra `expireStalePendingCheckouts` onu `EXPIRED` yapar → **müşteri para öder, hizmet açılmaz.**

**Düzeltme:**

```java
// dto/IyzicoWebhookRequest.java
public record IyzicoWebhookRequest(
        @JsonProperty("iyziPaymentId")  @NotNull  Long iyziPaymentId,
        @JsonProperty("paymentConversationId") @NotBlank @Size(max = 128) String paymentConversationId,
        @JsonProperty("status")         @NotBlank @Size(max = 32)  String status,
        @JsonProperty("iyziEventType")  @Size(max = 64)  String iyziEventType,
        @JsonProperty("iyziReferenceCode") @Size(max = 128) String iyziReferenceCode
) {
    /** Bizim Payment.id — checkout'ta conversationId olarak gönderilmişti. */
    public Long localPaymentId() {
        try { return Long.valueOf(paymentConversationId); }
        catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_CONVERSATION_INVALID",
                    "Webhook conversation kimliği geçersiz");
        }
    }
}
```

```java
// SubscriptionService.verifyWebhookSignature — iyzico'nun kendi alanlarıyla
String data = secretKey
        + nullSafe(request.iyziEventType())
        + nullSafe(request.iyziPaymentId())           // iyzico'nun ID'si
        + nullSafe(request.paymentConversationId())   // bizim ID'miz
        + nullSafe(request.status());
String computed = calculateHmacSha256(data, secretKey);
// ... MessageDigest.isEqual ile karşılaştırma (mevcut hali doğru)
```

```java
// applyWebhookOutcome — lookup artık conversationId üzerinden
Payment payment = paymentRepository.findById(request.localPaymentId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));
```

> ⚠️ İmza string'inin tam sırası ve encoding'i (hex vs base64) iyzico'nun **güncel** webhook V3 dokümanına karşı bire bir doğrulanmalı. Kodda hex-lowercase üretiliyor; sandbox'ta gerçek bir webhook yakalayıp header ile karşılaştırmadan canlıya çıkmayın. Bu, "sandbox'ta bir kez elle doğrulanacak" tipik bir maddedir — teorik olarak doğrulanamaz.

---

### K-2 — CheckoutForm `callbackUrl`'i webhook endpoint'ine bağlanmış (iki farklı akış birbirine karıştırılmış)

**Risk:** Kritik
**Dosya:** `backend/.../integration/RealIyzicoClient.java:74-79`, `application.yml` → `payments.iyzico.callback-url`, `docs/iyzico-sandbox-test-guide.md`

```java
request.setCallbackUrl(properties.callbackUrl());
// IYZICO_CALLBACK_URL default = .../api/v1/payments/iyzico/webhook
```

iyzico'da bunlar **iki ayrı mekanizma**:

| | `callbackUrl` (CheckoutForm) | Webhook |
|---|---|---|
| Kim çağırır | **Kullanıcının tarayıcısı** (redirect) | iyzico sunucuları (server-to-server) |
| Content-Type | `application/x-www-form-urlencoded` | `application/json` |
| Gövde | tek alan: `token` | `iyziEventType`, `iyziPaymentId`, … |
| İmza header | yok | `X-IYZ-SIGNATURE-V3` |

Şu an kullanıcı ödemeyi bitirince tarayıcısı, JSON + imza bekleyen webhook endpoint'ine form POST'u atıyor → **415/400** → kullanıcı ham hata ekranında kalıyor. `docs/iyzico-sandbox-test-guide.md` de aynı yanlış varsayımı belgelemiş (satır 25, 42, 156).

**Düzeltme:** İki endpoint'i ayırın. Callback tarafında **mutlaka** `CheckoutForm.retrieve(token)` ile sunucu-sunucu teyit yapın (K-3):

```java
@PostMapping(value = "/iyzico/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
public ResponseEntity<Void> checkoutCallback(@RequestParam("token") String token) {
    Long paymentId = subscriptionService.finalizeCheckoutByToken(token); // retrieve + doğrula + state
    return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(frontendBaseUrl + "/payment/result?paymentId=" + paymentId))
            .build();
}
```

`SecurityConfig` içinde bu path'i de `permitAll()` listesine ekleyin, CSP'deki `form-action 'self' https://*.iyzico.com https://*.iyzipay.com` zaten uyumlu.

---

### K-3 — Ödeme sonucu sağlayıcıya karşı hiç teyit edilmiyor (retrieve yok, tutar teyidi yok)

**Risk:** Kritik
**Dosya:** `backend/.../service/SubscriptionService.java` → `applyWebhookOutcome()`

Tek gerçeklik kaynağı, webhook gövdesindeki `status` string'i:

```java
if ("SUCCESS".equalsIgnoreCase(request.status())) {
    completePaymentSuccess(payment, subscription, request.providerReference());
}
```

Hiçbir yerde `CheckoutForm.retrieve()` / `Payment.retrieve()` çağrılmıyor; `paidPrice`, `currency`, `paymentStatus` iyzico'ya sorulmuyor. İmza şemasında bir sapma (bkz. K-1), secret key sızıntısı veya iyzico tarafında bir format değişikliği doğrudan **"imzasız/yanlış imzalı istekle ücretsiz abonelik"**e dönüşür. Savunma tek katmanlı.

**İyi haber:** Tutar **client'tan gelmiyor** — `createPendingPayment()` fiyatı `subscription.getPkg().getPrice()` ile DB'den okuyor, `SubscriptionCheckoutRequest` içinde hiçbir tutar alanı yok. Yani klasik "price tampering" mümkün değil. Eksik olan, **ödenen tutarın beklenen tutarla eşleştiğinin teyidi**.

**Düzeltme:** `IyzicoClient` arayüzüne bir doğrulama metodu ekleyin (CLAUDE.md'nin "4 dış servis arayüzü" istisnası kapsamında, yeni soyutlama değil):

```java
// integration/IyzicoClient.java
CheckoutVerification verifyCheckout(String checkoutToken);

public record CheckoutVerification(boolean success, String conversationId,
                                   BigDecimal paidPrice, String currency,
                                   String paymentId, String paymentTransactionId) {}
```

```java
// SubscriptionService — hem callback hem webhook bu ortak yola girsin
private void applyVerifiedSuccess(Payment payment, CheckoutVerification v) {
    if (!v.success()) {
        markFailed(payment); return;
    }
    if (!"TRY".equals(v.currency())
            || v.paidPrice().compareTo(payment.getAmount()) != 0) {
        log.error("Payment amount mismatch: paymentId={}, expected={}, paid={} {}",
                payment.getId(), payment.getAmount(), v.paidPrice(), v.currency());
        throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH",
                "Ödeme tutarı beklenen tutarla eşleşmiyor");
    }
    // paymentTransactionId'yi sakla — refund bunu ister (bkz. Y-5)
    completePaymentSuccess(payment, payment.getSubscription(), v.paymentTransactionId());
}
```

İmza doğrulaması kalsın; retrieve **ikinci** katman olsun. İkisi birlikte fail-closed davranır.

---

## 🟠 YÜKSEK

### Y-1 — Otomatik yenileme production'da hiç çalışmaz → abonelik süresiz ACTIVE kalır (ücretsiz erişim)

**Risk:** Yüksek (gelir kaybı + erişim kontrolü baypası)
**Dosya:** `backend/.../integration/RealIyzicoClient.java:138-142`, `service/SubscriptionBillingService.java:106`, `service/SubscriptionRenewalJob.java:69-73`

```java
// RealIyzicoClient
public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
    throw new PaymentProviderException("Iyzico recurring charge is not implemented");
}
```

Zincir şöyle işliyor:

1. `processDue()` tx1'de `PENDING` bir `Payment` rezerve eder → **commit**.
2. `iyzicoClient.charge(...)` **throw** eder.
3. `SubscriptionRenewalJob` istisnayı yakalar, loglar, `continue` der.
4. tx2 (`finalizeCharge`) **hiç çalışmaz** → `failedChargeCount` artmaz, `PAST_DUE`'ya geçilmez, `EXPIRED` olmaz.
5. Ertesi gün: önceki pencereden kalan `PENDING` satır bulunur → aynı key ile resume → yine throw. Sonsuz döngü.

**Net etki:** `end_at` geçmişte kalmış bir abonelik **süresiz `ACTIVE`** durur. `MessageService` ve `SessionService` erişim kapısı olarak `ACTIVE` durumuna baktığı için öğrenci **ödeme yapmadan süresiz mesajlaşır ve seans alır**; koç kontenjanı da hiç serbest kalmaz.

Bu, "fail-closed" olduğu düşünülen bir tasarımın pratikte **fail-open** çıktığı yer.

**Düzeltme — iki seçenek, biri şart:**

**(a) MVP için önerilen — erişimi kapatan güvenli davranış.** Sağlayıcı hatası da "başarısız tahsilat" sayılsın:

```java
// SubscriptionBillingService.processDue
ChargeResult result;
try {
    result = iyzicoClient.charge(reserve.savedCardToken(), reserve.amount(), reserve.idempotencyKey());
    if (result == null) {
        result = ChargeResult.failed("PROVIDER_NULL_RESPONSE");
    }
} catch (PaymentProviderException e) {
    // Sağlayıcı hatası tahsilatı BAŞARISIZ sayar: erişim grace/expiry akışına girer,
    // sessizce süresiz ACTIVE kalmaz.
    log.error("Recurring charge failed for subscription {}: {}", subscriptionId, e.getMessage(), e);
    result = ChargeResult.failed("PROVIDER_CALL_FAILED");
}
return tx.execute(status -> finalizeCharge(subscriptionId, reserve.paymentId(), result, now));
```

Ek olarak, "yenileme hiç denenemedi" durumu için bir **watchdog** ekleyin: `status IN (ACTIVE) AND end_at < now() - interval '2 days'` sorgusu boş dönmüyorsa alarm üretin (Sentry). Sessiz gelir kaybının tek gerçek savunması budur.

**(b) Recurring'i gerçekten implemente edin** — iyzico kart saklama (`CardStorage` / `createCardRequest`) ile `cardUserKey` + `cardToken` saklayıp `Payment.create` çağırın. Bu, Y-2 ile birlikte ele alınmalı ve MVP kapsamını ciddi büyütür.

**Ne yaparsanız yapın, (a)'daki fail-closed davranış ve watchdog deploy öncesi girmelidir** — (b)'yi ertelemek bir ürün kararı, (a)'yı ertelemek bir güvenlik açığı.

---

### Y-2 — Kayıtlı kart altyapısı yok; `savedCardToken` sabit bir stub değeri

**Risk:** Yüksek
**Dosya:** `backend/.../service/SubscriptionService.java` → `preparePendingSubscription()`

```java
subscription.setSavedCardToken("stub-card-token-" + UUID.randomUUID());
```

Her abonelikte üretilen bu değer hiçbir gerçek kartı temsil etmiyor. Ürün "iptal edilene kadar otomatik yenilenen aylık abonelik" vaat ediyor (CLAUDE.md); bu vaadin arkasında çalışan bir mekanizma yok.

**Düzeltme:** Kısa vadede iki dürüst seçenek var — ya (b) yolunu implemente edin, ya da **auto-renew'ü üründen kaldırıp** kullanıcıya "her ay elle yenileyin" akışı sunun ve `Subscription.autoRenew` default'unu `false` yapın. Yarım implementasyonla canlıya çıkmak, mesafeli satış sözleşmesi açısından da riskli (kullanıcıya taahhüt edilen otomatik yenileme gerçekleşmiyor).

---

### Y-3 — Frontend checkout host allowlist'i gerçek iyzico ödeme sayfası hostunu içermiyor

**Risk:** Yüksek (fonksiyonel kırılma)
**Dosya:** `frontend/src/subscriptionCheckout/CheckoutSection.tsx:62-69`

```ts
const allowedHosts = [
  "sandbox-api.iyzipay.com", "sandbox-api.iyzico.com",
  "api.iyzico.com", "api.iyzipay.com", "www.iyzico.com",
];
```

Bunlar **API** hostları. `CheckoutFormInitialize.getPaymentPageUrl()` ise **ödeme sayfası** hostunu döner — iyzico'da bu `sandbox-cpp.iyzipay.com` / `cpp.iyzipay.com` ailesidir. Backend'deki kontrol (`SubscriptionService.validateCheckoutResult`) `endsWith(".iyzipay.com")` kullandığı için geçirir; **frontend exact-match listesi reddeder** → kullanıcı "Güvenli olmayan veya izin verilmeyen ödeme yönlendirme adresi." hatası alır.

**Düzeltme:** Frontend'i backend ile aynı kurala hizalayın (suffix + zorunlu https), böylece iyzico bir alt alan adı değiştirdiğinde ödeme akışı kırılmaz:

```ts
const isTrustedProvider = (u: URL) =>
  u.protocol === "https:" &&
  (u.hostname === "iyzico.com"  || u.hostname.endsWith(".iyzico.com") ||
   u.hostname === "iyzipay.com" || u.hostname.endsWith(".iyzipay.com"));
```

Sandbox'ta gerçek `paymentPageUrl`'i bir kez loglayıp hangi host'un geldiğini teyit edin.

---

### Y-4 — iyzico çağrılarında efektif timeout 140 saniye ve yapılandırılamıyor

**Risk:** Yüksek (kullanılabilirlik)
**Dosya:** `backend/.../integration/RealIyzicoClient.java`; kütüphane tarafı: `com.iyzipay.HttpClient:24,76-77`

> Bu madde, kütüphane kaynağı incelendikten sonra düzeltildi. İlk değerlendirme "timeout yok" diyordu; doğrusu **timeout var ama çok uzun ve değiştirilemez**.

`iyzipay-java` kendi `HttpClient`'ında timeout'u **set ediyor**, ama tek bir sabit üzerinden:

```java
private static final int TIMEOUT = 140000;   // HttpClient.java:24
conn.setConnectTimeout(TIMEOUT);             // 140 saniye
conn.setReadTimeout(TIMEOUT);                // 140 saniye
```

`Options` sınıfında yalnızca `apiKey`/`secretKey`/`baseUrl`/`proxyHost`/`proxyPort` var — **timeout için hiçbir ayar noktası yok**. Sabit `private static final` olduğu için override edilemez.

Pratik etki: askıda kalan bir iyzico bağlantısı bir Tomcat thread'ini **140 saniye** tutar. Resend istemcisinde 3s/5s kullanılmışken (`ResendConfig`) ödeme yolunda ~47 kat fazla. Checkout trafiği altında thread pool tükenmesi için birkaç yavaş çağrı yeterli.

⚠️ **`sun.net.client.defaultConnectTimeout` / `defaultReadTimeout` sistem property'leri işe yaramaz** — kütüphane `setConnectTimeout`/`setReadTimeout`'u açıkça çağırdığı için property'leri ezer.

**Düzeltme:** Çağrıyı kendi tarafınızda zaman sınırına alın. Tek yol bu:

```java
@Component
public class IyzicoCallExecutor implements AutoCloseable {
    // Ayrı, sınırlı havuz: iyzico yavaşlarsa Tomcat thread'leri değil bu havuz dolar.
    private final ExecutorService pool = Executors.newFixedThreadPool(8, Thread.ofPlatform()
            .name("iyzico-", 0).daemon(true).factory());

    public <T> T call(String operation, Duration timeout, Supplier<T> providerCall) {
        Future<T> future = pool.submit(providerCall::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);   // HttpURLConnection'ı gerçekten kesmez; havuz sınırı asıl korumadır
            throw new PaymentProviderException("Iyzico " + operation + " timed out", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof PaymentProviderException ppe
                    ? ppe : new PaymentProviderException("Iyzico " + operation + " failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Iyzico " + operation + " interrupted", e);
        }
    }

    @Override public void close() { pool.shutdownNow(); }
}
```

```java
// RealIyzicoClient
CheckoutFormInitialize response = executor.call("checkout", Duration.ofSeconds(10),
        () -> CheckoutFormInitialize.create(request, options));
```

Havuzun sınırlı olması burada timeout'un kendisinden daha önemli: `future.cancel` altta yatan soketi kapatmaz, ama sızıntı sekiz thread'le sınırlı kalır ve istek thread'i 10 saniyede serbest kalır.

`AUDIT.md` #6'da açılmış, **hâlâ açık**.

---

### Y-5 — Refund `providerReference` yanlış alanı taşıyor olabilir; iade prod'da başarısız olur

**Risk:** Yüksek
**Dosya:** `backend/.../integration/RealIyzicoClient.java:144-182` (kodda TODO olarak zaten kabul edilmiş)

```java
// TODO: ... iyzico requires paymentTransactionId for refunds.
request.setPaymentTransactionId(providerReference);
```

`providerReference` şu an webhook gövdesinden gelen serbest bir string (`request.providerReference()`). iyzico iadesi **`paymentTransactionId`** ister (sepet kalemi düzeyindeki işlem kimliği), genel `paymentId` değil. Yanlış değerle iade `PROVIDER_CALL_FAILED` döner; `RefundRequest` `REJECTED` olur ve öğrenci parasını alamaz.

**Düzeltme:** K-3'teki retrieve adımında `paymentTransactionId`'yi ayrı bir kolona yazın ve iadede onu kullanın:

```sql
-- Flyway: V__add_payment_transaction_reference.sql
ALTER TABLE payments ADD COLUMN provider_transaction_reference varchar(128);
```

```java
payment.setProviderReference(v.paymentId());                    // genel ödeme kimliği (audit)
payment.setProviderTransactionReference(v.paymentTransactionId()); // iade için gereken
```

---

## 🟡 ORTA

### O-1 — Proxy arkasında IP tabanlı rate limit tek kovaya düşüyor

**Risk:** Orta (brute-force koruması etkisiz + kendi kendine DoS)
**Dosya:** `application.yml:128-130`, `security/ratelimit/ClientIpResolver.java`

```yaml
trust-proxy-headers: ${RATE_LIMIT_TRUST_PROXY_HEADERS:false}
trusted-proxy-cidrs: ${RATE_LIMIT_TRUSTED_PROXY_CIDRS:}
```

Default `false` — mimari olarak **doğru bir fail-closed tercihi** (spoofing'e kapalı). Ama Railway/Fly gibi bir proxy arkasında `request.getRemoteAddr()` her istek için proxy IP'sini döner. Sonuç:

- `login.ip-limit: 20/dk` → **tüm platform** için 20/dk. Bir kullanıcının login denemeleri diğer herkesi kilitler.
- Saldırgan tarafında IP limiti hiçbir ayrım yapmaz; tek gerçek koruma `identifier-limit: 5` (e-posta başına) olarak kalır.

**Düzeltme (deploy konfigürasyonu):** Railway'in çıkış CIDR'lerini öğrenip:

```bash
RATE_LIMIT_TRUST_PROXY_HEADERS=true
RATE_LIMIT_TRUSTED_PROXY_CIDRS=<railway-proxy-cidr-1>,<railway-proxy-cidr-2>
```

CIDR'leri öğrenemiyorsanız `X-Forwarded-For`'un **sağdan** n'inci girdisini almak (proxy sayısı sabitse) daha güvenli bir alternatiftir; şu anki kod soldaki (istemci tarafından tamamen kontrol edilebilen) girdiyi alıyor — bu yüzden CIDR allowlist'i olmadan `trust-proxy-headers=true` **açmayın**.

---

### O-2 — HSTS header'ı hiç gönderilmiyor + OAuth2 redirect_uri yanlış üretilir

**Risk:** Orta
**Dosya:** `application.yml:85`, `security/SecurityConfig.java:49-58`

```yaml
forward-headers-strategy: ${SERVER_FORWARD_HEADERS_STRATEGY:none}
```

Spring Security'nin `HstsHeaderWriter`'ı `Strict-Transport-Security`'yi **yalnızca `request.isSecure()` true olduğunda** yazar. TLS Railway proxy'sinde sonlandığı ve `forward-headers-strategy=none` olduğu için uygulama isteği **http** görür → HSTS asla gönderilmez. Aynı sebeple `{baseUrl}/login/oauth2/code/google` iç host/http ile üretilir ve Google OAuth callback'i kırılır.

**Düzeltme (deploy konfigürasyonu):**

```bash
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

ve HSTS'yi açıkça, preload ile tanımlayın:

```java
.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .preload(true)
        .maxAgeInSeconds(31536000))
    .permissionsPolicyHeader(p -> p.policy(
        "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()"))
    .contentSecurityPolicy(...)   // mevcut
    .referrerPolicy(...))         // mevcut
```

> Not: `preload` yalnızca alan adının tamamını kalıcı olarak HTTPS'e bağlamaya hazırsanız açılmalı; geri alması zordur.

---

### O-3 — Redis yapılandırılmazsa rate limiting sessizce instance-local'e düşer

**Risk:** Orta
**Dosya:** `application.yml:53-56, 124-127`

```yaml
redis.url: ${REDIS_URL:redis://localhost:6379}
rate-limit.store: ${RATE_LIMIT_STORE:REDIS}
rate-limit.failure-policy: ${RATE_LIMIT_FAILURE_POLICY:IN_MEMORY_FALLBACK}
```

`REDIS_URL` prod'da unutulursa: store=REDIS → localhost'a bağlanamaz → `IN_MEMORY_FALLBACK` devreye girer → **hiçbir hata görünmeden** limitler replica başına ayrışır ve 3 replica'da efektif limit 3 katına çıkar.

**Düzeltme:** Bu tercihi bilinçli tutmak makul, ama **görünür** olmalı. Fallback'e her düşüşte `WARN` yerine bir Sentry event'i üretin ve startup'ta doğrulayın:

```java
@Component
class RateLimitProductionConfigurationValidator implements SmartInitializingSingleton {
    // IyzicoProductionConfigurationValidator ile aynı desen
    public void afterSingletonsInstantiated() {
        if (isProductionProfile() && "REDIS".equals(props.getStore())
                && redisUrl.contains("localhost")) {
            throw new IllegalStateException("REDIS_URL must be set when RATE_LIMIT_STORE=REDIS");
        }
    }
}
```

Projede zaten `IyzicoProductionConfigurationValidator`, `FrontendUrlProductionConfigurationValidator`, `MediaConfigurationValidator`, `AdminBootstrapSecurityValidator` var — bu, o desenin eksik kalan üyesi.

---

### O-4 — CSRF global kapalı; cookie ile çalışan iki endpoint var

**Risk:** Orta-Düşük (mevcut savunmalar iyi, derinlik eksik)
**Dosya:** `security/SecurityConfig.java:48`, `security/RefreshTokenCookieService.java`

`csrf.disable()` bearer-token API'si için doğru; ancak `/api/v1/auth/refresh` ve `/api/v1/auth/logout` **HttpOnly cookie** ile çalışıyor. Mevcut savunmalar:

- `SameSite=Lax` → sıradan cross-site POST'ları engeller ✅
- `Path=/api/v1/auth` → cookie yüzeyi dar ✅
- `requireTrustedBrowserOrigin()` → `Origin` header allowlist kontrolü ✅

Boşluk: `Origin` header'ı **yoksa** kontrol atlanıyor (kod yorumunda bilinçli olarak "non-browser clients may omit Origin" deniyor). Modern tarayıcılar cross-site POST'ta `Origin`'i her zaman gönderir, o yüzden pratik risk düşük — ama savunma derinliği için:

```java
public void requireTrustedBrowserOrigin(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !origin.isBlank()) {
        if (!corsProperties.allowedOrigins().contains(origin)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "UNTRUSTED_ORIGIN", "İstek kaynağına izin verilmiyor");
        }
        return;
    }
    // Origin yok: Fetch Metadata ile karar ver. Tarayıcı isteği ise cross-site olmamalı.
    String site = request.getHeader("Sec-Fetch-Site");
    if (site != null && !"same-origin".equals(site) && !"same-site".equals(site)) {
        throw new ApiException(HttpStatus.FORBIDDEN, "UNTRUSTED_ORIGIN", "İstek kaynağına izin verilmiyor");
    }
}
```

---

### O-5 — 7 günlük tam iade, tüketilen seansları hiç dikkate almıyor

**Risk:** Orta (iş mantığı istismarı / gelir kaybı)
**Dosya:** `service/RefundPolicy.java`, `service/RefundRequestService.java:97`

```java
// Session evidence is operational audit data only. It never changes the seven-day decision above.
```

Öğrenci 7 gün içinde seans alıp katılabilir, sonra **tam** iade talep edebilir. `refundEligibleStudent()` kalan tutarın tamamını iade eder. Seans kanıtı (`paidSessionsCount`, `completedSessionsCount`, `serviceStarted`) toplanıyor ama karara **girmiyor**.

Hafifletici: admin onayı var (`approve`) ve admin reddedebilir. Yani bu tamamen açık bir kapı değil, **manuel bir kapı** — ama hacim arttıkça sürdürülemez ve karar kriteri kodda yazılı değil.

**Düzeltme:** Bu bir hukuk/ürün kararı (CLAUDE.md de "unresolved" diyor), ama en azından politikayı kodlayın:

```java
public static Decision evaluate(Instant purchaseAt, Instant now, BigDecimal remainingRefundable,
                                boolean serviceStarted, int completedSessions, int totalSessions) {
    // ... mevcut 7 gün kontrolü ...
    if (serviceStarted) {
        // Mesafeli Satış Yönetmeliği m.15/1-h: ifaya başlanmış hizmette cayma hakkı,
        // tüketicinin onayı ile ifaya başlanmışsa kullanılamaz. Burada pro-rata öneriliyor.
        BigDecimal consumedRatio = BigDecimal.valueOf(completedSessions)
                .divide(BigDecimal.valueOf(Math.max(totalSessions, 1)), 4, RoundingMode.HALF_UP);
        BigDecimal proRata = remainingRefundable
                .multiply(BigDecimal.ONE.subtract(consumedRatio))
                .setScale(2, RoundingMode.HALF_UP);
        return new Decision(proRata.signum() > 0, deadline, null, proRata);
    }
    return new Decision(true, deadline, null, remainingRefundable);
}
```

> ⚠️ Bu, mesafeli satış mevzuatına dokunan bir karar. Yukarıdaki kod **teknik bir şablon**, hukuki tavsiye değil — oranı ve istisnaları avukatınıza doğrulatın.

---

### O-6 — İade talebi oluşturmada rate limit yok

**Risk:** Orta-Düşük
**Dosya:** `controller/RefundRequestController.java:21-26`

Projedeki diğer kullanıcı-tetiklemeli yazma uçları (`checkout`, `report`, `trial`, `media`) `AuthenticatedActionRateLimitService` ile korunuyor; `POST /api/v1/refund-requests` korumasız. `ACTIVE_REFUND_REQUEST_EXISTS` kontrolü çoğaltmayı engelliyor ama admin kuyruğunu spam'lemeye/DB yazma yüküne karşı sınır yok.

**Düzeltme:** Mevcut deseni uygulayın:

```yaml
# application.yml → app.rate-limit
refund-request-create:
  user-limit: ${RATE_LIMIT_REFUND_REQUEST_USER_LIMIT:5}
  window-seconds: ${RATE_LIMIT_REFUND_REQUEST_WINDOW_SECONDS:3600}
```

```java
@PostMapping
public RefundRequestResponse create(@AuthenticationPrincipal Long studentId, ...) {
    actionRateLimit.checkRefundRequestCreate(studentId);
    return service.create(studentId, request);
}
```

---

### O-7 — Güvenlik header'ları yalnızca API yanıtlarında; SPA'nın kendi header'ları yok

**Risk:** Orta
**Dosya:** `security/SecurityConfig.java:49-58` + frontend deploy hedefi

`SecurityConfig`'teki CSP mükemmele yakın (`script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`, `form-action` iyzico'ya kısıtlı). Ama bu header'lar **backend'in JSON yanıtlarına** iliştiriliyor. React SPA ayrı bir host'tan (Vercel/Netlify/Cloudflare Pages) servis ediliyorsa, kullanıcının HTML yüklediği origin'de **hiçbir CSP yok** — yani XSS'e karşı asıl işe yarayacağı yerde header eksik.

Eksik olanlar (her iki tarafta): `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Resource-Policy`.

**Düzeltme:** SPA host'una eşdeğer header'ları koyun. Vercel örneği:

```json
// vercel.json
{
  "headers": [{
    "source": "/(.*)",
    "headers": [
      { "key": "Content-Security-Policy", "value": "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https://api.<alan-adiniz> wss://api.<alan-adiniz>; frame-src https://www.youtube-nocookie.com; form-action 'self' https://*.iyzico.com https://*.iyzipay.com" },
      { "key": "Strict-Transport-Security", "value": "max-age=31536000; includeSubDomains" },
      { "key": "X-Content-Type-Options", "value": "nosniff" },
      { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
      { "key": "Permissions-Policy", "value": "camera=(), microphone=(), geolocation=(), payment=()" },
      { "key": "Cross-Origin-Opener-Policy", "value": "same-origin" }
    ]
  }]
}
```

---

### O-8 — Backend bağımlılıkları için CVE taraması yok

**Risk:** Orta
**Dosya:** `.github/workflows/ci.yml`

CI frontend için `npm audit --omit=dev --audit-level=high` çalıştırıyor (👏), ama **Maven tarafında hiçbir tarama yok**. Özellikle `iyzipay-java 2.0.142` transitive bağımlılıkları (gson, apache httpclient) ve `aws-sdk 2.31.54` (2025 ortası sürümü, güncel değil) taranmamış.

**Frontend taraması sonucu (bugün çalıştırıldı):** 288 paket, **1 high** — `browserslist` (GHSA-c83g-rgw3-j3cx, GHSA-73wf-gq98-2v4g), yalnızca `devDependency` (vite build zinciri), **runtime maruziyeti yok**. `npm audit fix` ile kapanır, engelleyici değil.

**Düzeltme:** CI'a OWASP Dependency-Check ekleyin ve Dependabot açın:

```yaml
- name: OWASP Dependency-Check
  run: >
    ./mvnw --batch-mode org.owasp:dependency-check-maven:12.1.0:check
    -DfailBuildOnCVSS=7 -DnvdApiKey=${{ secrets.NVD_API_KEY }}
```

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: maven
    directory: /backend
    schedule: { interval: weekly }
  - package-ecosystem: npm
    directory: /frontend
    schedule: { interval: weekly }
  - package-ecosystem: github-actions
    directory: /
    schedule: { interval: weekly }
```

Ayrıca `aws-sdk.version`'ı güncel 2.x sürümüne çekin.

---

### O-9 — Gerçek production secret'ları iCloud ile senkronize edilen bir dizinde düz metin

**Risk:** Orta (operasyonel)
**Dosya:** `backend/src/main/resources/application-local.yml` (git'e girmemiş ✅, ama `~/Desktop` altında)

Dosya şu anahtarları **düz metin** taşıyor: `NEON_DATABASE_URL`, `JWT_SECRET`, `RESEND_API_KEY`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `GOOGLE_CLIENT_SECRET`.

`.gitignore` doğru kurulmuş (`application-local*.yml`, `frontend/.env`) ve **git geçmişi temiz** — 188 commit'in tamamı ve tüm ref'ler tarandığında secret adı desenine uyan yalnızca üç dosya çıkıyor ve üçü de `<AÇISAL_PARANTEZ>` placeholder'ları taşıyan şablonlar: `backend/src/main/resources/application-local.yml.example`, `src/main/resources/application-local.yml.example` (eski yol), `frontend/.env.example`. Gerçek bir `application-local.yml` veya `.env` hiçbir commit'te bulunmuyor.

Asıl mesele: repo `~/Desktop/dev/demo` altında ve **macOS Desktop iCloud Drive'a senkronize** (bu denetim sırasında dosya okumalarının yavaşlığından da belli oldu). Yani bu secret'lar Apple'ın sunucularında bir kopya olarak duruyor.

`AUDIT.md` #8 aynı konuyu açmış; dosya hâlâ orada.

**Düzeltme:**
1. Bu değerler prod'da da kullanılıyorsa **hepsini rotate edin** (Neon şifresi, JWT_SECRET, Resend key, R2 key pair, Google client secret).
   - ⚠️ `JWT_SECRET` rotasyonu tüm access token'ları geçersiz kılar; refresh cookie'leri DB'de olduğu için kullanıcılar sessizce yeniden token alır — yine de düşük trafikli bir saatte yapın.
2. Local secret'ları repo dışına taşıyın: `~/.config/yks/application-local.yml` + `SPRING_CONFIG_ADDITIONAL_LOCATION` ile işaret edin, ya da `direnv`/1Password CLI kullanın.
3. Repo'yu iCloud senkronizasyonu dışındaki bir yola taşımayı değerlendirin (`~/dev/demo` gibi) — bu, denetim boyunca yaşanan dosya okuma yavaşlığını da çözer.

---

## ⚪ DÜŞÜK

| # | Bulgu | Dosya | Not |
|---|---|---|---|
| D-1 | OAuth2 tek kullanımlık kod query string'de taşınıyor (tarayıcı geçmişi / Referer sızıntısı) | `security/OAuth2LoginSuccessHandler.java:62-66` | 120 sn TTL + tek kullanım + `Referrer-Policy: strict-origin-when-cross-origin` ile risk kabul edilebilir. Fragment (`#code=`) kullanımı marjinal iyileştirme. |
| D-2 | `processWebhook(request)` — imza doğrulamasız public overload | `service/SubscriptionService.java:280-283` | Yalnızca javadoc yorumu koruyor. `@VisibleForTesting` yerine `package-private` yapın veya test'e özel bir seam'e taşıyın. |
| D-3 | JWT'de `iss` / `aud` claim'i yok | `security/JwtService.java:31-42` | Tek anahtarlı tek servis olduğu için pratik risk yok; ileride ikinci bir servis eklenirse gerekir. |
| D-4 | Actuator `info` endpoint'i açık (auth gerekli) | `application.yml:87-91` | `anyRequest().authenticated()` kapsıyor. Yine de `include: health` yeterli. |
| D-5 | `JwtAuthenticationFilter` her istekte DB'ye gidiyor | `security/JwtAuthenticationFilter.java:60` | Güvenlik açısından **doğru** tercih (anlık suspend/delete etkisi). Yük artarsa kısa TTL'li cache düşünülebilir. |
| D-6 | `browserslist` high CVE | `frontend/package-lock.json` | devDependency; `npm audit fix`. |

---

## ✅ Doğru Yapılmış — Değiştirmeyin

Denetimde **açık bulunamayan** ve bilinçli olarak iyi kurulmuş alanlar:

- **SQL Injection:** Tüm sorgular JPA/JPQL veya parametreli native query. `createQuery` string birleştirmesi yok. İki native query de `:named` parametre kullanıyor.
- **XSS:** Frontend'de tek bir `dangerouslySetInnerHTML`, `innerHTML` veya `eval` yok. React'in default escaping'i her yerde geçerli.
- **Token depolama:** Access token **yalnızca bellekte**; refresh token `HttpOnly` + `Secure` + `SameSite=Lax` + `Path=/api/v1/auth` cookie. Eski `localStorage` anahtarları aktif olarak temizleniyor ve bunu doğrulayan test var (`authSessionStorage.test.tsx`).
- **IDOR:** Kontrol edilen her uçta sahiplik doğrulaması var — `MediaService.requireOwner`, `SubscriptionBillingService.cancel` → `NOT_SUBSCRIPTION_OWNER`, `succeedPayment` → `NOT_PAYMENT_OWNER`, `RefundRequestService` → `NOT_SUBSCRIPTION_OWNER`. Public media token'ı 256-bit `SecureRandom`.
- **Yetkilendirme:** Route seviyesi (`/api/v1/admin/**` → `hasRole('ADMIN')`) + metot seviyesi `@PreAuthorize` çift katman. `@PreAuthorize` taşımayan 10 controller'ın hepsi ya gerçekten public ya `anyRequest().authenticated()` altında sahiplik kontrollü.
- **Fiyat manipülasyonu:** Tutar **hiçbir zaman** client'tan alınmıyor; `Package.price` DB'den okunuyor. Negatif tutar `INVALID_REFUND_AMOUNT` ile, aşırı iade `EXCEEDS_REFUNDABLE_AMOUNT` ile engelli. Komisyon işlem anında snapshot'lanıyor.
- **Idempotency:** `UNIQUE(idempotency_key)` + deterministik key üretimi (`checkout:{subId}`, `charge:{subId}:{date}`, `refund:{payId}:{n}`). Webhook tekrarları durum makinesinde `IDEMPOTENT` olarak karşılanıyor. Refund'da PENDING rezervasyon deseni, eşzamanlı iki tam iadenin aşırı iadeye dönüşmesini engelliyor.
- **Yarış koşulları:** `findByIdForUpdate` satır kilitleri, `incrementActiveStudentCountIfRoom` atomik koşullu UPDATE, `Session.availability_id` UNIQUE ile DB seviyesinde çift rezervasyon koruması, `OPTIMISTIC_FORCE_INCREMENT`.
- **Transaction sınırları:** Dış çağrılar iki commit'lenmiş transaction'ın **arasında** (tx1 rezerve → external → tx2 finalize). Çökme sonrası resume mantığı kurgulanmış.
- **Rate limiting:** 15 farklı eylem için IP + identifier ikili limitleme, SHA-256 hash'li anahtarlar, Redis'e taşınabilir soyutlama.
- **WebSocket:** CONNECT'te JWT zorunlu, SUBSCRIBE/SEND destination allowlist'i, katılımcı kontrolü, admin read-only, bağlantı/subscription kotaları.
- **Hata yönetimi:** RFC 9457 `ProblemDetail`, catch-all handler generic 500 döner (stack trace / mesaj sızıntısı yok).
- **Sıralama/sayfalama:** Her listeleme ucunda sort field whitelist'i, `max-page-size: 100`.
- **Loglama:** Token/şifre/kart/imza içeren log ifadesi yok (tek istisna zararsız bir stub log'u).
- **Startup validator'ları:** `IyzicoProductionConfigurationValidator` (provider host allowlist, SSRF'e karşı private/loopback callback reddi), `AdminBootstrapSecurityValidator`, `FrontendUrlProductionConfigurationValidator`, `MediaConfigurationValidator` — fail-fast deseni örnek nitelikte.
- **CI:** Action'lar commit SHA'sına pinlenmiş, `permissions: contents: read`, `npm ci` lockfile'dan, prod bağımlılık audit'i.
- **Secrets:** HEAD'de izlenen hiçbir secret dosyası yok; `.gitignore` doğru; `application-local.yml.example` ve `.env.example` şablon olarak tutulmuş.

---

## Deploy Öncesi Kontrol Listesi

### 🚫 Bunlar kapatılmadan gerçek para ile canlıya çıkmayın

- [ ] **K-1** `IyzicoWebhookRequest` alanları `@JsonProperty("iyziPaymentId")` / `paymentConversationId` ile gerçek payload'a hizalandı; lookup conversationId üzerinden; imza `iyziPaymentId` ile hesaplanıyor
- [ ] **K-1** İmza algoritması sandbox'ta yakalanan **gerçek** bir webhook'un `X-IYZ-SIGNATURE-V3` header'ına karşı bire bir doğrulandı
- [ ] **K-2** `callbackUrl` ayrı bir form-POST endpoint'ine (`/api/v1/payments/iyzico/callback`) bağlandı; webhook JSON endpoint'i ayrı kaldı; `SecurityConfig` permitAll listesi güncellendi
- [ ] **K-2** `docs/iyzico-sandbox-test-guide.md` düzeltildi (callback ≠ webhook)
- [ ] **K-3** `CheckoutForm.retrieve(token)` ile sunucu-sunucu teyit eklendi; `paidPrice` + `currency` beklenen değerle karşılaştırılıyor; uyuşmazlıkta `PAYMENT_AMOUNT_MISMATCH`
- [ ] **Y-1** Sağlayıcı istisnası "başarısız tahsilat" olarak ele alınıyor (fail-closed); `PAST_DUE` → `EXPIRED` akışı gerçekten tetikleniyor
- [ ] **Y-1** "ACTIVE ama `end_at` 2 günden eski" watchdog sorgusu + Sentry alarmı kuruldu
- [ ] **Y-2** Auto-renew ya gerçekten implemente edildi (iyzico kart saklama) ya da üründen kaldırılıp `autoRenew` default'u `false` yapıldı
- [ ] **Y-3** Frontend host allowlist'i suffix bazlı kurala çevrildi; sandbox'ta gerçek `paymentPageUrl` host'u teyit edildi
- [ ] **Y-4** iyzico çağrıları sınırlı bir havuz + `Future.get(timeout)` ile sarıldı (kütüphanenin 140 sn'lik sabit timeout'u değiştirilemiyor)
- [ ] **Y-5** `provider_transaction_reference` kolonu eklendi (Flyway) ve iade bu değeri kullanıyor
- [ ] Sandbox'ta uçtan uca senaryo: başarılı ödeme → abonelik ACTIVE, başarısız ödeme → PENDING kalır, çift webhook → IDEMPOTENT, kısmi + tam iade

### 🔧 Deploy konfigürasyonu (env değişkenleri)

- [ ] `IYZICO_ENABLED=true`, `IYZICO_MODE=production`, `IYZICO_BASE_URL=https://api.iyzipay.com`
- [ ] `IYZICO_API_KEY` / `IYZICO_SECRET_KEY` production değerleri (sandbox değil)
- [ ] `IYZICO_CALLBACK_URL` = public HTTPS callback adresi (validator zaten localhost/private IP'yi reddeder)
- [ ] `SERVER_FORWARD_HEADERS_STRATEGY=framework` — **O-2**, aksi halde HSTS yok + OAuth callback kırık
- [ ] `RATE_LIMIT_TRUST_PROXY_HEADERS=true` + `RATE_LIMIT_TRUSTED_PROXY_CIDRS=<railway-cidrs>` — **O-1**
- [ ] `REDIS_URL` gerçek Redis'e işaret ediyor (`rediss://` tercih edilir) — **O-3**
- [ ] `CORS_ALLOWED_ORIGINS` yalnızca gerçek frontend origin'i (wildcard reddediliyor ✅)
- [ ] `SWAGGER_ENABLED` **set edilmedi veya `false`** (default false ✅ — yine de doğrulayın)
- [ ] `DEMO_SEED_ENABLED=false` ve Flyway `locations` demo-seed içermiyor (default ✅)
- [ ] `ADMIN_PASSWORD_HASH` gerçek bir BCrypt hash (validator zorunlu kılıyor)
- [ ] `JWT_SECRET` ≥ 32 byte, rotate edilmiş, yalnızca env'de
- [ ] `RESEND_FROM` doğrulanmış alan adı; `FRONTEND_BASE_URL` / `OAUTH2_FRONTEND_REDIRECT_URI` prod origin'i
- [ ] `R2_*` production bucket ve rotate edilmiş anahtarlar
- [ ] Frontend build'inde `VITE_API_BASE_URL` prod API origin'i (stub ödeme zaten `import.meta.env.DEV` ile ölü kod ✅)

### 🔐 Secret hijyeni

- [ ] **O-9** `application-local.yml`'deki tüm değerler rotate edildi: Neon, JWT_SECRET, Resend, R2 (ikisi de), Google client secret
- [ ] Local secret dosyası repo dışına / iCloud senkronizasyonu dışına taşındı
- [ ] Repo'nun iCloud senkronize `~/Desktop` altından çıkarılması değerlendirildi
- [ ] GitHub'da secret scanning + push protection açık
- [x] Git geçmişinde secret dosyası yok — 188 commit / tüm ref'ler tarandı, yalnızca `.example` şablonları (placeholder'lı) mevcut
- [ ] Yine de içerik bazlı bir doğrulama için `gitleaks detect --source . --log-opts="--all" --redact` çalıştırıldı (dosya adı taraması, gövdeye gömülmüş bir secret'ı yakalamaz)

### 🛡️ Header / TLS / izleme

- [ ] **O-2** HSTS açıkça yapılandırıldı (`includeSubDomains`, `preload` — preload kararı bilinçli)
- [ ] **O-7** SPA host'unda CSP + HSTS + `X-Content-Type-Options` + `Referrer-Policy` + `Permissions-Policy` tanımlı
- [ ] Backend'e `Permissions-Policy` + `Cross-Origin-Opener-Policy` eklendi
- [ ] TLS: yalnızca TLS 1.2+, HTTP → HTTPS yönlendirmesi, sertifika otomatik yenileme çalışıyor
- [ ] Sentry DSN prod'da tanımlı; `PaymentProviderException` ve webhook imza hataları için alarm kuralı var
- [ ] Ödeme yolunda structured log: `paymentId`, `subscriptionId`, `idempotencyKey`, `iyziEventType` — kart verisi / imza / token **yok**
- [ ] DB yedeği + point-in-time recovery doğrulandı (Neon)

### 🧪 Test / süreç

- [ ] `cd backend && ./mvnw verify` yeşil
- [ ] CLAUDE.md'nin zorunlu kritik yolları geçiyor: auth/security, booking race, **payment idempotency**, weekly quota, message gate
- [ ] **O-8** CI'a OWASP Dependency-Check eklendi (`failBuildOnCVSS=7`); Dependabot açık
- [ ] `npm audit fix` ile browserslist kapatıldı
- [ ] **O-6** `POST /api/v1/refund-requests` rate limit'e bağlandı
- [ ] **O-5** İade politikasının hizmet ifası boyutu hukuken netleştirildi ve kodda yazılı

---

## Denetimin Sınırları

Dürüst olmak gerekirse şunlar **yapılmadı**:

1. **Git geçmişi taraması dosya adı düzeyinde tamamlandı, içerik düzeyinde tamamlanamadı.** 188 commit'in ve tüm ref'lerin dokunduğu her yol tarandı: secret adı desenine uyan tek şey üç `.example` şablonu ve üçü de placeholder taşıyor — yani gerçek bir secret dosyası hiçbir zaman commit'lenmemiş. **Doğrulayamadığım:** normal görünümlü bir dosyanın (bir test, bir doküman, bir migration) gövdesine gömülmüş bir secret olup olmadığı. Repo iCloud senkronize bir dizinde olduğu için `git log -p` bunun için kullanılamayacak kadar yavaş çalıştı (~5 dakikada 28 KB). Repo'yu iCloud dışına kopyalayıp şunu çalıştırın:
   ```bash
   gitleaks detect --source . --log-opts="--all" --redact
   ```

2. **Dinamik test yok.** Bulgular kaynak kod okumasına dayanıyor. Özellikle K-1'deki iyzico payload sözleşmesi, sandbox'ta gerçek bir webhook yakalanarak doğrulanmalı — dokümantasyondan çıkarılan bir sonuç, canlı trafikle teyit edilmiş bir sonuç değildir.

3. **Backend bağımlılık ağacı taranmadı.** `./mvnw dependency:tree` ve OWASP Dependency-Check çalıştırılmadı (build dizini iCloud tarafından tahliye edilmiş durumda). Bu yüzden "backend'de bilinen CVE yok" **diyemem** — sadece "taranmamış" diyebilirim. O-8'deki CI adımı bunu kalıcı olarak çözer.

4. **Hukuki değerlendirme yok.** O-5'teki iade politikası ve mesafeli satış yükümlülükleri hakkındaki notlar teknik gözlemdir, hukuki görüş değildir.

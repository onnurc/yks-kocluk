# Doğrulama Katmanı — Dört İtirazın Resmî Dokümantasyona Karşı Sınanması

**Tarih:** 2026-09-06
**Kaynak:** iyzico resmî webhook dokümantasyonu
- `https://docs.iyzico.com/ek-servisler/webhook` (TR)
- `https://docs.iyzico.com/en/advanced/webhook` (EN)

İki sayfa da aynı üç imza formülünü, aynı alan tablolarını ve aynı `iyziEventType` listelerini veriyor — çelişki yok, bu yüzden aşağıdaki kararlar tek kaynağa değil iki bağımsız sayfaya dayanıyor.

**İlişki:** Bu rapor [SECURITY-REVIEW-2026-09-06.md](SECURITY-REVIEW-2026-09-06.md) ve [SECURITY-REVIEW-ADDENDUM-iyzipay-java.md](SECURITY-REVIEW-ADDENDUM-iyzipay-java.md) üzerine bir **doğrulama katmanıdır**. O iki dosya değiştirilmedi; nerede yanıldıklarını burada açıkça işaretliyorum. Çelişki halinde **bu rapor bağlayıcıdır**.

**Kod değişikliği yapılmadı.** Aşağıdaki tüm kod blokları öneridir.

---

## Özet Karar Tablosu

| # | İtiraz | Karar |
|---|---|---|
| 1 | Webhook imzası ayraçsız; ek rapor §3 yanlış alarm | ✅ **DOĞRULANDI** — §3'ün ilgili kısmı geri çekildi |
| 2 | HPP/Checkout Form imzasında `token` var, sıralama farklı | ✅ **DOĞRULANDI** — K-1'in önerdiği dizilim eksik |
| 3 | `iyziEventType` Checkout Form'da `CHECKOUT_FORM_AUTH` | ✅ **DOĞRULANDI** — dördüncü, bağımsız uyuşmazlık |
| 4a | İmza başlığı için hesapta özellik açılmalı | ✅ **DOĞRULANDI** — deploy önkoşulu |
| 4b | 15 dk arayla 3 deneme, 2xx beklenir | ✅ **DOĞRULANDI** — hata kodu haritamız yanlış |

Doğrulama sırasında çıkan, hiçbir raporda olmayan iki ek bulgu: **V-3** (COACH_FULL rollback → para alındı, hizmet yok) ve **V-5** (`providerReference` alanı iyzico payload'ında hiç yok).

---

## İTİRAZ 1 — Webhook imzası gerçekten ayraçsız mı?

### Karar: ✅ **DOĞRULANDI.** Ek rapor §3'ün ilgili iddiası **geri çekildi**.

### Kanıt

Dokümantasyonun **"Webhook / İmza Doğrulama"** bölümü **üç ayrı imza formülü** tanımlıyor ve üçü de ayraç kullanmıyor — düz string birleştirme:

```javascript
// Direct Format
const key = secretKey + iyziEventType + paymentId + paymentConversationId + status;
const hmac256 = crypto.createHmac('sha256', secretKey).update(key).digest('hex');

// HPP Format (Checkout Form, PWI)
const key = secretKey + iyziEventType + iyziPaymentId + token + paymentConversationId + status;
const hmac256 = crypto.createHmac('sha256', secretKey).update(key).digest('hex');

// Subscription Format
const key = merchantId + secretKey + eventType + subscriptionReferenceCode
          + orderReferenceCode + customerReferenceCode;
const hmac256 = crypto.createHmac('sha256', secretKey).update(key).digest('hex');
```

Doğrulanan noktalar:
- **Ayraç yok.** Hiçbir formülde `:` veya başka bir ayırıcı geçmiyor.
- **`secretKey` hem HMAC anahtarı hem de veri dizesinin başında.** (Abonelik formatında `merchantId`'den sonra ikinci sırada.)
- **Çıktı hex, küçük harf** (`digest('hex')`), base64 değil.

Bu, `iyzipay-java` içindeki `ResponseSignatureGenerator`'ın (`SEPARATOR = ":"`) şemasından **tamamen farklı** — ama o sınıf **API yanıtları** için (`CheckoutForm`, `Payment`, `Refund`…), webhook için değil. İtirazın öne sürdüğü "iki ayrı şema" ayrımı doğru:

| | Ne için | Ayraç | Kütüphane desteği |
|---|---|---|---|
| `ResponseSignatureGenerator` | Senkron **API yanıtları** | `:` | ✅ `verifySignature()` hazır |
| `X-IYZ-SIGNATURE-V3` | **Webhook** bildirimleri | yok | ❌ kütüphanede yok, elle yazılmalı |

### Kodumuzda etkilenen yer

`backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java:351`

```java
String data = secretKey + iyziEventType + paymentIdStr + paymentConversationId + statusStr;
```

**Birleştirme biçimi doğru.** `calculateHmacSha256` (hex, küçük harf) doğru. `MessageDigest.isEqual` ile sabit zamanlı karşılaştırma (satır 354-356) doğru — hatta kütüphanenin kendi `HashValidator`'ından (`StringUtils.equals`, sabit zamanlı değil) daha iyi.

**Hatalı olan tek şey: alan listesi ve sırası** (bkz. İtiraz 2). Şema değil, girdiler.

### Önerilen düzeltme

Bu itiraz için **kod düzeltmesi gerekmiyor.** Yalnızca kod yorumuna dokümantasyon referansı eklemek, gelecekte aynı yanlış alarmı önler:

```java
    /**
     * Webhook imzası (X-IYZ-SIGNATURE-V3) — docs.iyzico.com/ek-servisler/webhook.
     *
     * DİKKAT: Bu şema, iyzipay-java'daki ResponseSignatureGenerator'dan (":" ayraçlı,
     * senkron API yanıtları için) FARKLIDIR ve kasıtlı olarak ayraçsızdır. Kütüphane
     * webhook imzasını kapsamaz; burası elle yazılmak zorundadır.
     *
     *   HPP    : secretKey + iyziEventType + iyziPaymentId + token + paymentConversationId + status
     *   Direct : secretKey + iyziEventType + paymentId + paymentConversationId + status
     *   HMAC-SHA256(key = secretKey), hex, küçük harf.
     */
    private void verifyWebhookSignature(IyzicoWebhookRequest request, String signatureV3) {
```

### Hangi bulguyu değiştiriyor

| Bulgu | Durum |
|---|---|
| **Ek rapor §3** — "Bizim kodumuz ayraçsız birleştiriyor… resmî konvansiyonla çelişiyor" | 🔴 **GERİ ÇEKİLDİ** |
| **Ek rapor §3** — "Kütüphane `verifySignature()` sunuyor, kullanmıyoruz" (API yanıtları için) | ✅ **GEÇERLİ** — değişmedi |
| **Ek rapor §3** — "Kütüphane webhook imzasını kapsamıyor, elle yazmak zorundasınız" | ✅ **GEÇERLİ** — doğrulandı |
| **Ana rapor K-1** — ayraçsız şema önerisi | ✅ **DOĞRUYDU** |

**İki rapor arasındaki çelişkinin çözümü:** Ana rapor K-1 haklıydı, ek rapor §3 haksızdı. §3'ün `:` ayracından yola çıkıp webhook koduna dair yaptığı çıkarım, iki farklı imza mekanizmasını birbirine karıştırmaktan kaynaklanıyor.

---

## İTİRAZ 2 — HPP imza diziliminde `token` var mı?

### Karar: ✅ **DOĞRULANDI.** K-1'in önerdiği dizilim eksik ve yanlış formata ait.

### Kanıt

Dokümantasyonun **"Webhook Formatları"** bölümündeki iki alan tablosu:

**Direct (API) format alanları:**

| Alan | Tip |
|---|---|
| `paymentConversationId` | string |
| `merchantId` | string |
| **`paymentId`** | **string** |
| `status` | string |
| `iyziReferenceCode` | string |
| `iyziEventType` | string |
| `iyziEventTime` | long |
| `iyziPaymentId` | long |

**HPP / Checkout Form format alanları:**

| Alan | Tip |
|---|---|
| `paymentConversationId` | string |
| `merchantId` | string |
| **`token`** | **string** |
| `status` | string |
| `iyziReferenceCode` | string |
| `iyziEventType` | string |
| `iyziEventTime` | long |
| `iyziPaymentId` | long |

Kritik gözlemler:
1. **HPP payload'ında `paymentId` alanı hiç yok.** Onun yerine `token` var.
2. **Direct payload'ında `token` yok**, `paymentId` var ve tipi **string** (bizim DTO'muz `Long`).
3. `iyziPaymentId` (long) **her iki formatta da** var, ama imzada yalnızca HPP formülünde kullanılıyor.
4. HPP imza sırası: `secretKey + iyziEventType + iyziPaymentId + token + paymentConversationId + status`

Biz `CheckoutFormInitialize` kullandığımız için (`RealIyzicoClient.java:116`) bize **HPP formatı** gelecek.

### Kodumuzda etkilenen yer

| Dosya:satır | Sorun |
|---|---|
| `dto/IyzicoWebhookRequest.java:8-9` | `@NotNull Long paymentId` — HPP payload'ında bu alan **yok** → doğrulama hatası → **her webhook 400** |
| `dto/IyzicoWebhookRequest.java` | `token`, `iyziPaymentId`, `merchantId`, `iyziReferenceCode`, `iyziEventTime` alanları **hiç tanımlı değil** |
| `service/SubscriptionService.java:347` | `paymentIdStr` imzaya giriyor — HPP'de böyle bir alan yok |
| `service/SubscriptionService.java:351` | İmza dizisinde `iyziPaymentId` ve `token` **eksik** |
| `service/SubscriptionService.java:316-320` | `paymentId.toString().equals(paymentConversationId)` — `paymentId` null olduğu için NPE riski + mantıksal olarak yanlış |
| `service/SubscriptionService.java:367` | `findById(request.paymentId())` — lookup yanlış alandan |

`RealIyzicoClient.java:66` bizim tarafımızda doğru: `request.setConversationId(paymentId.toString())`. Yani webhook'un `paymentConversationId` alanı **bizim `Payment.id`'mizi** taşıyacak — lookup anahtarı bu olmalı.

### Önerilen düzeltme (uygulanmadı)

**DTO — her iki formatı da karşılayacak şekilde:**

```java
package com.ykskocluk.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * iyzico webhook gövdesi — docs.iyzico.com/ek-servisler/webhook.
 * Tek endpoint hem Direct hem HPP formatı alabilir; ayrım iyziEventType üzerinden yapılır.
 *   HPP    : token dolu,     paymentId yok
 *   Direct : paymentId dolu, token yok
 */
public record IyzicoWebhookRequest(
        @JsonProperty("iyziEventType")
        @NotBlank(message = "iyziEventType boş olamaz")
        @Size(max = 64) String iyziEventType,

        @JsonProperty("iyziPaymentId")
        Long iyziPaymentId,

        @JsonProperty("token")
        @Size(max = 256) String token,

        @JsonProperty("paymentId")
        @Size(max = 128) String paymentId,          // Direct format; HPP'de gelmez. String!

        @JsonProperty("paymentConversationId")
        @NotBlank(message = "paymentConversationId boş olamaz")
        @Size(max = 128) String paymentConversationId,

        @JsonProperty("status")
        @NotBlank(message = "status boş olamaz")
        @Size(max = 32) String status,

        @JsonProperty("merchantId")
        @Size(max = 64) String merchantId,

        @JsonProperty("iyziReferenceCode")
        @Size(max = 128) String iyziReferenceCode,

        @JsonProperty("iyziEventTime")
        Long iyziEventTime
) {
    /** conversationId = checkout'ta gönderdiğimiz kendi Payment.id'miz. */
    public Long localPaymentId() {
        try {
            return Long.valueOf(paymentConversationId);
        } catch (NumberFormatException e) {
            return null;   // çağıran taraf 200 + no-op ile karşılasın (bizim işlemimiz değil)
        }
    }
}
```

> ⚠️ Mevcut `IyzicoWebhookRequest(Long, String, String)` kısa constructor'ı (satır 20-22) testlerde kullanılıyor olabilir. Kaldırmadan önce `grep -rn "new IyzicoWebhookRequest(" backend/src/test` ile çağıranları kontrol edin.

**İmza doğrulama — formata göre dallanan:**

```java
    /** HPP (Hosted Payment Page) event türleri — docs.iyzico.com/ek-servisler/webhook. */
    private static final Set<String> HPP_EVENT_TYPES = Set.of(
            "CHECKOUT_FORM_AUTH", "BANK_TRANSFER_AUTH", "BKM_AUTH", "BALANCE",
            "CONTACTLESS_AUTH", "CONTACTLESS_REFUND", "CREDIT_PAYMENT_AUTH",
            "CREDIT_PAYMENT_PENDING", "CREDIT_PAYMENT_INIT",
            "PWI_TKN_FUND", "PWI_TKN_AUTH", "PWI_TKN_THREEDS_AUTH");

    private String webhookSignaturePayload(IyzicoWebhookRequest r, String secretKey) {
        if (HPP_EVENT_TYPES.contains(r.iyziEventType())) {
            // secretKey + iyziEventType + iyziPaymentId + token + paymentConversationId + status
            return secretKey + str(r.iyziEventType()) + str(r.iyziPaymentId()) + str(r.token())
                    + str(r.paymentConversationId()) + str(r.status());
        }
        // Direct: secretKey + iyziEventType + paymentId + paymentConversationId + status
        return secretKey + str(r.iyziEventType()) + str(r.paymentId())
                + str(r.paymentConversationId()) + str(r.status());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
```

```java
    private void verifyWebhookSignature(IyzicoWebhookRequest request, String signatureV3) {
        if (iyzicoProperties == null || !iyzicoProperties.enabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "WEBHOOK_DISABLED", "Iyzico entegrasyonu etkin değil");
        }
        if (signatureV3 == null || signatureV3.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza başlığı eksik");
        }
        String secretKey = iyzicoProperties.secretKey() == null ? "" : iyzicoProperties.secretKey();

        String computed = calculateHmacSha256(webhookSignaturePayload(request, secretKey), secretKey);

        // Sabit zamanlı karşılaştırma korunuyor (kütüphanenin HashValidator'ından daha güvenli).
        boolean matches = MessageDigest.isEqual(
                computed.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                signatureV3.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            // 3 denemede kaybolur — sessiz kalmamalı.
            log.error("Webhook signature mismatch: eventType={}, conversationId={}",
                    request.iyziEventType(), request.paymentConversationId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza doğrulanamadı");
        }
    }
```

**Lookup — conversationId üzerinden:**

```java
        Long localPaymentId = request.localPaymentId();
        if (localPaymentId == null) {
            return IyzicoWebhookResponse.ignored("Bu bildirim bu sisteme ait değil");   // 200
        }
        Payment payment = paymentRepository.findById(localPaymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Ödeme bulunamadı"));
```

### Ek kazanç: `token` webhook'ta geliyor → K-3 basitleşiyor

Ek raporda `CheckoutForm.retrieve()` çağırabilmek için `payments.checkout_token` kolonu eklemeyi **zorunlu** göstermiştim. HPP webhook'u `token`'ı zaten taşıdığı için **bu kolon artık zorunlu değil** — retrieve'i doğrudan payload'daki token ile çağırabilirsiniz:

```java
CheckoutVerification v = iyzicoClient.verifyCheckout(request.token());
```

Kolonu yine de eklemek savunma derinliği sağlar (gelen token'ı bizim sakladığımızla eşleştirmek), ama **K-3'ün önkoşulu değil**. Ek rapordaki "zorunlu" ifadesi → **opsiyonel** olarak düzeltilmelidir.

### Hangi bulguyu değiştiriyor

| Bulgu | Durum |
|---|---|
| **Ana rapor K-1** — "alan adı `iyziPaymentId` olmalı" | ⚠️ **KISMEN DOĞRU** — HPP'de imzaya `iyziPaymentId` **ve `token`** girer, sıra farklı |
| **Ana rapor K-1** — DTO'da `localPaymentId()` ile conversationId'den lookup | ✅ **DOĞRULANDI** |
| **Ana rapor K-1** — "`paymentId` alanı gelmez, `@NotNull` patlar" | ✅ **DOĞRULANDI** (HPP'de gerçekten yok) |
| **Ana rapor K-1** — `@JsonProperty("iyziPaymentId") Long iyziPaymentId` önerisi | ⚠️ **EKSİK** — `token` alanı da gerekli |
| **Ek rapor §2.1** — `checkout_token` kolonu **zorunlu** | ⚠️ **OPSİYONEL**'e düşürüldü |

---

## İTİRAZ 3 — `iyziEventType` kontrolü her webhook'u reddediyor mu?

### Karar: ✅ **DOĞRULANDI.** K-1'den bağımsız, dördüncü uyuşmazlık.

### Kanıt

Dokümantasyonun **"iyziEventType Değerleri"** bölümü üç grup tanımlıyor:

**Direct / API formatı (8 değer):**
`PAYMENT_API`, `API_AUTH`, `THREE_DS_AUTH`, `THREE_DS_CALLBACK`, `BKM_AUTH`, `BALANCE`, `CONTACTLESS_AUTH`, `CONTACTLESS_REFUND`

**HPP formatı (12 değer):**
`CHECKOUT_FORM_AUTH`, `BANK_TRANSFER_AUTH`, `BKM_AUTH`, `BALANCE`, `CONTACTLESS_AUTH`, `CONTACTLESS_REFUND`, `CREDIT_PAYMENT_AUTH`, `CREDIT_PAYMENT_PENDING`, `CREDIT_PAYMENT_INIT`, `PWI_TKN_FUND`, `PWI_TKN_AUTH`, `PWI_TKN_THREEDS_AUTH`

**Abonelik formatı (2 değer):**
`subscription.order.success`, `subscription.order.failure`

**`PAYMENT_API` yalnızca Direct/API listesinde var — HPP listesinde yok.**

### Bizim akışımızda hangileri gelebilir?

| Event type | Gelir mi? | Neden |
|---|---|---|
| `CHECKOUT_FORM_AUTH` | ✅ **Ana akış** | `CheckoutFormInitialize` kullanıyoruz |
| `PAYMENT_API` | ❌ **Asla** | Direct API akışı; biz kullanmıyoruz |
| `BKM_AUTH`, `BANK_TRANSFER_AUTH`, `CREDIT_PAYMENT_*`, `PWI_TKN_*` | ⚠️ Hesapta o ödeme yöntemi açıksa | Checkout Form'da BKM/havale/PWI seçenekleri açıksa gelebilir |
| `BALANCE`, `CONTACTLESS_*` | ⚠️ Hesap düzeyi | Bizim ödeme akışımızla ilgisiz ama aynı endpoint'e düşebilir |
| `subscription.order.*` | ❌ Şimdilik | Yerleşik aboneliğe geçilirse gelir (ek rapor §5) |

### Kodumuzda etkilenen yer

`backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java:312-315`

```java
    private void validateWebhookIdentity(IyzicoWebhookRequest request) {
        if (!"PAYMENT_API".equals(request.iyziEventType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_EVENT", "Geçersiz webhook olay türü");
        }
```

Gerçek `CHECKOUT_FORM_AUTH` bildirimi bu satırda **400** ile reddedilir. İtiraz haklı: **imza tamamen düzeltilse bile bu kontrol tek başına her webhook'u öldürür.** K-1'den bağımsız, ayrı bir yama gerektirir.

Ayrıca bu kontrol `INVALID_WEBHOOK_EVENT` → 400 döndürdüğü için, bizimle ilgisiz `BALANCE` gibi bildirimler de iyzico'nun 3 deneme bütçesini boşa harcar (bkz. İtiraz 4b).

### Önerilen düzeltme (uygulanmadı)

```java
    /** Bu servisin gerçekten işlediği event türleri. */
    private static final Set<String> HANDLED_EVENT_TYPES = Set.of("CHECKOUT_FORM_AUTH");

    private WebhookDecision classify(IyzicoWebhookRequest request) {
        if (!HANDLED_EVENT_TYPES.contains(request.iyziEventType())) {
            // Meşru ama bizim akışımıza ait değil (BALANCE, CONTACTLESS_REFUND, PWI_TKN_*, ...).
            // 400 DEĞİL: iyzico bunu kalıcı hata sayıp 3 deneme boşa harcar. ACK'le ve geç.
            log.debug("Ignoring unhandled webhook event type: {}", request.iyziEventType());
            return WebhookDecision.IGNORE;
        }
        return WebhookDecision.PROCESS;
    }

    private enum WebhookDecision { PROCESS, IGNORE }
```

```java
    @Transactional
    public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request, String signatureV3) {
        verifyWebhookSignature(request, signatureV3);        // imza her zaman önce
        if (classify(request) == WebhookDecision.IGNORE) {
            return IyzicoWebhookResponse.ignored("Bu olay türü işlenmiyor");   // HTTP 200
        }
        return applyWebhookOutcome(request);
    }
```

> İmza doğrulaması **her zaman** event-type filtresinden önce çalışmalı. Aksi halde imzasız bir istek "bilinmeyen tür" diye 200 alır ve bir oracle oluşur.

### Hangi bulguyu değiştiriyor

**Hiçbirini — bu tamamen yeni.** Ana rapor K-1 üç uyuşmazlık sayıyordu (alan adı, kimlik invariantı, imza girdisi). Bu **dördüncüsü** ve diğerlerinden bağımsız olarak tek başına yeterli bir kırılma noktası. Yeni bulgu **V-1** olarak numaralandırıldı.

---

## İTİRAZ 4a — İmza başlığı için hesapta özellik açılmalı mı?

### Karar: ✅ **DOĞRULANDI.** Deploy önkoşulu, ve tedarik süresi var.

### Kanıt

Dokümantasyonun **"Webhook / İmza Doğrulama"** bölümü açıkça belirtiyor: `X-IYZ-SIGNATURE-V3` değerinin header'da gönderilebilmesi için **hesap üzerinde webhook signature özelliğinin aktif olması gerekiyor**; aktivasyon için `entegrasyon@iyzico.com` adresine başvurulması söyleniyor.

Ayrıca webhook bildirimlerinin kendisi ayrı bir adım: üye işyeri panelinden **Ayarlar → Üye İşyeri Ayarları → Üye İşyeri Bildirimleri** üzerinden etkinleştiriliyor. Yani **iki ayrı anahtar** var:

1. Webhook bildirimlerinin gönderilmesi → panelden
2. Bildirimlerde imza başlığının bulunması → iyzico entegrasyon ekibinden talep

### Kodumuzda etkilenen yer

`backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java:337-339`

```java
        if (signatureV3 == null || signatureV3.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza başlığı eksik");
        }
```

Bu **doğru bir fail-closed tercih** — imzasız bildirimi kabul etmek, ana rapor K-3'teki tek katmanlı güven sorununu daha da kötüleştirirdi. Ama sonucu şu: **özellik açılmadan hiçbir webhook geçmez.** Kod bunu 401 ile reddeder, iyzico 3 kez dener, sonra pes eder ve bildirim kalıcı olarak kaybolur.

Bu bir kod hatası değil, bir **operasyonel bağımlılık** — ve iki raporda da yok.

### Önerilen düzeltme (uygulanmadı)

Kod tarafında yapılacak tek şey teşhisi kolaylaştırmak:

```java
        if (signatureV3 == null || signatureV3.isBlank()) {
            // Neredeyse her zaman tek bir sebep: hesapta "webhook signature" özelliği açık değil.
            // Aktivasyon: entegrasyon@iyzico.com — docs.iyzico.com/ek-servisler/webhook
            log.error("Webhook received WITHOUT X-IYZ-SIGNATURE-V3 header. "
                    + "Is the webhook-signature feature enabled on the iyzico account? "
                    + "eventType={}, conversationId={}",
                    request.iyziEventType(), request.paymentConversationId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza başlığı eksik");
        }
```

**Asıl iş kod dışında** ve deploy checklist'ine girmeli:

- [ ] iyzico panelinde **Ayarlar → Üye İşyeri Ayarları → Üye İşyeri Bildirimleri** açık, webhook URL'i production adresine ayarlı
- [ ] `entegrasyon@iyzico.com` adresine **webhook signature** özelliğinin aktivasyonu için talep gönderildi ve **onay alındı**
- [ ] Sandbox hesabında da aynı özellik açık (aksi halde K-1 düzeltmesini test edemezsiniz)
- [ ] İmzasız gelen bir webhook için Sentry alarmı tanımlı

> ⏱️ **Bu madde tedarik süresi olan tek maddedir.** Bir e-postanın yanıtlanmasını beklemek gün alabilir ve bu özellik açılmadan K-1'in doğruluğunu sandbox'ta **kanıtlayamazsınız**. Bu yüzden aşağıdaki öncelik sıralamasında en başa alındı.

### Hangi bulguyu değiştiriyor

**Hiçbirini — tamamen yeni.** Yeni bulgu **V-4**. Ana rapor "Deploy konfigürasyonu" bölümünde `IYZICO_*` env değişkenleri var ama **iyzico hesabı tarafındaki** yapılandırma hiç yok.

---

## İTİRAZ 4b — Retry davranışı ve HTTP durum kodları

### Karar: ✅ **DOĞRULANDI.** Mevcut hata kodu haritamız retry bütçesini yakıyor.

### Kanıt

Dokümantasyonun **"Webhook Yeniden Deneme"** bölümü:

- İlk bildirim ödeme denemesinden **10–15 saniye** sonra gönderiliyor.
- Sunucunuz **2xx** dönene kadar **15 dakikada bir** tekrar deneniyor.
- **3 denemeden sonra duruyor.**

Yani toplam pencere yaklaşık **45 dakika** ve ondan sonra bildirim **kalıcı olarak kaybolur**.

### Kodumuzda etkilenen yer

Tüm hata yolları `ApiException` → `GlobalExceptionHandler` → ilgili HTTP durumu. Hiçbiri 2xx değil:

| Satır | Hata | Şu anki durum |
|---|---|---|
| `SubscriptionService.java:334` | `WEBHOOK_DISABLED` | 401 |
| `SubscriptionService.java:338, 358` | `INVALID_WEBHOOK_SIGNATURE` | 401 |
| `SubscriptionService.java:314` | `INVALID_WEBHOOK_EVENT` | 400 |
| `SubscriptionService.java:318` | `PAYMENT_CONVERSATION_MISMATCH` | 400 |
| `SubscriptionService.java:323` | `PROVIDER_REFERENCE_MISSING` | 400 |
| `SubscriptionService.java:364` | `UNSUPPORTED_WEBHOOK_STATUS` | 400 |
| `SubscriptionService.java:368` | `PAYMENT_NOT_FOUND` | 404 |
| `SubscriptionService.java:371` | `WEBHOOK_PAYMENT_MISMATCH` | 409 |
| `SubscriptionService.java:274` | `COACH_FULL` | 409 + **tüm transaction rollback** |

#### En kötüsü: ara durum bildirimleri

`SubscriptionService.java:363-365`:

```java
        if (!"SUCCESS".equalsIgnoreCase(request.status()) && !"FAILURE".equalsIgnoreCase(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_WEBHOOK_STATUS", ...);
        }
```

Dokümantasyonun **geçerli `status` değerleri** listesi yalnızca SUCCESS/FAILURE değil:

`FAILURE`, `SUCCESS`, `INIT_THREEDS`, `CALLBACK_THREEDS`, `BKM_POS_SELECTED`, `INIT_APM`, `INIT_CONTACTLESS`, ve yalnızca HPP'de: `INIT_BANK_TRANSFER`, `INIT_CREDIT`, `PENDING_CREDIT`

3D Secure akışında `INIT_THREEDS` ve `CALLBACK_THREEDS` bildirimleri **normal seyirde gelir**. Kodumuz her birine 400 döner. Sonuç: bir ödeme akışında 3 deneme bütçesi **asıl `SUCCESS` bildirimi gelmeden önce** ara durumlara harcanabilir.

#### `PENDING_PAYMENT` temizleyicisiyle etkileşim

`SubscriptionBillingService.expireStalePendingCheckouts` 30 dakikalık bir eşik kullanıyor, ama `SubscriptionRenewalJob.scheduledRun()` **günde bir kez 03:00'te** çalışıyor. Yani abonelik hemen değil, **bir sonraki 03:00'te** `EXPIRED` olur ve `PENDING` ödeme `FAILED` işaretlenir.

Net etki: webhook 45 dakikalık pencerede geçemezse, **başarılı bir ödeme kaydımızda `FAILED` olarak durur**. Müşteri ödemiştir, sistem ödemediğini düşünür, otomatik iade tetiklenmez.

### Önerilen düzeltme (uygulanmadı) — durum kodu haritası

| Senaryo | Şu an | Olması gereken | Gerekçe |
|---|---|---|---|
| İmza geçersiz / eksik | 401 | **401 (değiştirme)** + `log.error` + Sentry | ACK etmek güvenlik açığı olur. Ama 3 denemede kaybolacağı için alarm zorunlu. |
| `WEBHOOK_DISABLED` (iyzico kapalıyken istek) | 401 | **401 (değiştirme)** | Aynı gerekçe. Prod'da hiç olmamalı. |
| Bizim akışımıza ait olmayan event (`BALANCE`, `CONTACTLESS_REFUND`, `PWI_TKN_*`) | 400 | **200 + no-op** | Kalıcı olarak ilgisiz. Retry hiçbir zaman düzeltmez, bütçe yakar. |
| Ara durum (`INIT_THREEDS`, `CALLBACK_THREEDS`, `BKM_POS_SELECTED`, `INIT_APM`, `INIT_CONTACTLESS`, `INIT_BANK_TRANSFER`, `INIT_CREDIT`, `PENDING_CREDIT`) | 400 | **200 + no-op** | Meşru ara bildirim. İşlenecek durum değişikliği yok. |
| `SUCCESS` / `FAILURE` başarıyla işlendi | 200 | **200** ✅ | Değişiklik yok |
| Idempotent tekrar (zaten SUCCESS/FAILED) | 200 | **200** ✅ | Değişiklik yok |
| `paymentConversationId` sayı değil / bizim değil | 400 | **200 + no-op + `log.warn`** | Yanlış ortama düşmüş bildirim; retry düzeltmez |
| `PAYMENT_NOT_FOUND` | 404 | **500 (retry istensin)** + 3. denemede Sentry | Replica gecikmesi / yarış olabilir; 3 deneme ucuz, kayıp pahalı |
| `WEBHOOK_PAYMENT_MISMATCH` (CHARGE değil / subscription null) | 409 | **200 + no-op + Sentry** | Veri tutarsızlığı; retry düzeltmez, insan müdahalesi gerekir |
| `COACH_FULL` | 409 + rollback | **200** + ödeme SUCCESS + iade kuyruğu (bkz. **V-3**) | **Para tahsil edilmiş.** Rollback en kötü sonuç. |
| DB / altyapı hatası | 500 | **500** ✅ | Retry mantıklı |

**Genel kural:** *2xx = "bu bildirimi kesin olarak ele aldım **veya** hiçbir zaman ele alamayacağım". 5xx = "şu an başaramadım ama tekrar denersen başarabilirim".* Kalıcı hataya 4xx dönmek en kötü seçenek: ne işliyoruz ne de tekrar deneme şansı bırakıyoruz.

**Uygulama iskeleti:**

```java
    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCESS", "FAILURE");

    /** Meşru ara bildirimler — ACK'lenir, durum değiştirmez. */
    private static final Set<String> INTERMEDIATE_STATUSES = Set.of(
            "INIT_THREEDS", "CALLBACK_THREEDS", "BKM_POS_SELECTED", "INIT_APM",
            "INIT_CONTACTLESS", "INIT_BANK_TRANSFER", "INIT_CREDIT", "PENDING_CREDIT");

    private IyzicoWebhookResponse applyWebhookOutcome(IyzicoWebhookRequest request) {
        String status = request.status() == null ? "" : request.status().toUpperCase(Locale.ROOT);

        if (INTERMEDIATE_STATUSES.contains(status)) {
            log.debug("Intermediate webhook status acknowledged: {} conversationId={}",
                    status, request.paymentConversationId());
            return IyzicoWebhookResponse.ignored("Ara durum bildirimi alındı");      // 200
        }
        if (!TERMINAL_STATUSES.contains(status)) {
            // Dokümante edilmemiş yeni bir durum. ACK'le (retry düzeltmez) ama görünür kıl.
            log.warn("Unknown webhook status acknowledged: {} conversationId={}",
                    status, request.paymentConversationId());
            return IyzicoWebhookResponse.ignored("Bilinmeyen durum bildirimi alındı"); // 200
        }
        // ... mevcut SUCCESS / FAILURE mantığı
    }
```

`IyzicoWebhookResponse`'a bir fabrika metodu:

```java
    public static IyzicoWebhookResponse ignored(String message) {
        return new IyzicoWebhookResponse("IGNORED", message);
    }
```

`PAYMENT_NOT_FOUND`'u retry'lanabilir kılmak için ayrı bir istisna tipi (mevcut `ApiException` sözleşmesini bozmadan):

```java
    // exception/WebhookRetryableException.java — GlobalExceptionHandler'da 503'e eşlenir
    public class WebhookRetryableException extends RuntimeException { ... }
```

```java
        Payment payment = paymentRepository.findById(localPaymentId)
                .orElseThrow(() -> new WebhookRetryableException(
                        "Payment not found yet; asking iyzico to retry: " + localPaymentId));
```

### Hangi bulguyu değiştiriyor

**Hiçbirini — tamamen yeni.** İki rapor da webhook'un **doğruluğuna** odaklanmış, **teslim edilebilirliğine** hiç bakmamış. Yeni bulgu **V-2**.

---

# Doğrulama Sırasında Çıkan Ek Bulgular

## V-3 🔴 — `COACH_FULL` webhook'ta transaction'ı geri alıyor: para tahsil edilir, hizmet verilmez, iade tetiklenmez

**Risk:** Kritik (para alınıp hizmet verilmemesi)
**Dosya:** `service/SubscriptionService.java:271-275`, `:305-310`, `:388`

`processWebhook(request, signatureV3)` `@Transactional` (satır 305). İçinden çağrılan `completePaymentSuccess` şunu yapıyor:

```java
271    private void completePaymentSuccess(Payment payment, Subscription subscription, String providerReference) {
272        int updated = coachProfileRepository.incrementActiveStudentCountIfRoom(subscription.getCoachProfile().getId());
273        if (updated == 0) {
274            throw new ApiException(HttpStatus.CONFLICT, "COACH_FULL", "Koçun kontenjanı dolu");
275        }
```

Bu istisna `@Transactional` sınırından geçtiği için **tüm webhook transaction'ı geri alınır**. Sonuç zinciri:

1. Öğrenci ödemeyi yapar, iyzico parayı tahsil eder.
2. Webhook gelir, koç bu arada dolmuştur → `COACH_FULL` → rollback.
3. `Payment` `PENDING` kalır — `SUCCESS` olarak **kaydedilmez**.
4. 409 döner → iyzico 15 dk arayla 2 kez daha dener → koç hâlâ dolu → pes eder.
5. Bir sonraki 03:00'te `expireStalePendingCheckouts` aboneliği `EXPIRED`, ödemeyi `FAILED` yapar.
6. **Sistemde hiçbir yerde "bu müşteri ödedi" bilgisi kalmaz.** Otomatik iade tetiklenmez, admin panelinde başarısız bir ödeme olarak görünür.

**Erişilebilir mi?** Evet. `preparePendingSubscription` (satır ~168-212) checkout aşamasında **kontenjan kontrolü yapmıyor** — yalnızca koçun var ve `APPROVED` olduğunu doğruluyor (satır 196). Kontenjan yalnızca aktivasyon anında, atomik `incrementActiveStudentCountIfRoom` ile kontrol ediliyor. İki öğrenci son slot için eşzamanlı checkout başlatabilir; pencere checkout ile webhook arası (10–15 sn + kullanıcının kartı girme süresi). Düşük olasılıklı ama gerçek, ve sonucu sessiz para kaybı.

**Önerilen düzeltme (uygulanmadı) — iki katmanlı:**

**(a) Pencereyi daralt** — checkout başlatılırken kontenjanı ön-kontrol edin (garanti değil, ama çoğu vakayı eler):

```java
        // preparePendingSubscription içinde, coach bulunduktan sonra:
        if (coach.getActiveStudentCount() >= coach.getMaxStudentCapacity()) {
            throw new ApiException(HttpStatus.CONFLICT, "COACH_FULL",
                    "Koçun kontenjanı dolu. Lütfen başka bir koç seçin.");
        }
```

**(b) Asıl düzeltme — rollback etme, parayı kaydet:**

```java
    private void completePaymentSuccess(Payment payment, Subscription subscription, String providerReference) {
        Instant now = Instant.now();
        int updated = coachProfileRepository.incrementActiveStudentCountIfRoom(subscription.getCoachProfile().getId());

        // Para SAĞLAYICI tarafında tahsil edildi. Kontenjan dolduysa bile ödemeyi kaydetmeden
        // rollback yapmak, "müşteri ödedi ama sistemde izi yok" durumunu üretir.
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderReference(providerReference);
        payment.setSucceededAt(now);
        paymentRepository.saveAndFlush(payment);

        if (updated == 0) {
            subscription.setStatus(SubscriptionStatus.TERMINATED);
            subscription.setAutoRenew(false);
            subscription.setCancelledAt(now);
            subscription.setTerminationReason("COACH_FULL_AFTER_PAYMENT");
            subscriptionRepository.saveAndFlush(subscription);

            log.error("PAID BUT NOT ACTIVATED — coach full. paymentId={}, subscriptionId={}, coachProfileId={}",
                    payment.getId(), subscription.getId(), subscription.getCoachProfile().getId());
            events.publishEvent(new PaymentRequiresRefundEvent(payment.getId(), "COACH_FULL"));
            return;   // çağıran taraf 200 dönecek: bildirimi ele aldık
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(now);
        subscription.setEndAt(now.plus(subscription.getPkg().getDurationDays(), ChronoUnit.DAYS));
        subscriptionRepository.saveAndFlush(subscription);
        events.publishEvent(new PurchaseConfirmedEvent(/* ... mevcut haliyle ... */));
    }
```

```java
        // applyWebhookOutcome içinde
        completePaymentSuccess(payment, subscription, providerReferenceFrom(request));
        return payment.getSubscription().getStatus() == SubscriptionStatus.TERMINATED
                ? IyzicoWebhookResponse.ignored("Ödeme alındı, kontenjan dolu — iade sürecine alındı")
                : new IyzicoWebhookResponse("PROCESSED", "Ödeme başarıyla tamamlandı ve abonelik aktif edildi");
```

> ⚠️ `PaymentRequiresRefundEvent` yeni bir tip ve **otomatik iade mi, admin kuyruğu mu** olacağı bir ürün kararıdır. `@TransactionalEventListener(AFTER_COMMIT)` ile `SubscriptionService.refund(...)`'a bağlanabilir (CLAUDE.md'nin izin verdiği tek event kullanımı), ama otomatik iade tetiklemek para hareketi olduğu için bilinçli onay ister. Minimum: log + Sentry + admin panelinde görünür bir durum.

**Not:** Bu senaryo `succeedPayment` (stub, satır ~247) yolunda da var, ama orası yalnızca `local/stub/test` profillerinde erişilebilir.

---

## V-5 🟡 — `providerReference` alanı iyzico webhook payload'ında hiç yok

**Risk:** Orta (K-1'in bir uzantısı, ama ayrı bir kod satırı)
**Dosya:** `dto/IyzicoWebhookRequest.java:13-14`, `service/SubscriptionService.java:321-325`, `:388`

```java
321        if ("SUCCESS".equalsIgnoreCase(request.status())
322                && (request.providerReference() == null || request.providerReference().isBlank())) {
323            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_REFERENCE_MISSING", ...);
```

Doküman alan tablolarında `providerReference` diye bir alan **yok** — ne Direct'te ne HPP'de. Sağlayıcı referansı olarak kullanılabilecek alanlar: `iyziPaymentId` (long), `iyziReferenceCode` (string), ve HPP'de `token`.

Yani bu kontrol **her başarılı ödemede** tetiklenir. K-1 düzeltilse bile ayrıca ele alınmalı.

**Önerilen düzeltme (uygulanmadı):**

```java
    /** iyzico'nun ödeme kimliği — refund ve mutabakat bu değerle yapılır. */
    private String providerReferenceFrom(IyzicoWebhookRequest request) {
        if (request.iyziPaymentId() != null) {
            return request.iyziPaymentId().toString();
        }
        return request.iyziReferenceCode();   // son çare
    }
```

```java
        if ("SUCCESS".equalsIgnoreCase(request.status()) && request.iyziPaymentId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROVIDER_REFERENCE_MISSING",
                    "Başarılı ödeme bildirimi iyziPaymentId içermeli");
        }
```

Bu değer, ek rapor §4'teki `Refund.createV2(paymentId)` çağrısının ihtiyaç duyduğu `paymentId`'dir — yani **Y-5'in çözümü buradan besleniyor**. Daha da iyisi: K-3'teki `CheckoutForm.retrieve()` teyidinden `form.getPaymentId()` alınırsa hem doğrulanmış hem yetkili kaynaktan gelmiş olur.

---

# 1. Güncellenmiş Öncelik Sırası

**Evet, sıralama değişiyor.** İki sebeple: (a) **V-4** tedarik süresi olan bir önkoşul, (b) **K-2**'nin kritikliği düştü çünkü webhook'un `token` taşıdığı ortaya çıktı.

| Yeni sıra | Madde | Neden bu sırada |
|---|---|---|
| **0** | **V-4** — iyzico hesabında webhook + imza özelliğinin açılması | **Önkoşul.** E-posta trafiği gün alabilir. Bu açılmadan K-1'in doğruluğunu sandbox'ta kanıtlayamazsınız. Bugün başlatın. |
| **1** | **K-1 + V-1 + V-5** — webhook sözleşmesinin HPP formatına hizalanması | Tek bir iş kalemi olarak ele alın: DTO alanları + imza dizilimi + event type + provider reference. Parçalı düzeltmek anlamsız; hepsi aynı isteği reddediyor. |
| **2** | **K-3** — `CheckoutForm.retrieve()` ile sunucu teyidi | Artık daha ucuz: `token` webhook'ta geliyor, kolon eklemeye gerek yok. Elle yazılmış imza kodundaki olası bir hatanın tek başına ücretsiz aboneliğe dönüşmesini engelleyen katman. |
| **3** | **V-3** — `COACH_FULL` rollback'i | Para alınıp hizmet verilmemesi. Düzeltmesi küçük, sonucu ağır. |
| **4** | **V-2** — retry semantiği / HTTP durum kodu haritası | 45 dakikalık pencere dışında bildirim kalıcı kayıp. Ara durumlarda 400 dönmek en yaygın kayıp sebebi olacak. |
| **5** | **Y-1** — yenileme fail-open (süresiz ACTIVE) | Değişmedi. Ödeme akışından bağımsız, kendi başına kritik. |
| **6** | **K-2** — callback endpoint'inin ayrılması | ⬇️ **Düştü.** Webhook `token` taşıdığı için para akışı K-2 olmadan da tamamlanabilir. Kalan etki kullanıcı deneyimi: ödeme sonrası tarayıcı JSON endpoint'ine form-POST atıp hata ekranı görüyor. Hâlâ gerekli, ama artık "para çalışmıyor" değil "dönüş sayfası bozuk". |
| **7** | **Y-3, Y-4, Y-5** — frontend host allowlist, timeout, refund v2 | Değişmedi. Y-5'in çözümü **V-5** + ek rapor §4 ile netleşti. |

---

# 2. Geri Çekilen Bulgular

| Bulgu | Kaynak | Durum | Açıklama |
|---|---|---|---|
| "Webhook imza kodumuz resmî `:` ayraçlı şemayla çelişiyor" | Ek rapor **§3** | 🔴 **GERİ ÇEKİLDİ** | Webhook imzası dokümantasyon gereği **ayraçsız**. `verifyWebhookSignature`'ın birleştirme biçimi, HMAC anahtarı ve hex çıktısı **doğru**. Hata yalnızca hangi alanların birleştirildiğinde. |
| "`ResponseSignatureGenerator`'ın `:` ayracı iyzico'nun **genel** imza konvansiyonunu gösteriyor" | Ek rapor **§3** | 🔴 **GERİ ÇEKİLDİ** | Genel bir konvansiyon yok. API yanıtları ve webhook'lar birbirinden bağımsız iki şema kullanıyor. |
| "`payments.checkout_token` kolonu eklenmeli (**zorunlu**)" | Ek rapor **§3**, §2.1 | ⚠️ **OPSİYONEL'E DÜŞÜRÜLDÜ** | HPP webhook'u `token`'ı zaten taşıyor. Kolon savunma derinliği için yararlı ama K-3'ün önkoşulu değil. |
| "İmza `iyziPaymentId` ile hesaplanmalı" | Ana rapor **K-1** #3 | ⚠️ **KISMEN DOĞRU → GENİŞLETİLDİ** | Doğru yönde ama eksik: HPP diziliminde `iyziPaymentId`'den sonra **`token`** de var. |
| "`@JsonProperty("iyziPaymentId") Long iyziPaymentId` + `paymentConversationId` yeterli" (DTO önerisi) | Ana rapor **K-1** | ⚠️ **EKSİK** | `token`, `merchantId`, `iyziReferenceCode`, `iyziEventTime` de gerekli; `paymentId` **String** olmalı (Direct formatı için). |
| "İmza şeması ve encoding (hex/base64) doğrulanmalı" uyarısı | Ana rapor **K-1** notu | ✅ **ÇÖZÜLDÜ** | Dokümantasyon net: HMAC-SHA256, hex, küçük harf. Artık açık soru değil. |

**Geçerliliğini koruyanlar** (yanlış anlaşılmasın diye): Ek rapor §3'ün "kütüphane 14 modelde `verifySignature()` sunuyor, biz hiçbirini kullanmıyoruz" tespiti **tamamen geçerli** — o, senkron API yanıtları (`CheckoutFormInitialize`, `CheckoutForm`, `Refund`, `Payment`) için ve K-3'ün temelini oluşturuyor. Geri çekilen yalnızca oradan webhook koduna yapılan çıkarım.

---

# 3. Yeni Bulgular

| # | Bulgu | Risk | Dosya:satır | Kaynak |
|---|---|---|---|---|
| **V-1** | `iyziEventType` kontrolü `PAYMENT_API` bekliyor; Checkout Form akışında `CHECKOUT_FORM_AUTH` gelir → imza düzelse bile her webhook 400 | 🔴 Kritik | `SubscriptionService.java:313` | İtiraz 3 |
| **V-2** | Ara durum bildirimlerine (`INIT_THREEDS`, `CALLBACK_THREEDS`, …) ve ilgisiz event türlerine 4xx dönülüyor → 3 denemelik retry bütçesi yanıyor, bildirim kalıcı kayboluyor | 🟠 Yüksek | `SubscriptionService.java:314, 318, 323, 364, 368, 371` | İtiraz 4b |
| **V-3** | `COACH_FULL` webhook transaction'ını geri alıyor → para tahsil edilmiş, ödeme `PENDING` kalıyor, 03:00'te `FAILED` oluyor, iade tetiklenmiyor | 🔴 Kritik | `SubscriptionService.java:271-275` + `@Transactional` `:305` | Doğrulama |
| **V-4** | Webhook bildirimleri (panel) ve `X-IYZ-SIGNATURE-V3` başlığı (entegrasyon@iyzico.com) hesap düzeyinde ayrı ayrı etkinleştirilmeli; açılmadan kod imzasız istekleri 401 ile reddeder | 🔴 Kritik (operasyonel, **tedarik süreli**) | `SubscriptionService.java:337-339` — kod doğru, eksik olan hesap yapılandırması | İtiraz 4a |
| **V-5** | `providerReference` alanı iyzico payload'ında hiç yok → `PROVIDER_REFERENCE_MISSING` her `SUCCESS`'te tetiklenir; doğru alan `iyziPaymentId` | 🟡 Orta | `IyzicoWebhookRequest.java:13-14`, `SubscriptionService.java:321-325` | Doğrulama |
| **V-6** | Tek webhook endpoint'i üç farklı payload formatı (Direct / HPP / Subscription) alabilir; hiçbir ayrıştırma yok | 🟡 Orta | `PaymentController.java:21-26` | Doğrulama |

**V-6 hakkında not:** Bugün yalnızca HPP bekliyorsunuz, ama hesapta BKM/havale/PWI açılırsa veya ileride yerleşik aboneliğe geçilirse (ek rapor §5) aynı endpoint `subscription.order.success` gibi tamamen farklı şemalı payload'lar almaya başlar — ki onun imza formülü `merchantId` ile başlıyor. `iyziEventType` üzerinden formata dallanan bir yapı (İtiraz 2'deki `webhookSignaturePayload`) bunu şimdiden karşılar.

---

# 4. Sandbox'ta Elle Doğrulanması Gerekenler

Dokümantasyondan **kesin çıkaramadığım** her şey. Bunları gerçek bir sandbox webhook'u yakalayıp (ngrok + ham gövde ve header'ları loglayarak) teyit edin.

### İmza hesaplamasının kenar durumları

- [ ] **`iyziPaymentId` null geldiğinde ne oluyor?** Doküman JS örneği düz string birleştirme yapıyor; JavaScript'te `undefined` bir değer `"undefined"` string'ine dönüşür, bizim önerdiğimiz `str()` yardımcısı ise `""` üretir. `FAILURE` bildirimlerinde `iyziPaymentId` boş gelebilir — **bu farkı doğrulamadan prod'a çıkmayın.** Farklıysa `str()` yerine iyzico'nun ürettiği davranışı taklit etmek gerekir.
- [ ] **`token` `FAILURE` bildirimlerinde dolu geliyor mu?** Aynı gerekçe.
- [ ] **`iyziPaymentId` (long) string'e nasıl çevriliyor?** `String.valueOf(long)` beklenen davranış, ama iyzico tarafında bilimsel gösterim veya sıfır dolgusu olmadığını gerçek bir payload'la teyit edin.
- [ ] **İmza büyük/küçük harf.** Doküman `digest('hex')` diyor (küçük harf). Kodumuz iki tarafı da `toLowerCase` yaptığı için güvende, ama gerçek header'ın hangi biçimde geldiğini bir kez görün.
- [ ] **`status` alanının büyük/küçük harf tutarlılığı** — imza `status`'u ham haliyle kullanıyor; bizim `equalsIgnoreCase` karşılaştırmalarımız imzayı etkilemiyor ama ham değeri imzaya sokarken normalize **etmemeliyiz**.

### Akış davranışı

- [ ] **`CHECKOUT_FORM_AUTH` dışında hangi event türleri gerçekten bizim endpoint'imize düşüyor?** Hesabınızda BKM/havale/PWI açıksa `BKM_AUTH`, `BANK_TRANSFER_AUTH`, `PWI_TKN_*` gelebilir. Gelenleri loglayıp `HANDLED_EVENT_TYPES` setini gerçeğe göre kalibre edin.
- [ ] **3DS akışında `INIT_THREEDS` / `CALLBACK_THREEDS` bildirimleri gerçekten geliyor mu?** V-2'nin aciliyeti buna bağlı. Geliyorsa retry bütçesi yanma senaryosu gerçek.
- [ ] **Callback (tarayıcı form-POST) ile webhook aynı anda mı geliyor, hangisi önce?** K-2'nin tasarımı buna göre şekillenir — iki yoldan gelen aynı sonucun idempotent kalması gerekir.
- [ ] **İlk bildirim gerçekten 10–15 sn sonra mı geliyor?** Bizim `checkout` transaction'ımız iyzico çağrısından önce commit ediyor, yani `Payment` satırı hazır olmalı — ama replica gecikmesi varsa `PAYMENT_NOT_FOUND` yarışı gerçek olur (V-2'deki 500-retry önerisinin gerekçesi).

### Hesap yapılandırması

- [ ] **Sandbox hesabında webhook signature özelliği açık mı?** Değilse `X-IYZ-SIGNATURE-V3` hiç gelmez ve K-1 düzeltmesini test edemezsiniz. Prod'dan **önce** sandbox'ta açtırın.
- [ ] **Prod ve sandbox `secretKey` değerleri imza için aynı anahtar mı?** Doküman `secretKey` diyor; API auth ile aynı anahtar olduğunu varsayıyoruz — teyit edin.

### Ek rapordan devreden, hâlâ açık maddeler

- [ ] `Refund.createV2` yanıtında `currency` null geldiğinde `Refund.verifySignature()` doğru çalışıyor mu (ek rapor §4 notu)
- [ ] Java 21'de `verifySignature()` çağrısı `DatatypeConverter` yüzünden patlıyor mu (ek rapor §1)
- [ ] `CheckoutFormInitialize.getPaymentPageUrl()` hangi host'u dönüyor — frontend allowlist'i buna göre (ana rapor Y-3)

---

## Bu Doğrulamanın Sınırları

1. **Hiçbir şey sandbox'a karşı çalıştırılmadı.** Yukarıdaki kararların tamamı resmî dokümantasyona dayanıyor. Dokümantasyon ile gerçek davranışın ayrıştığı durumlar olabilir — özellikle null alanların imzaya nasıl girdiği konusunda, ki bu tam olarak dokümanın sessiz kaldığı yer.

2. **Doküman iki formatın örnek payload'ını vermiyor.** Yalnızca abonelik formatının JSON örneği var. Direct ve HPP için elimde **alan tabloları** var, ham örnek yok. Alan adlarını tablodan aldım; JSON'daki tam yazımın (büyük/küçük harf) tabloyla birebir aynı olduğunu varsaydım.

3. **`fraudStatus` webhook'ta yok.** Ek rapor §2.2'deki fraud kontrolü yalnızca `CheckoutForm.retrieve()` yanıtından gelebilir — webhook payload'ında böyle bir alan yok. Bu, K-3'ün (retrieve teyidi) neden vazgeçilmez olduğunun bir gerekçesi daha.

4. **Kod değiştirilmedi.** Tüm öneriler metin halinde. Uygulanmadan önce mevcut webhook testlerinin (`grep -rn "processWebhook" backend/src/test`) hangi varsayımlara dayandığını kontrol edin — özellikle `IyzicoWebhookRequest`'in üç argümanlı kısa constructor'ı.

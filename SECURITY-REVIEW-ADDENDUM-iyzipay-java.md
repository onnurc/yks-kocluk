# Ek Rapor — `RealIyzicoClient` ↔ Resmî `iyzipay-java` Karşılaştırması

**Tarih:** 2026-09-06
**Yöntem:** `https://github.com/iyzico/iyzipay-java` geçici bir klasöre clone'landı (`85a63a2`, `v2.0.142`, master). `src/main/java/com/iyzipay/` altındaki model/request sınıfları ve `src/test/java/com/iyzipay/sample/` altındaki 27 resmî örnek okundu, `backend/.../integration/RealIyzicoClient.java` ile satır satır karşılaştırıldı.
**İlgili:** [SECURITY-REVIEW-2026-09-06.md](SECURITY-REVIEW-2026-09-06.md) — bu ek, oradaki **K-3**, **Y-1**, **Y-2**, **Y-4**, **Y-5** bulgularını somutlaştırıyor ve birinde düzeltme içeriyor.

---

## Özet

| Soru | Cevap |
|---|---|
| 1. Versiyon güncel mi? | ✅ **Evet.** `2.0.142` = upstream `VERSION` = en son tag. Sürüm sorunu yok. |
| 2. Samples'ta olup bizde eksik olan? | ❌ **Çok var.** En kritiği: `CheckoutForm.retrieve()` hiç çağrılmıyor, `fraudStatus`/`paymentStatus` hiç okunmuyor, alıcı bilgileri tamamen sahte. |
| 3. Kütüphanenin doğrulama yardımcısı var mı, biz elle mi yazdık? | ❌ **En ciddi bulgu.** `ResponseSignatureGenerator` + `HashValidator` + her modelde `verifySignature(secretKey)` var. Hiçbirini kullanmıyoruz; **`:` ayraçlı** resmî şemayı bilmeden **ayraçsız** kendi versiyonumuzu yazmışız. |
| 4. Deprecated API? | ⚠️ **Kütüphanede tek bir `@Deprecated` yok**, ama `Refund.create` (v1) yerine `Refund.createV2` var ve v2 bizim Y-5 sorunumuzu doğrudan çözüyor. |

---

## 1. Versiyon durumu ✅

```
pom.xml            <iyzipay.version>2.0.142</iyzipay.version>
upstream VERSION   2.0.142
en son tag         v2.0.142   (git tag --sort=-v:refname | head -1)
son commit         85a63a2 "Automatic commit by iyzico-ci v2.0.142"
```

**Sonuç: güncel.** Ayrıca `v2.0.141`'deki `27e9e53 "Removes sha1 hashing"` commit'i sayesinde eski SHA-1 tabanlı auth tamamen kalkmış; HMAC-SHA256 (`IyziAuthV2Generator`) kullanılıyor. Bu açıdan doğru sürümdesiniz.

### Ama kütüphanenin kendi bağımlılıkları taranmalı

Upstream `pom.xml`'den (scope=compile, yani hepsi bizim runtime classpath'imize giriyor):

| Bağımlılık | Sürüm | Not |
|---|---|---|
| `com.google.code.gson:gson` | **2.8.9** | Kasım 2021. CVE-2022-25647'nin *düzeltilmiş* sürümü, yani bilinen açık taşımıyor — ama 4 yıl bayat. |
| `org.apache.commons:commons-lang3` | 3.18.0 | Güncel; CVE-2025-48924 düzeltmesini içeriyor ✅ |
| `javax.xml.bind:jaxb-api` | 2.3.1 | `DatatypeConverter` için gerekli (aşağıya bkz.) |
| `javax.ejb:javax.ejb-api` | 3.2 | **Gereksiz** |
| `javax.persistence:javax.persistence-api` | 2.2 | **Gereksiz ve kafa karıştırıcı** — biz Jakarta Persistence kullanıyoruz |
| `javax.transaction:jta` | 1.1 | **Gereksiz** |
| `javax.jms:javax.jms-api` | 2.0.1 | **Gereksiz** |
| `javax.resource:javax.resource-api` | 1.7.1 | **Gereksiz** |
| `javax.annotation:javax.annotation-api` | 1.3.2 | **Gereksiz** |

Ödeme SDK'sı, hiç kullanmadığı altı adet Java EE API'sini uygulamanıza sokuyor. `javax.persistence-api 2.2`'nin, Hibernate 7 + `jakarta.persistence` kullanan bir uygulamanın classpath'inde durması özellikle istenmeyen bir durum.

**Düzeltme — `backend/pom.xml`:**

```xml
<dependency>
    <groupId>com.iyzipay</groupId>
    <artifactId>iyzipay-java</artifactId>
    <version>${iyzipay.version}</version>
    <exclusions>
        <exclusion><groupId>javax.ejb</groupId>        <artifactId>javax.ejb-api</artifactId></exclusion>
        <exclusion><groupId>javax.persistence</groupId><artifactId>javax.persistence-api</artifactId></exclusion>
        <exclusion><groupId>javax.transaction</groupId><artifactId>jta</artifactId></exclusion>
        <exclusion><groupId>javax.jms</groupId>        <artifactId>javax.jms-api</artifactId></exclusion>
        <exclusion><groupId>javax.resource</groupId>   <artifactId>javax.resource-api</artifactId></exclusion>
        <exclusion><groupId>javax.annotation</groupId> <artifactId>javax.annotation-api</artifactId></exclusion>
        <!-- jaxb-api ve gson'u ÇIKARMAYIN — ikisi de gerçekten kullanılıyor. -->
    </exclusions>
</dependency>
```

`gson`'ı güncel bir sürüme çekmek isterseniz `<dependencyManagement>` ile pin'leyin; kütüphane basit `Gson().toJson/fromJson` kullandığı için 2.11+ uyumlu olmalı, ama sandbox'ta bir uçtan uca test şart.

### ⚠️ Java 21'de `DatatypeConverter` tuzağı — deploy öncesi mutlaka doğrulayın

`ResponseSignatureGenerator`, `IyziAuthV2Generator`, `DigestHelper` ve `FileBase64Encoder` **`javax.xml.bind.DatatypeConverter`** kullanıyor. Bu sınıf Java 11'de JDK'dan çıkarıldı; kütüphane bunu `jaxb-api 2.3.1` ile karşılıyor (compile scope, yani gelecek). Ancak `jaxb-api` yalnızca **API** jar'ıdır ve `DatatypeConverter.printHexBinary()` çalışma zamanında bir `DatatypeConverterInterface` implementasyonu arar.

Bu, "derlenir ama ilk çağrıda `NoClassDefFoundError`/`LinkageError` atar" tipik senaryosudur ve şu an **hiç tetiklenmiyor** çünkü `verifySignature()`'ı hiçbir yerde çağırmıyoruz. Bu ek raporun önerdiği düzeltmeleri uyguladığınız anda tetiklenecek.

**Deploy öncesi tek satırlık kanıt testi yazın** — bu, "muhtemelen çalışır" ile "çalıştığını gördüm" arasındaki farktır:

```java
@Test
void iyzipay_signature_helper_runs_on_java21() {
    CheckoutForm form = new CheckoutForm();
    form.setSignature("deadbeef");
    // Amaç doğru/yanlış değil; DatatypeConverter'ın runtime'da çözülmesi.
    assertThatCode(() -> form.verifySignature("test-secret")).doesNotThrowAnyException();
}
```

Patlarsa çözüm, `org.glassfish.jaxb:jaxb-runtime` (veya `com.sun.xml.bind:jaxb-impl` 2.3.x) eklemektir.

---

## 2. Resmî samples'ta olup bizde eksik olanlar

### 2.1 🔴 `CheckoutForm.retrieve()` hiç çağrılmıyor — resmî akışın yarısı eksik

`CheckoutFormSample.java` iki test içeriyor ve **ikisi de zorunlu adım**:

```java
// 1) initialize
CheckoutFormInitialize init = CheckoutFormInitialize.create(request, options);
assertTrue(init.verifySignature(options.getSecretKey()));          // ← bizde YOK

// 2) callback geldikten sonra sonucu SORGULA
RetrieveCheckoutFormRequest r = new RetrieveCheckoutFormRequest();
r.setToken("token");                                                // callback'ten gelen token
CheckoutForm form = CheckoutForm.retrieve(r, options);
assertTrue(form.verifySignature(options.getSecretKey()));           // ← bizde YOK
assertEquals(Status.SUCCESS.getValue(), form.getStatus());
```

Bizim `RealIyzicoClient`'ta yalnızca (1)'in `create` kısmı var. `IyzicoClient` arayüzünde `retrieve` karşılığı bir metot **hiç tanımlı değil**:

```java
public interface IyzicoClient {
    CheckoutResult initializeCheckout(...);
    ChargeResult charge(...);
    RefundResult refund(...);
    // retrieve/verify yok
}
```

Bu, ana rapordaki **K-3**'ün ("sağlayıcıya karşı hiç teyit yok") kütüphane tarafındaki kanıtı. Resmî SDK teyit yolunu hazır sunuyor; biz onu hiç bağlamamışız ve yerine webhook gövdesindeki `status` string'ine güveniyoruz.

**Düzeltme — arayüze ekleyin:**

```java
// integration/IyzicoClient.java
CheckoutVerification verifyCheckout(String checkoutToken);

public record CheckoutVerification(
        boolean signatureValid, String paymentStatus, Integer fraudStatus,
        String conversationId, BigDecimal paidPrice, String currency,
        String paymentId, String paymentTransactionId) {}
```

```java
// integration/RealIyzicoClient.java
@Override
public CheckoutVerification verifyCheckout(String checkoutToken) {
    RetrieveCheckoutFormRequest request = new RetrieveCheckoutFormRequest();
    request.setLocale(Locale.TR.getValue());
    request.setToken(checkoutToken);

    CheckoutForm form = executor.call("retrieveCheckout", Duration.ofSeconds(10),
            () -> CheckoutForm.retrieve(request, options));

    if (form == null || !Status.SUCCESS.getValue().equals(form.getStatus())) {
        throw new PaymentProviderException("Iyzico checkout retrieve rejected");
    }
    // Kütüphanenin kendi imza doğrulayıcısı — elle HMAC yazmayın (bkz. §3)
    if (!form.verifySignature(properties.secretKey())) {
        throw new PaymentProviderException("Iyzico checkout response signature mismatch");
    }
    // İlk sepet kaleminin transaction id'si — iade için gerekli (bkz. §4 / Y-5)
    String transactionId = form.getPaymentItems() == null || form.getPaymentItems().isEmpty()
            ? null : form.getPaymentItems().getFirst().getPaymentTransactionId();

    return new CheckoutVerification(true, form.getPaymentStatus(), form.getFraudStatus(),
            form.getConversationId(), form.getPaidPrice(), form.getCurrency(),
            form.getPaymentId(), transactionId);
}
```

### 2.2 🔴 `fraudStatus` hiç okunmuyor

`CheckoutForm extends PaymentResource` ve `PaymentResource:19` şu alanı taşıyor:

```java
private Integer fraudStatus;
```

iyzico'nun dolandırıcılık motorunun kararı. Yaygın semantiği: **`-1` = reddedildi/fraud**, **`0` = incelemede (beklemede)**, **`1` = onaylandı**. Kütüphane bunun için bir enum/sabit **sunmuyor** (ham `Integer`), bu yüzden değerleri kendi iyzico panelinizden/dokümanınızdan teyit edin.

Bizim kodumuzda `fraudStatus` kelimesi **hiç geçmiyor**. Yani iyzico "bu işlem incelemede" ya da "bu fraud" dese bile aboneliği anında `ACTIVE` yaparız.

`paymentStatus` de aynı şekilde okunmuyor — `SUCCESS` dışında `INIT_THREEDS`, `CALLBACK_THREEDS`, `BKM_POS_SELECTED`, `PENDING_CREDIT`, `FAILURE` gibi değerler alabilir.

**Düzeltme — teyit edilmiş sonucu uygularken:**

```java
private void applyVerifiedSuccess(Payment payment, CheckoutVerification v) {
    if (!"SUCCESS".equals(v.paymentStatus())) {
        markFailed(payment);
        return;
    }
    Integer fraud = v.fraudStatus();
    if (fraud != null && fraud < 0) {
        // Fraud reddi: erişim AÇILMAZ.
        log.warn("Fraud-rejected payment: paymentId={}, fraudStatus={}", payment.getId(), fraud);
        markFailed(payment);
        return;
    }
    if (fraud != null && fraud == 0) {
        // İncelemede: PENDING'de bırak. iyzico kararını verince webhook tekrar gelir.
        log.info("Payment under fraud review: paymentId={}", payment.getId());
        return;   // idempotent state machine bunu zaten kaldırıyor
    }
    if (!"TRY".equals(v.currency()) || v.paidPrice().compareTo(payment.getAmount()) != 0) {
        throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_AMOUNT_MISMATCH",
                "Ödeme tutarı beklenen tutarla eşleşmiyor");
    }
    completePaymentSuccess(payment, payment.getSubscription(), v.paymentId(), v.paymentTransactionId());
}
```

> `fraudStatus == 0` (incelemede) durumunda aboneliği `PENDING_PAYMENT`'ta bırakmak, `expireStalePendingCheckouts`'un 30 dakikalık penceresiyle çakışabilir. Fraud incelemesi daha uzun sürebilir; `PAYMENT_PENDING_CHECKOUT_TIMEOUT_MINUTES` değerini gözden geçirin veya fraud-review durumunu ayrı bir statüde tutun.

### 2.3 🟠 Alıcı (Buyer) bilgileri tamamen sahte — fraud skorlaması körleşiyor

`RealIyzicoClient.java:81-102`:

```java
// Map buyer details. Since checkout API doesn't pass user details directly to client,
// and it is sandbox client, we use placeholder details.
buyer.setName("John");
buyer.setSurname("Doe");
buyer.setEmail("john.doe@example.com");
buyer.setGsmNumber("+905555555555");
buyer.setIdentityNumber("74300864791");   // Fake but valid TCKN
buyer.setRegistrationAddress("Uskudar");
```

Yorumun kendisi "sandbox olduğu için" diyor — ama bu kod `IYZICO_ENABLED=true` olduğu her ortamda, **production dahil**, çalışıyor. Sonuçlar:

- **Fraud motoru işlevsiz.** iyzico her işlemde aynı isim, aynı e-posta, aynı TCKN, aynı telefonu görür. Hız/tekrar bazlı kuralları tetikler ya da tam tersi hiçbir sinyal üretemez. Yüksek `fraudStatus` reddi veya toplu hesap askıya alma riski var.
- **Chargeback savunması yok.** İtiraz halinde sunulacak alıcı kaydı gerçek müşteriye ait değil.
- **Üye işyeri sözleşmesi.** Gerçek alıcı bilgisi iletme yükümlülüğüyle çelişir.
- **Uygulanabilir.** Bilgi elde var: `subscription.getStudent()` üzerinden ad, e-posta erişilebilir.

Ayrıca resmî örnekte olup bizde hiç olmayan fraud sinyalleri: `buyer.setIp(...)`, `buyer.setLastLoginDate(...)`, `buyer.setRegistrationDate(...)`, `buyer.setZipCode(...)` ve adreslerde `setZipCode(...)`.

**Düzeltme — imzayı gerçek verilerle besleyin:**

```java
// IyzicoClient arayüzü — checkout çağrısı alıcı bağlamını da taşımalı
CheckoutResult initializeCheckout(CheckoutContext context);

public record CheckoutContext(Long subscriptionId, Long paymentId, BigDecimal amount,
                              String idempotencyKey, String buyerFullName, String buyerEmail,
                              Instant buyerRegisteredAt, String buyerIp) {}
```

```java
Buyer buyer = new Buyer();
buyer.setId("student-" + ctx.subscriptionId());
buyer.setName(firstNameOf(ctx.buyerFullName()));
buyer.setSurname(lastNameOf(ctx.buyerFullName()));
buyer.setEmail(ctx.buyerEmail());
buyer.setIp(ctx.buyerIp());                                    // ClientIpResolver'dan
buyer.setRegistrationDate(formatIyzico(ctx.buyerRegisteredAt()));  // "yyyy-MM-dd HH:mm:ss"
buyer.setIdentityNumber(...);   // ← aşağıya bakın
```

⚠️ **TCKN konusu bir ürün/KVKK kararı, teknik değil.** `identityNumber` iyzico'nun zorunlu alanı ama şu an TCKN toplamıyorsunuz ve öğrencilerin bir kısmı **reşit değil**. Sahte TCKN göndermeye devam etmek de, gerçek TCKN toplamaya başlamak da (ek KVKK aydınlatma + veri minimizasyonu gerekçesi + saklama süresi) sonuç doğurur. **Bunu iyzico üye işyeri temsilcinize ve hukuk danışmanınıza sorun** — sahte TCKN'yi "geçici" diye canlıya taşımayın.

### 2.4 🟡 `enabledInstallments` ayarlanmamış — taksit `paidPrice`'ı değiştirebilir

Resmî örnek taksitleri açıkça listeliyor (`setEnabledInstallments(List.of(2,3,6,9))`). Biz hiç set etmiyoruz, yani hesabınızda tanımlı ne varsa geçerli. Taksitli işlemde `paidPrice > price` olur (vade farkı). Aylık abonelikte taksit anlamsız; ayrıca §2.1'deki tutar teyidi taksit varsa yanlış alarm üretir.

```java
request.setEnabledInstallments(List.of(1));   // yalnız tek çekim
```

### 2.5 🟡 `forceThreeDS` ayarlanmamış

`CreateCheckoutFormInitializeRequest:25` `forceThreeDS` alanını sunuyor. Ayarlanmadığında 3DS kararı hesap yapılandırmanıza kalır. Türkiye'de kart-sahibi-yok işlemlerde 3DS chargeback sorumluluğunu bankaya kaydırır — abonelik satışında istediğiniz şey bu.

```java
request.setForceThreeDS(1);
```

### 2.6 Özet karşılaştırma tablosu

| Resmî örnekteki adım/parametre | Bizde | Etki |
|---|---|---|
| `CheckoutFormInitialize.verifySignature()` | ❌ yok | Sahte initialize yanıtı tespit edilemez |
| `CheckoutForm.retrieve()` | ❌ hiç yok | **K-3** — sunucu teyidi yok |
| `CheckoutForm.verifySignature()` | ❌ yok | Teyit yanıtı doğrulanmaz |
| `getFraudStatus()` | ❌ okunmuyor | Fraud reddi/incelemesi yok sayılır |
| `getPaymentStatus()` | ❌ okunmuyor | Sadece HTTP `status` bakılıyor |
| `getPaidPrice()` karşılaştırması | ❌ yok | Tutar teyidi yok |
| `paymentItems[].paymentTransactionId` | ❌ okunmuyor | **Y-5** — iade referansı yanlış |
| Gerçek `Buyer` verisi | ❌ sabit "John Doe" | Fraud + chargeback + sözleşme |
| `buyer.setIp()` | ❌ yok | Fraud sinyali eksik |
| `enabledInstallments` | ❌ yok | `paidPrice` sapabilir |
| `forceThreeDS` | ❌ yok | 3DS garantisi yok |
| `Refund.verifySignature()` | ❌ yok | İade yanıtı doğrulanmaz |
| `RefundReason` / `description` | ❌ yok | İade denetim izi zayıf |
| `Status.SUCCESS` kontrolü | ✅ var | — |
| Güvenilir checkout URL kontrolü | ✅ var (kütüphanede yok, bizim eklememiz) | İyi bir ek |

---

## 3. 🔴 En ciddi bulgu — kütüphanenin imza doğrulayıcısı var, biz elle yazmışız (ve şema farklı)

Kütüphane bunları sunuyor:

- `com.iyzipay.ResponseSignatureGenerator` — HMAC-SHA256 + hex-lowercase üretici
- `com.iyzipay.HashValidator` — karşılaştırma
- **14 modelde** hazır `verifySignature(String secretKey)`: `CheckoutForm`, `CheckoutFormInitialize`, `Payment`, `Refund`, `ThreedsPayment`, `Apm`, `Bkm`, …

Bizim `SubscriptionService.calculateHmacSha256(...)` bunların hiçbirini kullanmıyor. **Ve şema aynı değil.**

### Fark: parametre ayracı

Resmî üretici parametreleri **iki nokta üst üste (`:`) ile birleştiriyor**:

```java
// ResponseSignatureGenerator.java:18, 35-39
String SEPARATOR = ":";
default String appendSignatureParams(List<Object> signatureParameters) {
    return signatureParameters.stream()
            .map(this::convertParamToString)
            .collect(Collectors.joining(SEPARATOR));   // ← ":" ile birleştir
}
```

Bizim kodumuz **ayraçsız birleştiriyor**:

```java
// SubscriptionService.verifyWebhookSignature
String data = secretKey + iyziEventType + paymentIdStr + paymentConversationId + statusStr;
```

Ayrıca resmî üretici HMAC anahtarı olarak secretKey kullanır ama **secretKey'i veriye eklemez**; bizim kod hem anahtar hem verinin başı olarak kullanıyor.

### `BigDecimal` normalizasyonu — elle yazınca kaçırılan detay

```java
// ResponseSignatureGenerator.java:41-49
default String convertParamToString(Object parameter) {
    if (Objects.isNull(parameter))            return EMPTY_PARAM;      // null → ""
    else if (parameter instanceof BigDecimal) return ((BigDecimal) parameter)
                                                     .stripTrailingZeros().toPlainString();
    else                                      return parameter.toString();
}
```

`1.20` → `1.2`, `100.00` → `1E+2`… değil, `toPlainString()` sayesinde `100`. Bu tür normalizasyon kuralları elle yazılan imza kodunda neredeyse her zaman yanlış yapılır. Kütüphaneyi kullanmanın asıl gerekçesi bu.

### Kütüphane webhook imzasını **kapsamıyor**

Dürüst olmak gerekirse: repoda `webhook`, `iyziEventType` veya `X-IYZ-SIGNATURE-V3` geçen **tek bir satır yok**. Yani `SubscriptionService`'teki webhook imza doğrulaması için hazır bir yardımcı gerçekten yok — o kısmı elle yazmak zorundasınız. Ancak:

1. **Callback tarafında** (ana rapor K-2) elle yazmanıza gerek yok: `CheckoutForm.retrieve()` + `verifySignature()` resmî yol.
2. Webhook imzasını elle yazarken bile, resmî `ResponseSignatureGenerator`'ın `:` ayracı ve `BigDecimal` normalizasyonu **iyzico'nun genel imza konvansiyonunu** gösteriyor. Sizin ayraçsız şemanız bu konvansiyonla çelişiyor — bu, ana rapordaki **K-1**'in "imza şemasını sandbox'ta gerçek bir webhook'a karşı doğrulayın" uyarısını çok daha acil hale getiriyor.

**Önerilen düzen:** Webhook'u yalnızca bir **tetikleyici** olarak kullanın, gerçeklik kaynağı olarak değil:

```java
@Transactional
public IyzicoWebhookResponse processWebhook(IyzicoWebhookRequest request, String signatureV3) {
    verifyWebhookSignature(request, signatureV3);   // 1. katman — elle, kaçınılmaz
    Payment payment = loadPayment(request.localPaymentId());
    // 2. katman — resmî SDK ile sunucu-sunucu teyit. Asıl karar burada verilir.
    CheckoutVerification v = iyzicoClient.verifyCheckout(payment.getCheckoutToken());
    applyVerifiedSuccess(payment, v);
    return ...;
}
```

Böylece elle yazılmış imza kodundaki olası bir hata, tek başına ücretsiz aboneliğe dönüşmez. Bunun için `Payment` üzerinde `checkout_token` saklamanız gerekir (şu an saklanmıyor — `CheckoutResult.checkoutToken()` yalnızca yanıtta dönüp atılıyor):

```sql
ALTER TABLE payments ADD COLUMN checkout_token varchar(256);
CREATE INDEX idx_payments_checkout_token ON payments (checkout_token);
```

---

## 4. Deprecated / eski API kullanımı

**Kütüphanede `@Deprecated` işaretli tek bir sınıf veya metot yok** — `grep -rn "@Deprecated" src/main/java` boş dönüyor. Yani formel olarak deprecated bir API kullanmıyoruz.

Ama **eski nesil bir varyant** kullanıyoruz ve yenisi Y-5'i doğrudan çözüyor:

### `Refund.create` (v1) → `Refund.createV2`

```java
// Bizim kod — CreateRefundRequest, paymentTransactionId İSTER
CreateRefundRequest request = new CreateRefundRequest();
request.setPaymentTransactionId(providerReference);   // ← elimizde bu yok (Y-5'teki TODO)
Refund refundResponse = Refund.create(request, options);   // POST /payment/refund
```

```java
// Kütüphanedeki v2 — CreateRefundV2Request, sadece paymentId İSTER
public class CreateRefundV2Request extends Request {
    private String paymentId;   // ← genel ödeme kimliği; retrieve'den kolayca gelir
    private BigDecimal price;
    private String ip;
}
Refund.createV2(request, options);   // POST /v2/payment/refund
```

`RefundSample.should_refund_v2_payment()` resmî örneği bunu gösteriyor. **Y-5'teki "paymentTransactionId'yi nasıl bulacağız" problemi v2 ile tamamen ortadan kalkıyor** — kalem düzeyinde transaction id çözmeye gerek yok.

**Düzeltme:**

```java
@Override
public RefundResult refund(String providerPaymentId, BigDecimal amount, String idempotencyKey) {
    CreateRefundV2Request request = new CreateRefundV2Request();
    request.setLocale(Locale.TR.getValue());
    request.setConversationId(idempotencyKey);
    request.setPaymentId(providerPaymentId);     // retrieve'den gelen paymentId
    request.setPrice(amount);
    request.setIp(callerIp);                     // "127.0.0.1" yerine gerçek IP

    Refund response = executor.call("refund", Duration.ofSeconds(15),
            () -> Refund.createV2(request, options));

    if (response == null || !Status.SUCCESS.getValue().equals(response.getStatus())) {
        return new RefundResult(false, null,
                response == null ? "PROVIDER_CALL_FAILED" : response.getErrorCode(),
                response == null ? null : response.getErrorMessage());
    }
    // Kütüphanenin kendi doğrulayıcısı — params: paymentId, price, currency, conversationId
    if (!response.verifySignature(properties.secretKey())) {
        throw new PaymentProviderException("Iyzico refund response signature mismatch");
    }
    return new RefundResult(true, response.getPaymentId(), null, null);
}
```

> Not: v2 `currency` almıyor, dolayısıyla `Refund.verifySignature()`'ın `currency` parametresi v2 yanıtında `null` gelebilir — `convertParamToString` null'ı `""`e çevirdiği için bu tasarım gereği çalışır, ama **sandbox'ta bir kez doğrulayın**.

### Diğer eski kullanımlar

- `request.setIp("127.0.0.1")` — resmî örnekler gerçek istemci IP'si veriyor (`"85.34.78.112"`). Sabit loopback IP fraud değerlendirmesini bozar. `ClientIpResolver` zaten elinizde.
- `Refund` çağrısında `RefundReason` + `description` yok. `RefundSample` iki ayrı örnekte (`DOUBLE_PAYMENT`, `FRAUD`) kullanıyor. İade denetim izi için ekleyin.

---

## 5. 🟠 Bonus — iyzico'nun **yerleşik abonelik ürünü** kullanılmıyor (Y-1 / Y-2)

Ana raporda "auto-renew prod'da çalışmıyor, ya implemente edin ya kaldırın" demiştim. Kütüphaneyi inceleyince ortaya çıkan şey: **üçüncü ve muhtemelen en iyi seçenek var.**

`src/main/java/com/iyzipay/model/subscription/` altında tam bir abonelik API'si duruyor:

```
Subscription.java                            SubscriptionPricingPlan.java
SubscriptionCheckoutForm.java                SubscriptionPricingPlanList.java
SubscriptionCheckoutFormInitialize.java      SubscriptionProduct.java
SubscriptionCardUpdateCheckoutFormInitialize.java   SubscriptionProductList.java
SubscriptionCustomer.java                    SubscriptionOperation.java
SubscriptionInitialize.java                  SubscriptionOrder.java
SubscriptionCustomerList.java                SubscriptionSearch.java
```

ve `src/test/java/com/iyzipay/sample/subscription/` altında 14 resmî örnek.

`SubscriptionCheckoutFormInitializeSample`:

```java
InitializeSubscriptionCheckoutFormRequest request = new InitializeSubscriptionCheckoutFormRequest();
request.setCustomer(customer);
request.setCallbackUrl("https://www.merchant.com/callback");
request.setPricingPlanReferenceCode("23893e87-ef29-4b96-936f-e50ffce1f362");
request.setSubscriptionInitialStatus(SubscriptionInitialStatus.ACTIVE.name());

SubscriptionCheckoutFormInitialize response = SubscriptionCheckoutFormInitialize.create(request, options);
```

Bu modelde **iyzico** şunları üstlenir: kartı saklamak, her ay tahsil etmek, başarısız denemeleri yeniden denemek, kart güncelleme sayfasını sunmak (`SubscriptionCardUpdateCheckoutFormInitialize`), iptal/yükseltme (`SubscriptionOperation`).

Sizin tarafta kalan: `pricingPlanReferenceCode` eşlemesi ve webhook'larla `Subscription` durumunu senkronize etmek. Ortadan kalkan: `SubscriptionRenewalJob`, günlük cron, `charge:{subId}:{date}` idempotency anahtarları, `savedCardToken`, `failedChargeCount`, PAST_DUE retry penceresi, kart saklama uyumluluğu (PCI kapsamı).

CLAUDE.md'nin **"operasyonel basitlik kritik"** ve **"aşırı mühendislikten kaçın"** ilkeleriyle bu yön doğrudan uyumlu — kendi tekrarlayan tahsilat motorunuzu yazmak, tanımı gereği aşırı mühendisliktir.

**Ama bu bir mimari karar, deploy öncesi bir yama değil.** Mevcut `Subscription`/`Payment` şemanız ve `SubscriptionBillingService` durum makinesi buna göre yeniden düşünülür. Önerim:

1. **Şimdi:** Ana rapordaki **Y-1 (a)** fail-closed düzeltmesi + watchdog. Bu, "sessiz ücretsiz erişim" açığını kapatır ve deploy'u serbest bırakır.
2. **Sonra:** iyzico yerleşik aboneliğine geçişi ayrı bir faz olarak planlayın. Üye işyeri temsilcinizle hesabınızda abonelik ürününün açık olup olmadığını teyit ederek başlayın — her hesapta etkin değil.

---

## Ek Kontrol Listesi (ana rapordakine eklenecek)

### Kütüphane kullanımı

- [ ] `CheckoutFormInitialize.verifySignature(secretKey)` initialize sonrası çağrılıyor
- [ ] `IyzicoClient` arayüzüne `verifyCheckout(token)` eklendi; `CheckoutForm.retrieve()` + `verifySignature()` kullanılıyor
- [ ] `Payment.checkout_token` kolonu eklendi (Flyway) ve initialize'da saklanıyor
- [ ] `fraudStatus` kontrol ediliyor: `< 0` → erişim açılmaz, `== 0` → PENDING'de bekletilir
- [ ] `paymentStatus == "SUCCESS"` ayrıca kontrol ediliyor
- [ ] `paidPrice` + `currency` beklenen değerle karşılaştırılıyor
- [ ] `Refund.create` → `Refund.createV2` geçişi yapıldı; `paymentTransactionId` çözme ihtiyacı kalktı (**Y-5 kapandı**)
- [ ] `Refund.verifySignature(secretKey)` çağrılıyor
- [ ] `RefundReason` + `description` iade isteğine ekleniyor
- [ ] Elle yazılmış `calculateHmacSha256` yalnızca webhook imzası için kaldı; callback/retrieve yolunda kütüphane kullanılıyor

### İstek parametreleri

- [ ] `Buyer` gerçek öğrenci verisiyle dolduruluyor (ad, soyad, e-posta, kayıt tarihi)
- [ ] `buyer.setIp()` gerçek istemci IP'si (`ClientIpResolver`) ile besleniyor
- [ ] `identityNumber` (TCKN) konusu iyzico temsilcisi + hukuk ile netleştirildi — sahte TCKN canlıya taşınmadı
- [ ] `request.setEnabledInstallments(List.of(1))` — abonelikte taksit kapalı
- [ ] `request.setForceThreeDS(1)` — 3DS zorunlu
- [ ] `refund` isteğinde `ip` sabit `127.0.0.1` değil

### Bağımlılık hijyeni

- [ ] `iyzipay-java` için kullanılmayan `javax.*` EE API'leri `<exclusions>` ile çıkarıldı
- [ ] `gson` sürümü `<dependencyManagement>` ile güncellendi (opsiyonel; uçtan uca test şart)
- [ ] **Java 21'de `verifySignature()` çalışıyor** — `DatatypeConverter` kanıt testi yeşil (gerekiyorsa `jaxb-runtime` eklendi)
- [ ] OWASP Dependency-Check `iyzipay-java` transitive'lerini de kapsıyor (ana rapor O-8)

---

## Bu Karşılaştırmanın Sınırları

- Kütüphane **kaynağı** okundu; `mvn dependency:tree` çalıştırılamadı (build dizini iCloud tarafından tahliye edilmiş). Yukarıdaki bağımlılık listesi upstream `pom.xml`'den okundu — Spring Boot 4 BOM'unun bazılarını farklı sürüme çekip çekmediği **doğrulanmadı**.
- `fraudStatus` değerlerinin (`-1`/`0`/`1`) semantiği kütüphanede **kodlanmamış**; ham `Integer`. Yaygın iyzico konvansiyonunu aktardım, ama kendi panelinizden teyit edin.
- Hiçbir şey sandbox'a karşı çalıştırılmadı. `Refund.createV2`'nin `currency` içermemesi ve v2 imza doğrulamasının davranışı gibi ayrıntılar gerçek bir sandbox çağrısıyla doğrulanmalı.
- iyzico yerleşik abonelik ürününün **hesabınızda etkin olup olmadığı** kontrol edilemedi; bu, üye işyeri temsilcinize sorulacak bir konu.

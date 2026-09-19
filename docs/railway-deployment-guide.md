# Railway deploy hazırlığı — proje tarafı notları

Bu doküman Railway paneline hiç dokunmadan, **repodaki** hazırlığı özetler. Railway panelindeki
adımlar (servis oluşturma, env var girme, health check ayarı vb.) bilinçli olarak burada anlatılmıyor
— o kısmı sen yapacaksın. Burada olan: hangi dosyaların hazır olduğu, hangi env değişkenlerinin
var olduğu ve neden, ve kodda bulduğum bir uyumluluk sorununun ne olduğu ve nasıl düzeltildiği.

İlgili dosyalar:
- [`backend/Dockerfile`](../backend/Dockerfile) + [dockerfile-explained.md](./dockerfile-explained.md)
- [`backend/.dockerignore`](../backend/.dockerignore)
- `backend/src/main/resources/application.yml` — bu oturumda tek bir satır eklendi (`server.port`), aşağıda açıklanıyor.

---

## 1. Frontend nereye deploy edilmeli: Railway (static) mi, Vercel mi?

Karar sana ait — burada ikisinin ne zaman daha mantıklı olduğunu anlatıyorum.

| | **Railway (static/Nixpacks servis)** | **Vercel** |
| --- | --- | --- |
| Ne zaman mantıklı | Backend zaten Railway'deyse ve tek panelden, tek faturadan yönetmek istiyorsan | Frontend'e özel, üretim kalitesinde bir deneyim istiyorsan |
| CDN / global dağıtım | Yok (tek bölgede çalışan bir konteyner; Vite build çıktısını statik dosya sunucusu gibi serve eder) | Var — Vercel'in edge ağı, statik varlıkları kullanıcıya coğrafi olarak en yakın noktadan sunar |
| Preview deploy (her PR için otomatik URL) | Yok (elle kurman gerekir) | Var, kutudan çıkma özellik — her PR/branch için otomatik bir preview linki |
| Build/deploy hızı | Docker/Nixpacks build süresine bağlı, backend ile aynı kuyruğu paylaşabilir | Vite projeleri için optimize edilmiş, genelde çok hızlı |
| Maliyet modeli | Kullanım bazlı (CPU/RAM/ağ), statik bir SPA için bile "servis" gibi ücretlendirilir | Ücretsiz katman SPA'lar için oldukça cömert; trafik arttıkça plan yükseltmesi gerekir |
| Domain/SSL yönetimi | Railway üzerinden, backend ile aynı yerden | Vercel üzerinden, ayrı ama son derece basit |
| Operasyonel basitlik (tek panel) | Yüksek — CLAUDE.md'nin "Operational simplicity is critical" ilkesine daha yakın | Bir panel daha eklemiş olursun |

**Özetle:** Statik bir SPA'yı (bu proje React + Vite + TypeScript, backend'e HTTP ile konuşuyor,
sunucu tarafı render yok) sadece build edip dosyaları servis etmek yeterli — bu iş için Vercel
amaca daha uygun araç (CDN, preview deploy, sıfır ek yapılandırma). Railway'de statik frontend
barındırmak *çalışır* ama Railway'in asıl güçlü olduğu yer (uzun süre çalışan servisler, DB'ye
yakınlık, arka plan job'ları) burada devreye girmiyor. Tek panelden yönetim isteğin bu farkı
kapatacak kadar güçlüyse Railway de makul bir seçim.

---

## 2. Backend environment değişkenleri — eksiksiz liste

Kaynak: `backend/src/main/resources/application.yml` ve `backend/src/main/java/.../config/*Validator.java`.
"Fail-fast" sütunu, değişken eksik/hatalı olduğunda **uygulamanın hiç ayağa kalkmayıp** hemen
başlangıçta çökmesini mi, yoksa sessizce yanlış davranmasını mı sağladığını gösterir — ikincisi
daha tehlikeli çünkü deploy "başarılı" görünür ama davranış yanlıştır.

> Değer yazılmadı, hepsi placeholder. Gerçek secret'ları yalnızca Railway panelinde gireceksin.

### Veritabanı (Neon Postgres)

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `NEON_DATABASE_URL` | JDBC bağlantı adresi | `jdbc:postgresql://ep-xxxx.neon.tech/dbname?sslmode=require` | Değer yoksa Spring başlangıçta "Could not resolve placeholder" hatasıyla çöker — default yok |
| `NEON_DATABASE_USERNAME` | DB kullanıcı adı | `demo_owner` | Aynı — default yok |
| `NEON_DATABASE_PASSWORD` | DB parolası | `<PLACEHOLDER>` | Aynı — default yok |

### Admin bootstrap

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `ADMIN_PASSWORD_HASH` | Flyway seed migration'ının ADMIN hesabına yazacağı BCrypt hash | `$2b$12$<53-karakter-bcrypt>` | Yoksa/geçersizse (cost 10-16 aralığında geçerli BCrypt değilse) veya eski proje-içi varsayılan hash'le aynıysa, `local`/`test`/`stub` dışındaki her profilde `AdminBootstrapSecurityValidator` uygulamayı **başlatmadan** durdurur. Ayrıca DB'de zaten kayıtlı ADMIN hash'i de aynı kurala göre kontrol edilir. |

### Redis / Rate limiting

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `REDIS_URL` | Rate-limit sayaçlarının paylaşıldığı Redis | `rediss://default:<pass>@host:6379` | Yok — varsayılan `redis://localhost:6379`, Railway'de gerçek bir Redis olmadan bağlantı hatası verir (uygulama ayakta kalır ama rate-limit isteklerinde hata) |
| `APP_ENV` | Redis anahtarlarının önüne eklenen ortam etiketi (`RATE_LIMIT_ENVIRONMENT` alanı) | `production` / `staging` | Yok |
| `RATE_LIMIT_ENABLED` | Rate limiting'i tamamen açar/kapar | `true` | Yok |
| `RATE_LIMIT_STORE` | `REDIS` veya `IN_MEMORY` | `REDIS` | Yok |
| `RATE_LIMIT_FAILURE_POLICY` | Redis çökerse ne olacağı: `IN_MEMORY_FALLBACK` veya `FAIL_CLOSED` | `IN_MEMORY_FALLBACK` | Yok |
| `RATE_LIMIT_TRUST_PROXY_HEADERS` | `X-Forwarded-For` güvenilsin mi | `true` (Railway proxy arkasında ise) | Yok |
| `RATE_LIMIT_TRUSTED_PROXY_CIDRS` | Güvenilen proxy IP aralıkları | `10.0.0.0/8` | Yok |
| `RATE_LIMIT_LOGIN_IP_LIMIT` / `..._IDENTIFIER_LIMIT` / `..._WINDOW_SECONDS` | Login endpoint limiti | `20` / `5` / `60` | Yok |
| `RATE_LIMIT_REGISTER_IP_LIMIT` / `..._IDENTIFIER_LIMIT` / `..._WINDOW_SECONDS` | Kayıt limiti | `20` / `3` / `3600` | Yok |
| `RATE_LIMIT_REFRESH_IP_LIMIT` / `..._TOKEN_LIMIT` / `..._WINDOW_SECONDS` | Token refresh limiti | `30` / `30` / `60` | Yok |
| `RATE_LIMIT_OAUTH2_EXCHANGE_IP_LIMIT` / `..._WINDOW_SECONDS` | OAuth2 code exchange limiti | `30` / `60` | Yok |
| `RATE_LIMIT_FORGOT_PASSWORD_IP_LIMIT` / `..._EMAIL_LIMIT` / `..._WINDOW_SECONDS` | Şifremi unuttum limiti | `10` / `3` / `900` | Yok |
| `RATE_LIMIT_RESET_PASSWORD_IP_LIMIT` / `..._TOKEN_LIMIT` / `..._WINDOW_SECONDS` | Şifre sıfırlama limiti | `20` / `5` / `900` | Yok |
| `RATE_LIMIT_CHANGE_PASSWORD_IP_LIMIT` / `..._USER_LIMIT` / `..._WINDOW_SECONDS` | Şifre değiştirme limiti | `10` / `5` / `900` | Yok |
| `RATE_LIMIT_EMAIL_VERIFICATION_IP_LIMIT` / `..._USER_LIMIT` / `..._WINDOW_SECONDS` | E-posta doğrulama limiti | `30` / `10` / `600` | Yok |
| `RATE_LIMIT_EMAIL_VERIFICATION_RESEND_IP_LIMIT` / `..._USER_LIMIT` / `..._WINDOW_SECONDS` | Doğrulama kodu tekrar gönderme limiti | `20` / `5` / `3600` | Yok |
| `RATE_LIMIT_COACH_APPLICATION_IP_LIMIT` / `..._EMAIL_LIMIT` / `..._WINDOW_SECONDS` | Koç başvurusu limiti | `10` / `3` / `3600` | Yok |
| `RATE_LIMIT_MEDIA_PRESIGN_USER_LIMIT` / `..._WINDOW_SECONDS` | Dosya yükleme presign limiti | `12` / `600` | Yok |
| `RATE_LIMIT_MESSAGE_SEND_USER_LIMIT` / `..._WINDOW_SECONDS` | Mesaj gönderme limiti | `60` / `60` | Yok |
| `RATE_LIMIT_TRIAL_CREATE_USER_LIMIT` / `..._WINDOW_SECONDS` | Deneme dersi oluşturma limiti | `5` / `3600` | Yok |
| `RATE_LIMIT_REPORT_CREATE_USER_LIMIT` / `..._WINDOW_SECONDS` | Şikayet oluşturma limiti | `10` / `3600` | Yok |
| `RATE_LIMIT_MEDIA_COMPLETE_USER_LIMIT` / `..._WINDOW_SECONDS` | Medya tamamlama limiti | `30` / `600` | Yok |
| `RATE_LIMIT_CHECKOUT_CREATE_USER_LIMIT` / `..._WINDOW_SECONDS` | Ödeme başlatma limiti | `5` / `3600` | Yok |

Tüm rate-limit sayısal değişkenlerinin makul varsayılanları var; ilk deploy'da hiçbirini
girmesen de sistem çalışır. Yalnızca `REDIS_URL` gerçek bir değer istiyor.

### Kimlik doğrulama / JWT

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `JWT_SECRET` | Access/refresh token imzalama anahtarı (HS256, ≥32 byte) | 32+ karakterlik rastgele string | Yoksa Spring başlangıçta placeholder hatasıyla çöker — default yok |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client id | `xxxxx.apps.googleusercontent.com` | **Yok** — eksikse `dummy-client-id` ile sessizce ayağa kalkar, Google login'i sessizce çalışmaz |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | `GOCSPX-<...>` | Aynı, sessiz varsayılan var |
| `OAUTH2_FRONTEND_REDIRECT_URI` | Google login sonrası frontend'e dönüş adresi | `https://app.example.com/oauth/callback` | `local`/`test`/`stub` dışında `FrontendUrlProductionConfigurationValidator`: public HTTPS olmalı, `FRONTEND_BASE_URL` ile **aynı origin** olmalı |
| `OAUTH2_LOGIN_CODE_TTL_SECONDS` | OAuth2 tek kullanımlık kod ömrü | `120` | Yok |
| `WEBSOCKET_MAX_CONNECTIONS_PER_USER` | Kullanıcı başına eşzamanlı WS bağlantısı | `10` | Yok |
| `WEBSOCKET_MAX_SUBSCRIPTIONS_PER_SESSION` | Oturum başına STOMP subscription sınırı | `20` | Yok |
| `WEBSOCKET_SERVER_HEARTBEAT_MILLIS` / `WEBSOCKET_CLIENT_HEARTBEAT_MILLIS` | STOMP heartbeat aralığı | `10000` | Yok |

### Frontend entegrasyonu / CORS

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `CORS_ALLOWED_ORIGINS` | İzin verilen frontend origin'leri (virgülle ayrık) | `https://app.example.com` | Boşsa veya `*` içeriyorsa `CorsProperties` başlangıçta hata fırlatır — her profilde geçerli |
| `FRONTEND_BASE_URL` | Şifre sıfırlama linki + mesaj bildirim linki için taban URL | `https://app.example.com` | `local`/`test`/`stub` dışında: public HTTPS origin olmalı, path içermemeli, `OAUTH2_FRONTEND_REDIRECT_URI` ile aynı origin olmalı |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Railway'in proxy'sinden gelen `X-Forwarded-*` başlıklarının güvenilip güvenilmeyeceği | `framework` | Yok (fail-fast değil) ama **Railway'de `framework` olarak ayarlanmazsa** `{baseUrl}`'den üretilen OAuth2 callback URL'i ve secure-cookie davranışı yanlış scheme/host kullanabilir |

### E-posta (Resend)

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `RESEND_API_KEY` | Resend API anahtarı | `re_<...>` | Yoksa Spring placeholder hatasıyla çöker — default yok |
| `RESEND_FROM` | Gönderen adresi | `Uniform Akademi <noreply@uniformakademi.com>` | `local`/`stub` dışında (`test` hariç, o profil bu config'i hiç yüklemiyor) env var **açıkça set edilmemişse** `ResendConfig.requireExplicitResendFrom` çöker — yaml'daki sandbox varsayılanına (`onboarding@resend.dev`) production'da asla düşülmesin diye |
| `RESEND_REPLY_TO` | Yanıt adresi (opsiyonel) | `merhaba@uniformakademi.com` | Yok, boşsa Resend isteğinden tamamen çıkarılır |

### Ödeme (iyzico)

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `IYZICO_ENABLED` | Gerçek iyzico client'ını aktif eder | `true` | Yok |
| `IYZICO_MODE` | `sandbox` / `production` (bilgi amaçlı) | `production` | Yok |
| `IYZICO_API_KEY` | iyzico API key | `<PLACEHOLDER>` | Yok (validator sadece URL'leri kontrol eder, key'in dolu olduğunu değil) |
| `IYZICO_SECRET_KEY` | iyzico secret key | `<PLACEHOLDER>` | Yok |
| `IYZICO_BASE_URL` | iyzico API adresi | `https://api.iyzipay.com` | `IYZICO_ENABLED=true` ve `local`/`test`/`stub` dışındaysa: yalnızca `api.iyzipay.com`, `sandbox-api.iyzipay.com`, `api.iyzico.com`, `sandbox-api.iyzico.com` host'larından biri olmalı, port/userinfo/query/fragment içermemeli |
| `IYZICO_CALLBACK_URL` | iyzico'nun ödeme sonucu POST edeceği webhook | `https://api.example.com/api/v1/payments/iyzico/webhook` | Aynı koşulda: public HTTPS olmalı, localhost/private host olamaz |

### Medya (Cloudflare R2)

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `R2_ENABLED` | R2 depolamayı aktif eder | `true` | Yok |
| `R2_ACCOUNT_ID` | Cloudflare hesap id | `<PLACEHOLDER>` | Yok |
| `R2_ACCESS_KEY_ID` | R2 access key | `<PLACEHOLDER>` | Yok |
| `R2_SECRET_ACCESS_KEY` | R2 secret key | `<PLACEHOLDER>` | Yok |
| `R2_BUCKET` | Bucket adı | `demo-media-prod` | Yok |
| `R2_ENDPOINT` | R2 S3-uyumlu endpoint | `https://<account>.r2.cloudflarestorage.com` | Yok |
| `R2_UPLOAD_URL_EXPIRATION_MINUTES` | Presigned upload URL ömrü | `10` | Yok |
| `R2_DOWNLOAD_URL_EXPIRATION_MINUTES` | Presigned download URL ömrü | `10` | Yok |
| `MEDIA_PUBLIC_BASE_URL` | Backend'in herkese açık origin'i (medya redirect için) | `https://api.example.com` | `R2_ENABLED=true` ve `local` dışında: değer zorunlu, public **HTTPS** origin olmalı (localhost/127.0.0.1/0.0.0.0 kabul edilmez), path/query/fragment/userinfo içermemeli |
| `MEDIA_PROFILE_IMAGE_MAX_BYTES` | Profil fotoğrafı boyut sınırı | `5242880` | Yok |
| `MEDIA_DOCUMENT_MAX_BYTES` | Belge boyut sınırı | `10485760` | Yok |
| `MEDIA_PENDING_UPLOAD_EXPIRATION` | Tamamlanmamış yükleme süresi | `24h` | Yok |
| `MEDIA_PENDING_CLEANUP_BATCH_SIZE` | Temizlik job batch boyutu | `100` | Yok |
| `MEDIA_PENDING_CLEANUP_CRON` | Bekleyen medya temizlik cron'u | `0 10 * * * *` | Yok |
| `MEDIA_RETIRED_CLEANUP_CRON` | Emekli medya temizlik cron'u | `0 */15 * * * *` | Yok |

### Ödeme/oturum zamanlaması ve diğer job'lar

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `PAYMENT_COMMISSION_RATE` | Platform komisyon oranı | `0.20` | Yok |
| `PAYMENT_RETRY_DAYS` | PAST_DUE deneme penceresi (gün) | `3` | Yok |
| `PAYMENT_PENDING_CHECKOUT_TIMEOUT_MINUTES` | Ödenmemiş checkout zaman aşımı | `30` | Yok |
| `PAYMENT_RENEWAL_CRON` | Abonelik yenileme job'u | `0 0 3 * * *` | Yok |
| `MESSAGE_NOTIFICATION_DEBOUNCE` | "Yeni mesaj" e-postası debounce süresi | `30m` | Yok |
| `MEET_LINK_ENABLED` | Otomatik Google Meet link üretimi (şu an bilinçli olarak kapalı) | `false` | Yok |
| `SESSION_REMINDER_LEAD_TIME` | Ders hatırlatma penceresi | `24h` | Yok |
| `SESSION_REMINDER_CRON` | Hatırlatma job cron'u | `0 */15 * * * *` | Yok |
| `PASSWORD_RESET_CLEANUP_CRON` | Süresi dolmuş reset token temizliği | `0 20 3 * * *` | Yok |
| `EMAIL_VERIFICATION_CLEANUP_CRON` | Süresi dolmuş doğrulama kodu temizliği | `0 30 3 * * *` | Yok |
| `UNVERIFIED_REGISTRATION_CLEANUP_CRON` | 24 saat doğrulanmamış hesap temizliği | `0 0 * * * *` | Yok |
| `DEMO_SEED_ENABLED` | Demo seed cleanup component'inin aktifliği | `false` | Yok — **prod'da `false` kalmalı**, `true` sadece local'de anlamlı |
| `SWAGGER_ENABLED` | Swagger UI + `/v3/api-docs` erişimi | Set etme (prod) | Yok — varsayılan **`false`** (fail-closed). Prod'da hiç set etme; QA için gerekiyorsa **yalnızca staging environment'ına** `true` ver, böylece unutulan bir değişken prod'da Swagger'ı açık bırakamaz |

### Ağ / platform

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `PORT` | Uygulamanın dinleyeceği port | Railway tarafından otomatik enjekte edilir | Sen elle set etme — bkz. bölüm 6 |

---

## 3. Frontend build-time environment değişkenleri

Kaynak: `frontend/.env.example`, `frontend/src/api/apiBaseUrl.ts`. Vite bu değerleri **build
anında** koda gömer (`import.meta.env.*`); deploy edilmiş statik dosyalarda sonradan değiştirilemez
— her ortam için ayrı bir build gerekir.

| Değişken | Ne işe yarar | Örnek format | Fail-fast durumu |
| --- | --- | --- | --- |
| `VITE_API_BASE_URL` | Backend'in base URL'i (REST + WebSocket proxy hedefi) | `https://api.example.com` | Production build'de (`import.meta.env.DEV === false`) boşsa, `resolveApiBaseUrl` **tarayıcıda ilk API çağrısında** `Error` fırlatır. Not: `npm run build` komutunun kendisi bu durumda **başarısız olmaz** — derleme geçer, ama deploy edilen site açılır açılmaz her API isteği patlar. Ayrıca HTTPS olmayan veya local/private bir host olursa da aynı şekilde reddedilir. |
| `VITE_ENABLE_STUB_PAYMENT_SUCCESS` | Yerel ödeme stub akışını UI'da gösterir | `false` | Yok — kullanım yerleri `import.meta.env.DEV` ile korunuyor, yani production build'de bu değişken ne olursa olsun etkisizdir; Railway/Vercel'e hiç eklemesen de sorun olmaz |

**Pratik sonuç:** Railway (static) veya Vercel'de build komutunu çalıştırmadan önce
`VITE_API_BASE_URL`'i deployed backend'in gerçek, herkese açık HTTPS adresine ayarlaman şart —
yoksa build geçer ama site tamamen kırık görünür (network hataları).

---

## 4. Staging ile production arasında farklı olması gereken değişkenler

Önce bir uyarı: bu projede ayrı bir `staging` Spring profili yok (`CLAUDE.md` / `docs/handoff.md`:
Railway "bare"/default profili çalıştırıyor). Yani `AdminBootstrapSecurityValidator`,
`FrontendUrlProductionConfigurationValidator`, `IyzicoProductionConfigurationValidator` gibi tüm
fail-fast kontroller **staging'de de production'daki kadar sıkı** çalışır — staging için "daha
gevşek" bir mod yok, sadece profile olarak `local`/`test`/`stub` seçilirse gevşer (ki bunlar
staging için uygun profiller değil). Aşağıdaki tablo bu yüzden değerlerin farkı hakkında,
kuralların farkı hakkında değil.

| Değişken | Staging | Production | Neden ayrı olmalı |
| --- | --- | --- | --- |
| `NEON_DATABASE_URL` / `_USERNAME` / `_PASSWORD` | Ayrı staging DB | Ayrı prod DB | `docs/handoff.md`'nin kendi kuralı: gerçek kullanıcı verisiyle test verisi asla aynı DB'de olmamalı |
| `REDIS_URL` | Ayrı Redis instance (veya aynı instance + farklı `APP_ENV`) | Ayrı Redis instance | Aynı Redis paylaşılıyorsa rate-limit sayaçları karışabilir |
| `APP_ENV` | `staging` | `production` | Redis anahtar namespace'ini ayırır — özellikle Redis paylaşılıyorsa önemli |
| `JWT_SECRET` | Staging'e özel, farklı bir değer | Production'a özel, farklı bir değer | Staging secret sızarsa prod oturumları etkilenmemeli |
| `ADMIN_PASSWORD_HASH` | Staging'e özel hash | Production'a özel hash | Gerçek admin şifresi asla staging'de kullanılmamalı |
| `GOOGLE_CLIENT_ID` / `_SECRET` | Staging domaini için ayrı OAuth client | Prod domaini için ayrı OAuth client | Google Console her client için authorized redirect URI'ları domain bazlı eşleştirir — tek client iki domain'i birden kapsayamaz |
| `FRONTEND_BASE_URL` | `https://staging.example.com` | `https://app.example.com` | Farklı frontend deploy'ları |
| `OAUTH2_FRONTEND_REDIRECT_URI` | `https://staging.example.com/oauth/callback` | `https://app.example.com/oauth/callback` | `FRONTEND_BASE_URL` ile aynı origin olmak zorunda (validator kontrol ediyor) |
| `CORS_ALLOWED_ORIGINS` | Staging frontend origin'i | Prod frontend origin'i | Yanlış origin karışırsa CORS ya fazla izin verir ya da staging'i kırar |
| `IYZICO_MODE` / `IYZICO_BASE_URL` / `IYZICO_API_KEY` / `IYZICO_SECRET_KEY` | `sandbox`, sandbox key'ler | `production`, gerçek key'ler | Staging'de gerçek para hareketi olmamalı |
| `IYZICO_CALLBACK_URL` | Staging backend origin'i | Prod backend origin'i | Farklı backend deploy adresleri |
| `MEDIA_PUBLIC_BASE_URL` | Staging backend origin'i | Prod backend origin'i | Aynı sebep |
| `R2_BUCKET` (+ ilgili R2 key'ler, farklı bucket kullanılıyorsa) | Ayrı staging bucket'ı (önerilir) | Ayrı prod bucket'ı | Test yüklemeleri gerçek kullanıcı dosyalarıyla karışmasın |
| `RESEND_FROM` | Aynı doğrulanmış domain kalabilir, ama isim ayırt edici olsun (örn. `"Uniform Akademi (Staging)"`) | Gerçek gönderen adı | Staging'den kaçan bir mailin gerçek kullanıcıyı yanıltmaması için |
| `SWAGGER_ENABLED` | `true` (QA için faydalı) | `false` | Prod'da API şemasını herkese açmamak |
| `VITE_API_BASE_URL` (frontend build) | Staging backend URL'i | Prod backend URL'i | Ayrı build gerektirir (build-time değişken) |

---

## 5. Health check uyumluluğu: `/api/v1/health`

**Railway'in beklentisi:** health check path'ine düz HTTP `GET` atar, ayarlanan sürede (Railway
panelinde sen ayarlayacaksın) `2xx` dönerse servisi sağlıklı sayar, dönmezse deploy'u başarısız
işaretler / restart döngüsüne sokar. Body formatı Railway için önemli değil — JSON olması şart
değil, sadece status code önemli.

Kodu inceledim (`HealthController` → `HealthService` → `HealthCheckRepository`):

| Kontrol | Durum |
| --- | --- |
| Endpoint auth gerektirmiyor mu? | ✅ `SecurityConfig` içinde `/api/v1/health` `permitAll()` — Railway'in auth header'sız isteği sorunsuz geçer |
| Başarılı durumda `2xx` dönüyor mu? | ✅ `HealthResponse` düz bir DTO, açık bir `@ResponseStatus` yok → varsayılan `200 OK` |
| DB erişilemezse gerçekten hata (non-2xx) dönüyor mu? | ✅ `healthCheckRepository.findAll()` DB'ye ulaşamazsa fırlatılan exception projenin global `@RestControllerAdvice`'ı üzerinden `5xx` bir `ProblemDetail`'e dönüşür — Railway bunu doğru şekilde "unhealthy" sayar |
| **Uygulama Railway'in verdiği porttan dinliyor mu?** | ❌ **BULUNDU VE DÜZELTİLDİ** — aşağıya bak |
| Küçük bir tasarım notu (bilgi amaçlı, değiştirmedim) | `HealthCheck` tablosunda hiç satır yoksa servis `{"status":"DOWN"}` ile **yine de 200 OK** döner (DB bağlantısı çalıştığı için exception fırlamıyor, sadece `.findFirst()` boş geliyor). Yani Railway bunu "sağlıklı" sayar; bu "Phase 0 proof endpoint" olarak tasarlanmış, health semantiğini genişletmek bu görevin kapsamı dışında olduğu için dokunmadım — bilmen yeterli. |

### Bulunan asıl sorun: `server.port`

`application.yml`'de `server.port` hiç tanımlı değildi. Spring Boot varsayılan olarak her zaman
**8080** portunu dinler. Railway ise konteynerine rastgele bir `PORT` ortam değişkeni enjekte eder
ve proxy'sini **o porta** yönlendirir — `SERVER_PORT` değil, `PORT` okur. Eğer uygulama sabit
8080'de kalıp Railway'in verdiği portu görmezden gelirse, Railway'in proxy'si servise hiç
ulaşamaz ve health check (dolayısıyla tüm trafik) başarısız olur.

Düzeltme olarak `application.yml`'e şu satırı ekledim:

```yaml
server:
  port: ${PORT:8080}
```

Bu, Railway'de `PORT` set edildiğinde onu kullanır; senin local'de (`./mvnw spring-boot:run`)
çalıştırdığın ortamda `PORT` diye bir env var olmadığı için sessizce `8080`'e döner — yani local
dev akışını hiç etkilemez, sadece Railway uyumluluğunu ekler.

---

## 6. Oluşturulan / değiştirilen dosyalar

- [`backend/Dockerfile`](../backend/Dockerfile) — yeni, multi-stage (JDK 21 build + JRE 21 runtime)
- [`backend/.dockerignore`](../backend/.dockerignore) — yeni
- [`docs/dockerfile-explained.md`](./dockerfile-explained.md) — yeni, Dockerfile'ın satır satır açıklaması
- [`docs/railway-deployment-guide.md`](./railway-deployment-guide.md) — bu dosya
- `backend/src/main/resources/application.yml` — tek satır eklendi: `server.port: ${PORT:8080}`

Hiçbir dosya commit/push edilmedi; hepsi çalışma dizininde duruyor, gözden geçirip kendi
Railway kurulumunla birlikte commit etmek sana kalmış.

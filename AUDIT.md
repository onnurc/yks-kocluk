# Backend Security & Correctness Audit

**Scope:** `/backend` (Java 21, Spring Boot 4.0.6). Read-only review, no code modified.
**Not covered:** `/frontend` (excluded per project scope), `CoachStatsService`, `PackageService`,
`UniversityService`, `HealthService`, `DemoSeedCleanupComponent`, `OAuth2LoginSuccessHandler`
internals, `ResendMailClient`/`StubMailClient`, `JitsiMeetClient`/`StubMeetClient`, MapStruct
mapper bodies, full entity/Bean-Validation annotations, migrations V11/V13–V17 in detail, and the
69 existing test files' contents. These are lower-risk/read-only-output surfaces; flag if you want
them covered in a follow-up pass.

Overall impression: this is a carefully built codebase that already implements most of the hard
parts correctly (atomic capacity guard, UNIQUE-based double-booking guard, idempotent webhook
processing, the correct tx1/external/tx2 pattern in `SubscriptionBillingService`, STOMP auth,
child-safety message gate, minor-consent gate). The findings below are real gaps, not generic
best-practice noise — most cluster around the payment/webhook path and a few inconsistencies with
the project's own stated conventions.

---

## Critical

### 1. Unauthenticated payment webhook activates subscriptions for free when iyzico is disabled

**File:** [PaymentController.java:12-27](backend/src/main/java/com/ykskocluk/demo/controller/PaymentController.java), [SecurityConfig.java:43-48](backend/src/main/java/com/ykskocluk/demo/security/SecurityConfig.java), [SubscriptionService.java:226-249](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java)

```java
// SecurityConfig.java
.requestMatchers(
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
        "/api/v1/payments/iyzico/webhook")
.permitAll()
```

```java
// SubscriptionService.processWebhook
if (iyzicoProperties != null && iyzicoProperties.enabled()) {
    // ... signature check ...
}
Payment payment = paymentRepository.findById(request.paymentId())...
if ("SUCCESS".equalsIgnoreCase(request.status())) {
    ...
    completePaymentSuccess(payment, subscription, ...); // activates the subscription
```

`payments.iyzico.enabled` defaults to `false` (`application.yml`: `enabled: ${IYZICO_ENABLED:false}`).
When disabled, the entire signature-verification block is skipped — the webhook handler trusts
`request.paymentId()` and `request.status()` with **zero authentication**, and the endpoint is
`permitAll()` regardless of profile (unlike `StubPaymentController`, which is properly gated
behind `@ConditionalOnProperty`).

**Concrete exploit:** any authenticated student can:
1. `POST /api/v1/subscriptions/checkout` → gets back their own `paymentId` in `SubscriptionCheckoutResponse` (a PENDING payment/subscription is created, no charge has happened).
2. `POST /api/v1/payments/iyzico/webhook` with `{"paymentId": <that id>, "status": "SUCCESS", "paymentConversationId": "...", "iyziEventType": "..."}` and **no signature header**.
3. Their subscription flips to `ACTIVE` and coach capacity is consumed — completely free, no card ever charged.

This works today against any deployment that hasn't explicitly set `IYZICO_ENABLED=true` — which, per `application.yml`, is the out-of-the-box default.

**Fix:** Never skip signature verification based on a feature flag on a `permitAll()` production
endpoint. Either:
- Require the signature header unconditionally (fail closed) and route the "no real payment yet"
  demo/dev path through the already-gated `StubPaymentController` exclusively, or
- Add `@ConditionalOnProperty` to `PaymentController` itself (mirroring `StubPaymentController`),
  so the real webhook route doesn't exist at all while iyzico is disabled.

---

## High

### 2. External payment calls made inside `@Transactional` methods — violates the project's own transaction-boundary rule

**File:** [SubscriptionService.java:94-109](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java) (`checkout`), [SubscriptionService.java:288-358](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java) (`refund`)

```java
@Transactional
public SubscriptionCheckoutResponse checkout(Long studentUserId, SubscriptionCreateRequest request) {
    Subscription subscription = createPendingSubscription(studentUserId, request);
    Payment payment = createPendingPayment(subscription);
    CheckoutResult checkoutResult = iyzicoClient.initializeCheckout(...); // external call, tx still open
    ...
}
```

```java
@Transactional
public RefundResponse refund(Long paymentId, BigDecimal refundAmount, String reason) {
    Payment originalPayment = paymentRepository.findByIdForUpdate(paymentId)... // row lock taken
    ...
    RefundResult refundResult = iyzicoClient.refund(...); // external call, still holding the lock + tx
    ...
}
```

`CLAUDE.md` is explicit: *"No external calls inside a DB transaction... external calls are
synchronous, after commit."* `SubscriptionBillingService.processDue` (the renewal job) implements
this correctly via `TransactionTemplate` with a genuine tx1/external-call/tx2 split — so the
pattern is known and used correctly elsewhere in the same codebase, just not here.

**Why it bites:** `checkout()` holds a DB transaction (and a Hikari connection, pool size 10 per
`application.yml`) open for the full round-trip to iyzico's sandbox API. `refund()` is worse: it
holds a **pessimistic row lock** (`findByIdForUpdate`) on the `Payment` row for the duration of an
external HTTP call. If iyzico is slow or the connection stalls (no configured HTTP timeout — see
finding #6), concurrent refund attempts on the same payment block, and under load the entire
10-connection Hikari pool can be exhausted by a handful of slow checkout/refund calls, starving
every other request (bookings, login, dashboard) — a self-inflicted denial of service.

**Fix:** Apply the same `TransactionTemplate` split used in `SubscriptionBillingService`: commit
the PENDING payment row first, call `iyzicoClient` with no transaction open, then finalize in a
second transaction.

### 3. Real iyzico recurring charge is a hardcoded stub — auto-renewal never actually collects payment

**File:** [RealIyzicoClient.java:132-139](backend/src/main/java/com/ykskocluk/demo/integration/RealIyzicoClient.java)

```java
@Override
public ChargeResult charge(String savedCardToken, BigDecimal amount, String idempotencyKey) {
    // Recurring charge via saved card (stubbed in sandbox phase as card storage / recurrences are out of scope)
    String reference = "sandbox-stub-ref-" + UUID.randomUUID();
    log.info("[RealIyzicoClient] Stub charge for saved card token={} amount={} (key={}) -> success, ref={}", ...);
    return new ChargeResult(true, reference);
}
```

Even with `IYZICO_ENABLED=true` (the "real" client), `charge()` — the method the daily
`SubscriptionRenewalJob` → `SubscriptionBillingService.processDue` path calls for every renewal —
unconditionally returns success without calling iyzico or any saved card at all. Combined with
`subscription.setSavedCardToken("stub-card-token-" + UUID.randomUUID())` in
`SubscriptionService.createPendingSubscription` (line 148), there is no real saved-card mechanism
anywhere in the codebase yet.

**Why this matters for the business rule in `CLAUDE.md`** ("auto-renewing monthly subscription
— renews until the user cancels"): as implemented, every subscription renews forever at zero
actual cost to the student once the initial checkout succeeds, because the recurring charge leg
is entirely fake. This is flagged as **High** rather than Critical because the surrounding
machinery (idempotency keys, retry/expiry state machine, commission snapshot) all correctly
assumes a real charge outcome — the moment a real `charge()` is wired in, everything downstream
should work. But if this "real" client name is trusted to be production-ready, it's a silent
revenue-integrity gap. **Needs verification:** confirm with the team whether this is a known,
tracked placeholder (PHASES.md doesn't call it out explicitly) versus an oversight.

---

## Medium

### 4. Non-constant-time webhook signature comparison

**File:** [SubscriptionService.java:246](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java)

```java
if (!computedSignature.equalsIgnoreCase(signatureV3)) {
    throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "İmza doğrulanamadı");
}
```

`String.equalsIgnoreCase` short-circuits on the first mismatched character, leaking timing
information about how many leading hex characters of the HMAC an attacker has guessed correctly.
This is the standard timing side-channel against HMAC verification (CWE-208).

**Fix:**
```java
if (!MessageDigest.isEqual(
        computedSignature.getBytes(StandardCharsets.UTF_8),
        signatureV3.getBytes(StandardCharsets.UTF_8))) {
    throw new ApiException(...);
}
```
(Case-fold both sides first if case-insensitivity must be preserved — hex output only, so
lower-casing before comparing is safe.)

### 5. No rate limiting on auth endpoints

**File:** [SecurityConfig.java:43-48](backend/src/main/java/com/ykskocluk/demo/security/SecurityConfig.java), [AuthController.java](backend/src/main/java/com/ykskocluk/demo/controller/AuthController.java)

`register`, `login`, and `refresh` are `permitAll()` with no throttling anywhere in the codebase
(confirmed — no rate-limiter, bucket, or interceptor exists). `login` does a BCrypt comparison
per attempt (correctly resistant to hash-cracking) but nothing stops high-volume credential
stuffing or brute force against a single account. Given this is a pre-MVP/solo-dev project this
may be an accepted risk for now, but worth a deliberate decision rather than an oversight —
especially since `/api/v1/auth/refresh` and the webhook are also unthrottled attack surface.

### 6. Missing timeouts on external iyzico HTTP calls

**File:** [RealIyzicoClient.java:38-58](backend/src/main/java/com/ykskocluk/demo/integration/RealIyzicoClient.java)

```java
this.options = new Options();
this.options.setApiKey(properties.apiKey());
this.options.setSecretKey(properties.secretKey());
this.options.setBaseUrl(properties.baseUrl());
```

No connect/read timeout is configured on the iyzipay SDK `Options` object. Combined with finding
#2 (external call inside an open transaction/lock), a hanging iyzico call can block a request
thread and a DB connection/lock indefinitely. **Needs verification:** the iyzipay-java SDK may
have its own internal default timeout — I did not find one set explicitly in this codebase, and
couldn't inspect the SDK's internals from the repo alone.

### 7. N+1 query in admin payment listing

**File:** [SubscriptionService.java:409-439](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java)

```java
Page<Payment> page = paymentRepository.findAll(pageable);
Page<AdminPaymentResponse> mapped = page.map(payment -> {
    BigDecimal totalRefunded = BigDecimal.ZERO;
    if (payment.getType() == PaymentType.CHARGE) {
        List<Payment> existingRefunds = paymentRepository.findBySourcePaymentIdAndStatus(payment.getId(), PaymentStatus.SUCCESS);
        ...
```

For every `CHARGE` row in a page (up to `max-page-size: 100` per `application.yml`), a separate
query fetches its refunds — up to 100 extra round-trips per admin page load. Low urgency (admin-only,
low traffic) but a straightforward fix: batch-load refunds for all payment IDs on the page in one
`findBySourcePaymentIdInAndStatus(List<Long>, PaymentStatus)` query, same pattern already used
correctly in `CoachSearchService.loadTracks` and `AdminConversationService.listConversations`.

### 8. Plaintext production-shaped secrets sitting on disk in `application-local.yml`

**File:** `backend/src/main/resources/application-local.yml` (confirmed gitignored via
`.gitignore:7`, and confirmed via `git log --all --diff-filter=A` that it was **never** committed
— so this is not a repo leak)

The file contains a live-looking Neon Postgres connection string with embedded username/password
and a live-looking Resend API key (`re_...`), unencrypted, as plain local dev convenience. Since
it's correctly gitignored and never committed, this is a much lower-severity note than it would
otherwise be — flagging only because real-looking credentials sit in cleartext on a developer
machine. If these are in fact live/shared credentials (not a personal sandbox), rotate them and
treat this file as sensitive (e.g., don't screen-share it, back it up encrypted).

---

## Low / Nitpick

### 9. `ConsentService.revokeConsent` hardcodes `documentVersion` regardless of consent type

**File:** [ConsentService.java:99-119](backend/src/main/java/com/ykskocluk/demo/service/ConsentService.java)

```java
public ConsentResponse revokeConsent(Long userId, ConsentType consentType) {
    ...
    record.setDocumentVersion(CURRENT_KVKK_VERSION); // always KVKK's version, even for non-KVKK types
```

If a `TERMS` (or any future non-KVKK) consent is revoked, the revocation record is stamped with
the KVKK document version string, which is semantically wrong and could confuse an audit trail
review later. Low impact today since `ConsentType.KVKK` appears to be the only type actively
enforced (`checkConsentRequiredForAction` only checks KVKK), but worth fixing before a second
consent type goes live.

### 10. Inconsistent sort-field whitelisting on admin listing endpoints

**Files:** [SubscriptionService.java:409-459](backend/src/main/java/com/ykskocluk/demo/service/SubscriptionService.java) (`listPayments`, `listSubscriptions`), [ReportService.java:89-98](backend/src/main/java/com/ykskocluk/demo/service/ReportService.java) (`listReports`)

These three admin methods pass the caller's `Pageable` straight to `repository.findAll(pageable)` /
`findByStatus(status, pageable)` with no `validateSort` call, unlike `CoachSearchService`,
`CoachProfileService`, and `AdminConversationService`, which all whitelist sortable fields exactly
as `CLAUDE.md` requires ("Whitelist sortable fields per endpoint — do not blindly pass
user-supplied sort strings to JPA"). Impact is low since these are `ADMIN`-only endpoints, but an
unrecognized `sort=` value will currently surface as an unhandled `PropertyReferenceException` →
generic 500 via the catch-all handler, rather than a clean `400 INVALID_SORT_FIELD` like the other
endpoints.

### 11. WebSocket handshake allows any origin while REST CORS is locked down

**File:** [WebSocketConfig.java:28-31](backend/src/main/java/com/ykskocluk/demo/config/WebSocketConfig.java)

```java
registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
```

vs. `SecurityConfig.corsConfigurationSource()` restricting REST to `http://localhost:5173`. STOMP
CONNECT still requires a valid bearer JWT, so this isn't directly exploitable without an already-
leaked token, but it's an inconsistency worth tightening to the same origin allowlist as the REST
API, especially before any production domain is added — defense-in-depth against a token leaked
via an XSS on the frontend being relayed from an arbitrary origin.

### 12. Per-request DB round trip for suspension check via `ApplicationContext.getBean`

**Files:** [JwtAuthenticationFilter.java:61](backend/src/main/java/com/ykskocluk/demo/security/JwtAuthenticationFilter.java), [StompAuthChannelInterceptor.java:79](backend/src/main/java/com/ykskocluk/demo/security/StompAuthChannelInterceptor.java)

Both fetch `UserRepository` via `applicationContext.getBean(...)` rather than constructor
injection, and both hit the DB on every authenticated request/STOMP CONNECT just to check
`UserStatus.SUSPENDED`. Functionally correct (this is in fact what makes suspension take effect
immediately without a token-revocation list — a real strength), but the `getBean()` indirection is
an unusual pattern (normally used to dodge a circular-dependency issue) and worth a comment
explaining why, or switching to normal constructor injection if there's no cycle. Not a security
issue — just flagged since I couldn't tell if it was deliberate.

---

## What I'd verify next if you want to go deeper
- Whether `IYZICO_ENABLED` is actually `true` in any currently-reachable deployment (determines how live finding #1 is right now).
- The OAuth2 login path (`OAuth2LoginSuccessHandler`) end-to-end — I confirmed the redirect URI is config-driven but didn't trace the full token-issuance flow.
- `DemoSeedCleanupComponent` — what it cleans up and whether `DEMO_SEED_ENABLED=true` in any shared environment could interact badly with real user data.
- Bean Validation annotations on all entities/DTOs (spot-checked several, didn't exhaustively verify every field, e.g. max lengths matching DB column sizes).

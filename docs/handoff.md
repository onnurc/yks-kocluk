# Handoff — YKS Coaching Platform (Backend)

Single-file resume point for a fresh session. **Factual, based on the code as it exists now**
(not the v4 plan). For binding rules see `CLAUDE.md`; for phase intent see `PHASES.md`.

_Last updated: 2026-06-27._

---

## Status at a glance

- **Phases complete:** 0, 0.5, 1, 2, 3, 4 (4a–4d), 5a, 5b, 5c, 6 (real Meet — Jitsi), 7 (real Mail — Resend),
  **8 Stage 1 (8a–8d) — auto-renew lifecycle, STUB-first iyzico**.
- **In progress / next:** 8 **Stage 2** (real iyzico **sandbox**: saved-card tokenization + recurring charge,
  replacing `StubIyzicoClient`). Then Phase 9 (KVKK / hardening).
- **Tests:** `./mvnw verify` is **GREEN — 157 tests** (1 skipped: the `@Disabled` live Resend smoke).
- **Neon (prod DB):** Flyway at **v10** (all of V1–V10 applied live; V10 = payments/auto-renew). Schema
  matches tests; `ddl-auto:validate` passes on `-Plocal` boot. **8b/8c/8d added no migration.**
- **Build:** Java 21, Spring Boot 4.0.6, Maven. (`pom.xml` `java.version` = 21.)

### Stack (as wired)
Spring Boot 4.0.6 · Spring Security 7 (stateless JWT + Google OAuth2) · Spring Data JPA/Hibernate ·
Flyway (SQL-first) · PostgreSQL (Neon) · Spring WebSocket + STOMP · **Jackson 3 (`tools.jackson`)** ·
MapStruct 1.6.3 · Lombok · jjwt 0.12.7 · springdoc-openapi 3.0.3 · Testcontainers 2.0.x (BOM-managed) ·
JUnit/Mockito/MockMvc. Money = `BigDecimal numeric(12,2)` TRY; time = UTC `Instant`; no hard deletes;
all entities extend `BaseEntity` (id, created_at, updated_at, version) with JPA auditing.

---

## How to run / test

```bash
# Tests (Colima/Docker MUST be running — Testcontainers spins real Postgres 18; no H2).
# The Ryuk socket override is already set in pom.xml surefire config, so a plain build works:
./mvnw verify
./mvnw test -Dtest=SomeTest        # run a single test class

# Run against Neon locally (needs the gitignored application-local.yml — see below):
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# App on http://localhost:8080 ; Swagger at /swagger-ui.html
```

- **`application-local.yml`** (gitignored, NOT committed) supplies `NEON_DATABASE_URL` (jdbc form,
  pooled host with `-pooler`, credentials as `user`/`password` query params, `channelBinding=require`),
  `JWT_SECRET`, and **`RESEND_API_KEY`** (Phase 7). Template is `application-local.yml.example`.
  Resend sender defaults to `onboarding@resend.dev` (sandbox; sends to the account owner without
  domain verification). **iyzico sandbox keys are NOT in here yet** — add when Phase 8 starts.
- **Admin seed (V3):** `admin@yks.local` / dev password **`admin1234`** (dev-only BCrypt hash baked
  into V3; prod overrides via `ADMIN_PASSWORD_HASH`). Use it to approve coaches in smoke tests.

---

## What each phase delivered

### Phase 0 — Setup
`BaseEntity` + `@EnableJpaAuditing`; scoped security; `GET /api/v1/health` → `{"status":"UP"}`;
Flyway V1 baseline; Testcontainers wired (real Postgres). `ddl-auto: validate` — Flyway owns schema.

### Phase 0.5 — Risk PoC (throwaway)
Findings: iyzico sandbox was blocked at PoC time (no creds → stub continued); Google Meet needs
Workspace → **Jitsi fallback** chosen for Phase 6. **Update (2026-06-27): iyzico sandbox is now
provisioned** (merchant **3429394**; sandbox API key + secret live in the iyzico sandbox panel,
not yet in repo/config) → **Phase 8 is no longer blocked on credentials.** Production iyzico
application (real company/tax info) still deferred to launch.

### Phase 1 — Auth  (V2)
- Entities: `User` (email unique, passwordHash nullable, role, status, googleSub, emailVerified),
  `RefreshToken` (hashed, rotating).
- Endpoints: `POST /api/v1/auth/{register,login,refresh,logout}`. JWT HS256 access (15m) +
  opaque rotating refresh (30d, stored hashed). Google OAuth2 account-linking by verified email.
- Invariants: stateless; `@AuthenticationPrincipal` resolves to `Long userId`; role claim →
  `ROLE_x` authority. ProblemDetail (RFC 9457) for ALL errors incl. 401/403 and framework
  exceptions (global `@RestControllerAdvice` extends `ResponseEntityExceptionHandler`).

### Phase 2 — Coach & Profile  (V3 seed admin, V4)
- Entities: `CoachProfile` (user 1:1, university, headline/bio/dept/gradYear, status
  PENDING/APPROVED/REJECTED, `active_student_count`, `max_student_capacity`=10 default, no price),
  `StudentProfile`, `University`, `CoachSubject` (track join).
- Endpoints: `POST/GET /api/v1/coach/profile/me`, `PUT /coach/profile/me`; student profile;
  `GET /api/v1/universities`; admin `GET /api/v1/admin/coaches?status=`, `POST .../{id}/approve`,
  `POST .../{id}/reject`.
- Invariants: only admin changes status; post-approval edits stay APPROVED; `AccessDeniedException`
  re-thrown in the handler so it routes to the 403 ProblemDetail handler (not 500).

### Phase 3 — Search  (read-only)
- Endpoints: `GET /api/v1/coaches` (APPROVED-only; filters track/universityId/q; paginated,
  sort whitelist), `GET /api/v1/coaches/{id}`. Roles STUDENT+ADMIN (COACH 403).
- Seam: `CoachStatsService` returns derived rating/totalSessions (was placeholder; **totalSessions
  wired in 4d**, rating still null until Reviews). Search query untouched by the wiring.
- Note: `cast(:q as string)` in the JPQL to avoid Postgres `lower(bytea)` on null filter.

### Phase 4 — Availability & Booking  (V5–V8, built 4a→4d)
Cross-phase decision: minimal `Package`/`Subscription` introduced here (direct activation);
Phase 8 layers the payment lifecycle on top.

**4a — Packages & Subscriptions + capacity (V5).**
- `Package` (name unique, weeklySessions, durationDays, price, active; 2 seeded: "Aylık 1x" 1/30d/1500,
  "Aylık 2x" 2/30d/2500). `Subscription` (student, coachProfile, pkg, status ACTIVE/EXPIRED/CANCELLED,
  startAt/endAt). Enum field is `pkg` (`package` is reserved).
- Endpoints: `GET /api/v1/packages` (auth); `POST /api/v1/subscriptions` + `GET /me` (STUDENT).
- **Invariants:** capacity race → atomic conditional UPDATE
  `incrementActiveStudentCountIfRoom` (`SET active=active+1 WHERE active < max`; 0 rows → `COACH_FULL`,
  bypasses @Version on purpose). One ACTIVE sub per (student,coach) → **partial unique index**
  `where status='ACTIVE'`; same-student race caught via saveAndFlush → `ALREADY_SUBSCRIBED`.

**4b — Coach Availability slots (V6).**
- `CoachAvailability` (coachProfile, startTime/endTime UTC, `is_booked`; **UNIQUE(coach_profile_id,
  start_time)**).
- Endpoints: `POST/GET /api/v1/coach/availability`, `DELETE /coach/availability/{id}` (COACH, unbooked
  only); `GET /api/v1/coaches/{id}/availability` (STUDENT/ADMIN, open future slots of APPROVED coach).
- Invariants: reject past slot (`SLOT_IN_PAST`) and end≤start (`INVALID_SLOT_RANGE`) — validated in the
  service (unit-testable); duplicate start → `SLOT_DUPLICATE`.

**4c — Booking / Session (V7).**
- `Session` (student, coachProfile, subscription, **availability nullable+UNIQUE**, status
  PLANNED/COMPLETED/CANCELLED/LATE_CANCELLED/NO_SHOW, start/end snapshotted off the slot).
- Endpoints: `POST /api/v1/sessions` + `GET /me` (STUDENT); `GET /api/v1/coach/sessions` (COACH).
- **Invariants:**
  - **Double-booking guarantee = UNIQUE(`sessions.availability_id`) at INSERT** (saveAndFlush + catch →
    `SLOT_TAKEN`). Never read `is_booked` to decide; `is_booked` is a UI flag flipped after the guard.
  - **Weekly quota** = Mon–Sun in **Europe/Istanbul** → UTC window; counts quota-consuming sessions
    by subscription vs `pkg.weeklySessions`; `>=` → `QUOTA_EXCEEDED`.
  - Booking requires an **ACTIVE** subscription with the coach (`NO_ACTIVE_SUBSCRIPTION`) — the same
    lookup the message gate builds on.

**4d — Cancellation lifecycle + after-commit side effects.**
- Endpoints: `POST /api/v1/sessions/{id}/cancel` (STUDENT); `POST /api/v1/coach/sessions/{id}/complete`,
  `.../{id}/no-show` (COACH).
- **Invariants:**
  - Cancel: **≥24h before start → CANCELLED**, slot reopened (`availability=null`, `is_booked=false`),
    quota returned. **<24h → LATE_CANCELLED**, slot kept, quota burned. NO_SHOW → quota burned.
  - Quota-consuming set grew to **{PLANNED, COMPLETED, LATE_CANCELLED, NO_SHOW}** — the only change to
    quota logic (the 4c query is byte-for-byte unchanged; statuses are passed in).
  - **Meet link + email = the one sanctioned `@TransactionalEventListener(AFTER_COMMIT)`**
    (`SessionNotificationListener`): external calls run **outside** the tx; a failure is logged, never
    rolls back the booking. Persisting the link uses `setMeetLink` with **`REQUIRES_NEW`** (a default-
    propagation write inside an after-commit callback would NOT commit — known gotcha). `meet_link`
    column = V8. Clients: `integration.MeetClient`/`MailClient` (2 of the 4 sanctioned interfaces) with
    `Stub*` impls (real in Phase 6/7).
  - `CoachStats.totalSessions` wired = count of COMPLETED per coach — **edited only
    `CoachStatsService`** (+ a new repo count method); search path untouched (Phase 3 seam held).

### Phase 5 — Messaging  (V9; 5a + 5b + 5c done)

**5a — REST messaging + child-safety gate.**
- Entities: `Conversation` (student, coachProfile, **`last_message_at`** denormalized; UNIQUE(student,
  coach) = one thread/pair), `Message` (conversation, sender, content ≤4000, `read_at` nullable;
  `created_at` = sent time). Attachments **closed in beta** (text-only; no Attachment entity yet).
- Endpoints: `POST /api/v1/conversations` (STUDENT, open-or-get), `GET /conversations` (STUDENT/COACH
  inbox by last_message_at), `GET /conversations/{id}/messages` (paged), `POST /conversations/{id}/messages`
  (REST send fallback), `POST /conversations/{id}/read`.
- **Invariants:**
  - **Gate (server-side):** open requires `existsByStudentIdAndCoachProfileId` (**any** status — active or
    past) else `403 MESSAGING_NOT_ALLOWED`; never-subscribed never messages.
  - **Membership re-checked on every send/read/history** (`403 NOT_CONVERSATION_PARTICIPANT`) — trust
    neither role nor path.
  - `last_message_at` bumped to the saved message's `createdAt` **in the same tx** as the insert.
  - mark-read flips `read_at` **only on the other party's** messages (`sender.id <> readerId` in the
    bulk update).
- Logic lives in `MessageService` (transport-agnostic on purpose — see 5b).

**5b — WebSocket/STOMP real-time.**
- `WebSocketConfig`: endpoint `/ws` (+ SockJS), broker `/topic`, app prefix `/app`, auth interceptor on
  client inbound channel. `ChatStompController.@MessageMapping("/conversations/{id}/send")` → **the same
  `MessageService.sendMessage`** → broadcast to `/topic/conversations/{id}` (payload serialized with the
  Jackson 3 `ObjectMapper`).
- `StompAuthChannelInterceptor`: **CONNECT** validates the JWT from the `Authorization` native header
  (missing/invalid/expired → connection rejected); **SUBSCRIBE** to a conversation topic checked against
  `MessageService.isParticipant` (else rejected server-side).
- SecurityConfig permits `/ws/**` (handshake carries no token — JWT is in the CONNECT frame). Safe: no
  subscribe/send is possible without a valid CONNECT.
- Token-expiry model: **validate at CONNECT; client reconnects with a fresh token on expiry.** Mid-session
  expiry is NOT actively enforced per-frame (see Known gaps).

**5c — Admin oversight (read-only, no schema change).**
- Endpoints: `GET /api/v1/admin/conversations` (list) + `GET /api/v1/admin/conversations/{id}/messages`,
  **ADMIN-only** via class-level `@PreAuthorize("hasRole('ADMIN')")` (the P2 pattern — there is no
  `/api/v1/admin/**` URL matcher; `anyRequest().authenticated()` + the annotation IS the rule). Non-admin → 403.
- **`AdminConversationService` is physically separate from `MessageService`** (child-safety): reads go straight
  to the repositories with **no membership gate**; it never calls `MessageService` and `MessageService` grew no
  `...ForAdmin` methods. All methods `@Transactional(readOnly = true)`.
- **Invisible read-only:** admin reads never touch `read_at` / `last_message_at` / membership — asserted by
  snapshot→read→re-fetch (fresh tx per repo call; no spanning context).
- **N+1-free list:** one entity-graph data query (+ Spring count) + **one** `count … group by` aggregate for the
  page's ids, merged in the service (0-message conversations default to count 0). Proven via Hibernate
  `Statistics` (statement count constant across page sizes).
- Pagination: default 20, **global max 100** (`spring.data.web.pageable.max-page-size`); sort whitelists —
  list `{lastMessageAt, createdAt}`, messages `{createdAt}` → `INVALID_SORT_FIELD` 400 otherwise.
- DTO: `ConversationSummaryResponse` (student{id,fullName} + coach{id,fullName,universityName} + lastMessageAt +
  messageCount); messages reuse `MessageResponse`. Tests: `AdminConversationControllerTest` (web slice, real
  `@PreAuthorize`) + `AdminConversationIntegrationTest` (Testcontainers).
- **No migration** — schema unchanged (still V9). **Deferred:** admin-access audit log → Phase 9.

### Phase 6 — Real video: Jitsi  (commit `150597f`, 116 tests)
- **What:** replaced `StubMeetClient` with **`JitsiMeetClient`** (real impl of the `MeetClient` seam).
  Generates `https://meet.jit.si/yks-{UUID}` locally from `UUID.randomUUID()` (v4 — **unguessable**,
  the mandated entropy source; minors-facing). **No HTTP call, no I/O** — a Jitsi room *is* its URL,
  created when the first participant opens it. Method is total (no checked exceptions). Base URL is a
  hardcoded constant (no config property — YAGNI until self-hosting).
- **Wiring:** `JitsiMeetClient` `@Profile("!test")`, `StubMeetClient` `@Profile("test")` — mutually
  exclusive ⇒ exactly one `MeetClient` bean per profile, no `@Primary`. (Caught that `StubMeetClient`
  was an unguarded `@Component`; a second impl without gating would have failed startup.)
- **Seam untouched:** `MeetClient` interface, `SessionNotificationListener` (AFTER_COMMIT),
  `SessionService.setMeetLink` (REQUIRES_NEW) — all unchanged. The Phase 4d failure-swallow test
  (`SessionNotificationListenerTest`) still stands as proof a client throw never rolls back the booking.
- **No migration** (`meet_link` already V8). Tests: `JitsiMeetClientTest` (UUID v4, two calls differ),
  `MeetClientWiringTest` (`ApplicationContextRunner` — exactly one bean per profile, no DB).

### Phase 7 — Real email: Resend  (commit `1a2072d`, 122 tests; live send confirmed)
- **Scope (locked):** real **Resend transport + booking-confirmation email ONLY**. The real
  deliverable is the reusable transport every later (A) email plugs into. **Nothing time-driven.**
- **What:** `StubMailClient` → `@Profile("test")`; new **`ResendMailClient`** `@Profile("!test")` posts
  to `https://api.resend.com/emails` via Spring `RestClient` (baseUrl constant, `Authorization: Bearer
  <key>`), inline Turkish HTML body, start time rendered **Europe/Istanbul**. `MailClient` keeps its
  single method `sendSessionBooked` — no new methods.
- **Config:** `ResendProperties` (`@ConfigurationProperties("app.resend")`: `apiKey`, `from`) +
  `ResendConfig` (`@Profile("!test") @EnableConfigurationProperties`, supplies the `RestClient.Builder`
  with **connect 3s / read 5s** timeouts via `SimpleClientHttpRequestFactory`). `RESEND_API_KEY` is
  env-only (no default); `from` defaults to `onboarding@resend.dev`.
- **Failure semantics (double backstop):** `sendSessionBooked` is total — wraps `retrieve()` in
  try/catch, logs the Resend message id on 2xx, **swallows-and-logs** any error/timeout. On top of the
  listener's own catch. A Resend outage/hang can **never** block the after-commit thread or roll back
  the committed booking.
- **Test isolation:** `ResendConfig` is `@Profile("!test")`, so under `test` the stub is active and
  **zero network** is wired — full-context tests hit no HTTP and need no Resend key.
- **No migration.** Tests: `ResendMailClientTest` (`MockRestServiceServer` — request shape on 2xx,
  500 → does-not-throw), `MailClientWiringTest` (exactly one `MailClient` per profile),
  `ResendLiveSmokeTest` (**`@Disabled` in the repo**; env-var-guarded; flipped locally only at smoke time).
- **Live smoke PASSED (2026-06-27):** real send `onboarding@resend.dev` → `onurcetinkaya149@gmail.com`,
  Resend message id `34f773d3-64c5-4a7e-b9a4-b68db3e38649`, HTTP 2xx. Key used for the smoke was
  rotated afterward (see Known gaps → resolved).

### Phase 8 Stage 1 — Auto-renew lifecycle, STUB-first  (V10; commits `c0277e6` 8a · `4db867b`/`a585ec2` 8b · `520a980` 8c · `ece8807` 8d)

**The whole auto-renew flow works end-to-end behind a stub `IyzicoClient` — no real money, no company.**
Real iyzico is Stage 2 (sandbox).

**8a — model & seam (V10).**
- `SubscriptionStatus` += **`PAST_DUE`** ({ACTIVE, PAST_DUE, EXPIRED, CANCELLED}). `Subscription` += `autoRenew`
  (default true), `savedCardToken`, `cancelledAt`, `failedChargeCount`, `lastChargeAttemptAt`. **`end_at` is the
  renewal trigger** (no separate next-renewal field).
- `Payment` entity: `type` (CHARGE/REFUND), `amount`, `status` (**PENDING**/SUCCESS/FAILED), **`idempotency_key`
  UNIQUE**, `provider_reference`, commission snapshot (`commission_rate/amount/coach_payout_amount`),
  `source_payment_id` (refund seam, unused). V10 also **widened the live-sub partial-unique index** to
  `where status in ('ACTIVE','PAST_DUE')`.
- **`IyzicoClient`** interface (4th sanctioned seam) + **`StubIyzicoClient`** (plain `@Component`, always succeeds,
  `stub-ref-{uuid}`). Subscription creation stamps `autoRenew=true` + a `stub-card-token-{uuid}`.
  `app.payment.commission-rate` (0.20) / `retry-days` (3).

**8b — billing lifecycle core (`SubscriptionBillingService`).** `processDue(subId, now)` with explicit tx
boundaries via `TransactionTemplate`: **tx1 reserve PENDING (UNIQUE key)** → **charge OUTSIDE any tx** →
**tx2 finalize + advance**. Crash between tx1 and charge → next run **resumes** the stale PENDING with its
own key (provider-idempotent → never a double charge); a concurrent same-day run finds today's PENDING and
**SKIPs**. Commission snapshotted at reserve (frozen). Capacity **decrement** on every →EXPIRED (atomic,
floored at 0). **Booking gate widened to {ACTIVE, PAST_DUE}** (grace keeps access open). Mandatory idempotency
concurrency test + every lifecycle branch tested.

**8c — scheduled job (`SubscriptionRenewalJob`).** `@EnableScheduling` (in `SchedulingConfig`, `@Profile("!test")`
so it never auto-fires in tests), daily cron `0 0 3 * * *` Europe/Istanbul. Selects due set (ACTIVE past end_at
∪ all PAST_DUE) and delegates to `processDue`; per-subscription try/catch. Overlapping-run idempotency tested.
**Single-instance beta; UNIQUE(idempotency_key) makes multi-instance safe (≤1 charge/sub/day).**

**8d — cancel endpoint + emails.** `POST /api/v1/subscriptions/{id}/cancel` (STUDENT, ownership in service) →
200 `SubscriptionResponse` (now exposes `autoRenew`; `savedCardToken` never exposed). **Idempotent**
(re-cancel = no-op, no re-stamp, no second email; terminal → 409). Four emails via `MailClient` (Stub + Resend,
best-effort), dispatched **after** the billing tx commits — renewals from the job, cancellation from the
controller, each in its own try/catch: `CHARGED_SUCCESS`→renewal, `CHARGED_FAILED_PAST_DUE`→payment-failed
(per retry, "attempt N/3"), `CHARGED_FAILED_EXPIRED`+`EXPIRED_NO_RENEW`→expired, cancel→confirmation.

**Locked auto-renew rules (as built):** renew on the `end_at` day (charge → push `end_at` +`durationDays`,
stay ACTIVE); on failure → **PAST_DUE** grace, **retry once/day for 3 attempts**, each failure emails; 3rd
failure → **EXPIRED** + capacity freed; cancel sets `auto_renew=false` + keeps access **until `end_at`** then
EXPIRES (no refund, no immediate cutoff).

**Fund-distribution / payout = STILL AN UNFILLED SEAM** — `Payment` snapshots commission, but nothing pays
coaches; model **A (iyzico Marketplace sub-merchant) vs B (single-merchant manual payout) pending the
accountant**. Not hardcoded into the flow.

**Stage 2 (next) swaps in:** a real `RealIyzicoClient @Profile("!test")` (saved-card **tokenization** + **recurring
charge**) replacing the stub, the stub flipping to `@Profile("test")` — same final shape as Meet/Mail. Sandbox
keys (merchant 3429394) go in `application-local.yml`. No core-flow change expected (the seam is the only swap).

---

## What's next

| Phase | Scope |
| --- | --- |
| **8 Stage 1** | ✅ **DONE** (8a–8d, stub-first) — see "What each phase delivered → Phase 8 Stage 1". Full auto-renew lifecycle behind `StubIyzicoClient`. |
| **8 Stage 2** | Real iyzico **sandbox**: `RealIyzicoClient @Profile("!test")` doing saved-card **tokenization** + **recurring charge**, replacing the stub (stub → `@Profile("test")`). Sandbox keys (merchant 3429394) into `application-local.yml`. No core-flow change — the `IyzicoClient` seam is the only swap. Then a live sandbox smoke (like the Resend one). **Production (real money) is later, gated on company formation** — a config/URL/key swap. Refund execution + **fund distribution/payout (model A vs B)** still deferred (payout pending accountant). |
| **9** | KVKK / hardening: `ConsentRecord` (under-18), `Report` + user suspension (`User.status=SUSPENDED`), PII anonymization on delete. **Admin-access audit log** (log admin reads of minors' threads — KVKK; closes the 5c interim gap). Consider mid-session WS token-expiry enforcement here (tie to suspension). |

### Deferred slices (carved out of the doc's original phasing — deliberate, see below)

| Slice | Scope |
| --- | --- |
| **Scheduling / Reminders** (own phase) | The two **time-driven** emails still deferred: **session reminder** (X h before start) and a **pre-renewal notice** (*"your subscription auto-renews on X — you can cancel"*, sent ahead of the charge). **Scheduling infra now EXISTS** (Phase 8c: `@EnableScheduling` in `SchedulingConfig`, `SubscriptionRenewalJob`), so these can hang off the same daily job (or a sibling). Still needs a **`reminder_sent_at` marker** for idempotency + a catch-up story. Note: the *renewal-succeeded / payment-failed / expired* emails already ship (8d) — what's left here is the **proactive pre-event reminders**, not the post-event ones. |
| **In-app `Notification` entity** (own slice) | Persisted in-app notifications (`Notification`: `user_id`, `type`, `title`, `content`, `read_at`, `related_type`, `related_id`) + list/mark-read endpoints. **Not built.** Structurally independent of the email transport (no shared code) → its own vertical slice (entity→repo→service→controller→DTO→mapper→**V10 migration**→tests). May pair naturally with the Scheduling phase (shared triggers). |

> **PHASES.md divergence (recorded, deliberate):** `PHASES.md` Phase 7 bundles "persist in-app
> notifications (`Notification` entity)" **into** the notification phase. We **split it out**: Phase 7 =
> outbound Resend email only; the in-app `Notification` entity is its own future slice. Same
> split-a-big-phase move already used for Phase 4 (4a–4d) and Phase 5 (5a–5c) — consistent with how this
> project is run. The two (B) time-driven emails were likewise carved into the Scheduling phase.

### Monetization model (CHANGED 2026-06-27 — reverses the earlier one-time-purchase model)

- **Auto-renew is now IN SCOPE** (reverses "one-time monthly purchase / no auto-renew / single charge").
  Subscriptions **auto-renew monthly until the user cancels**. Requires **saved-card tokenization** +
  a **scheduled monthly charge**. (The old "iyzico single-charge, no recurring" DO-NOT / rejected-proposal
  in CLAUDE.md has been removed — recurring is the model now.)
- **Fund-distribution model is NOT finalized** (pending the user's accountant). Two candidates:
  **(A)** iyzico **Marketplace / sub-merchant** — iyzico auto-splits funds to coach + platform; **(B)**
  **single platform merchant** — all funds to the platform, which tracks each coach's earnings and pays
  out manually/batch. **Design rule:** build subscription / renewal / cancellation logic now; keep
  **fund distribution / payout as an isolated SEAM** — do not hardcode either model. (The earlier
  "no escrow / Marketplace" rejected-proposal is now **OPEN / under review**, not settled.)
- **Build strategy = stub-first (Path B), same pattern as MeetClient/MailClient:** `IyzicoClient`
  interface + **stub** impl first → build & test the **entire auto-renew flow** (renewal fields,
  cancellation, scheduled-charge job, emails) against a stub returning "success" — no real money, no
  company. Then **real iyzico sandbox** (saved-card + recurring) — still no company. **Production (real
  money) is last**, a config/URL/key swap, gated on company formation.
- **Legal + company deferred to pre-launch (DELIBERATE sequencing).** Legal review (auto-renew, minor
  consent, fund distribution, KVKK) and company formation are handled by the user in parallel; **development
  does NOT block on them.** Full system is built stub-first then sandbox-first; only production go-live waits
  on legal + company.

---

## Known gaps (intentional, scheduled)

- **Subscriptions still activate WITHOUT a real charge — but the full auto-renew lifecycle now runs on a STUB.**
  Phase 8 Stage 1 (done) charges/renews/retries/expires/cancels via `StubIyzicoClient` (always succeeds), so no
  real money moves yet and creation isn't payment-gated at signup. **Stage 2** swaps in real iyzico sandbox
  (tokenization + recurring charge). `end_at`-driven renewal, PAST_DUE→EXPIRED, and capacity release are all live
  against the stub.
- **Fund distribution / payout to coaches is an unfilled SEAM.** `Payment` snapshots commission per charge, but
  nothing pays coaches out — model **A (iyzico Marketplace sub-merchant) vs B (single-merchant manual payout)**
  is **pending the accountant**, deliberately not hardcoded. Refund execution is likewise deferred (the table
  supports a `type=REFUND` / `source_payment_id` row).
- **Mid-session WebSocket token expiry is not enforced** — a JWT is validated only at CONNECT; an
  already-open session is not force-closed when its access token later expires (client is expected to
  reconnect). Revisit in **Phase 9** alongside suspension (a suspended/expired user should be cut off).
- **iyzico sandbox — PROVISIONED (2026-06-27).** Merchant **3429394**; sandbox API key + secret are in
  the iyzico sandbox panel, **not yet copied into `application-local.yml`/config**. Stub `IyzicoClient`
  still in place; real integration is **Phase 8** (no longer blocked on credentials). **Production iyzico
  application** (real company/tax info) still deferred to launch.
- **Google OAuth2 not live-tested** (no Workspace/creds); login wiring exists but is inert without creds.
- **Reviews don't exist** → `CoachStats.rating` is always null (totalSessions is real). A future phase
  adds Reviews; wiring rating is localized to `CoachStatsService`.
- **Admin oversight reads currently leave no audit trail** — Phase 5c ships ADMIN conversation/message reads
  with no access log. A deliberate, documented interim gap; the **admin-access audit log (KVKK)** closes it in
  **Phase 9**.
- **5c integration tests are ID-scoped but not isolated** — `AdminConversationIntegrationTest` asserts on
  specific seeded conversation ids (not page-wide totals), but the shared, non-rollback `@SpringBootTest`
  context accumulates rows across classes; the list-based tests fetch a large page (size 1000) to stay correct.
  **Tech-debt:** add per-class cleanup (or a transactional/isolated fixture) so the large-page workaround can go.
- _(Resolved)_ **iCloud sync + repo not versioned** — repo moved to `~/dev/demo` (non-synced) and
  `git init` done (initial commit `cda5c73`); see "Security rotations — RESOLVED" below.

---

## Security rotations — RESOLVED (2026-06-27)

Both standing credential-rotation action items are **DONE** (no longer open):

- **Neon password — ROTATED.** Old `npg_KlC7…` (shared in plaintext during setup) replaced with a fresh
  password reset from the Neon console. `NEON_DATABASE_URL` in the gitignored `application-local.yml`
  updated (jdbc form, pooled `-pooler` host, `sslmode=require&channelBinding=require` preserved).
  Verified by booting `-Plocal`: connects to Neon, Flyway validates 9 migrations, schema at V9.
- **Resend API key — ROTATED.** The key used for the Phase 7 live smoke (exposed in chat) was replaced
  with a fresh key; `RESEND_API_KEY` in `application-local.yml` updated. Old key retired.
- Both secrets live **only** in the gitignored `application-local.yml` — verified absent from every
  tracked/committed file. Repo now lives at `~/dev/demo` (non-iCloud-synced); `git init` done
  (initial commit `cda5c73`).

---

## Workflow rules (follow these every session)

1. **Plan first, wait for approval.** Present a short plan (entities / migration / endpoints+roles /
   DTOs / tests) and wait before writing code. Don't scaffold the whole project; one vertical slice
   at a time.
2. **Split risky phases into independently-testable sub-steps** (as Phase 4 → 4a–4d and Phase 5 → 5a/5b/5c),
   approved one at a time.
3. **Never edit an applied migration.** Neon is at v9 — any schema change is a **new** `V10+` file. Editing
   an applied file breaks the Flyway checksum.
4. **One `./mvnw` build at a time.** Overlapping Maven runs corrupt `target/` (spurious
   `FileNotFoundException ...Test.class` / "wrong name" classloader errors). Clean build = one process.
5. **Apply new migrations to Neon after each phase** (boot with `local` profile, confirm Flyway reaches the
   expected version, run a quick live smoke test). Done through v9.
6. **Honor CLAUDE.md DO-NOTs / Rejected Proposals.** No new abstractions/interfaces beyond the 4 external
   clients; concrete services; ProblemDetail everywhere; derived data computed at runtime (exception:
   `last_message_at` is a deliberate perf denormalization, not business-derived); Testcontainers (no H2).
7. **Security at write-time** — `@PreAuthorize` on every controller; enforce ownership/membership in the
   service, never trust role or path alone.

---

## Migrations (V1–V10, all applied to Neon)

V1 baseline · V2 auth · V3 seed_admin · V4 coach_profile · V5 packages_subscriptions ·
V6 coach_availability · V7 sessions · V8 session_meet_link · V9 messaging · **V10 payments_autorenew**
(subscription auto-renew fields, `payments` ledger with UNIQUE idempotency_key, widened live-sub index).

## Critical-path tests (the ones to never break)
`AuthServiceTest` · `SubscriptionCapacityConcurrencyTest` · `SessionDoubleBookingConcurrencyTest` ·
`SessionQuotaBoundaryTest` · `SessionLifecycleTest` · `MessageServiceTest` / `MessageGateIntegrationTest`
(child-safety gate) · `WebSocketAuthTest` (STOMP auth) · **`SubscriptionRenewalConcurrencyTest`** /
**`SubscriptionBillingServiceTest`** (payment idempotency + auto-renew lifecycle).

---

## Phase 5c — Admin oversight (DONE)

**Shipped as designed** (see the as-built summary under "What each phase delivered → 5c"). The locked
decisions below were all honored; kept here as the rationale record.

- **Endpoints:** `GET /api/v1/admin/conversations` and `GET /api/v1/admin/conversations/{id}/messages`,
  **ADMIN-only** — reuse the Phase 2 admin security pattern (class-level `@PreAuthorize("hasRole('ADMIN')")`,
  same shape as `AdminCoachController`). Non-admin → **403**.
- **Separate service, no gate:** create a dedicated **`AdminConversationService`** that reads **directly
  from the repositories** — admin reads must **never** go through `MessageService`. Participants only ever
  touch `MessageService`; admins only ever touch `AdminConversationService`. This physical separation is a
  **child-safety requirement** — do **NOT** add `...ForAdmin` methods to `MessageService`.
- **Read-only and invisible:** admin reads must **not** touch `read_at`, `last_message_at`, or any
  membership state (an admin viewing a thread changes nothing). **This must be asserted in tests** — it's
  the most easily-missed Phase 5 invariant (e.g. snapshot `read_at`/`last_message_at` before the admin
  read, assert unchanged after).
- **List scope (narrow for beta):** list + order by `lastMessageAt desc`; each summary = participants
  (student name + coach name/university) + `lastMessageAt` + `messageCount`. **No filters yet.**
  - Pagination **mandatory**: `Pageable` (default 20, **max 100**), sort whitelist, wrap in
    `PageResponse<T>`. Avoid N+1 — use a projection / DTO query for the summary (incl. messageCount),
    don't loop per-conversation.
- **Deferred:** admin-access **audit log** is **Phase 9** (KVKK-relevant). Phase 5c ships without it — a
  deliberate, documented interim gap (admins can read threads with no access trail until Phase 9).

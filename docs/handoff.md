# Handoff — YKS Coaching Platform (Backend)

Single-file resume point for a fresh session. **Factual, based on the code as it exists now**
(not the v4 plan). For binding rules see `CLAUDE.md`; for phase intent see `PHASES.md`.

_Last updated: 2026-06-21._

---

## Status at a glance

- **Phases complete:** 0, 0.5, 1, 2, 3, 4 (4a–4d), 5a, 5b, 5c.
- **In progress / next:** 6 (real video — Jitsi fallback).
- **Tests:** `./mvnw verify` is **GREEN — 110 tests**.
- **Neon (prod DB):** Flyway at **v9** (all of V1–V9 applied live). Schema matches tests.
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
  pooled host with `-pooler`, credentials as `user`/`password` query params, `channelBinding=require`)
  and `JWT_SECRET`. Template is `application-local.yml.example`.
- **Admin seed (V3):** `admin@yks.local` / dev password **`admin1234`** (dev-only BCrypt hash baked
  into V3; prod overrides via `ADMIN_PASSWORD_HASH`). Use it to approve coaches in smoke tests.

---

## What each phase delivered

### Phase 0 — Setup
`BaseEntity` + `@EnableJpaAuditing`; scoped security; `GET /api/v1/health` → `{"status":"UP"}`;
Flyway V1 baseline; Testcontainers wired (real Postgres). `ddl-auto: validate` — Flyway owns schema.

### Phase 0.5 — Risk PoC (throwaway)
Findings: iyzico sandbox blocked (no creds → stub continues, real in Phase 8); Google Meet needs
Workspace → **Jitsi fallback** chosen for Phase 6.

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

---

## What's next

| Phase | Scope |
| --- | --- |
| **6** | Real video: implement `MeetClient` for real (Google Meet needs Workspace → **Jitsi fallback** per Phase 0.5). Replaces the stub. |
| **7** | Notifications: implement `MailClient` for real via **Resend**. Replaces the stub. |
| **8** | Payment lifecycle (**iyzico**): `Payment` entity, idempotency key UNIQUE, webhook status, refund = new row (`type=REFUND`, `source_payment_id`), commission snapshot. Subscription becomes **payment-gated** (no longer direct-activate). Refund-before-payout ordering. |
| **9** | KVKK / hardening: `ConsentRecord` (under-18), `Report` + user suspension (`User.status=SUSPENDED`), PII anonymization on delete. **Admin-access audit log** (log admin reads of minors' threads — KVKK; closes the 5c interim gap). Consider mid-session WS token-expiry enforcement here (tie to suspension). |

---

## Known gaps (intentional, scheduled)

- **Subscriptions activate WITHOUT payment.** 4a creates `Subscription` directly as ACTIVE. **Phase 8**
  introduces the iyzico-gated lifecycle. Until then there is no charge.
- **Mid-session WebSocket token expiry is not enforced** — a JWT is validated only at CONNECT; an
  already-open session is not force-closed when its access token later expires (client is expected to
  reconnect). Revisit in **Phase 9** alongside suspension (a suspended/expired user should be cut off).
- **iyzico sandbox approval still pending** (no creds). Stub `IyzicoClient` continues; real integration
  is Phase 8.
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
- **Repo is under active iCloud sync** — `~/Library/Mobile Documents/com~apple~CloudDocs/Desktop` is a
  symlink to `~/Desktop`, so `~/Desktop/demo` (incl. the real-secret `application-local.yml`) is syncing to
  iCloud now. This is the source of the `" 2"` conflict copies AND puts secrets in iCloud. **Action item:**
  move the repo to a non-synced path (e.g. `~/dev/demo`) **before** `git init`; re-create
  `application-local.yml` there from the `.example`; rotate the Neon password.
- **Not a git repository yet** — Phase 0–5c is unversioned. `.gitignore` already covers `target/` and
  `application-local.yml`, but is inert until `git init`. Before first commit, verify with `git status` that
  no `application-local.yml` is staged.
- **Neon password** was shared in plaintext during setup — rotate it before any real launch.

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

## Migrations (V1–V9, all applied to Neon)

V1 baseline · V2 auth · V3 seed_admin · V4 coach_profile · V5 packages_subscriptions ·
V6 coach_availability · V7 sessions · V8 session_meet_link · V9 messaging.

## Critical-path tests (the ones to never break)
`AuthServiceTest` · `SubscriptionCapacityConcurrencyTest` · `SessionDoubleBookingConcurrencyTest` ·
`SessionQuotaBoundaryTest` · `SessionLifecycleTest` · `MessageServiceTest` / `MessageGateIntegrationTest`
(child-safety gate) · `WebSocketAuthTest` (STOMP auth).

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

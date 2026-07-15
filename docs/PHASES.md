# YKS Coaching Platform — Development Phases

> For each phase: **Goal** (why), **Scope** (what you'll do), **Done when** (completion criteria), **Emphasize in prompt** (what to highlight when writing the Claude Code prompt).

---

## General rules (apply to every phase)

- **Vertical slice:** complete one feature end-to-end (Entity → Repository → Service → Controller → DTO → Mapper → validation → security → test → working), then move on.
- **Plan first:** present the phase plan to Claude Code before writing any code — wait for approval.
- **Stub-first:** external services (iyzico, Meet, mail, R2) behind interfaces; stub implementation first, real one in its own phase.
- **Transaction boundary:** no external calls inside a DB transaction; Meet link / email after commit.
- **One module at a time:** do not scaffold the entire project at once.

---

## Phase 0 — Setup & Skeleton (SB4)

**Goal:** A running, DB-connected, architecturally validated empty skeleton.

**Scope:**

- Initializr: Maven · Java 21 · Spring Boot 4.0.6 · Jar. Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Spring Security, Validation, Flyway, Lombok, OAuth2 Client, WebSocket, Actuator.
- Add missing deps to pom manually: `springdoc-openapi-starter-webmvc-ui 3.0.x`, MapStruct + annotation processor, jjwt, Testcontainers (postgresql + junit-jupiter). Pin all versions; fix until build is green.
- Set up layer-based package structure: `controller / service / repository / entity / dto / enums / exception / mapper / security / config / integration`.
- Neon Postgres connection (HikariCP + pooled connection string). `application.yml` with `default`, `local`, `stub`, `test` profiles. No secrets committed — read from env vars / gitignored `application-local.yml`.
- First Flyway migration (V1 baseline).
- Minimal Security config (permit all for now), global `@RestControllerAdvice` skeleton (ProblemDetail format — override framework exceptions too), Swagger up.
- **Single vertical slice:** a simple endpoint (health or minimal read) running controller → service → repository → DB — prove the wiring.
- One `@WebMvcTest` + one Testcontainers repository test — prove the test infrastructure.

**Done when:** App starts, connects to Neon, migration applies, Swagger opens, one endpoint works end-to-end, tests pass, backend verify is green.

**Emphasize in prompt:** *"We use SB 4.0.6 / Java 21 — do not suggest SB3 configs. Pin dependency versions SB4-compatible. Fix until build is green. Add `@AutoConfigureTestDatabase(replace = Replace.NONE)` on all `@DataJpaTest` tests."*

---

## Phase 0.5 — Risk PoC (iyzico + Meet) *(throwaway spike)*

**Goal:** Prove the two riskiest integrations are feasible BEFORE building anything on top of them.

**Scope:**

- iyzico sandbox: get test credentials, do one test charge, see the webhook.
- Google Meet: generate a link via Calendar API; find out whether a personal account works or Workspace is required.
- **Decide Plan B:** if Workspace is blocked, fallback = manual link field or Jitsi (the `MeetClient` interface makes the swap cheap).

**Done when:** You've personally seen a successful sandbox payment + a generated Meet link (or confirmed the fallback). Feasibility and cost are known.

**Emphasize in prompt:** *"This is a spike — do not integrate into the main project; throwaway code. The goal is just to see if it works."* Real integration happens in Phase 6 and 8 behind the interfaces.

---

## Phase 1 — Auth

**Goal:** Identity foundation — everything else leans on this; get security right from the start.

**Scope:**

- `User` + `RefreshToken` entity + migration. Password hashing (BCrypt).
- Registration (STUDENT / COACH; ADMIN not public), login (email + password), Google OAuth2.
- JWT access + rotating refresh (hashed, stored in DB). Spring Security 7 config, roles, `@PreAuthorize`.
- Edge case: same email arrives via both Google and password — decide account-linking behavior.

**Done when:** Register / login / token refresh working, role-based access enforced, visible in Swagger, happy-path + auth failure scenarios tested.

**Emphasize in prompt:** *"Security is never added later — apply `@PreAuthorize` the moment each controller is written. Refresh tokens are rotating and hashed."*

---

## Phase 2 — Coach & Profile

**Goal:** Coach profile + admin approval flow; student profile.

**Scope:**

- `CoachProfile`, `University`, `StudentProfile`, `CoachSubject` entities + migration.
- Coach applies → profile PENDING. Admin approves / rejects → APPROVED coach becomes bookable.
- `CoachSubject` join table: which tracks (NUMERICAL / EQUAL_WEIGHT / VERBAL / LANGUAGE) the coach mentors — used for search filtering.
- `active_student_count`, `max_student_capacity`, `payout_account_ready` fields.

**Done when:** Coach can create profile, admin approves, approved coaches are bookable, student profile exists, `CoachSubject` filtering works. Tested.

**Emphasize in prompt:** *"Approval flow: PENDING → APPROVED/REJECTED. Only APPROVED coaches appear in search and booking. Admin endpoints protected with ADMIN role. `CoachSubject` is the filter join table — no string field filter."*

---

## Phase 3 — Search (Coach Discovery)

**Goal:** Student can filter and find approved coaches.

**Scope:**

- APPROVED coaches only; filter by track (`CoachSubject`) / university / availability; paginated (`PageResponse<T>` wrapper, default size 20, sort whitelist).
- No price filter (uniform pricing). Derived fields (rating from `Review` — P1 so null/0 for now; total sessions from `Session`) computed at runtime, not stored.

**Done when:** Student lists and filters approved coaches with pagination. Tested.

**Emphasize in prompt:** *"Read-only. Derived data is not stored — compute at query time. Whitelist sort fields. Wrap `Page<T>` in `PageResponse<T>`."*

---

## Phase 4 — Availability & Booking *(most concurrency-sensitive phase)*

**Goal:** Coach posts slots, student reserves without double-booking; weekly quota enforced.

**Scope:**

- `CoachAvailability` (slot, `start_time` Instant/UTC, `UNIQUE(coach_profile_id, start_time)`).
- `Session` entity. Reservation: UNIQUE slot prevents double-booking; Session created, linked to Subscription.
- **Weekly quota:** calendar week (Mon–Sun, Europe/Istanbul); PLANNED + COMPLETED sessions that week vs `Package.weekly_sessions`.
- Cancellation rules: early (>24h) returns the quota; late cancellation / NO_SHOW burns it.
- **Transaction boundary:** Session + quota in one transaction (commit). Meet link + email after commit — but `MeetClient` / `MailClient` are still STUB in this phase (fake link / no-op).
- `Session.availability_id` UNIQUE constraint — DB-level double-booking guarantee.

**Done when:** Coach posts slots, student reserves without double-booking, quota enforced, cancellation rules work. Concurrency test (two simultaneous reservations for the same slot) included. Tested.

**Emphasize in prompt:** *"Double-booking protection via UNIQUE `availability_id`. External calls (Meet/mail) are STUB in this phase and called after commit — no external calls inside the transaction."*

---

## Phase 5 — Messaging (Realtime Chat) *(~1.5–2 weeks — do not rush)*

**Goal:** Student–coach persistent real-time chat with the child-safety message gate.

**Scope:**

- `Conversation` (`UNIQUE(student_user_id, coach_profile_id)`), `Message` (`content`, `read_at`, `attachment_url`).
- Spring WebSocket + STOMP. JWT in CONNECT frame; on expiry client reconnects with a fresh token.
- **Message gate (child-safety):** student may only message a coach they have an active or past Subscription with. No subscription = no message. (A free trial also creates a Subscription, so it opens the gate.)
- **Attachment policy (beta):** either closed, or student → coach question photos only + all attachments admin-visible + no free-form file sharing.

**Done when:** Messages persisted to DB, read receipts working, gate enforced server-side, attachment policy applied. Tested.

**Emphasize in prompt:** *"Enforce the message gate SERVER-SIDE — never trust the client. A Conversation cannot be opened without a subscription check. Restrict attachments per policy."*

---

## Phase 6 — Session (Real Meet Integration)

**Goal:** Replace the Phase 4 stub with the real `MeetClient`.

**Scope:**

- Real `MeetClient` implementation using Google Calendar / Meet API (replaces stub).
- Auto-generate a Meet link on booking (after commit), write it to `Session.meet_link`.
- Workspace requirement / Plan B fallback (decision from Phase 0.5) comes into play here.

**Done when:** Real Meet links generated for sessions, after commit. Tested with mocked `MeetClient`.

**Emphasize in prompt:** *"Only fill in the real `MeetClient` implementation — the call site (after-commit) is already wired from Phase 4. If it fails, log it; the session is not rolled back."*

---

## Phase 7 — Notifications (Real MailClient + In-app)

**Goal:** Replace the mail stub with the real implementation; send booking and re-purchase notifications.

**Scope:**

- Real `MailClient` via Resend (replaces stub).
- Emails: booking confirmation, reminder (reduce no-shows), **end-of-month re-purchase reminder** (critical — no auto-renew), consent / transaction notifications.
- Persist in-app notifications (`Notification` entity). All after commit.

**Done when:** Emails sent via Resend, in-app notifications persisted and working. Tested with mocked `MailClient`.

**Emphasize in prompt:** *"All notifications are after-commit. No auto-renew → end-of-month reminder is mandatory."*

---

## Phase 8 — Payment (iyzico — real) *(riskiest; PoC done in Phase 0.5)*

**Goal:** Real `IyzicoClient` with single-charge flow; subscription activation.

**Scope:**

- `Package`, `Subscription`, `Payment` entities + migration.
- Purchase flow: student picks coach + package → Subscription (PENDING_PAYMENT) → iyzico single charge (`idempotency_key`, idempotent) → webhook → Subscription ACTIVE.
- **Capacity:** on successful payment, atomic conditional update `active_student_count++` (WHERE active < max; 0 rows = full → CONFLICT).
- price snapshot, `payout_status`, commission snapshot (`commission_rate`, `commission_amount`, `coach_payout_amount`).
- Refund = new Payment row (`type=REFUND`, `source_payment_id` → original). Do not mutate existing row.
- Refund ↔ payout order: do not release payout before the refund window closes.

**Done when:** Student purchases, payment processed idempotently, subscription activates, capacity counter increments atomically. Idempotency + `IyzicoClient` mock tested.

**Emphasize in prompt:** *"Idempotency (same key twice = one charge), status via webhook, capacity via atomic conditional UPDATE, payout ONLY after refund window closes. Commission snapshot at transaction time."*

---

## Phase 9 — KVKK / Hardening *(launch blocker — cannot be deferred)*

**Goal:** Legal/ethical minimum + production hardening.

**Scope:**

- `ConsentRecord` (parental consent for under-18 — RECORDED even if collected offline), age check via `date_of_birth`.
- `Report` (complaints) + admin visibility; message retention + admin access policy.
- Privacy policy. Rate limiting (auth brute-force), Sentry, Actuator health checks.
- Input validation pass + error handling review; real `StorageClient` (R2) if not done yet.
- Ensure `User.status=SUSPENDED` works end-to-end (reported user can be suspended by admin).

**Done when:** KVKK minimum in place, reporting + admin visibility working, monitoring and hardening complete. Ready for closed beta (backend side).

**Emphasize in prompt:** *"KVKK minimum cannot be deferred. Reporting + admin visibility must be present in beta. Suspension flow must work end-to-end."*

---

## Phase 10 — Frontend Integration, Monorepo, & QA

**Goal:** Unify frontend and backend in a monorepo, implement end-to-end flows, and complete KVKK/consent validation.

**Scope:**
- **Monorepo Restructuring:** Separated project into `/backend` (Java 21, Spring Boot 4) and `/frontend` (React + Vite + TypeScript).
- **Date of Birth & Consent Validation:** Added `dateOfBirth` input to student registration. Gated minors (<18) by checking consent status (`PENDING`, `ACCEPTED`, `REVOKED`) via `GET /api/v1/consents/status`.
- **Enforcement Gates:** Implemented backend gates blocking minors without accepted consent from: checkout, bookings, starting conversations, and sending messages.
- **Admin Finance & Payout Seam:** Added refund/termination modals, webhook verification, and payout logging/ledger.

**Done when:** Both frontend and backend compile and build, and all integration tests compile and run.

---

## Reminder

**Monorepo layout:** `/backend` houses the Maven API, and `/frontend` houses the React + Vite + TypeScript client. Both must build cleanly for a feature to be considered complete.
# Handoff — YKS Coaching Platform (Backend)

## Dashboard Frontend API Contract (final hardening pass, 2026-08-09)

### Shared authentication, routing, and readiness

There is one login flow. After login (and on application reload), call `GET /api/v1/auth/me` and route only from `UserResponse.role`: `STUDENT` -> student dashboard, `COACH` -> coach dashboard, `ADMIN` -> admin dashboard. `UserResponse` contains `id`, `role`, `status`, `emailVerified`, `legalOnboardingCompleted`, `hasLocalPassword`, and `passwordChangedAt`; it intentionally contains no dashboard aggregates. A coach obtains approval/profile readiness from `GET /api/v1/coach/profile/me` (`CoachProfileResponse.status`). Student subscription state comes from dashboard/subscription data, never auth identity.

`emailVerified` proves email ownership; `legalOnboardingCompleted` proves current required onboarding acceptances. They are independent. Checkout, paid booking, trial creation, conversation creation, and message send apply both gates and return distinct `EMAIL_VERIFICATION_REQUIRED` or `LEGAL_ONBOARDING_REQUIRED` errors. Read-only dashboard/history endpoints remain available to an authenticated active account. The JWT filter blocks `SUSPENDED` and `DELETED` accounts with `USER_SUSPENDED` and `USER_DELETED` before controller execution.

All timestamps are ISO-8601 UTC `Instant` values. Calendar-month/week calculations use `Europe/Istanbul`; range filters are half-open `[from,to)`. Money is a JSON decimal backed by `BigDecimal`, in TRY. Paginated responses use `PageResponse<T>`: `{content,page,size,totalElements,totalPages,last}`; page size is capped at 100. Invalid ranges/sorts return `INVALID_DATE_RANGE`/`INVALID_SORT_FIELD`.

### Authorization matrix

| Surface | STUDENT | COACH | ADMIN |
| --- | --- | --- | --- |
| `/api/v1/students/**`, `/sessions/**`, `/subscriptions/**`, `/trial-consultations/**` | self only | denied | denied |
| `/api/v1/coaches/**` discovery | approved public coach data | denied | read-only discovery allowed |
| `/api/v1/coach/**` | denied | principal-derived self resources only | denied |
| `/api/v1/conversations/**` | membership + relationship gates | membership + relationship gates | denied; uses separately audited admin surface |
| `/api/v1/admin/**` | denied | denied | allowed; operational management, never impersonation |

No self endpoint accepts a role or owner ID from the client. A coach/student resource ID is always checked against the authenticated principal. Cross-owner lookups deliberately use not-found responses where appropriate to avoid leaking resource existence.

### Student endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/students/me/dashboard` | STUDENT | — | `StudentDashboardResponse` (`user`, latest `DashboardSubscription|null`, latest `DashboardPayment|null`) | `USER_NOT_FOUND` |
| `GET /api/v1/subscriptions/me` | STUDENT | — | `List<SubscriptionResponse>` | — |
| `POST /api/v1/subscriptions/checkout` | STUDENT | `SubscriptionCheckoutRequest` | `SubscriptionCheckoutResponse` | readiness errors, `COACH_NOT_APPROVED`, `ALREADY_SUBSCRIBED`, checkout legal errors |
| `POST /api/v1/subscriptions/{id}/cancel` | STUDENT/self | path ID | `SubscriptionResponse` | `SUBSCRIPTION_NOT_FOUND`, invalid state |
| `GET /api/v1/sessions/me` | STUDENT | — | `List<SessionResponse>`; newest start first | — |
| `POST /api/v1/sessions` | STUDENT | `availabilityId` | `SessionResponse` | readiness errors, `COACH_NOT_APPROVED`, `NO_ACTIVE_SUBSCRIPTION`, `QUOTA_EXCEEDED`, `SLOT_TAKEN`, `SLOT_IN_PAST` |
| `POST /api/v1/sessions/{id}/cancel` | STUDENT/self | path ID | `SessionResponse` | `SESSION_NOT_FOUND`, invalid state |
| `GET /api/v1/coaches` | STUDENT/ADMIN | `track`, `universityId`, `q`, pageable; default newest | `PageResponse<CoachSummaryResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/coaches/{id}` | STUDENT/ADMIN | coach profile ID | `CoachDetailResponse` | `COACH_NOT_FOUND` |
| `GET /api/v1/coaches/{id}/availability` | STUDENT/ADMIN | coach profile ID | future open `List<AvailabilityResponse>` | `COACH_NOT_FOUND` |
| `POST /api/v1/trial-consultations` | STUDENT | `availabilityId` | `TrialConsultationResponse` | readiness errors, `COACH_NOT_AVAILABLE`, `TRIAL_ALREADY_EXISTS`, `SLOT_TAKEN`, `SLOT_IN_PAST` |
| `GET /api/v1/trial-consultations/me` | STUDENT | — | `List<TrialConsultationResponse>`; newest start first | — |
| `POST /api/v1/trial-consultations/{id}/cancel` | STUDENT/self | path ID | `TrialConsultationResponse` | `TRIAL_NOT_FOUND`, `INVALID_TRIAL_STATE` |
| `POST /api/v1/conversations` | STUDENT | `coachId` | `ConversationResponse` | readiness errors, relationship/access errors |
| `GET /api/v1/conversations` | STUDENT/COACH | — | `List<ConversationResponse>`; latest activity first | membership/access errors |
| `GET /api/v1/conversations/{id}/messages` | STUDENT/COACH/member | pageable, default size 30 | `PageResponse<MessageResponse>` | membership/access errors |
| `POST /api/v1/conversations/{id}/messages` | STUDENT/COACH/member | `content` | `MessageResponse` | readiness errors, active-message relationship errors |

The nullable dashboard subscription/payment pair is the stable no-subscription state. Detailed sessions and conversations stay in their dedicated endpoints. `ACTIVE` and `PAST_DUE` are live booking/subscription relationships under the existing grace rule; `EXPIRED` and `CANCELLED` retain messaging history but cannot send or book.

### Coach endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/coach/dashboard/summary` | COACH/self | — | `CoachDashboardSummaryResponse` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/students` | COACH/self | `status=ACTIVE|HISTORICAL|ALL`, pageable; default newest + ID tie-break | `PageResponse<CoachStudentResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/coach/calendar/sessions` | COACH/self | `from`, `to`, `status`, `studentId`, pageable; default nearest + ID tie-break | `PageResponse<SessionResponse>` | `INVALID_DATE_RANGE`, `INVALID_SORT_FIELD` |
| `GET /api/v1/coach/conversations/summary` | COACH/self | — | `List<CoachConversationSummaryResponse>` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/dashboard/packages` | COACH/self | — | `List<CoachPackageSummaryResponse>` | `PROFILE_NOT_FOUND` |
| `GET /api/v1/coach/sessions` | COACH/self | — | `List<SessionResponse>` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/sessions/{id}/complete` / `no-show` | COACH/owner | path ID | `SessionResponse` | `SESSION_NOT_FOUND`, invalid state |
| `GET /api/v1/coach/trial-consultations` | COACH/self | — | `List<TrialConsultationResponse>` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/trial-consultations/{id}/{confirm|cancel|complete|no-show}` | COACH/owner | path ID | `TrialConsultationResponse` | `TRIAL_NOT_FOUND`, `INVALID_TRIAL_STATE`, `TRIAL_NOT_STARTED` |
| `GET /api/v1/coach/availability` | COACH/self | — | `List<AvailabilityResponse>`; earliest first | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/availability` | COACH/self | `AvailabilityCreateRequest` | `AvailabilityResponse` | `COACH_NOT_APPROVED`, `SLOT_OVERLAP`, `SLOT_DUPLICATE`, range errors |
| `DELETE /api/v1/coach/availability/{id}` | COACH/owner | path ID | 204 | `SLOT_NOT_FOUND`, `SLOT_BOOKED` |
| `GET /api/v1/coach/profile/me` | COACH/self | — | `CoachProfileResponse`, including approval `status` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/coach/profile`, `PUT /api/v1/coach/profile/me` | COACH/self | profile request DTO | `CoachProfileResponse` | validation/ownership errors |

Summary definitions: active students are distinct students on `ACTIVE|PAST_DUE`; completed-this-month uses Istanbul month boundaries; upcoming sessions are future `PLANNED` paid sessions; unread count is messages addressed to the coach with null `readAt`; availability configured means at least one future unbooked slot; pending trials are `REQUESTED`. `grossSales` is lifetime successful `CHARGE` volume attributable to the coach/package, not payout, earnings, or profit. Coaches cannot mutate package prices.

### Admin endpoints

| Method and path | Role | Query/body | Response | Important errors |
| --- | --- | --- | --- | --- |
| `GET /api/v1/admin/dashboard/summary` | ADMIN | — | `AdminDashboardSummaryResponse` | `ACCESS_DENIED` for non-admin |
| `GET /api/v1/admin/users` | ADMIN | `role`, `status`, `search`, pageable | `PageResponse<AdminUserDirectoryResponse>` | `INVALID_SORT_FIELD` |
| `POST /api/v1/admin/users/{id}/suspend|unsuspend` | ADMIN | optional reason | `SuspendResponse` | deleted/admin protections, invalid state |
| `GET /api/v1/admin/coaches` | ADMIN | `status=PENDING|APPROVED|REJECTED|SUSPENDED|ALL`, `search`, pageable | `PageResponse<AdminCoachDirectoryResponse>` | `INVALID_SORT_FIELD` |
| `GET /api/v1/admin/coaches/{id}` | ADMIN | profile ID | `AdminCoachDirectoryResponse` | `PROFILE_NOT_FOUND` |
| `POST /api/v1/admin/coaches/{id}/approve|reject` | ADMIN | reject reason where required | `CoachProfileResponse` | deleted/suspended protections, invalid state |
| `GET /api/v1/admin/subscriptions` | ADMIN | `status`, `studentId`, `coachId`, `packageId`, `from`, `to`, pageable | `PageResponse<AdminSubscriptionResponse>` | range/sort errors |
| `POST /api/v1/admin/subscriptions/{id}/terminate` | ADMIN | optional reason | `AdminSubscriptionTerminateResponse` | not found/invalid state |
| `GET /api/v1/admin/payments` | ADMIN | `type`, `status`, relationship/package/date filters, pageable | `PageResponse<AdminPaymentResponse>` | range/sort errors |
| `GET /api/v1/admin/finance/summary` | ADMIN | optional `from`, `to` | `AdminFinanceSummaryResponse` | `INVALID_DATE_RANGE` |
| `POST /api/v1/admin/payments/{paymentId}/refund` | ADMIN | amount, reason | `RefundResponse` | provider/stub and refundable-headroom errors |
| `GET /api/v1/admin/refunds` | ADMIN | status/relationship/package/date filters, pageable | refund `PageResponse<AdminPaymentResponse>` | range/sort errors |
| `GET /api/v1/admin/reports` | ADMIN | `status`, pageable | `PageResponse<ReportResponse>` | `INVALID_SORT_FIELD` |
| `PATCH /api/v1/admin/reports/{id}/status` | ADMIN | target status | `ReportResponse` | not found/invalid transition |
| `GET /api/v1/admin/sessions` | ADMIN | `type=ALL|PAID|TRIAL`, `status`, IDs, `from`, `to`, pageable | `PageResponse<AdminOperationalSessionResponse>` | `INVALID_SESSION_STATUS`, range/sort errors |

KPI definitions: students exclude `DELETED`; active coaches are `APPROVED` profiles with `ACTIVE` users; pending approvals are `PENDING` profiles; active subscriptions are `ACTIVE|PAST_DUE`; monthly sales/revenue are successful `CHARGE` ledger rows; monthly refunds are successful `REFUND` rows; open reports are `OPEN|REVIEWED`; scheduled sessions are future `PLANNED` paid sessions; completed-this-month counts paid `COMPLETED` sessions. The operational paid/trial union is read-only. Admin conversation content remains on the separate reason/audit-gated `/api/v1/admin/conversations` surface.

### Paid sessions, trials, and known limits

Paid sessions require a live subscription and consume weekly quota in `PLANNED|COMPLETED|LATE_CANCELLED|NO_SHOW`; early `CANCELLED` does not consume quota. Trials require no subscription/payment, consume no quota, and do not create a paid messaging relationship. Both reserve the same concrete availability under a pessimistic lock; availability ranges cannot overlap per coach, and cancellation reopens an eligible slot. No Flyway migration was added in this pass; schema remains V1-V24.

Remaining bounded-list risks: student/coach trial histories, legacy `/sessions/me` and `/coach/sessions`, conversation inboxes, and own availability are unpaginated compatibility contracts. Migrate them only with an explicit frontend versioning plan. The refund service-commencement eligibility rule remains unresolved; the current admin refund endpoint invokes the configured real/stub provider behavior and writes refund ledger rows without inventing that legal rule.

## Admin Dashboard backend foundation

- Admin uses the same shared login system and the authenticated `ADMIN` role; admin registration remains unavailable. Frontend role routing is not a separate authentication system.
- `GET /api/v1/admin/dashboard/summary` exposes repository-backed KPIs. Student count excludes `DELETED`; active coaches are `APPROVED` profiles whose user is `ACTIVE`; active subscriptions are `ACTIVE` or `PAST_DUE`; open reports are `OPEN` or `REVIEWED`.
- Monthly sales/revenue/refund/completed-session metrics use Europe/Istanbul calendar boundaries. Gross revenue is successful `CHARGE` ledger volume; refunds are successful `REFUND` rows; neither is profit. `netCollectedAmount` is gross successful charges minus successful refunds.
- Coach directory supports `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`, and `ALL`, repository-side search and pagination. Approval now rejects suspended/deleted identities. User directory supports role/status/search filters without password, token, verification-hash, reset evidence, or legal-document content.
- Admin suspend behavior and notification remain unchanged. `POST /api/v1/admin/users/{id}/unsuspend` restores only `SUSPENDED` non-admin users; deleted identities cannot be restored.
- Existing subscription/payment read models now support status, relationship, package, date, and pagination filters. Finance summaries use ledger rows only; no coach payout, commission settlement, or profit calculation is exposed.
- Existing immediate provider refund endpoint remains the source of truth. `GET /api/v1/admin/refunds` lists refund ledger attempts/completions. No request-approval domain was invented because service-commencement eligibility remains a product/legal decision and the current refund action calls the configured provider directly.
- Existing report moderation and best-effort safety emails are reused. Admin message access was not broadened; the existing reason/audit-gated conversation oversight remains unchanged.
- `GET /api/v1/admin/sessions` provides a paginated read-only union of paid sessions and trial consultations. Admin receives no scheduling mutation endpoint.
- No migration was added; this slice is query/read-model functionality over migrations through V24.

## Coach Dashboard backend foundation (V24)

- Authentication remains one shared login system. The authenticated `role` drives frontend routing to the student, coach, or admin dashboard; there is no separate coach login.
- Coach-self endpoints resolve the coach from the JWT principal and are `COACH`-only: dashboard summary, paginated students, filtered calendar reads, conversation summaries, package/activity summaries, own availability, own profile, and trial-consultation management.
- An active student is a distinct student with an `ACTIVE` or `PAST_DUE` subscription to the coach. The student list separates `ACTIVE`, `HISTORICAL`, and `ALL`; weekly usage follows the existing Europe/Istanbul quota calculation and existing quota-consuming session statuses.
- Paid sessions still require a live subscription and weekly quota. Trial consultations are separate persisted records and never create a subscription/payment, consume quota, or unlock messaging.
- Public trial availability reuses the coach's explicit future, unbooked availability slots. A pessimistic lock on the availability row serializes paid and trial reservation; cancellation unlinks and reopens the slot.
- MVP trial rule: at most one non-cancelled trial per student/coach. The lifecycle is `REQUESTED -> CONFIRMED -> COMPLETED|NO_SHOW`, with `REQUESTED|CONFIRMED -> CANCELLED`.
- Coach package prices remain platform-controlled. Package summaries report current subscriptions and attributable successful-charge `grossSales`; no payout/earnings capability is implemented.
- `V24__trial_consultations.sql` adds the trial table, status check, ownership/time indexes, optimistic version, unique slot reference, and partial unique index enforcing the MVP student/coach rule.

Single-file resume point for a fresh session. **Factual, based on the code as it exists now**
(not the v4 plan). For binding rules see `CLAUDE.md`; for phase intent see `PHASES.md`.

_Last updated: 2026-06-27._

## Frontend legal/compliance integration

- Registration loads the current `TERMS_OF_USE`, `EXPLICIT_CONSENT`, and `KVKK_NOTICE`. It sends the two required accepted document IDs plus independent optional email/SMS marketing choices. The KVKK notice is a link, not a separate acceptance checkbox.
- Google OAuth returns to `/oauth/callback`, exchanges its one-time code, and sends users whose `legalOnboardingCompleted` is false to `/legal-onboarding`. Product routes remain unavailable until the current Terms and Explicit Consent are accepted.
- Subscription checkout loads and displays the current pre-information form, distance-sales agreement, and refund/cancellation policy. One required checkbox sends all three current document IDs with `legalDocumentsAccepted: true`.
- `/privacy` exposes authenticated marketing and cookie preferences, Explicit Consent withdrawal/re-onboarding, account-deletion status, and confirmed account deletion.
- The legacy frontend guardian/minor KVKK modal and guardian-required copy were retired. Date of birth remains in student registration because the backend still requires it.
- Legal content is rendered as plain text from the backend. Seeded backend legal text remains placeholder content and is not production-approved.
- Authenticated cookie preferences are synchronized with the backend. Anonymous-visitor cookie preference/banner behavior remains browser/frontend work and is not implemented by this integration.
- Frontend component testing is now configured with Vitest, jsdom, and Testing Library; `npm test` covers the core registration, OAuth/onboarding, checkout, withdrawal, preference, and deletion behaviors.
- The broader visual redesign remains separate from this focused integration.

---

## Status at a glance

- **Phases complete:** 0, 0.5, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 (Full Backend + Frontend workflows complete, rate limiting security layer integrated, except payout which is only prepared as an ADR).
- **In progress / next:** Closed beta QA, production infrastructure provisioning, and legal/tax model finalization.
- **Tests:** Backend verify is **GREEN — 85+ unit tests** (including rate limit bounds, concurrent subscription renewals and minor consent rules). Testcontainers are compiled but bypassed locally due to the absence of a local Docker daemon.
- **Neon (prod DB):** Flyway at **v17** (all V1–V17 applied. V17 = date of birth and consent status).
- **Build:** Java 21 + Spring Boot 4 (backend); React + Vite + TypeScript (frontend).

### Stack (as wired)
Monorepo:
- **Backend:** Spring Boot 4.0.6, Spring Security 7 (stateless JWT + Google OAuth2), Spring Data JPA/Hibernate, Flyway, PostgreSQL, WebSockets/STOMP, Jackson, Testcontainers.
- **Frontend:** React + Vite + TypeScript, client routing, state provider, Tailwind/Vanilla CSS.

---

## How to run / test

```bash
# Tests (Colima/Docker MUST be running — Testcontainers spins real Postgres 18; no H2).
# Run from the backend directory:
cd backend
.\mvnw.cmd verify                  # on Windows
./mvnw verify                      # on macOS/Linux
.\mvnw.cmd test -Dtest=SomeTest    # run a single test class

# Run against Neon locally (needs the gitignored application-local.yml — see below):
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
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
  **REJECTED resubmission:** a REJECTED coach can call `createOwn` again — the existing profile is
  updated in-place (fields + tracks), status reset to PENDING, rejectionReason cleared. PENDING or
  APPROVED profiles still throw `PROFILE_ALREADY_EXISTS` (409). No duplicate rows created.

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
  only, requires APPROVED coach status); `GET /api/v1/coaches/{id}/availability` (STUDENT/ADMIN, open future slots of APPROVED coach).
- Invariants: reject past slot (`SLOT_IN_PAST`), end≤start (`INVALID_SLOT_RANGE`), and non-approved coach (`COACH_NOT_APPROVED` 403) — validated in the
  service (unit-testable); duplicate start → `SLOT_DUPLICATE`; **overlapping time ranges** →
  `SLOT_OVERLAP` (409, service-level JPQL check: `existing.startTime < newEndTime AND
  existing.endTime > newStartTime`). Adjacent slots (end == start) are allowed. DB-level exclusion
  constraint (`btree_gist`) deferred — service guard sufficient for single-instance MVP.

**4c — Booking / Session (V7).**
- `Session` (student, coachProfile, subscription, **availability nullable+UNIQUE**, status
  PLANNED/COMPLETED/CANCELLED/LATE_CANCELLED/NO_SHOW, start/end snapshotted off the slot).
- Endpoints: `POST /api/v1/sessions` + `GET /me` (STUDENT); `GET /api/v1/coach/sessions` (COACH).
- **Invariants:**
  - **Double-booking guarantee = UNIQUE(`sessions.availability_id`) at INSERT** (saveAndFlush + catch →
    `SLOT_TAKEN`). Never read `is_booked` to decide; `is_booked` is a UI flag flipped after the guard.
  - **Approved Coach Gate:** The coach associated with the availability slot must be in the `APPROVED` status, otherwise booking is rejected with `COACH_NOT_APPROVED` (403).
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
  - **Split Messaging Access Gates (Server-side)**:
    - **History Access Gate**: Allowed for students with status `ACTIVE`, `PAST_DUE`, `EXPIRED`, or `CANCELLED`. If a newer attempt is `PENDING_PAYMENT` or `FAILED`, but an older valid subscription exists, history access is granted. Open/read/history paths use this gate.
    - **Send Gate**: Allowed strictly only for students with `ACTIVE` subscription status. Blocked for all other statuses. Send path uses this gate.
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

### Safety & Moderation Notification Emails (Phase 10 / fix/safety-notification-emails)

- **What:** Added transactional email notifications for report, admin status update, and suspension events via the best-effort `MailClient` Resend integration.
- **Rules:**
  - **Report creation:** Confirms to the reporter that their report was received. Intentionally does **not** email the reported user (anti-harassment check).
  - **Report status moderation:** Emails the reporter on a successful transition of report status to `REVIEWED`, `RESOLVED`, or `DISMISSED`. No email sent on invalid transitions or same-status no-ops.
  - **User suspension:** Emails the suspended user to notify them that their account access has been restricted. Stays quiet if target user is not found or suspension fails (e.g. self-suspend check).
- **Execution:** Mail dispatch happens synchronously at the controller level immediately after service-level `@Transactional` write-tx commits. Caught-and-swallowed inside a double-backstop try-catch block so mail-server failures/timeouts **never** block the API response or roll back database records. SMS and unsuspend flows remain future-phase/deferred.
- **Tests:** Added 10 controller/service tests ensuring best-effort exception swallowing, mail-client method targeting, DTO mail metadata propagation, and state-transition gate checks.

---


## What's next

| Phase / Slice | Scope |
| --- | --- |
| **KVKK & Consent** | ✅ **DONE** (Phases 7-8). Implemented `dateOfBirth` validation, minor age checks (under 18), `ConsentStatus` tracking (`PENDING`, `ACCEPTED`, `REVOKED`), parental consent modal frontend, and backend enforcement gates. |
| **Admin Oversight & Safety** | ✅ **DONE**. Created safety reports, user suspension dashboard, and admin conversation inspection audit logger (`AdminConversationAccessLog`). Also implemented admin safety report status moderation flow (`PATCH /api/v1/admin/reports/{reportId}/status` to transition report status, tracking reviewedBy/reviewedAt) and the duplicate open report safety guard (`DUPLICATE_OPEN_REPORT`). |
| **Admin Finance & Payout Seam** | ✅ **DONE** (Phases 9-10). Created refund/termination modal popups, webhook signature validation, idempotency guards, and model-neutral payout ledger seam (`PayoutService` and `PayoutLedger`). |
| **Auth Rate Limiting** | ✅ **DONE** (Stabilization). Implemented Redis-compatible transient state rate limiting behind `RateLimitStore` with clean in-memory fallback. Protects `/auth/login`, `/auth/register`, and `/auth/refresh` against brute-force/abuse. |
| **Future / Launch Blockers** | Real money transactions production configuration, legal review of auto-renew/refund policies, final production DB seeding, and domain registration. |

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
### Known gaps (intentional, scheduled)

- **Fund distribution / payout to coaches is NOT implemented:** No database schema or production code exists. Only architectural recommendations are prepared in `docs/adr_coach_payout_architecture.md`.
- **Mid-session WebSocket token expiry is not enforced** — a JWT is validated only at CONNECT; an already-open session is not force-closed when its access token later expires.
- **Refresh Token Reuse/Theft Detection is deferred for MVP:** Refresh tokens are stored hashed and rotated on use, but full theft/reuse detection (i.e. revoking the entire token family/session if a revoked token is reused) is not implemented.
- **Google OAuth2 not live-tested** (no Workspace/creds); login wiring exists but is inert without creds.
- **Reviews don't exist** → `CoachStats.rating` is always null (totalSessions is real).
- **Tax/Billing details:** The exact choice of Split Payout vs Single Platform Payout is pending the project accountant's feedback.
- **Admin oversight reads currently leave no audit trail** — Phase 5c ships ADMIN conversation/message reads
  with no access log. A deliberate, documented interim gap; the **admin-access audit log (KVKK)** closes it in
  **Phase 9**.
- **5c integration tests are ID-scoped but not isolated** — `AdminConversationIntegrationTest` asserts on
  specific seeded conversation ids (not page-wide totals), but the shared, non-rollback `@SpringBootTest`
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

1. **Feature Branch Workflow:** Always work on a feature branch (e.g., `feature/post-merge-completion`). Never commit directly to `main`.
2. **Compile and Test BEFORE Pull Request:** Compile both backend and frontend, run tests locally, fix all compiler and test errors, and then submit a PR to `main`.
3. **Never edit applied migrations:** Applied Flyway migrations (like V14, V15, V16) are permanently locked on Neon DB. Any schema change must be a fresh migration (e.g. V17, V18).
4. **Plan first, wait for approval:** Present a short plan and wait before starting execution.
5. **Honor CLAUDE.md guidelines:** Concrete services, standard ProblemDetail formatting, atomic capacity limits, and Testcontainers.

---

## Migrations (V1–V24)

V1 baseline · V2 auth · V3 seed_admin · V4 coach_profile · V5 packages_subscriptions · V6 coach_availability · V7 sessions · V8 session_meet_link · V9 messaging · V10 payments_autorenew · V11 webhook_verifications · V12-V13 safety/admin · V14-V16 demo seed · V17 legacy minor-consent status · V18 OAuth login codes · V19 versioned legal documents and acceptances · V20 checkout legal documents and transaction-linked evidence · V21 privacy preferences and account deletion · V22 password recovery/security · V23 email verification.

## Versioned registration legal acceptance

- `KVKK_NOTICE` (Aydınlatma Metni) and `EXPLICIT_CONSENT` (Açık Rıza) are distinct. KVKK Notice is publicly readable and has no mandatory acceptance checkbox.
- Registration requires current published `TERMS_OF_USE` and `EXPLICIT_CONSENT` document IDs. Marketing email and SMS preferences are separate, optional opt-ins.
- No guardian account or guardian verification exists. `date_of_birth` and legacy `consent_records` remain for compatibility/audit, but age or a revoked legacy record no longer creates a separate checkout, booking, conversation, or message gate.
- Existing users are backfilled as legally onboarded to avoid lockout. Newly created Google OAuth users are not auto-accepted and use `POST /api/v1/auth/legal-onboarding` before protected product actions.
- V19 seeds clearly labelled placeholder version `1.0` documents. Approved legal wording must replace them before production reliance.

## Checkout legal acceptance

- The UI presents one mandatory checkbox covering the current Ön Bilgilendirme Formu, Mesafeli Satış Sözleşmesi, and İade / İptal Politikası.
- The backend validates all three submitted current document IDs and creates three separate immutable acceptance records. Every record snapshots document type, version, content hash, and acceptance time.
- Checkout acceptance evidence is linked to both the reserved subscription and its initial payment attempt. The database permits one active evidence row per checkout/document and keeps registration/onboarding uniqueness separate.
- Package, coach, duplicate-subscription, user, and legal-onboarding checks run before checkout-document validation. Subscription/payment/evidence commit together; Iyzico initialization remains outside the transaction and is never called after legal validation failure.
- V20 content is explicit placeholder text pending approved legal wording.
- Refund eligibility and the technical event defining service commencement remain unresolved and unchanged. This work adds no automatic refund decision or coach-transfer behavior.

## Privacy preferences, consent withdrawal, and account deletion

- Marketing email and SMS permissions are optional, explicit, channel-specific states. Registration/onboarding opt-ins now persist through `marketing_preferences`; existing users and omitted flags default to not granted. Transactional service, payment, booking, subscription, security, and moderation emails do not consult marketing preferences.
- Authenticated cookie preferences are stored in `privacy_preferences`. Necessary storage is always enabled, while analytics and marketing default to false and require the current effective published `COOKIE_POLICY`. Anonymous visitor choices remain a frontend/browser-local concern; no anonymous tracking identifier was introduced.
- `POST /api/v1/privacy/explicit-consent/withdraw` timestamps active Explicit Consent evidence and sets `legalOnboardingCompleted=false`. Terms and KVKK Notice are untouched. Checkout, booking, conversation creation, and message sending retain their existing legal-onboarding gate; the existing onboarding API can record the current consent again.
- Account deletion is a synchronous controlled workflow: it records a deletion request, sets `UserStatus.DELETED`, revokes refresh tokens and pending OAuth login codes, unlinks Google identity, clears profile PII and optional preferences, and replaces account identity with a unique non-original placeholder. Deleted identity hashes prevent password/Google recreation with the same identity.
- The main user, messages, reports, subscriptions, payments/refunds, legal acceptances, checkout evidence, and audit/security records are retained for integrity and dispute/accounting needs. Exact statutory retention periods—including message retention—and operational deletion of external storage objects remain production legal-policy TODOs.
- Suspended users cannot currently self-request deletion because the JWT filter blocks all suspended-account API access; an admin/support remediation path is still needed. Refund/service-commencement and coach-transfer rules are unchanged.

## Critical-path tests (the ones to never break)
`AuthServiceTest` · `SubscriptionCapacityConcurrencyTest` · `SessionDoubleBookingConcurrencyTest` · `SessionQuotaBoundaryTest` · `SessionLifecycleTest` · `MessageServiceTest` / `MessageGateIntegrationTest` (child-safety gate) · `WebSocketAuthTest` · `SubscriptionRenewalConcurrencyTest` · `SubscriptionBillingServiceTest` · `MinorConsentServiceTest` · `InMemoryRateLimitStoreTest` · `AuthRateLimitServiceTest` · `AuthControllerRateLimitTest`.

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

---

## Rate Limiting & Transient State

- **Endpoints protected:**
  - `POST /api/v1/auth/login` (IP limit: 20/min, Email limit: 5/min)
  - `POST /api/v1/auth/register` (IP limit: 5/min)
  - `POST /api/v1/auth/refresh` (IP limit: 30/min, Token fingerprint limit: 30/min)
- **Response behavior:** Breaches yield `HTTP 429 Too Many Requests` with a ProblemDetail JSON carrying `errorCode: RATE_LIMIT_EXCEEDED` and a standard `Retry-After: <seconds>` header.
- **Client IP Resolution:** Defaulting to `request.getRemoteAddr()`. Supports reverse proxies (e.g. Railway) via `app.rate-limit.trust-proxy-headers=true`, parsing the first IP of `X-Forwarded-For`.
- **Sensitive data privacy:** Key identifiers are formatted to prevent sensitive data leaks. IP addresses are hashed using SHA-256; login emails are normalized (trim, lowercase) and hashed; refresh tokens are hashed to create unique fingerprints.
- **Transient state architecture:**
  - Transient state (such as rate limits) is decoupled behind the `RateLimitStore` interface.
  - The current production-ready MVP uses `InMemoryRateLimitStore` (ConcurrentHashMap in JVM memory), which automatically runs background cleanups on expired buckets.
  - **In-memory/per-instance constraint:** Since the state is in JVM memory, it is scoped per application instance. If the backend scales to 2+ instances, key counters won't be shared across nodes unless a shared store is introduced.
- **Redis Migration Path:**
  - Future Redis migration requires zero code changes to controllers or business services.
  - A developer simply needs to add the Spring Data Redis dependency, define `RedisRateLimitStore implements RateLimitStore`, and declare it as a `@Bean` to override the memory implementation.
  - Hashing rules, env-prefixes (`yks:{env}:rate-limit:...`), and window algorithms are already fully Redis-compatible (mapped directly to `INCR` + `EXPIRE` commands or custom Lua scripts).
# Password recovery and security (V22)

- Password-backed accounts can change their password at `/security`; changes are limited to once every 15 days.
- Google-only accounts have no local password and neither see nor receive a local-password reset flow.
- Forgot-password responses are intentionally generic. Eligible accounts receive a 30-minute, single-use link; only its SHA-256 hash is persisted and passwords are never emailed.
- Successful change/reset revokes every refresh token and increments the JWT `passwordVersion`, so older access tokens are rejected. Change requires re-login; reset never auto-logs in.
- Production requires `FRONTEND_BASE_URL`, `RESEND_API_KEY`, and `RESEND_FROM`. Reset mail is transactional and independent of marketing preferences.

## Email ownership verification (V23)

- Normal password registration creates an authenticated but limited account with `emailVerified=false`, issues a cryptographically random 6-digit code, stores only its BCrypt hash, and sends it after transaction commit. Codes are single-use and expire after 10 minutes.
- `POST /api/v1/auth/verify-email` and `POST /api/v1/auth/resend-verification` resolve the authenticated user rather than accepting a user ID. Resend has a persisted 60-second cooldown plus per-user/IP hourly rate limits; a new code invalidates older codes.
- Google sign-in trusts the actual Google `email_verified` claim. A verified Google email sets `emailVerified=true`, skips verification mail, and remains independently subject to legal onboarding.
- Email verification and `legalOnboardingCompleted` are separate readiness flags. Checkout, booking/session creation, conversation creation, and message sending require both; failures remain distinct as `EMAIL_VERIFICATION_REQUIRED` and `LEGAL_ONBOARDING_REQUIRED`.
- Frontend route priority is `/verify-email`, then `/legal-onboarding`, then the role home. `/privacy`, `/security`, verification, onboarding, and session-management auth calls remain reachable for limited accounts.
- Verification mail uses the existing best-effort Resend/stub transport and ignores marketing opt-in state. Production `RESEND_API_KEY`, verified sender/domain, and deployment configuration remain external work.

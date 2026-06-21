# YKS Coaching Platform — Project Scope & Architecture Decision Document (v4)

> **v4 changes (from the data-model hardening round):**
>
> - Shared `BaseEntity` (`created_at` / `updated_at` / `version` — audit + optimistic locking) on every entity
> - `User.status` (ACTIVE / SUSPENDED / DELETED) — user suspension + soft-delete
> - `Notification` entity added (in-app notification persistence — was missing in v3)
> - Report workflow fields (`reviewing_admin_id`, `resolution_note`, `resolved_at`)
> - Payment is now a **ledger**: `type` (CHARGE/REFUND) + `source_payment_id` + commission snapshot fields
> - UNIQUE on `Session.availability_id` (double-booking guaranteed at the DB level)
> - `CoachSubject` junction table (track-level coach-subject filter; replaces the `yks_turu` string filter)
> - Review integrity: subscription link + `UNIQUE(coach+student)` (P1)
> - `University.is_active` flag; money fields `numeric(12,2)`/`BigDecimal`; no hard-delete
> - Rejected proposals documented in §14 (coach pricing, escrow) — so they aren't mistaken for a "gap" again
>
> **Carried-over v3/v2 decisions:** interface exception for 4 external services · transaction-boundary rule · child-safety (message gate + file attachment) · one-off monthly purchase · parent panel deferred (KVKK minimum kept) · Java 21 + Spring Boot 4 (springdoc 3.x) · estimate 9–12 weeks.

---

## 1. Project Summary

A web platform that connects students preparing for the YKS exam with university-student coaches who have taken that exam. The student picks their own coach; the price is set by the platform (owner) and is uniform. Everything happens inside the platform — the goal is to remove dependency on WhatsApp/external tools.

**Core value:** in-platform messaging, transparent coach profiles, integrated Google Meet, secure payment.
**Competitor:** Kant Akademi.
**Differentiation:** coach selection, in-platform communication, transparency.
**Users:** Student, Coach, Admin. (Parent role/panel is a later phase.)

---

## 2. Business Model

A student makes a **monthly (one-off) purchase** for a coach + a package. No auto-renewal; when the month ends, a reminder email prompts them to buy again. Price is set by the owner, identical for all coaches. There can be multiple packages (monthly / 3-month / free trial) but the price does not vary by coach.

**Package = session entitlement:** each package contains a weekly session count (default 4/week).
**Commission:** 15–20% (recorded as a snapshot on the Payment at the moment of the transaction — §8).
**Payment:** iyzico single charge (not the recurring API).
**Payout:** manual at first + a refund window (§9).

---

## 3. Scope — MVP (Closed Beta)

**Core loop:** sign up → find a coach → buy a package → book an appointment → message → meet via Google Meet → coach gets paid.

- **Auth** — sign-up/login (email + Google), JWT, roles
- **Coach** — profile + admin approval flow
- **Search** — filtering by track/university/availability (no price filter)
- **Availability & Booking** — coach enters slots → student reserves (no double-booking)
- **Messaging** — in-platform real-time chat (persistent + read receipts; attachment policy §9)
- **Session** — automatic Google Meet link on the appointment
- **Notification** — email + in-app (persistent Notification table)
- **Payment** — iyzico single charge

**Closed-beta target:** ~25 coaches, 50 students, 30 completed appointments.

---

## 4. Out of Scope (later phases)

| Feature | Phase | Note |
| --- | --- | --- |
| Parent role + parent panel | P1+ | KVKK consent/age minimum stays in core (§9) |
| Auto-renewal (recurring iyzico) | Post-beta | — |
| Reviews/ratings | P1 | Table + integrity constraint designed (§8) |
| Progress tracking (practice-exam charts) | P1 | Needs a `TrialResult` time-series table; current static net columns aren't enough |
| AI question solver | P2 | — |
| Mobile app | P3 | — |
| Embedded video (Daily.co/WebRTC) | v2+ | Meet link is enough |
| Escrow/auto payout/reconciliation | Later | Manual payout + refund window is enough (§14) |

---

## 5. Constraints

- **Team:** solo full-stack (+ freelance designer).
- **Duration:** ~22 weeks to closed beta, MVP-focused.
- **Regulation:** Turkey, KVKK mandatory.
- **Payment:** iyzico.
- **Backend (fixed):** Java 21 + Spring Boot 4, Maven.
- **Frontend:** Next.js, separate and later. Backend is a frontend-agnostic REST API.
- **Budget:** low; free/affordable tiers.

---

## 6. Architecture Approach

A single Spring Boot **monolith** (not microservices). Classic layer-based (MVC): `controller / service / repository / entity / dto / enums / exception / mapper / security / config / integration`.

**Simplifications:** no Spring Modulith / broker / Redis relay. No unnecessary abstraction in business logic (concrete services).

**EXCEPTION — integration interfaces:** A thin interface is kept for each of four external services: `IyzicoClient`, `MeetClient`, `MailClient`, `StorageClient`. Rationale: this is a prerequisite for the stub-first workflow and for unit testing with Mockito. (This is not "port/adapter for everything"; just 4 seams.) No abstraction anywhere else.

**No event bus** — services call each other directly. Single exception: after-commit for external side effects (§9 transaction rule).

**Audit & concurrency:** all entities derive from a shared `BaseEntity` (`@MappedSuperclass`): `created_at` (`@CreatedDate`), `updated_at` (`@LastModifiedDate`), `version` (`@Version`, optimistic locking). `@EnableJpaAuditing` is on.

**Delete policy:** NO hard-delete. User "deletion" = `User.status=DELETED` + (if KVKK erasure arrives) PII anonymization. Message / Report rows are retained for security/audit. Cascade delete is not used.

**Identity:** Spring Security + stateless JWT.
**Availability:** concrete slots (UTC).
**Time:** store UTC everywhere (`Instant`), display in Europe/Istanbul.
**Money:** `numeric(12,2)` / `BigDecimal`, never float; currency TRY.

---

## 7. Tech Stack

| Layer | Technology | Note |
| --- | --- | --- |
| Language / Framework | Java 21 + Spring Boot 4.0.x (current 4.0.6) | Spring Framework 7; Java 21 recommended |
| Build | Maven | — |
| Database | PostgreSQL (Neon) | Relational, serverless |
| Migration | Flyway | SQL-first |
| ORM | Spring Data JPA / Hibernate | — |
| Identity | Spring Security + JWT + OAuth2 (Google) | — |
| Real-time | Spring WebSocket + STOMP | — |
| Cache / rate-limit | Redis (Upstash) — optional | — |
| File | Cloudflare R2 (S3-compatible) | Behind `StorageClient` |
| Payment | iyzico (single charge) | Behind `IyzicoClient` |
| Email | Resend | Behind `MailClient` |
| Video | Google Calendar + Meet API | Behind `MeetClient`; Workspace may be needed (early PoC) |
| API docs | springdoc-openapi-starter-webmvc-ui 3.0.x | 3.x series supports SB4 |
| Monitoring | Sentry + Actuator | — |
| Backend hosting | Fly.io / Railway (persistent container) | WebSocket → can't be serverless |
| Frontend hosting | Vercel (Next.js) | — |

**SB4 note:** the springdoc 3.x series supports SB4. The only real cost: Claude Code's training is SB3-heavy; add a note in CLAUDE.md saying "we use SB4, don't suggest SB3 config."
**Neon note:** scale-to-zero. Set up HikariCP with the pooled connection string; cold-start on the first query.

---

## 8. Data Model (v4)

All entities derive from `BaseEntity` → `id`, `created_at`, `updated_at`, `version` are shared. These aren't repeated below (only noted where meaningful). For a diagram see: `er-diagram.mermaid`.

### Identity

- **User** — `id`, `name`, `email` (UK), `password_hash` (null if Google-only), `role` (STUDENT/COACH/ADMIN), `date_of_birth`, `city`, `status` (ACTIVE/SUSPENDED/DELETED).
- **RefreshToken** — `user_id`, `token_hash` (UK), `expires_at`, `revoked`.

### Student

- **StudentProfile** — `user_id` (UK), `grade` (GRADE_9…GRADUATE), `track` (NUMERICAL/EQUAL_WEIGHT/VERBAL/LANGUAGE), `target_university`, `target_department`, `target_score`, initial TYT nets (`initial_tyt_turkish`, `_math`, `_science`, `_social`).

### KVKK / Safety Minimum (NOT a parent panel)

- **ConsentRecord** — `student_user_id`, `guardian_name`, `guardian_phone`, `guardian_email`, `consent_text_version`, `approved_at`, `ip`. Recorded even if collected offline.
- **Report** — `reporter_id`, `reported_user_id`, `conversation_id` (null), `reason`, `status` (OPEN/UNDER_REVIEW/RESOLVED), `reviewing_admin_id` (null), `resolution_note` (null), `resolved_at` (null).

### Coach

- **CoachProfile** — `user_id` (UK), `university_id`, `department`, `exam_year`, `exam_rank`, `exam_type`, `photo_url`, `video_url`, `about`, `coaching_experience_months`, `max_student_capacity`, `active_student_count` (atomic counter), `approval_status` (PENDING/APPROVED/REJECTED), `admin_note`, `payout_account_ready`.
- **University** — `name`, `city`, `is_active`.
- **CoachSubject** — `coach_profile_id`, `track` (NUMERICAL/EQUAL_WEIGHT/VERBAL/LANGUAGE). `UNIQUE(coach+track)`. Track-level filter (replaces the old `yks_turu` string).

### Booking & Communication

- **CoachAvailability** — `coach_profile_id`, `start_time` (UTC), `duration_minutes`, `is_booked`. `UNIQUE(coach+start_time)`.
- **Session** — `coach_profile_id`, `student_user_id`, `subscription_id`, `availability_id` (UK — double-booking guarantee), `start_time` (UTC), `platform`, `status` (PLANNED/COMPLETED/CANCELLED_BY_STUDENT/CANCELLED_BY_COACH/NO_SHOW), `meet_link`.
- **Conversation** — `student_user_id`, `coach_profile_id` (`UNIQUE(student+coach)`), `last_message_at`.
- **Message** — `conversation_id`, `sender_id`, `content`, `attachment_url` (beta: restricted/admin-visible), `read_at` (null).

### Package & Payment

- **Package** — `name`, `price`, `list_price`, `discount_percentage`, `duration_type` (MONTHLY/THREE_MONTH/SIX_MONTH), `weekly_sessions`, `exam_type`, `target_exam_date`, `is_popular`, `features`, `is_free`, `is_active`.
- **Subscription** — `student_user_id`, `coach_id` (→ CoachProfile), `package_id`, `price` (snapshot), `start_date`, `end_date`, `status` (PENDING_PAYMENT/ACTIVE/EXPIRED/CANCELLED), `payout_status` (PENDING/PAID).
- **Payment** (ledger) — `subscription_id`, `amount`, `commission_rate` (snapshot), `commission_amount` (snapshot), `coach_payout_amount` (snapshot), `type` (CHARGE/REFUND), `status` (INITIATED/SUCCESSFUL/FAILED/REFUNDED), `idempotency_key` (UK), `iyzico_payment_id`, `source_payment_id` (null; refund → original).

### Notification

- **Notification** — `user_id`, `type`, `title`, `content`, `read_at` (null), `related_type` (SESSION/MESSAGE/PAYMENT…), `related_id`.

### Optional

- **Review** (P1) — `coach_profile_id`, `student_user_id`, `subscription_id`, `content`, `rating`. `UNIQUE(coach+student)`.
- **LeadForm** — `full_name`, `phone`, `phone_owner`, `email`, `message`.

### Enums

`role`, `status`, `grade`, `track`, `approval_status`, `duration_type`, Session `status`, Subscription `status`, `payout_status`, Payment `type`/`status`, Report `status`.

> **Derived (not stored):** coach rating (from Review), total sessions (from Session).
> **Counter-protected:** capacity (`active_student_count`, atomic + periodic reconciliation — §9).

**Indexes (in Flyway):** `CoachProfile(approval_status)` · `CoachSubject(track)` · `CoachAvailability(coach_profile_id, is_booked, start_time)` · `Session(student_user_id, start_time)`, `Session(coach_profile_id, start_time)`, `Session(status)` · `Message(conversation_id, created_at)` · `Subscription(student_user_id, status)`, `Subscription(coach_id, status)` · `RefreshToken(expires_at)`.

---

## 9. Business Rules

**Transaction boundary** (the sharp edge of the no-events decision). If a service method both writes to the DB and makes an external call (Meet link, email): the external calls do **NOT** happen INSIDE the DB transaction. Commit first, then external call. If Meet/email blows up, the reservation is not rolled back; the error is logged, the link is generated later. (Note: `@TransactionalEventListener` is only for after-commit external calls; business logic uses direct service→service calls.)

**Weekly session quota.** "Week" = calendar week (Mon–Sun, Europe/Istanbul). Quota = PLANNED + COMPLETED sessions that week vs `package.weekly_sessions`. Early (>24h) cancellation gives the right back; late cancellation / NO_SHOW burns it.

**Capacity race.** Atomic conditional update: `UPDATE coach_profile SET active_student_count=active_student_count+1 WHERE id=? AND active_student_count < max_student_capacity` → 0 rows = full. Decrements when a subscription ends/is cancelled. Counter verification: since it's denormalized, a daily job compares it against the real active Subscription count.

**Double-booking.** UNIQUE slot (`CoachAvailability`) + `Session.availability_id` UNIQUE → guaranteed at the DB level. The `is_booked` flag is for UX/quick checks; the solid guarantee is in the UNIQUE constraint.

**Refund ↔ payout order.** Do NOT do the payout before the refund/dispute window has passed (e.g. 7 days after the period ends, if there's no open complaint). `payout_status` prevents double payment. Refund = new Payment row (`type=REFUND`, `source_payment_id` links to the original).

**Changing coach.** The student cancels the current subscription → a new subscription with the new coach. If a coach leaves, an admin redirects manually.

**WebSocket + JWT.** Token in the CONNECT frame; when it expires the client reconnects with a fresh token.

**Child-safety (made concrete).** Minor student + adult coach + one-to-one messaging = serious risk.

- **Message gate:** a student can message a coach they have an active or past subscription with. No subscription, no messaging (enforced server-side). The free trial also creates a Subscription, so it opens the gate.
- **File attachment policy (beta):** free image sharing is a moderation risk. In beta, either disabled, or only student→coach question photos + all attachments visible to admins + no free file uploads.
- **KVKK minimum:** `date_of_birth`, `ConsentRecord` (recorded even if taken offline), message retention, Report + admin visibility, ability to suspend a reported user via `User.status=SUSPENDED`.

---

## 10. Development Principles (how we work)

- **Vertical slice:** finish each feature end-to-end (entity→repository→service→controller→test→working).
- **Stub-first:** leave external services behind an interface with a fake implementation first.
- **Early PoC:** validate the two riskiest integrations (iyzico single charge + Meet link) in the first week.

---

## 11. Development Plan

**Order (vertical slices):** Phase 0 (setup + SB4) → 0.5 Risk PoC (iyzico+Meet) → Auth → Coach → Search → Availability/Booking → Messaging → Session → Notification → Payment → (Hardening/KVKK).

**Estimate (realistic):** ~9–12 weeks full-time backend. Claude Code's "write → check → test" loop speeds up the routine phases (entity/CRUD) but doesn't shorten the critical path (iyzico, Meet, concurrency, chat, review) — it helps you hit the good end of this range, not go below it. If you're part-time, spread it across the calendar; if you're new to Spring Boot, start from the upper end.

**Backend done ≠ product done.** The frontend (Next.js) is an entirely separate job and is outside this estimate.

---

## 12. Risks / Launch Blockers

- **KVKK + parental consent for under-18s** — non-deferrable; the minimum model is in core (§9).
- **Child-safety surface** — message gate + file attachment policy + reporting/admin visibility + suspension.
- **iyzico** — reduced to a single charge but early PoC. Plan B: swap behind the provider interface if needed.
- **Google Meet** — Workspace may be needed for automatic links; early PoC. Plan B: manual link / Jitsi.
- **Frontend is a separate job** — backend done ≠ product done.
- **Backend persistent container** (WebSocket → can't be serverless).

---

## 13. Decisions Made (summary)

- **Payment:** monthly one-off purchase (no auto-renew).
- **Price:** uniform, set by the owner (the coach does NOT set the price).
- **Parent panel:** deferred; the KVKK/safety minimum is kept.
- **Stack:** Java 21 + Spring Boot 4 (springdoc 3.x).
- **Architecture:** single monolith, layer-based, lean — exception: interface for 4 external services. No event bus, but external calls happen after commit.
- **Audit:** shared `BaseEntity` (created/updated/version). Delete: soft-delete (`User.status`) + anonymization.
- **Payment record:** ledger (refund is a separate row) + commission snapshot.
- **Double-booking:** `Session.availability_id` UNIQUE. Coach-subject: track-level `CoachSubject` junction table.
- **Availability:** concrete slots (UTC). (Open option: "weekly template → slot generation" for coach UX later.)
- **Messaging** only with a subscribed coach; chat file attachments disabled/restricted in beta + admin-visible.

---

## 14. Deliberately REJECTED Proposals (a decision, not a gap)

Documented so they don't come up again in old-requirement-based critiques:

- **Coach-based pricing:** REJECTED. The price is uniform and set by the owner; there is deliberately no price field on `CoachProfile`. Differentiation is not price comparison; it's coach selection + internal communication + transparency.
- **Escrow / iyzico Marketplace state machine** (IN_ESCROW/RELEASED): REJECTED. To simplify the riskiest integration, a single charge + refund window + manual payout was chosen. The refund window is the mechanism that stands in for a lightweight escrow. Automatic escrow/payout/reconciliation is deferred to post-beta.
- **Subject-level coach expertise** (Subject/CoachSubject-at-lesson-level): DEFERRED/OUT OF SCOPE. The product is coaching (mentorship), not subject teaching; matching is at the track level (`CoachSubject`). Lesson-level can be added later if needed.
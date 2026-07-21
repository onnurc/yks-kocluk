# CLAUDE.md — YKS Coaching Platform (Backend)

> **Detailed references:** `project-document-v4.md`, `er-diagram.mermaid`, `PHASES.md`
> This file contains the rules in effect for **every session**.
> In case of conflict, **this file + the v4 document are binding**.

---

## Project

Backend for a web platform that connects YKS exam students with university-student coaches who have already taken the exam. Students choose their own coach, start an auto-renewing monthly subscription (renews until the user cancels), book sessions, and communicate via in-platform chat + Google Meet. Pricing is set by the platform — uniform for all coaches; coaches do not set prices.

**Roles:** `STUDENT`, `COACH`, `ADMIN` (`PARENT` is a future phase).

---

## Context & Philosophy — READ THIS FIRST

- Solo developer, ~22 weeks, MVP-focused. **Operational simplicity is critical.**
- **Avoid over-engineering.** There are deliberate simplifications (see DO-NOTs and REJECTED PROPOSALS below). Do not break them by adding abstractions, patterns, or layers for "cleanliness."
- This repository is a **monorepo**: the backend is in `/backend` (Java 21, Spring Boot 4) and the frontend is in `/frontend` (React + Vite + TypeScript).

---

## Stack

| Layer | Technology |
| --- | --- |
| Language / Framework | Java 21, Spring Boot 4.0.6, Maven — **we use SB4; do not suggest SB3 configs or properties** |
| Database | PostgreSQL (Neon) + Spring Data JPA / Hibernate; Flyway (SQL-first migrations) |
| Security | Spring Security 7 + stateless JWT (access + rotating refresh, hashed) + OAuth2 (Google) |
| Rate Limiting | In-Memory JVM (InMemoryRateLimitStore), Redis-compatible key/TTL design |
| Real-time | Spring WebSocket + STOMP (chat) |
| External services | iyzico (payment) · Google Calendar/Meet (video) · Resend (email) · Cloudflare R2 (files) |
| API docs | springdoc-openapi-starter-webmvc-ui 3.0.x |
| Monitoring | Sentry + Actuator |
| Testing | JUnit + Spring Boot Test, MockMvc, Mockito, Testcontainers (PostgreSQL) |

**Pin all versions in `pom.xml`.** Fix until the build is GREEN in Phase 0. **Do not use H2** — use Testcontainers with real PostgreSQL for all tests touching the DB.

---

## Architecture — Firm Decisions

- **Single monolith.** No microservices.
- **Layer-based packages:** `controller / service / repository / entity / dto / enums / exception / mapper / security / config / integration`
- **Identity lives in this application** (no Supabase, no NextAuth). Persistent container (Fly.io / Railway); **no serverless**.
- **Transient State (Rate Limiting):** rate limiting state is kept behind `RateLimitStore` interface to keep controllers/services decoupled. IP addresses, emails, and refresh tokens are normalized and hashed (SHA-256) inside env-prefixed keys to make future Redis migration drop-in.
- **Refresh Tokens:** stored hashed (SHA-256) and rotated on refresh. Full stolen-token family reuse detection and family revocation are deferred for MVP (conscious risk).
- **Audit:** all entities extend a shared `BaseEntity` → `created_at` (`@CreatedDate`), `updated_at` (`@LastModifiedDate`), `version` (`@Version`). `@EnableJpaAuditing` is enabled.
- **Deletion:** no hard-deletes. "Delete" = `User.status=DELETED` + PII anonymization if required. Messages and Reports are retained. **Do not use cascade delete.**
- **Money:** `numeric(12,2)` / `BigDecimal`, never `float`; currency TRY.
- **Time:** store everywhere as UTC (`Instant`), display as Europe/Istanbul. No separate date + time fields.

---

## Deliberate DO-NOTs (over-engineering shield)

| ❌ Do NOT | ✅ Instead |
| --- | --- |
| Event bus / ApplicationEvents | Services call each other directly. **One exception:** `@TransactionalEventListener(AFTER_COMMIT)` — for external side effects only. |
| Facade / port-adapter / interface abstractions | Concrete service classes. **One exception:** thin interfaces for the 4 external services (`IyzicoClient`, `MeetClient`, `MailClient`, `StorageClient`) — required for stub-first workflow and Mockito. Do not add interfaces anywhere else. |
| Spring Modulith, message broker, Redis relay | — |
| Availability templates + slot generation | Concrete slots (`CoachAvailability`) only. |

---

## Rejected Proposals (do not suggest — these are decisions)

- **Coach-level pricing** — no; pricing is uniform and set by the owner. `CoachProfile` has no price field.
- **Fund-distribution model** — **UNDER REVIEW** (pending the user's accountant); NOT settled. No database schema is implemented, no IBANs are collected, and no active bank transfers are wired. Architectural recommendations are documented in `docs/adr_coach_payout_architecture.md`.
- **Lesson-level expertise** (`Lesson` / `CoachLesson`) — no; matching is track-level (`CoachSubject`). Coaching = mentorship, not subject tutoring.

---

## Code Standards

- Domain and entity names are in **English**. All class names, field names, and enum values use English identifiers. Turkish is reserved for **user-facing content only** (validation messages, screen labels, KVKK terminology).
- Every entity follows the same fixed pattern: **Entity → Repository → Service → Controller → DTO → Mapper (MapStruct) → validation (Bean Validation) → security (`@PreAuthorize`) → unit test.** (See the new-entity skill.)
- **Security is never added "later":** apply role checks at the time of writing each controller.
- Work with DTOs; **never leak entities out of controllers**. DB change = Flyway migration.
- **Derived data is not stored** (coach rating, total sessions → compute at runtime).

---

## Conventions — Apply Identically Across Every Module

### Error Format

All errors return as Spring `ProblemDetail` (RFC 9457, `application/problem+json`) via a global `@RestControllerAdvice` that extends `ResponseEntityExceptionHandler`.

**Required fields:** standard `type` / `title` / `status` / `detail` / `instance` + custom `errorCode` (machine-readable, e.g. `VALIDATION_ERROR`, `SLOT_FULL`, `QUOTA_EXCEEDED`) + `timestamp`. Validation errors → `400` + field-level error list.

**Override all framework-thrown exceptions** (e.g. `MethodArgumentNotValidException`, 404, 405) in the same handler — otherwise Spring's default `ProblemDetail` responses will leak alongside your custom ones.

Every module uses this format. **Do not invent a custom error format.**

### Transaction Boundary Rule

**No external calls inside a DB transaction.** External calls are synchronous, **after commit**:

- **(a)** Caller invokes them manually after the `@Transactional` method returns, OR
- **(b)** `@TransactionalEventListener(AFTER_COMMIT)` — use this **only** for after-commit external side effects; do not use for general inter-service communication.

**Do NOT use `@Async`** (thread pool issues + lost context + difficult error handling).

If an external call fails, the DB is **not** rolled back — log the error.

```java
// (a) manual, after-commit:
var session = bookingService.reserve(...);   // @Transactional — commits
meetClient.generateLink(session);            // after commit
mailClient.send(...);                        // after commit

// (b) event listener:
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onSessionBooked(SessionBookedEvent event) { ... }
```

### Testing

| Test type | Approach |
| --- | --- |
| Unit (service logic) | Mockito with mocked repositories/clients — no Spring context, no DB |
| Web layer | `@WebMvcTest` + MockMvc (service mocked) |
| Repository / integration | Testcontainers with real PostgreSQL — add `@AutoConfigureTestDatabase(replace = Replace.NONE)` to `@DataJpaTest` tests, otherwise Spring replaces the datasource with H2 by default |

**No vanity coverage % target** — but the following critical paths are **mandatory**:

- Auth / security
- Booking race condition (double-booking + capacity)
- Payment idempotency
- Weekly session quota
- Message gate

### Pagination & Sorting

Listing endpoints accept Spring `Pageable` (`@PageableDefault(size = 20)`). **Max size = 100.** Whitelist sortable fields per endpoint — do not blindly pass user-supplied sort strings to JPA.

Response: wrap `Page<T>` in a `PageResponse<T>` DTO:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 150, "totalPages": 8, "last": false }
```

### DTO Validation

- Request DTOs: `XxxRequest` suffix. Response DTOs: `XxxResponse` suffix.
- Use `@Valid` on controller request body parameters. Use `@Valid` on nested DTO fields.
- Use `@Validated` **only** when validation groups or method-level parameter validation is explicitly needed.
- Do not use validation groups unless the MVP genuinely requires them.
- Write validation messages in Turkish (user-facing): e.g. `"Ad boş olamaz"`, `"Geçerli bir e-posta girin"`.

---

## Critical Business Rules (code behavior)

| Rule | Implementation |
| --- | --- |
| **Double-booking** | `Session.availability_id` UNIQUE → DB-level guarantee. `is_booked` flag is for quick UI checks only. |
| **Capacity race** | Atomic conditional UPDATE: `WHERE active_student_count < max_student_capacity`. 0 rows affected = coach is full → throw CONFLICT. |
| **Payment idempotency** | `idempotency_key` UNIQUE. Status updated via webhook. Refund = new Payment row (`type=REFUND`, `source_payment_id` links to original). Do not mutate the existing row. Snapshot commission at transaction time (`commission_rate`, `commission_amount`, `coach_payout_amount`). |
| **Refund ↔ payout order** | Do not release payout before the refund window closes. |
| **Child-safety message gate** | A student can view history of active/past subscriptions (ACTIVE, PAST_DUE, EXPIRED, CANCELLED), but send messages only with an ACTIVE subscription. Enforced server-side. Chat attachments closed in beta. |
| **KVKK & Minor Consent** | `ConsentRecord` required for students under 18 (recorded online/offline). If consent is not accepted or is revoked (`REVOKED`), they are blocked from checkout, booking, starting conversations, and sending messages. |

---

## Development Order (Phases) — Details in PHASES.md

- **Phases 0-6:** Completed (Core features, Subscription management).
- **Phases 7-8:** Completed (KVKK Consent, Safety Reports, Admin Panel, Minors & Consent validation).
- **Phases 9-10:** Completed (Admin Finance, Webhook Signature, Minor Consent UX). Payout altyapısı yapılmamıştır, sadece ADR/karar hazırlığı tamamlanmıştır.

---

## Definition of Done — Per Module

A module is done when **ALL** of the following are true:

- [ ] Endpoints are working + Bean Validation is in place
- [ ] `@PreAuthorize` annotations are present
- [ ] Flyway migration is applied (same commit as entity change)
- [ ] All errors return standard `ProblemDetail` format
- [ ] Happy-path, error, and authorization tests pass (critical paths use Testcontainers)
- [ ] Visible in Swagger UI
- [ ] Build is green (`cd backend && ./mvnw verify`)

---

## Working Rules — Every Session

1. **Before writing any code:** present the plan for the current phase and wait for approval. A plan is not code — it is a short bullet list (~15–30 lines) covering:
    - (a) Entities to create + field summary
    - (b) Flyway migration(s)
    - (c) Endpoint list (method + path + role)
    - (d) DTOs
    - (e) Tests to write
2. **Do not scaffold the entire project at once.** Focus on the current phase's module; do not move on before completing a vertical slice.
3. **If something is ambiguous, do not assume — ask one question at a time.**
4. **When a module is complete:** summarize what was done, list the tests with the exact command to run them (`cd backend && ./mvnw test -Dtest=XxxTest`), then ask: *"Next step is X — do you approve?"*
5. **Honor the DO-NOTs and REJECTED PROPOSALS lists.** If you intend to suggest an abstraction, provide a justification first and wait for approval.
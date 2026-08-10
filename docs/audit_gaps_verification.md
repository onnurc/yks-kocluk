# Audit Gaps Verification

## Cloudflare R2 media/file verification

- Migration `V26__media_assets_and_profile_media.sql` adds metadata-only `media_assets`, owner/status/type indexes, strict type/visibility/status checks, and nullable coach/student profile asset references. Existing profiles remain valid.
- Presign is authenticated and accepts only media type, declared MIME, size and optional metadata filename. Bucket, visibility and object key are server-owned. Keys contain only category, numeric owner ID and UUID; filename, email and full name never enter the key.
- Policy enforcement happens before signing: profile images 5 MiB, coach intro videos 20 MiB and documents 10 MiB by default. MIME allowlists are media-type-specific and do not rely on extension.
- Intro video requires the authenticated owner's coach profile. Profile image requires the authenticated owner's coach/student profile. Finalize and delete require exact ownership; ADMIN cannot impersonate an upload owner.
- Finalize checks object existence, length and content type before activation. Pending media cannot resolve to public DTO URLs. Private files never resolve through the public base URL; signed GET access is restricted to owner or ADMIN.
- Public mapping resolves only `ACTIVE` + `PUBLIC` references. Without a public base URL it returns null, never an invalid or temporary URL.
- R2 secrets are configuration-only and are never returned or logged. There is no arbitrary remote URL fetch, filesystem persistence, backend body proxy, Cloudflare daemon, Stream integration, production DNS setup, or real Cloudflare API call.
- Disabled/default mode requires no R2 credentials and wires the non-persistent stub. Enabled mode fails startup configuration validation when required R2 settings are missing.
- Remaining hardening: presign-specific rate limiting if abuse data warrants it; malware scanning/quarantine; abandoned-pending and orphan cleanup; failed physical-delete retry/monitoring; real R2 CORS/custom-domain smoke tests.

## Confirmed account/chat/purchase/refund policy verification (2026-08-09)

- Removed the voluntary password cooldown from configuration, service errors, frontend copy and tests while retaining password/session invalidation controls.
- Conversation DTOs now make the stable platform ADMIN observer visible to both participants. Admin coach/student directory APIs reuse the existing conversation model and audited read surface; no concrete admin ID is hard-coded. Authorized ADMIN can subscribe read-only to the existing conversation topic for live fan-out, while ADMIN remains excluded from REST/STOMP send paths.
- Successful initial purchases now persist a canonical success instant and emit an after-commit, idempotent transactional Resend event. Marketing state is deliberately irrelevant and no recipient/provider secrets come from the callback body.
- V25 adds `payments.succeeded_at` plus the indexed, constrained `refund_requests` workflow table. The financial refund ledger remains separate. The policy is `[0,7d)` unconditional, `[7d,14d]` standard-eligible or manual-review based on paid-session evidence, and expired after 14 days. Trial consultations and future planned sessions are excluded; service commencement during days 8–14 never auto-rejects.
- Principal ownership and role checks protect student/admin workflows; the database prevents multiple active requests per original payment, approval calls the configured provider refund, and provider failure cannot stamp `REFUNDED`.

## Final dashboard integration and hardening verification (2026-08-09)

- Role separation is explicit at controller boundaries: student self, coach self, and admin management surfaces are mutually denied. Shared student/coach messaging additionally enforces conversation membership and subscription-derived history/send gates. Principal-derived identity is used throughout; no self owner ID or role is trusted from requests.
- Account readiness remains two independent gates. Sensitive checkout, paid booking, trial creation, conversation creation, and message send call the shared readiness service; `EMAIL_VERIFICATION_REQUIRED` is evaluated before the separate legal-onboarding rule and `LEGAL_ONBOARDING_REQUIRED` remains unchanged. Suspended/deleted JWT principals are blocked centrally.
- The student dashboard now uses the canonical `UserMapper`. Its embedded `UserResponse` therefore preserves `emailVerified`, `legalOnboardingCompleted`, `hasLocalPassword`, and `passwordChangedAt` instead of silently returning constructor defaults. No dashboard aggregates were added to auth identity.
- Coach student-page N+1 queries were removed: quota counts and next planned sessions are batch-loaded for the page, subscriptions fetch student/package data in the page query, and conversation IDs are loaded once. Coach conversation summaries batch-load latest messages, unread counts, and live-access student IDs. Package gross sales are grouped in one aggregate query rather than queried once per package.
- Coach student default ordering is deterministic newest-first (`createdAt DESC`, `id DESC`); calendar default ordering is nearest-first with an ID tie-break. Existing page-size cap remains 100. Existing unpaginated legacy compatibility endpoints are documented rather than changed incompatibly.
- Dashboard month/week windows consistently derive boundaries in `Europe/Istanbul` and persist/transport UTC `Instant`. Date filters are half-open `[from,to)`. No JVM-default timezone use was found in dashboard metric code.
- Paid `Session` and `TrialConsultation` remain separate. Concrete availability ranges cannot overlap for a coach; both reservation paths lock the same availability row, honor `booked`, and have unique slot references. Trial creation also explicitly checks active trial/paid overlaps. Trial cancellation unlinks and reopens the slot; trials create no payment/subscription/quota/message relationship.
- DTO review found no password hash/version, refresh/reset/verification token/hash, OAuth subject, legal evidence blob, card/provider secret, or raw provider payload in dashboard responses. Admin directories expose operational identity/status fields only; coach/student dashboard DTOs expose relationship data only.
- KPI and finance definitions are frozen in `docs/handoff.md`. Gross revenue/sales means successful charge volume; refunds are successful refund ledger volume; `netCollectedAmount` is gross charges minus refunds and is not payout/profit. V25 adds the separate policy-classified request workflow without changing these ledger definitions.
- No migration was added and V1-V24 were not modified.
- Verification in this environment: backend compile passed; the 52 focused tests in `StudentDashboardServiceTest`, `CoachDashboardServiceTest`, `AdminDashboardServiceTest`, `TrialConsultationServiceTest`, `SessionServiceTest`, `MessageServiceTest`, and `AccountReadinessServiceTest` passed. A broader controller/service/security selection produced 374 passes and 1 skip; its six `IyzicoWebhookSignatureTest` context errors were all caused by the unavailable Docker environment, not assertions. `test-compile`, tests-skipped `package`, and `git diff --check` passed. Docker CLI/daemon is unavailable, so PostgreSQL/Testcontainers dashboard and full integration suites were not run; run them on a Docker-enabled host before merge.

## Admin Dashboard verification

- Every new admin endpoint is class-level `ADMIN` method-secured and derives authorization from the authenticated principal. No client-provided admin identity or role is trusted.
- KPI counts and finance totals are database aggregates rather than entity-list counts. Coach directory fetches user/university in the page query; payment refunds remain batch-loaded. Major directories are bounded by Spring pagination and whitelisted sorting.
- User responses deliberately omit password hashes/version, OAuth identifiers, refresh/reset/verification tokens, legal evidence, and payment credentials. Deleted users are indicated only by the existing `DELETED` status/anonymized flag and cannot be unsuspended.
- Refund reporting reflects persisted `REFUND` ledger rows and provider success/failure. The backend does not invent an automated “service commenced” rule, a refund-request approval workflow, provider success, payout, commission settlement, or profit metric.
- Open-report KPI semantics exactly match the existing actionable set (`OPEN`, `REVIEWED`). Report moderation transitions and safety-notification behavior were not replaced.
- Paid/trial operational visibility is read-only. Existing message-content access remains separately reason/audit-gated; no unrestricted message-reading endpoint was added.
- No Flyway migration is required after V24.

## Coach Dashboard and trial-consultation verification

- All `/api/v1/coach/**` self-dashboard endpoints are method-secured for `COACH` and derive ownership from the authenticated principal; no client-provided coach ID is accepted.
- Existing coach approval/discovery, account readiness, availability overlap, paid booking/quota, profile moderation, and messaging membership/read-receipt rules remain the source of truth.
- Trial consultations use `V24` persistence separate from paid sessions. Request requires a ready `STUDENT`, an active/approved coach, and a future slot owned by that coach. It creates no payment/subscription and grants no messaging access.
- Paid/trial double booking is prevented by pessimistically locking the shared availability row and honoring `is_booked`; entity-specific unique availability constraints provide a second deterministic guard. The partial unique trial index permits a new trial only after cancellation.
- Dashboard active-student totals count distinct `ACTIVE`/`PAST_DUE` relationships. Monthly completion and upcoming counts use UTC instants with Europe/Istanbul calendar boundaries where a calendar boundary is required.
- Package pricing is read-only for coaches. `grossSales` means successful attributable charge volume, not payout or earnings; payout remains unimplemented.

## Scope

This document records the verification and audit findings of areas previously marked as "needs verification" or "deferred" in the YKS Coaching Platform codebase:
- OAuth2 login flow
- DemoSeedCleanupComponent
- Bean Validation
- MapStruct mappers
- Payout deferred state
- PARENT role deferred state

Note that:
- Auth rate limiting was handled and fully verified in a previous branch (`fix/auth-rate-limiting`).
- WebSocketAuthTest timing/flakiness was resolved and verified in a previous branch (`fix/websocket-auth-message-delivery`).
- Frontend lint cleanup is intentionally deferred to a future frontend QA/lint branch.

## Frontend legal/compliance integration status

- The frontend now consumes current legal documents through `GET /api/v1/legal-documents/{type}/current` and uses a reusable plain-text modal viewer in registration, checkout, footer links, onboarding, and privacy settings.
- Registration supplies current Terms and Explicit Consent IDs and optional, independent email/SMS marketing choices. The KVKK notice remains informational.
- Google OAuth has a callback/code-exchange route and a dedicated legal-onboarding route. `legalOnboardingCompleted` is retained in auth state and gates product routes without silently accepting documents.
- Checkout supplies the current pre-information, distance-sales, and refund/cancellation IDs and cannot start until the aggregate checkbox is selected.
- Authenticated marketing and cookie preferences, consent withdrawal, and account deletion are exposed under `/privacy`. Successful deletion clears local tokens before redirecting to login.
- The obsolete age-based guardian modal is no longer rendered and its frontend component was removed. Backend legacy consent compatibility remains unchanged.
- Anonymous cookie preferences are not synchronized to the authenticated backend API; a future public cookie-banner implementation must store them locally in the browser.
- Backend documents are still placeholder content. This integration renders them without asserting that they are production-final.
- Focused Vitest/Testing Library coverage verifies required registration and checkout consent, current document IDs, stale-document recovery, OAuth/onboarding redirects, cookie/marketing preferences, withdrawal, and confirmed deletion.

---

## OAuth2 Login Status

- **Secure Exchange Flow Implementation:** The backend Google OAuth2 login flow has been upgraded to a secure, single-use code exchange architecture:
  - Access and refresh tokens are **never** transported via the browser URL redirection query parameters.
  - Successful Google authentication redirects to the frontend URL carrying only a short-lived random code: `?code=<one-time-code>&provider=google`.
  - The login code is cryptographically secure, expires in 120 seconds (configurable), is single-use, and is stored in the database only as a SHA-256 fingerprint hash.
- **Backend Endpoint:** The new public REST endpoint is:
  - `POST /api/v1/auth/oauth2/exchange`
  - Body: `{ "code": "..." }`
  - Returns: `AuthResponse` containing the issued JWT access and refresh tokens, user details, etc., fully aligned with standard credentials login responses.
- **End-to-End Test Status:** End-to-end browser Google authentication was not executed in this audit due to a lack of active Google client credentials.
- **Frontend Integration Status:** The frontend React client does not currently contain a Google login button, an OAuth callback route, or token capture logic. This integration remains deferred to a future frontend/QA phase.
- **Required Env Vars:**
  - `GOOGLE_CLIENT_ID`
  - `GOOGLE_CLIENT_SECRET`
  - `OAUTH2_FRONTEND_REDIRECT_URI`
  - `OAUTH2_LOGIN_CODE_TTL_SECONDS` (default: 120)
- **Date of Birth & Legal Onboarding:** Newly provisioned STUDENT users via Google OAuth2 keep `dateOfBirth = null` and `legalOnboardingCompleted = false`. Tokens retain the existing exchange contract, but checkout, booking, conversation creation, and message sending remain unavailable until `POST /api/v1/auth/legal-onboarding` records current Terms and Explicit Consent acceptances. Documents are never silently auto-accepted.

## Registration legal-document foundation

- `legal_documents` stores versioned, hashed content with `DRAFT`, `PUBLISHED`, and `RETIRED` lifecycle states. Public reads resolve only current effective published rows.
- KVKK Aydınlatma and Açık Rıza are separate. Registration requires current Terms plus current Explicit Consent; KVKK Notice itself has no acceptance checkbox.
- Marketing email/SMS opt-ins are optional and stored as separate `MARKETING_OPT_IN` evidence sources when selected.
- Legacy minor-consent APIs and records remain for backward compatibility and audit history. Age-based enforcement is deprecated and no longer separately blocks product actions; there is no guardian-verification flow.
- Initial version `1.0` content is explicitly placeholder text pending the legal team's approved wording.

## Checkout legal-document acceptance

- New subscription checkout requests carry current IDs for `PRE_INFORMATION_FORM`, `DISTANCE_SALES_AGREEMENT`, and `REFUND_CANCELLATION_POLICY`, plus one aggregate acceptance flag matching the single UI checkbox.
- Although the UI has one checkbox, three separate `LegalAcceptance` rows are stored with version/hash snapshots and links to the exact subscription and initial payment attempt.
- V20 separates user-level acceptance uniqueness from checkout-level uniqueness, preventing duplicate evidence on the same pending attempt while allowing later genuine transactions to accept the same static legal version.
- Legal validation completes before any subscription/payment reservation or Iyzico initialization. The external call remains outside the database transaction.
- Final legal text is not present. The seeded version `1.0` documents are clearly marked placeholders.

## Privacy and account lifecycle foundation

- V21 adds one current marketing state per user/channel, one authenticated cookie-preference row per user, and a controlled account-deletion request. No existing user is backfilled as opted in; necessary storage is implied true and optional categories default false.
- Marketing permission is independent from transactional/service/safety notifications. No marketing sender, analytics provider, advertising SDK, or anonymous visitor identifier was added.
- Cookie preference writes validate the submitted document against the current effective published `COOKIE_POLICY`; wrong-type, stale, retired, and future-effective rows cannot become current preferences.
- Required Explicit Consent withdrawal preserves the acceptance snapshot with `withdrawn_at`, reopens legal onboarding, and leaves Terms/KVKK evidence untouched. The existing legal gate continues to protect checkout, booking, conversation creation, and message sending.
- Completed deletion anonymizes account/profile PII, makes coaches undiscoverable, revokes refresh/OAuth login codes, unlinks Google identity, and keeps the main user row as `DELETED`. Financial, subscription, legal/checkout evidence, message, report, security, and audit relations are not hard-deleted or rewritten.
- Deletion identity hashes are retained only to prevent a deleted password/Google identity from silently recreating an account. Exact legal retention periods, message-retention duration, external object cleanup, failed-request operator tooling, and a suspended-user support path remain production TODOs.
- Refund/service-commencement policy and coach-transfer behavior are unchanged.
- This older verification pass did not yet alter refund/service commencement; it is superseded by the V25 policy section at the top of this document.

---

## DemoSeedCleanupComponent

- **Component Assessment:** `DemoSeedCleanupComponent` was statically inspected.
- **Data Isolation:** The component targets a hardcoded, explicit list of demo seed identities (`*demo@example.com`) rather than broad, role-based deletions. Deletions are executed in a top-down order respecting foreign keys.
- **Changes Made:** No code changes were made to this component in this branch.
- **Production Safety:** `DEMO_SEED_ENABLED` (`app.demo-seed-enabled`) should remain disabled (`false`) in production-like environments unless demo seeding and cleanup is intentionally desired.
- **Risk Level:** The risk of accidental real user data deletion is low based on the current explicit demo identity targeting.

---

## Bean Validation

To align external request payloads with the database/schema constraints, this branch added DTO size validation constraints (`@Size` annotations) to the following request DTOs:
- `RegisterRequest.java` (`email` restricted to `max = 255`)
- `ConsentCreateRequest.java` (`documentVersion` restricted to `max = 50`)
- `ReportCreateRequest.java` (`reason` restricted to `max = 2000`, `details` restricted to `max = 4000`)
- `AdminConversationAccessRequest.java` (`reason` restricted to `max = 2000`)
- `AdminSubscriptionTerminateRequest.java` (`reason` restricted to `max = 2000`)
- `SuspendRequest.java` (`reason` restricted to `max = 2000`)
- `RefundRequest.java` (`reason` restricted to `max = 2000`)

---

## MapStruct

- **Mapper Assessment:** MapStruct mappers were statically inspected.
- **Current Design:** They are strictly response-oriented mappings (`toResponse`), translating entities to DTOs for safe client consumption.
- **Changes Made:** No mapper code changes were made in this branch.
- **Risk Level:** No broad client-to-entity mapping risks (which could allow users to override ID, role, or status parameters during persistence) were identified in this audit.

---

## Payout Deferred State

- **Current Status:** The platform's coach payout/fund distribution model remains **UNDER REVIEW** (Model A vs Model B).
- **Code Audit:** No payout database schema, IBAN/bank account collection inputs, or automated bank transfer production code has been added, in compliance with the decision to wait until a final accounting, legal, and business model decision is made.
- **Changes Made:** This branch does not implement payout features.

---

## PARENT Role Deferred State

- **Current Status:** The `PARENT` role is not implemented in the backend `Role` enum or the database.
- **Consent Implementation:** No guardian/minor verification flow is implemented. Historical `ConsentRecord` data remains available for audit only and does not form a separate product-action gate.
- **Future Phase:** A parent account and parent panel are not part of the confirmed registration policy or this foundation.
- **Changes Made:** This branch does not implement the `PARENT` role.

---

## Refresh Token Security & Rotation

- **Current Implementation:** Refresh tokens are stored securely as SHA-256 hashes and are rotated on every token refresh request (rotation invalidates the old token and issues a new one).
- **Conscious Gaps for MVP:**
  - **Token Family Reuse/Theft Detection:** The current rotation implementation revokes the presented token but does not detect if an old revoked token is reused. In a full theft detection system, reuse of a previously revoked token in the same token family would automatically revoke all active refresh tokens in that family/session. This family-level theft detection/revocation is consciously deferred for the MVP.
  - **Session Revocation:** There is no global "logout-all-sessions" or admin-facing session revocation system, which is also deferred for future hardening.

---

## Approved Coach Status Validation

- **Booking / Session Creation Guard:**
  - Enforced a security check in `SessionService.book` ensuring the coach profile's status is `APPROVED`.
  - Non-approved status (e.g. `PENDING` or `REJECTED`) throws a `403 Forbidden` with the errorCode `COACH_NOT_APPROVED` and detail `"Koç henüz onaylı değil"`.
- **Availability Creation Guard:**
  - Enforced a security check in `CoachAvailabilityService.createOwn` verifying that the authenticated coach profile status is `APPROVED`.
  - Non-approved status throws `403 Forbidden` with errorCode `COACH_NOT_APPROVED`.
- **Minor Consent Regression Coverage:**
  - Added unit test validation coverage verifying that minor STUDENTS without accepted guardian/KVKK consent are blocked from booking.

---

## Coach Management Hardening

- **REJECTED Coach Resubmission:**
  - A coach whose profile status is `REJECTED` can call `createOwn` again. The existing profile is updated in-place (headline, bio, university, department, graduationYear, tracks), status is reset to `PENDING`, and `rejectionReason` is cleared.
  - `PENDING` or `APPROVED` profiles still throw `PROFILE_ALREADY_EXISTS` (409). No duplicate profile rows are created.
  - Tested: `create_rejectedProfile_resubmitsToPending`, `create_pendingProfile_throwsConflict`, `create_approvedProfile_throwsConflict`.

- **Availability Overlap Guard:**
  - Service-level overlap detection added in `CoachAvailabilityService.createOwn` using JPQL: `existing.startTime < newEndTime AND existing.endTime > newStartTime`.
  - Overlapping slots are rejected with `409 SLOT_OVERLAP`. Adjacent slots (end == start) are allowed.
  - The existing `UNIQUE(coach_profile_id, start_time)` DB constraint remains for exact-start duplicate prevention (`SLOT_DUPLICATE`).
  - Tested: `create_overlappingSlot_throwsConflict`, `create_nonOverlappingSlot_succeeds`.

- **DB-Level Exclusion Constraint (Deferred):**
  - A PostgreSQL `EXCLUDE USING gist` constraint on `tstzrange(start_time, end_time)` would provide concurrent-safe overlap prevention at the DB level.
  - **Deferred** because it requires the `btree_gist` extension (not currently used in any migration), potential column type changes, and is a non-trivial migration for an MVP.
  - The service-level guard is sufficient for single-instance MVP. If the backend scales to concurrent writes, a DB-level constraint should be added. Track as: `fix/availability-db-exclusion-constraint`.

- **Non-APPROVED Coach Guards (Already on `main`):**
  - `CoachAvailabilityService.createOwn` checks `profile.getStatus() == APPROVED` → `403 COACH_NOT_APPROVED`.
  - `SessionService.book` checks `coach.getStatus() == APPROVED` → `403 COACH_NOT_APPROVED`.
  - Both have unit tests for `PENDING` and `REJECTED` status coaches.

- **Pagination Max-Size (Already on `main`):**
  - Enforced globally via `spring.data.web.pageable.max-page-size: 100` in `application.yml` (line 45).
  - This configures Spring Boot's `PageableHandlerMethodArgumentResolver` to cap any client-supplied `size` parameter at 100 across all endpoints using standard `Pageable` resolution.
  - All 8 paginated controller endpoints use `@PageableDefault` + standard `Pageable` — no custom resolver bypasses the cap.
  - No dedicated pagination-cap unit test exists. This is a framework-level enforcement (Spring Boot auto-configuration), not custom application code. A dedicated test is a low-priority follow-up.

---

## Messaging & WebSocket Access Hardening

- **Split Messaging Permission Gates**:
  - Centralized messaging authorization into two clear gates: **History Access Gate** (for reading/viewing/opening conversation history) and **Send Gate** (for sending new messages).
  - **History Access Gate**: Allowed for students with status `ACTIVE`, `PAST_DUE`, `EXPIRED`, or `CANCELLED`. If a newer renewal attempt is `PENDING_PAYMENT` or `FAILED`, but an older valid subscription exists, history access remains allowed.
  - **Send Gate**: Strictly allowed only for students with `ACTIVE` subscription status. All other statuses (`PAST_DUE`, `EXPIRED`, `CANCELLED`, `PENDING_PAYMENT`, `TERMINATED`, or no subscription) are blocked.
  - Enforced identically across REST endpoints (`openConversation`, `history`, `myConversations` use the History gate; `sendMessage` uses the Send gate) and STOMP channels (STOMP `SUBSCRIBE` uses the History gate via `isParticipant`; STOMP `SEND` uses the Send gate via `sendMessage`).
  - Tested: 32 tests passed successfully in `MessageServiceTest` and `StompAuthChannelInterceptorTest`.

---

## Admin Report Moderation Flow

- **Moderation Status Endpoint**:
  - Implemented `PATCH /api/v1/admin/reports/{reportId}/status` in `AdminReportController` allowing admins to transition report status (`OPEN`, `REVIEWED`, `RESOLVED`, `DISMISSED`).
  - Transition validations are enforced: `RESOLVED` and `DISMISSED` are terminal states and cannot be transitioned from. Transitions back to `OPEN` are disallowed. Non-terminal same-status no-ops return successfully.
  - Admin tracking (`reviewedBy`) and timestamp (`reviewedAt`) are populated on the first moderation action.
- **Duplicate Open Report Guard**:
  - Users are blocked from submitting duplicate reports against the same target while a previous report is open (statuses: `OPEN` or `REVIEWED`). Returns `409 Conflict` + `DUPLICATE_OPEN_REPORT` error code.
  - Tested: 21 tests passed successfully across `ReportServiceTest`, `AdminReportControllerTest`, and `ReportControllerTest`.

---

## Validation

- **Backend Package:** Clean package compilation completed successfully (`.\mvnw.cmd clean package -DskipTests`).
- **Backend Tests:** Unit and slice tests (`AuthServiceTest`, `InMemoryRateLimitStoreTest`, `ClientIpResolverTest`, `AuthRateLimitServiceTest`, `AuthControllerRateLimitTest`, `OAuth2LoginCodeServiceTest`, `AuthControllerOAuth2ExchangeTest`, `OAuth2LoginSuccessHandlerTest`) completed successfully (41/41 passing).
- **Frontend Build:** The React production build successfully compiled without errors (`npm run build`).
- **Frontend Lint:** Pre-existing lint violations are present and were intentionally not fixed on this branch.
- **Docker Dependency:** The full integration test suite utilizing Testcontainers/PostgreSQL was not run locally due to the absence of a local Docker daemon.

---

## Safety & Moderation Notification Emails

Safety/moderation flows now include transactional email notifications via the best-effort `MailClient` architecture:

- **Notifications Added:**
  - **Report Received:** Confirms to the reporter that their safety report was successfully received.
    - *Subject:* `Şikayetiniz alındı`
    - *Recipient:* Reporter only. (The reported user is intentionally **not** notified to prevent retaliation/harassment).
  - **Report Status Updated:** Notifies the reporter when the status of their report transitions to `REVIEWED`, `RESOLVED`, or `DISMISSED`.
    - *Subject:* Varies based on the new status (`Şikayetiniz incelemeye alındı` / `Şikayetiniz sonuçlandırıldı` / `Şikayetiniz kapatıldı`).
    - *Recipient:* Reporter only.
    - *No-ops:* Stays quiet (no email sent) on same-status updates that do not transition the state.
  - **User Suspended:** Notifies the suspended user that their account access has been restricted.
    - *Subject:* `Hesabınız askıya alındı`
    - *Recipient:* Suspended user only. No email is sent if the suspension is rejected (self-suspend or admin target validation fails) or if the user is not found.
- **Architectural Constraints & Best-Effort Delivery:**
  - Email notifications are **best-effort** and run outside of the main database transactional boundary (dispatched from the controller layer after successful service commits).
  - Mail dispatch uses a try/catch double-backstop to ensure any mail delivery exception (e.g. timeout or provider outage) is logged and **never** rolls back the database write or breaks the API response.
  - No `@Async` or queuing/multithreading is introduced, maintaining simple, synchronous best-effort execution.
  - SMS notifications and unsuspend flows remain deferred/future-phase items.
# Password recovery/security verification

- `V22__password_recovery_and_security.sql` adds `users.password_changed_at` plus hashed, expiring, single-use reset tokens and cleanup indexes.
- Public recovery endpoints use identical forgot-password responses, IP/identifier rate limits, and no raw-token logging or persistence.
- Password change/reset revokes refresh tokens and invalidates outstanding reset links. Incremented `passwordVersion` rejects all older JWTs deterministically.
- Manual deployment check: configure the production frontend base URL and transactional mail sender, then verify the delivered link opens `/reset-password` without storing the token in browser storage.

# Email ownership verification

- `V23__email_verification.sql` adds hashed, expiring, single-use verification evidence with user/expiry/active indexes, attempt counts, optimistic locking, and scheduled retired-code cleanup.
- Password registration remains legally atomic, leaves `emailVerified=false`, and publishes the transactional verification email only after commit. The raw 6-digit code is never persisted or logged; marketing opt-out does not suppress the mail.
- Verification expires after 10 minutes, invalidates all outstanding codes on success, and is idempotent after the account is verified. Resend replaces older active codes, enforces a 60-second database-backed cooldown, and also uses the shared per-IP/per-user limiter.
- Verified Google claims skip the code flow. Google-created users still have independent `legalOnboardingCompleted=false` until they accept the required documents.
- The centralized account-readiness service checks email first and then the existing legal gate for checkout, booking, conversation creation, and message sending. Privacy/security, current-user, legal onboarding, verify/resend, refresh, and logout remain outside this product-action gate.
- Frontend `/verify-email` keeps the code in component memory only, validates exactly six digits, maps invalid/expired errors, refreshes current-user state after success, and honors server resend timing. Route priority prevents an unverified user from bypassing protected product pages without conflating legal onboarding.
- Production delivery still requires a valid Resend API key and verified sender/domain. A manual deployed smoke should confirm delivery, ten-minute copy, cooldown behavior, and the verify → legal-onboarding/dashboard redirects.

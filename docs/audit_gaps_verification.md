# Audit Gaps Verification

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
- Refund/service-commencement behavior is deliberately unchanged: the exact commencement event remains unresolved, and no automatic eligibility logic is introduced here.

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

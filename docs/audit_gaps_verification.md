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
- **Date of Birth & Onboarding:** Newly provisioned STUDENT users via Google OAuth2 currently default to `dateOfBirth = null` (no fake data is created, and login is not blocked). This is an intentional design decision; the profile completion and onboarding flow is deferred and will be revisited in a future branch.

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
- **Consent Implementation:** The current guardian/minor consent flow operates as a KVKK age gating checklist (`ConsentRecord`) and does not imply or configure a real parent login role.
- **Future Phase:** The parent account and parent panel remain deferred to a future phase.
- **Changes Made:** This branch does not implement the `PARENT` role.

---

## Refresh Token Security & Rotation

- **Current Implementation:** Refresh tokens are stored securely as SHA-256 hashes and are rotated on every token refresh request (rotation invalidates the old token and issues a new one).
- **Conscious Gaps for MVP:**
  - **Token Family Reuse/Theft Detection:** The current rotation implementation revokes the presented token but does not detect if an old revoked token is reused. In a full theft detection system, reuse of a previously revoked token in the same token family would automatically revoke all active refresh tokens in that family/session. This family-level theft detection/revocation is consciously deferred for the MVP.
  - **Session Revocation:** There is no global "logout-all-sessions" or admin-facing session revocation system, which is also deferred for future hardening.

---

## Validation

- **Backend Package:** Clean package compilation completed successfully (`.\mvnw.cmd clean package -DskipTests`).
- **Backend Tests:** Unit and slice tests (`AuthServiceTest`, `InMemoryRateLimitStoreTest`, `ClientIpResolverTest`, `AuthRateLimitServiceTest`, `AuthControllerRateLimitTest`, `OAuth2LoginCodeServiceTest`, `AuthControllerOAuth2ExchangeTest`, `OAuth2LoginSuccessHandlerTest`) completed successfully (41/41 passing).
- **Frontend Build:** The React production build successfully compiled without errors (`npm run build`).
- **Frontend Lint:** Pre-existing lint violations are present and were intentionally not fixed on this branch.
- **Docker Dependency:** The full integration test suite utilizing Testcontainers/PostgreSQL was not run locally due to the absence of a local Docker daemon.

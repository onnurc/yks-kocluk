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

- **Backend Configuration & Setup:** Backend OAuth2 flow was statically inspected. `OAuth2LoginSuccessHandler` and `AuthService.upsertGoogleUser` are successfully implemented and configured. 
- **End-to-End Test Status:** End-to-end browser OAuth2 login was not executed in this audit due to a lack of active Google credentials.
- **Frontend Integration Status:** The frontend React client does not currently contain a Google login button, an OAuth callback route, or token capture logic. 
- **Current Assessment:** Google OAuth2 login is incomplete for the current frontend.
  - If OAuth2 is part of the MVP, a separate frontend integration branch must be created to implement the login button and `/oauth/callback` routing.
  - If OAuth2 is not part of the MVP, it must remain hidden or disabled in the UI.
- **Security Recommendation:** The backend currently transports the issued JWT access and refresh tokens through URL query parameters during redirection. This approach requires security review before production deployment, as tokens passed in URLs may leak through browser history, proxy/access logs, or the `Referer` header.

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

## Validation

- **Backend Package:** Clean package compilation completed successfully (`.\mvnw.cmd clean package -DskipTests`).
- **Backend Tests:** Focused unit and slice tests (`AuthServiceTest`, `InMemoryRateLimitStoreTest`, `ClientIpResolverTest`, `AuthRateLimitServiceTest`, `AuthControllerRateLimitTest`) completed successfully (32/32 passing).
- **Frontend Build:** The React production build successfully compiled without errors (`npm run build`).
- **Frontend Lint:** Pre-existing lint violations are present and were intentionally not fixed on this branch.
- **Docker Dependency:** The full integration test suite utilizing Testcontainers/PostgreSQL was not run locally due to the absence of a local Docker daemon.

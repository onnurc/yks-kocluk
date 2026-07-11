---
name: new-entity
description: >
  Use when adding a new domain entity from scratch in this Spring Boot project
  (e.g. Package, Subscription, CoachAvailability, Session, Message, Payment, Report,
  ConsentRecord). Applies the project's fixed layering pattern and all naming/time/money
  conventions. Triggered by: "add entity", "create entity", "add X model", "scaffold X",
  "yeni entity", "entity ekle", "X modelini ekle".
---

# New Entity Recipe

Every domain object in this project is added with the same vertical slice, in this
exact order. Do not skip any step. When done, the module works end-to-end and tests pass.

**Ground yourself first:** before scaffolding, check the current branch/checkout
(`git status --short`, `git branch --show-current`) and the highest existing `V*` migration.
This project has multiple in-flight branches with diverging schema/enum state (e.g. a
`SubscriptionStatus` with 4 values on one line of work vs. 6 on another) — do not assume the
patterns below describe every branch; verify against the files actually checked out right now.

## Hard Rules (always)
- **Names are in English** — class names, field names, enum values. Turkish only for
  user-facing content (validation messages, labels).
- **Time** — all date/time fields are `Instant` (UTC). No separate date + time columns.
  Display layer converts to Europe/Istanbul.
- **Money** — `BigDecimal` / `numeric(12,2)`. Never `float` or `double`.
- **No entity leaking** — entities never leave the controller layer; use DTOs always.
- **Mapper** — use MapStruct (`@Mapper(componentModel = "spring")`). No manual mapping code.
- **No internal interfaces** — concrete service classes only. Interfaces are only for the
  4 external clients: `IyzicoClient`, `MeetClient`, `MailClient`, `StorageClient`. `MeetClient`
  (Jitsi) and `MailClient` (Resend) have real implementations; `IyzicoClient` has a real
  sandbox implementation on this branch too (see step 6b for its gating — it differs from
  Meet/Mail). `StorageClient` (Cloudflare R2) is not built yet. Do not add a 5th interface.
- **Unique / counter fields** — enforce at DB level (UNIQUE constraints, atomic conditional
  UPDATE for counters). Never rely on application-level checks alone.

## Steps

### 1. Entity
- JPA entity in the `entity` package. Fields match the data model exactly.
- Extends `BaseEntity` (provides `id`, `created_at`, `updated_at`, `version`).
- Time fields: `Instant`. Money fields: `BigDecimal`. Enums: `@Enumerated(STRING)`.
- Declare UNIQUE / index constraints via `@Table` / `@Column`.
- Boolean/int defaults live on the field itself (e.g. `private boolean autoRenew = true;`,
  `private int failedChargeCount = 0;`), not set imperatively in every constructor path.

### 2. Flyway Migration
- New file in `src/main/resources/db/migration`: `V{n}__{description}.sql`. Check the highest
  existing `V*` file **on the currently checked-out branch** first — never reuse or skip a
  number, never edit an applied migration.
- Time columns: `timestamptz`. Money columns: `numeric(12,2)`.
- Include FK, UNIQUE, and index constraints here.
- If a lifecycle enum backing a live/active-set partial-unique index gains a new value that
  should also count as "live" (e.g. adding `PENDING_PAYMENT` to `SubscriptionStatus`), the
  index has to be dropped and recreated with the widened `WHERE status IN (...)` list in the
  same migration — the enum change alone does not update the index.

### 3. Repository
- `extends JpaRepository<Entity, Long>` in the `repository` package.
- Add only the query methods actually needed. Do not store derived data — compute at query time.
- **Atomic counter pattern** (e.g. capacity, active-count fields): a `@Modifying @Query` conditional
  UPDATE, one method per direction, each floored/capped by its own `WHERE`:
  ```java
  @Modifying
  @Query("update X set n = n + 1 where id = :id and n < cap")
  int incrementIfRoom(@Param("id") Long id);   // 0 rows => full, caller throws CONFLICT

  @Modifying
  @Query("update X set n = n - 1 where id = :id and n > 0")
  int decrementIfPositive(@Param("id") Long id); // floored at 0, never negative
  ```
  Bypasses `@Version` on purpose — the conditional `WHERE` + row lock is the guard. See
  `CoachProfileRepository.incrementActiveStudentCountIfRoom` / `decrementActiveStudentCount`.
- **Pessimistic lock for read-modify-write on money rows** (e.g. computing "how much of this
  payment has already been refunded" before allowing another refund): a `findByIdForUpdate`
  query method backed by a pessimistic lock, so two concurrent refund attempts on the same
  payment serialize instead of both reading a stale "remaining refundable" figure.

### 4. DTOs
- `XxxRequest` for input, `XxxResponse` for output. Prefer Java `record`.
- Bean Validation annotations on request DTO (`@NotNull`, `@Size`, `@Email`…).
- Validation messages in Turkish: `"Ad boş olamaz"`, `"Geçerli bir e-posta girin"`.

### 5. Mapper
- MapStruct mapper in the `mapper` package. Entity ↔ DTO. No manual mapping.

### 6. Service
- Concrete `@Service` class in the `service` package. Business logic lives here.
- DB-writing methods are `@Transactional`.
- **Transaction boundary — no external calls inside a transaction.** Two sanctioned shapes,
  pick based on what the operation needs:
  - **(a) Fire-and-forget after commit** (Meet link, confirmation email): the write commits
    normally, then either the caller invokes the external client manually after the
    `@Transactional` method returns, or a `@TransactionalEventListener(AFTER_COMMIT)` listener
    does it. A failure here is logged, never rolled back, never rethrown into the seam.
  - **(b) Money-critical / needs a result recorded on both sides of the call** (a paid charge,
    a refund, anything where the DB must remember "in flight" before calling out and reconcile
    after): use `TransactionTemplate` (injected via `PlatformTransactionManager`), **not**
    `@Transactional` self-invocation (self-calls don't open a new transaction). Shape:
    ```
    tx1 (TransactionTemplate)  reserve a row PENDING with a UNIQUE idempotency key
    ---  (no transaction open)  call the external client
    tx2 (TransactionTemplate)  finalize the row + apply the outcome
    ```
    A crash between tx1 and the call leaves a resumable PENDING row (retried with the *same*
    key next time — provider-idempotent, never a double side effect). Two concurrent callers
    collide on the UNIQUE key; the loser gets a `DataIntegrityViolationException` → treat as
    "already being handled," not an error. See `SubscriptionBillingService.processDue`.
  - **Do NOT use `@Async`** (thread pool issues + lost context + difficult error handling).
- **External client config** — if the entity's service talks to one of the 4 external clients
  and needs settings (API key, base URL, rate, mode, enabled flag, etc.), add an
  `@ConfigurationProperties` record in `config` (e.g. `PaymentProperties`, `ResendProperties`,
  `IyzicoProperties`) enabled via `@EnableConfigurationProperties` on a `@Configuration` class
  (they can share one, e.g. `PaymentConfig` enables both `PaymentProperties` and
  `IyzicoProperties`).

### 6a. Idempotency key (only if the service calls an external paid/critical API)
- Add a `UNIQUE` `idempotency_key` column (migration, step 2) on whatever row represents one
  attempt (see `Payment`). Key shape: something derived from the logical operation + a
  disambiguator — `"charge:{subscriptionId}:{localDate}"` for at-most-once-per-day,
  `"checkout:{subscriptionId}"` for at-most-once-per-checkout, `"refund:{paymentId}:{n}"` for
  the n-th partial refund of a payment.
- The UNIQUE constraint is the guard, not an application-level `existsBy...` check
  (same philosophy as the booking `UNIQUE(availability_id)` guard).

### 6b. Stub-first / real external client (only if adding a NEW external integration, or
    touching an existing one)
- Interface in `integration` (only if it's genuinely one of the 4 sanctioned clients).
- **Two different gating conventions currently coexist in this codebase — match whichever
  the client you're touching already uses; don't introduce a third:**
  - **Profile-split** (`MeetClient`/`MailClient`): stub is `@Profile("test")`, real impl is
    `@Profile("!test")` — mutually exclusive, exactly one bean per profile, no `@Primary`.
    Flip both annotations in the same commit that adds the real impl (an unguarded stub plus
    an unguarded second impl is a guaranteed `NoUniqueBeanDefinitionException` at boot).
  - **`@ConditionalOnProperty`** (`IyzicoClient`/`StubIyzicoClient`): the stub carries
    `@ConditionalOnProperty(name = "payments.iyzico.enabled", havingValue = "false", matchIfMissing = true)`
    and the real impl the inverse condition — toggled by one config flag
    (`IYZICO_ENABLED` / `payments.iyzico.enabled`) rather than by Spring profile. This lets the
    real client be exercised under the `test` profile too (e.g. a sandbox-vs-stub test matrix)
    without a profile switch.
  - Whichever convention: verify at most one bean of the interface type resolves per profile
    before calling the step done — a `ApplicationContextRunner` wiring test (see
    `MeetClientWiringTest`) or an explicit `@SpringBootTest` boot is the way to prove it, not
    inspection alone.

### 7. Controller
- REST controller in the `controller` package under `/api/v1/...`.
- Accepts and returns DTOs only. Validate with `@Valid`.
- **Security now:** add `@PreAuthorize` with the correct role to every endpoint immediately.
  Never leave it for later.
- Let the global exception handler deal with errors — no try/catch in controllers.
- **Dev-only / not-production endpoints** (e.g. a stub-payment-success shortcut for manual/QA
  testing without a real payment provider): gate the whole controller with
  `@ConditionalOnProperty(prefix = "...", name = "...-enabled", havingValue = "true")` (no
  `matchIfMissing` → disabled unless explicitly turned on) **in addition to** the normal
  `@PreAuthorize` role check. Never rely on the role check alone to keep a dev shortcut out of
  production — it must not exist as a bean unless the flag is set.

### 8. Tests
Package layout mirrors the convention already in `src/test/java/com/ykskocluk/demo/`:
- **Unit** (service logic) → `service/XxxServiceTest.java`: Mockito, mock repositories and
  external clients. No Spring context, no DB.
- **Web layer** → `controller/XxxControllerTest.java`: `@WebMvcTest` + MockMvc (service mocked).
- **Repository** → `repository/XxxRepositoryTest.java`: `@DataJpaTest` with
  `@AutoConfigureTestDatabase(replace = Replace.NONE)` + Testcontainers PostgreSQL. **Do not
  use H2.**
- **Full-context / integration / concurrency** (e.g. race conditions, multi-service flows) →
  top level of `com.ykskocluk.demo` (not nested), `@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`.
- Minimum required: happy-path + validation error + wrong role (403) test.
- If the entity touches money or an idempotency key: a concurrency test is mandatory (two
  threads via `ExecutorService` + `CountDownLatch`, asserting exactly one side effect happened —
  see `SessionDoubleBookingConcurrencyTest` for the harness shape).
- If a dev-only endpoint was added (step 7), test **both** states of its flag: one test class
  with the flag on asserting the bean/endpoint exists and works, one with it off (or default)
  asserting the bean is absent / the endpoint 404s — not just "it works when enabled."

### 9. Verify Swagger
- Confirm the endpoint appears in Swagger UI with correct schema.

## Definition of Done
Endpoints work + validation present + `@PreAuthorize` present + Flyway migration applied +
errors return standard `ProblemDetail` format + happy-path / error / auth tests pass + build green.

## After completing
Summarize what was added, state which tests pass and how to run them
(`./mvnw test -Dtest=XxxTest`), then ask: "Next step is X — do you approve?"
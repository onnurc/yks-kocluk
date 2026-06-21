---
name: new-entity
description: >
  Use when adding a new domain entity from scratch in this Spring Boot project
  (e.g. Package, Subscription, CoachAvailability, Session, Message).
  Applies the project's fixed layering pattern and all naming/time/money conventions.
  Triggered by: "add entity", "create entity", "add X model", "scaffold X",
  "yeni entity", "entity ekle", "X modelini ekle".
---

# New Entity Recipe

Every domain object in this project is added with the same vertical slice, in this
exact order. Do not skip any step. When done, the module works end-to-end and tests pass.

## Hard Rules (always)
- **Names are in English** — class names, field names, enum values. Turkish only for
  user-facing content (validation messages, labels).
- **Time** — all date/time fields are `Instant` (UTC). No separate date + time columns.
  Display layer converts to Europe/Istanbul.
- **Money** — `BigDecimal` / `numeric(12,2)`. Never `float` or `double`.
- **No entity leaking** — entities never leave the controller layer; use DTOs always.
- **Mapper** — use MapStruct (`@Mapper(componentModel = "spring")`). No manual mapping code.
- **No internal interfaces** — concrete service classes only. Interfaces are only for the
  4 external clients (IyzicoClient, MeetClient, MailClient, StorageClient).
- **Unique / counter fields** — enforce at DB level (UNIQUE constraints, atomic conditional
  UPDATE for counters). Never rely on application-level checks alone.

## Steps

### 1. Entity
- JPA entity in the `entity` package. Fields match the data model exactly.
- Extends `BaseEntity` (provides `id`, `created_at`, `updated_at`, `version`).
- Time fields: `Instant`. Money fields: `BigDecimal`. Enums: `@Enumerated(STRING)`.
- Declare UNIQUE / index constraints via `@Table` / `@Column`.

### 2. Flyway Migration
- New file in `src/main/resources/db/migration`: `V{n}__{description}.sql`.
- Never edit the schema by hand — every DB change = new migration.
- Time columns: `timestamptz`. Money columns: `numeric(12,2)`.
- Include FK, UNIQUE, and index constraints here.

### 3. Repository
- `extends JpaRepository<Entity, Long>` in the `repository` package.
- Add only the query methods actually needed. Do not store derived data — compute at query time.

### 4. DTOs
- `XxxRequest` for input, `XxxResponse` for output. Prefer Java `record`.
- Bean Validation annotations on request DTO (`@NotNull`, `@Size`, `@Email`…).
- Validation messages in Turkish: `"Ad boş olamaz"`, `"Geçerli bir e-posta girin"`.

### 5. Mapper
- MapStruct mapper in the `mapper` package. Entity ↔ DTO. No manual mapping.

### 6. Service
- Concrete `@Service` class in the `service` package. Business logic lives here.
- DB-writing methods are `@Transactional`.
- **Transaction boundary:** no external calls (Meet/mail/iyzico) inside a transaction.
  Call them after commit — either after the `@Transactional` method returns, or via
  `@TransactionalEventListener(AFTER_COMMIT)`. Do NOT use `@Async`.

### 7. Controller
- REST controller in the `controller` package under `/api/v1/...`.
- Accepts and returns DTOs only. Validate with `@Valid`.
- **Security now:** add `@PreAuthorize` with the correct role to every endpoint immediately.
  Never leave it for later.
- Let the global exception handler deal with errors — no try/catch in controllers.

### 8. Tests
- **Unit** (service logic): Mockito, mock repositories and external clients. No Spring context.
- **Web layer**: `@WebMvcTest` + MockMvc (mock service).
- **Repository / integration**: `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = Replace.NONE)`
    + Testcontainers PostgreSQL. **Do not use H2.**
- Minimum required: happy-path + validation error + wrong role (403) test.

### 9. Verify Swagger
- Confirm the endpoint appears in Swagger UI with correct schema.

## Definition of Done
Endpoints work + validation present + `@PreAuthorize` present + Flyway migration applied +
errors return standard `ProblemDetail` format + happy-path / error / auth tests pass + build green.

## After completing
Summarize what was added, state which tests pass and how to run them
(`./mvnw test -Dtest=XxxTest`), then ask: "Next step is X — do you approve?"
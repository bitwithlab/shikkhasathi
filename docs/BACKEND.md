# Backend — Spring Boot (`backend/`)

Java 21 · Spring Boot 4.1 · Spring Web MVC · Spring Security · Spring Data JPA / Hibernate 7 · PostgreSQL (two databases) · Flyway · Spring Data Redis · Bean Validation · Actuator · SpringDoc OpenAPI · Maven Wrapper.

This replaced the previous Node.js/Fastify API. The API under `/api/v1` is unchanged, so the website and the mobile app did not need changes.

## Run

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Windows: .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test                                                # needs local Postgres 16 + Redis (see docs/RUNBOOK.md)
./mvnw -DskipTests package && java -jar target/shikkhasathi-backend-*.jar
```

- **Profiles and roles:**
  - The `dev` profile seeds demo data into empty databases.
  - `SS_ROLE` selects what a process runs: `api` (no job workers), `worker`, or `all` (the default).
- **Endpoints:**
  - OpenAPI: `GET /api/v1/openapi.json`, with Swagger UI at `/api/v1/docs`.
  - Health: `/actuator/health` (liveness and readiness).

## Package layout

```
com.shikkhasathi
├── ShikkhaSathiApplication
├── config/            DatabaseConfig (CORE + CONTENT), AppProperties, JsonConfig, seed/DevSeeder
├── security/          JwtService, AuthInterceptor, @Auth, @RateLimit, @CacheControl, SecurityConfig
├── common/            ApiException + ErrorCode + ApiExceptionHandler, Cursor, Crypto, Ids, jobs/, audit/
├── integration/       Admin-editable integration settings (encrypted), catalog, test-connection
└── <module>/          auth, user, student, teacher, admin, catalog, course, marketplace, classroom,
                       learning, exam, payment, notification, search, …
    ├── controller/    thin @RestController classes — no business logic
    ├── service/       business logic + transactions
    ├── repository/    Spring Data repositories (@CoreRepository / @ContentRepository) and JDBC queries
    ├── entity/        JPA entities — GENERATED (scripts/gen-entities.py), never hand-edited
    ├── dto/           request/response records (Bean Validation on requests)
    ├── mapper/        entity → DTO mapping
    └── adapter/       external services (Real + Fake), where the module owns one
```

## Rules

### Two databases (CLAUDE.md §0B)
- Entities carry `@CoreEntity` or `@ContentEntity`. Repositories carry `@CoreRepository` or `@ContentRepository`.
- Transactions:
  - CORE uses `@Transactional` (primary).
  - CONTENT uses `@Transactional(transactionManager = "contentTransactionManager")`.
- JDBC templates for reporting, aggregate or bulk SQL: `@Qualifier("coreJdbc")` and `@Qualifier("contentJdbc")` (`NamedParameterJdbcTemplate`).
- No foreign keys across databases. Join in services with batched lookups (`where id = any(:ids)` / `in (:ids)`), never one query per row.

### Schema
- Flyway migrations live in `src/main/resources/db/migration/{core,content}/V<n>__*.sql`.
- `afterMigrate.sql` re-enables deny-all RLS on every table.
- After adding a migration, regenerate the entities: `python3 scripts/gen-entities.py`.
- Existing databases (created by the previous Drizzle migrator) are baselined at V1 automatically, and their data is kept.

### API contract
- The contract is defined by `backend/src/test/resources/contracts/endpoints.json`: 179 endpoints with method, path, auth level, success status and JSON Schemas, exported from the previous shared Zod contracts.
- Responses must validate against it. Tests call `contract("group.name", resp)`.
- Rules for matching JSON:
  - Field names are camelCase, exactly as in the contract.
  - Nullable fields are present with `null`. Never omit them.
  - Timestamps are ISO-8601 UTC with milliseconds (JsonConfig does this for `Instant`).
  - Money is integer paisa.
- Every endpoint is marked with `@Auth(Level.PUBLIC|OPTIONAL|USER|SELLER|VERIFIED_SELLER|ADMIN)`, matching the contract's `auth` field. RBAC runs before validation, so a student calling an admin route gets 403, not 422.
- Rate limits use `@RateLimit(max, window)` with the window in seconds. Cache headers use `@CacheControl("public, max-age=60")`.
- Success status: use `@ResponseStatus(HttpStatus.CREATED)` when the contract says 201.
- Errors: throw `ApiException` (`ApiException.notFound("…")` and friends) with the same Bangla messages as before. The envelope is always `{"error":{"code","message","details"}}`. Validation failures are 422 with `details: [{path, message}]`.
- Pagination: cursor-based with `Cursor.encode/decode/page`. The encoding is identical to before.

### Shared services
- **Integration settings:** `IntegrationService.get("brevo")` returns the effective values, resolved Admin, then env, then default. Also available:
  - `businessNumber` and `businessFlag`;
  - `mode`;
  - `registerTester(provider, tester)` for the "সংযোগ পরীক্ষা করুন" button.
- **Jobs:** `JobQueue.enqueue(JobNames.X, payload, EnqueueOptions…)` with a `JobHandler<T>` bean per job type. Handlers must be idempotent. Tests run jobs inline.
- **Audit:** `AuditService.log(Entry.of(actor, action, entity, id).before(..).after(..))` for payments, refunds, payouts, course status, settings and moderation.
- **Fake adapters:** every external integration has a Fake used outside production when it isn't configured. Fakes with in-memory state implement `ResettableFake`.
- **Crypto:** AES-256-GCM in the `v1:` format, `hmac`, `mask` and `randomToken` (`common/Crypto`).

### Tests
- Tests extend `support.IntegrationTest`, which provides:
  - real Postgres test databases (`ss_test_core`, `ss_test_content`, created automatically);
  - Redis db 15;
  - a truncate before every test;
  - `seed()` for the demo dataset;
  - `createUser(...)` and `loginAs(email)`;
  - request helpers `get`, `post`, `put`, `patch`, `delete`;
  - `contract(...)` for contract checks.
- `ContractCoverageTest` checks that every contract endpoint exists and enforces its auth level.
- `CompatibilityTest` proves the backend reads what the previous API wrote: ciphertexts, token signatures, cursors, argon2 hashes and the seed.

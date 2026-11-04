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

## Platform services API

Shared services that domain modules build on. Inject them as Spring beans. Every external adapter has a Fake that
is used outside production when the integration is not configured, and a tester behind "সংযোগ পরীক্ষা করুন"
(registered with `IntegrationService.registerTester`). Adapters call out through `common.http.HttpTransport`
(production bean `JdkHttpTransport`), so tests pass a lambda that records requests and returns canned responses.

### Storage — `com.shikkhasathi.file.service`
- `StorageService` (bean): `SupabaseStorage` when all four `SUPABASE_*` env vars are set, else `LocalStorage` (refuses to start in production without Supabase).
  - `signedUploadUrl(Bucket, key, mime)` → `UploadTarget(url, headers)` (client PUTs the file there)
  - `signedDownloadUrl(Bucket, key, ttlSec, downloadName)` / `signedDownloadUrl(Bucket, key)` (10 min)
  - `publicUrl(Bucket, key)` (public buckets only), `put`, `get`, `exists`
- `Bucket`: `INVOICES`, `VERIFICATION_DOCS`, `AVATARS` (CORE) and `COURSE_DOCS`, `COURSE_COVERS` (CONTENT, the only public one). `Bucket.of("course-docs")`.
- `StorageKeys.objectKey(prefix, filename)` → `prefix/<16 hex>.<ext>`; `EXT_BY_MIME`, `mimeFor(key)`.
- Local routes (dev/test only, 404 with Supabase): `PUT /api/v1/files/upload?token=`, `GET /api/v1/files/download?token=`, `GET /api/v1/files/public/{bucket}/**`. Tokens are `JwtService` purpose tokens (`upload`: b,k,m; `download`: b,k,n).

### Email — `com.shikkhasathi.notification.email`
- `EmailService.send(to, toName, EmailTemplate, data, locale, relatedEntity, attachments)` → email_logs id. There is also a short form `send(to, template, data)`. It inserts `email_logs` (`queued`) and enqueues `email.send`; OTP templates (`EMAIL_OTP`, `PASSWORD_RESET`, `WELCOME`) are enqueued as sensitive. Template data is never stored.
- `email.send` (`EmailSendJob`) delivers through Brevo API → Brevo SMTP → `FakeEmailProvider` (non-prod only) and updates `email_logs` (`sent` with provider + message id, or `failed` with error and attempts + 1). A retried job never re-sends a `sent` email.
- `EmailTemplates.render(template, data, locale)` → `Rendered(subject, html, text)` — bn + en, identical to the previous API. Money values are paisa (`total`, `amount`).
- `FakeEmailProvider` (bean, `ResettableFake`): `outbox()`, `last()`.
- `EmailMessage.EmailAttachment(name, contentBase64)` for invoice PDFs.

### SMS — `com.shikkhasathi.notification.sms`
- `SmsService.provider()` → `Optional<SmsProvider>`; `send(to, message)` (503 `INTEGRATION_DISABLED` when none); `isFake()`.
- `HttpSmsProvider` (BulkSMSBD-style GET), `FakeSmsProvider` (bean, `outbox()`, `last()`).

### Push — `com.shikkhasathi.notification.push`
- `PushService.provider()` → `Optional<PushProvider>`: `FcmProvider` when the service-account JSON is set (it signs an RS256 assertion and caches the access token), else `FakePushProvider` (bean, `sent()`) outside production.
- `PushProvider.send(List<Target(platform, token)>, PushMessage(title, body, link, data))` → tokens to delete.

### Notifications — `com.shikkhasathi.notification.service`
- `NotificationService.notify(NotifyInput)` → `true` if new.
  - Dedupe key is `user:type:ref`.
  - Channels are `NotificationTypes.DEFAULT_CHANNELS` overridden by the user's preferences. When in-app is off, the row is stored already read.
  - Push is queued as `notifications.dispatch`. Email is sent when the email channel is on and an email template is given.
  - Build the input like this: `NotifyInput.of(userId, NotificationTypes.ORDER_PAID, ref, title, body).withLink("/my-courses").withEmail(EmailTemplate.ORDER_PAID, data)`. Add `.withEmailByDefault()` for admin broadcasts.
- Push dispatch:
  - Quiet hours are 23:00–07:00 Asia/Dhaka. During them a push is delayed until 07:00, except for `NotificationTypes.URGENT`.
  - Classroom bursts are batched: more than 3 in 10 minutes become one summary.
  - Invalid tokens are deleted.
  - `useClock(Clock)` is a test hook.
- Other methods: `list`, `markRead`, `unreadCount`, `preferences`, `setPreferences`, `registerPushToken`, `unregisterPushToken`. These back the `notifications` contract group.

### Video — `com.shikkhasathi.course.adapter`
- `VideoService.provider()` → `BunnyStreamProvider` (when `provider=bunny` and library id + key are set) or `FakeVideoProvider`. In production without config it throws 503 `INTEGRATION_DISABLED`. `webhookSecret()`.
- `VideoProvider.createUpload(title)` → `UploadSlot(providerVideoId, uploadUrl, headers)` (TUS); `playbackUrl(id, ttlSec)` (Bunny directory token); `status(id)` → `VideoStatus(processing|ready|failed, durationSec)`.

### Search engine — `com.shikkhasathi.search.adapter`
- `SearchEngine.meili()` → `Optional<MeiliClient>`; `usePostgresFallback()` is true when no host is set.
- `MeiliClient`: `health()`, `configure()` (uploads `INDEX_SETTINGS`: searchable/filterable attributes, synonyms, typo tolerance), `upsert(docs)`, `remove(id)`, `searchIds(q, filters, limit)`, `MeiliClient.eq(field, value)`. Index `courses`. Building documents and ranking are the search module's job.

### Payment gateways — `com.shikkhasathi.payment.adapter`
- `PaymentGateways.activeGateway()` (falls back to fake outside production).
- `gatewayByName(name)` (throws `GatewayNotConfiguredException`).
- `callbackUrl(gateway, success|fail|cancel)`, `ipnUrl(gateway)`, `fake()`.
- `PaymentGateway`: `init(InitInput)` → `InitResult(redirectUrl, sessionId)`; `validate(params)` / `query(trxId, sessionId)` → `ValidateResult(ok, status, trxId, amount, gatewayRef, raw)`; `refund(RefundRequest)` → `RefundResult(supported, refundId)`. Amounts are paisa; `PaymentGateway.taka/toPaisa` convert.
- Implementations: `SslcommerzGateway`, `BkashGateway` (grant token cached), `FakeGateway`.
  - `FakeGateway` is a bean and a `ResettableFake`.
  - `sign(trxId, outcome)` = sha256(`JWT_ACCESS_SECRET:trxId:outcome`)[:24], the same as before.
  - Results are kept in `FakeGateway.RESULTS`.
  - The fake hosted page route `/payments/fake/:trxId` belongs to the payments module.

### Invoice PDF — `com.shikkhasathi.payment.invoice`
- `InvoicePdfRenderer.render(InvoiceData)` → PDF bytes (A4, same layout as before).
- Text is shaped by Java2D (HarfBuzz) and filled as vector outlines through PdfBoxGraphics2D, so Bangla conjuncts render correctly. Bangla runs use Hind Siliguri and Latin text uses Inter.
- Fonts are in `src/main/resources/fonts/*.ttf`, converted losslessly from the WOFF files in `Web/server/assets/fonts`, because Java AWT cannot load WOFF.

### Settings and health routes
- `integration.controller.SettingsController`: `GET /admin/integrations`, `PUT /admin/integrations/{provider}`, `POST /admin/integrations/{provider}/test` (10/min), and the public `GET /config`.
- `health`: `GET /health` and `GET /admin/system-health`. The system check reports, for both DBs, connectivity, latency, the latest Flyway version (for example `V1 initial schema`) and the RLS check. It also reports Redis, `JobQueue.stats()` and each integration's mode.

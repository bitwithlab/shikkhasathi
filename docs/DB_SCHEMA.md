# Database schema

Two Supabase Postgres databases (CLAUDE.md §0B). Flyway migrations are the source of truth:
- CORE: `backend/src/main/resources/db/migration/core/V*__*.sql`
- CONTENT: `backend/src/main/resources/db/migration/content/V*__*.sql`

JPA entities are generated from the migrated schema (`python3 scripts/gen-entities.py`) into `backend/src/main/java/com/shikkhasathi/<module>/entity/`. Hibernate validates them on startup.

Conventions: `id uuid` (v7, generated in the app), `created_at`, `updated_at` (timestamptz). Money = `bigint` **paisa**. Soft delete via `deleted_at` on users, courses, refunds, payouts. No cross-database foreign keys — cross-DB ids are validated in services and joined with batched `= ANY` lookups.

## CORE
| Area | Tables |
|---|---|
| Identity | `users` (roles[], phone/email unique, password_hash argon2id, google_sub, status), `auth_otps` (hashed codes), `sessions` (hashed refresh token, family for reuse detection), `student_profiles`, `seller_profiles` |
| Commerce | `orders`, `order_items`, `payments` (unique gateway+trx), `invoices` (unique number + order), `coupons`, `coupon_redemptions`, `enrollments` (unique student+course), `refunds`, `payouts`, `ledger_entries` (unique dedupe_key) |
| Learning activity | `lesson_progress` (unique student+lesson), `notes`, `bookmarks`, `questions`, `answers`, `reviews` (unique course+student), `exam_plans`, `flashcard_reviews`, `mock_attempts`, `mock_answers`, `wishlists`, `course_requests` (unique dedupe_key), `course_request_votes` |
| Classroom cache | `google_connections` (encrypted refresh token), `classroom_courses`, `classroom_coursework` (user, due_at), `classroom_submissions`, `classroom_materials`, `classroom_announcements`, `classroom_feed_items` (user, occurred_at), `classroom_push_registrations`, `classroom_sync_runs` |
| System | `notifications` (unique dedupe_key; user, read_at, created_at), `notification_preferences`, `push_tokens`, `reports`, `audit_logs`, `settings`, `integration_settings` (AES-256-GCM), `integration_tests`, `email_logs` |

## CONTENT
| Area | Tables |
|---|---|
| Catalog | `institutions` (slug unique, aliases[], status, merged_into_id), `institution_requests`, `departments`, `programs`, `subject_masters`, `institution_subjects` |
| Courses | `courses` (slug unique; status+published_at), `course_targets` (inst, dept, semester, subject; course_code), `course_sections`, `course_chapters`, `lessons`, `video_assets` (provider id or external link), `documents` (storage key or external link), `course_revisions`, `bundles`, `bundle_items` |
| Material | `mock_tests`, `mock_questions`, `mock_options`, `flashcards` |

Spec §7.8 minimum indexes are all present.

## Migrations
| DB | File | Content |
|---|---|---|
| core | `V1__initial_schema.sql` | all CORE tables (identical to the previous Drizzle `0000` migration; databases created before are baselined at V1) |
| content | `V1__initial_schema.sql` | all CONTENT tables (same) |

After every migration, `afterMigrate.sql` (one per database) enables RLS on every table, with no policies. `CompatibilityTest` fails in CI if any table lacks it. To add a migration, write `V<n+1>__<what>.sql` in the right folder, start the backend (or run the tests), then regenerate the entities.

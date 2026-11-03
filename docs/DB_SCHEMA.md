# Database schema

Two Supabase Postgres databases (CLAUDE.md §0B). Drizzle schemas are the source of truth:
- CORE: `Web/server/src/database/core/schema/{identity,commerce,learning,classroom,system}.ts`
- CONTENT: `Web/server/src/database/content/schema/{catalog,courses,material}.ts`

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
| core | `0000_*.sql` | all CORE tables |
| content | `0000_*.sql` | all CONTENT tables |

After every migration `src/database/migrate.ts` enables RLS on every table (no policies) and fails if any table lacks it; `test/rls.test.ts` enforces the same in CI. Generate new migrations with `pnpm --filter @shikkhasathi/server db:generate`.

-- ShikkhaSathi core database — initial schema (migrated from Drizzle 0000_elite_hawkeye.sql).
-- Existing databases are baselined at V1 (spring.flyway baseline-on-migrate) so no data is touched.

CREATE TABLE "auth_otps" (
	"id" uuid PRIMARY KEY NOT NULL,
	"target" text NOT NULL,
	"code_hash" text NOT NULL,
	"purpose" text NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"attempts" integer DEFAULT 0 NOT NULL,
	"consumed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "seller_profiles" (
	"user_id" uuid PRIMARY KEY NOT NULL,
	"slug" text NOT NULL,
	"display_name" text NOT NULL,
	"headline" text,
	"bio" text,
	"cover_url" text,
	"institution_id" uuid,
	"verification_status" text DEFAULT 'none' NOT NULL,
	"verification_method" text,
	"verification_data" jsonb,
	"verification_note" text,
	"verified_at" timestamp with time zone,
	"commission_rate" integer,
	"payout_method" text,
	"payout_account_enc" text,
	"payout_account_masked" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "sessions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"family_id" uuid NOT NULL,
	"refresh_token_hash" text NOT NULL,
	"device_id" text,
	"device_name" text,
	"platform" text DEFAULT 'web' NOT NULL,
	"ip" text,
	"user_agent" text,
	"expires_at" timestamp with time zone NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"rotated_at" timestamp with time zone,
	"revoked_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "student_profiles" (
	"user_id" uuid PRIMARY KEY NOT NULL,
	"institution_id" uuid,
	"department_id" uuid,
	"program_id" uuid,
	"semester" integer,
	"batch" text,
	"section" text,
	"low_data_mode" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "users" (
	"id" uuid PRIMARY KEY NOT NULL,
	"roles" text[] DEFAULT ARRAY['student']::text[] NOT NULL,
	"name" text NOT NULL,
	"phone" text,
	"email" text,
	"email_verified_at" timestamp with time zone,
	"phone_verified_at" timestamp with time zone,
	"password_hash" text,
	"google_sub" text,
	"avatar_url" text,
	"locale" text DEFAULT 'bn' NOT NULL,
	"theme" text DEFAULT 'system' NOT NULL,
	"status" text DEFAULT 'active' NOT NULL,
	"onboarding_completed_at" timestamp with time zone,
	"last_active_at" timestamp with time zone,
	"deletion_requested_at" timestamp with time zone,
	"deleted_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "coupon_redemptions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"coupon_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"order_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "coupons" (
	"id" uuid PRIMARY KEY NOT NULL,
	"code" text NOT NULL,
	"scope" text NOT NULL,
	"seller_id" uuid,
	"course_id" uuid,
	"type" text NOT NULL,
	"value" integer NOT NULL,
	"max_uses" integer,
	"used_count" integer DEFAULT 0 NOT NULL,
	"per_user_limit" integer DEFAULT 1 NOT NULL,
	"min_amount" bigint DEFAULT 0 NOT NULL,
	"starts_at" timestamp with time zone,
	"expires_at" timestamp with time zone,
	"active" integer DEFAULT 1 NOT NULL,
	"created_by" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "enrollments" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"order_id" uuid,
	"bundle_id" uuid,
	"access_until" timestamp with time zone,
	"status" text DEFAULT 'active' NOT NULL,
	"progress_percent" integer DEFAULT 0 NOT NULL,
	"last_lesson_id" uuid,
	"last_accessed_at" timestamp with time zone,
	"completed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "invoices" (
	"id" uuid PRIMARY KEY NOT NULL,
	"order_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"number" text NOT NULL,
	"billing_name" text NOT NULL,
	"billing_phone" text,
	"billing_email" text,
	"items_json" jsonb NOT NULL,
	"subtotal" bigint NOT NULL,
	"discount" bigint DEFAULT 0 NOT NULL,
	"vat" bigint DEFAULT 0 NOT NULL,
	"total" bigint NOT NULL,
	"storage_key" text,
	"emailed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "ledger_entries" (
	"id" uuid PRIMARY KEY NOT NULL,
	"seller_id" uuid NOT NULL,
	"type" text NOT NULL,
	"amount" bigint NOT NULL,
	"order_id" uuid,
	"order_item_id" uuid,
	"payout_id" uuid,
	"refund_id" uuid,
	"description" text,
	"dedupe_key" text NOT NULL,
	"available_at" timestamp with time zone NOT NULL,
	"balance_after" bigint NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "order_items" (
	"id" uuid PRIMARY KEY NOT NULL,
	"order_id" uuid NOT NULL,
	"course_id" uuid,
	"bundle_id" uuid,
	"seller_id" uuid NOT NULL,
	"title" text NOT NULL,
	"price" bigint NOT NULL,
	"discount" bigint DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "orders" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	"subtotal" bigint NOT NULL,
	"discount" bigint DEFAULT 0 NOT NULL,
	"total" bigint NOT NULL,
	"coupon_id" uuid,
	"currency" text DEFAULT 'BDT' NOT NULL,
	"payment_method" text,
	"paid_at" timestamp with time zone,
	"fulfilled_at" timestamp with time zone,
	"idempotency_key" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "payments" (
	"id" uuid PRIMARY KEY NOT NULL,
	"order_id" uuid NOT NULL,
	"gateway" text NOT NULL,
	"gateway_trx_id" text NOT NULL,
	"gateway_session_id" text,
	"method" text,
	"amount" bigint NOT NULL,
	"status" text DEFAULT 'initiated' NOT NULL,
	"raw_payload" jsonb,
	"validated_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "payouts" (
	"id" uuid PRIMARY KEY NOT NULL,
	"seller_id" uuid NOT NULL,
	"amount" bigint NOT NULL,
	"method" text NOT NULL,
	"account_masked" text,
	"status" text DEFAULT 'requested' NOT NULL,
	"reference" text,
	"processed_by" uuid,
	"processed_at" timestamp with time zone,
	"note" text,
	"deleted_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "refunds" (
	"id" uuid PRIMARY KEY NOT NULL,
	"order_id" uuid NOT NULL,
	"requested_by" uuid NOT NULL,
	"reason" text NOT NULL,
	"status" text DEFAULT 'requested' NOT NULL,
	"amount" bigint NOT NULL,
	"processed_by" uuid,
	"processed_at" timestamp with time zone,
	"gateway_refund_id" text,
	"note" text,
	"deleted_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "answers" (
	"id" uuid PRIMARY KEY NOT NULL,
	"question_id" uuid NOT NULL,
	"author_id" uuid NOT NULL,
	"body" text NOT NULL,
	"is_seller" boolean DEFAULT false NOT NULL,
	"status" text DEFAULT 'visible' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "bookmarks" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"lesson_id" uuid NOT NULL,
	"timestamp_sec" integer,
	"body" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_request_votes" (
	"id" uuid PRIMARY KEY NOT NULL,
	"request_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_requests" (
	"id" uuid PRIMARY KEY NOT NULL,
	"requester_id" uuid NOT NULL,
	"institution_id" uuid,
	"subject_master_id" uuid,
	"subject_name" text NOT NULL,
	"course_code" text,
	"exam_type" text NOT NULL,
	"note" text,
	"status" text DEFAULT 'open' NOT NULL,
	"vote_count" integer DEFAULT 1 NOT NULL,
	"claimed_by" uuid,
	"linked_course_id" uuid,
	"dedupe_key" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "exam_plans" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"subject_master_id" uuid,
	"subject_name" text NOT NULL,
	"course_code" text,
	"exam_type" text NOT NULL,
	"exam_date" timestamp with time zone NOT NULL,
	"course_ids" uuid[] DEFAULT '{}' NOT NULL,
	"plan_json" jsonb,
	"readiness" integer DEFAULT 0 NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "flashcard_reviews" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"flashcard_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"known" boolean NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "lesson_progress" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"lesson_id" uuid NOT NULL,
	"watched_seconds" integer DEFAULT 0 NOT NULL,
	"last_position_sec" integer DEFAULT 0 NOT NULL,
	"completed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "mock_answers" (
	"id" uuid PRIMARY KEY NOT NULL,
	"attempt_id" uuid NOT NULL,
	"question_id" uuid NOT NULL,
	"option_id" uuid,
	"flagged" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "mock_attempts" (
	"id" uuid PRIMARY KEY NOT NULL,
	"test_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"student_id" uuid NOT NULL,
	"started_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deadline_at" timestamp with time zone NOT NULL,
	"submitted_at" timestamp with time zone,
	"question_order" uuid[] NOT NULL,
	"score" integer,
	"correct_count" integer,
	"wrong_count" integer,
	"skipped_count" integer,
	"result_json" jsonb,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "notes" (
	"id" uuid PRIMARY KEY NOT NULL,
	"student_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"lesson_id" uuid NOT NULL,
	"timestamp_sec" integer,
	"body" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "questions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"lesson_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"seller_id" uuid NOT NULL,
	"author_id" uuid NOT NULL,
	"body" text NOT NULL,
	"timestamp_sec" integer,
	"status" text DEFAULT 'open' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "reviews" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"seller_id" uuid NOT NULL,
	"student_id" uuid NOT NULL,
	"rating" integer NOT NULL,
	"body" text,
	"seller_reply" text,
	"seller_replied_at" timestamp with time zone,
	"verified" boolean DEFAULT true NOT NULL,
	"status" text DEFAULT 'visible' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "wishlists" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_announcements" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"gc_course_id" text NOT NULL,
	"gc_announcement_id" text NOT NULL,
	"text" text NOT NULL,
	"author_name" text,
	"materials_json" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"alternate_link" text,
	"created_at_gc" timestamp with time zone,
	"updated_at_gc" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_courses" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"gc_course_id" text NOT NULL,
	"name" text NOT NULL,
	"section" text,
	"room" text,
	"description_heading" text,
	"teacher_names" text[] DEFAULT '{}' NOT NULL,
	"alternate_link" text,
	"color" text NOT NULL,
	"state" text DEFAULT 'ACTIVE' NOT NULL,
	"updated_at_gc" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_coursework" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"gc_course_id" text NOT NULL,
	"gc_coursework_id" text NOT NULL,
	"title" text NOT NULL,
	"description" text,
	"work_type" text DEFAULT 'ASSIGNMENT' NOT NULL,
	"due_at" timestamp with time zone,
	"max_points" numeric,
	"materials_json" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"alternate_link" text,
	"state" text DEFAULT 'PUBLISHED' NOT NULL,
	"created_at_gc" timestamp with time zone,
	"updated_at_gc" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_feed_items" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"kind" text NOT NULL,
	"gc_course_id" text NOT NULL,
	"ref_id" text NOT NULL,
	"title" text NOT NULL,
	"occurred_at" timestamp with time zone NOT NULL,
	"read_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_materials" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"gc_course_id" text NOT NULL,
	"gc_material_id" text NOT NULL,
	"title" text NOT NULL,
	"description" text,
	"materials_json" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"alternate_link" text,
	"created_at_gc" timestamp with time zone,
	"updated_at_gc" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_push_registrations" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"feed_type" text NOT NULL,
	"gc_course_id" text,
	"registration_id" text NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_submissions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"gc_course_id" text NOT NULL,
	"gc_coursework_id" text NOT NULL,
	"gc_submission_id" text NOT NULL,
	"state" text NOT NULL,
	"late" boolean DEFAULT false NOT NULL,
	"assigned_grade" numeric,
	"alternate_link" text,
	"updated_at_gc" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "classroom_sync_runs" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"kind" text NOT NULL,
	"trigger" text NOT NULL,
	"started_at" timestamp with time zone DEFAULT now() NOT NULL,
	"finished_at" timestamp with time zone,
	"ok" boolean,
	"changes" integer DEFAULT 0 NOT NULL,
	"error" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "google_connections" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"google_user_id" text NOT NULL,
	"email" text NOT NULL,
	"refresh_token_enc" text NOT NULL,
	"access_token_enc" text,
	"access_token_expires_at" timestamp with time zone,
	"scopes" text[] NOT NULL,
	"status" text DEFAULT 'active' NOT NULL,
	"last_full_sync_at" timestamp with time zone,
	"last_sync_at" timestamp with time zone,
	"last_manual_sync_at" timestamp with time zone,
	"next_sync_allowed_at" timestamp with time zone,
	"error_code" text,
	"error_message" text,
	"is_fake" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "audit_logs" (
	"id" uuid PRIMARY KEY NOT NULL,
	"actor_id" uuid,
	"action" text NOT NULL,
	"entity" text NOT NULL,
	"entity_id" text,
	"before_json" jsonb,
	"after_json" jsonb,
	"ip" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "email_logs" (
	"id" uuid PRIMARY KEY NOT NULL,
	"to_email" text NOT NULL,
	"template" text NOT NULL,
	"locale" text DEFAULT 'bn' NOT NULL,
	"subject" text,
	"status" text DEFAULT 'queued' NOT NULL,
	"provider" text,
	"provider_message_id" text,
	"error" text,
	"attempts" integer DEFAULT 0 NOT NULL,
	"related_entity" text,
	"sent_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "integration_settings" (
	"id" uuid PRIMARY KEY NOT NULL,
	"provider" text NOT NULL,
	"key" text NOT NULL,
	"value_encrypted" text NOT NULL,
	"is_secret" boolean DEFAULT false NOT NULL,
	"updated_by" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "integration_tests" (
	"provider" text PRIMARY KEY NOT NULL,
	"ok" boolean NOT NULL,
	"message" text NOT NULL,
	"tested_by" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "notification_preferences" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"type" text NOT NULL,
	"channel" text NOT NULL,
	"enabled" boolean NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "notifications" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"type" text NOT NULL,
	"title" text NOT NULL,
	"body" text NOT NULL,
	"link" text,
	"data_json" jsonb,
	"dedupe_key" text NOT NULL,
	"read_at" timestamp with time zone,
	"pushed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "push_tokens" (
	"id" uuid PRIMARY KEY NOT NULL,
	"user_id" uuid NOT NULL,
	"platform" text NOT NULL,
	"token" text NOT NULL,
	"last_seen_at" timestamp with time zone DEFAULT now() NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "reports" (
	"id" uuid PRIMARY KEY NOT NULL,
	"reporter_id" uuid NOT NULL,
	"target_type" text NOT NULL,
	"target_id" uuid NOT NULL,
	"reason" text NOT NULL,
	"details" text,
	"status" text DEFAULT 'open' NOT NULL,
	"resolved_by" uuid,
	"resolution" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "settings" (
	"key" text PRIMARY KEY NOT NULL,
	"value_json" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE "seller_profiles" ADD CONSTRAINT "seller_profiles_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "student_profiles" ADD CONSTRAINT "student_profiles_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "coupon_redemptions" ADD CONSTRAINT "coupon_redemptions_coupon_id_coupons_id_fk" FOREIGN KEY ("coupon_id") REFERENCES "public"."coupons"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "coupon_redemptions" ADD CONSTRAINT "coupon_redemptions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "coupon_redemptions" ADD CONSTRAINT "coupon_redemptions_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "enrollments" ADD CONSTRAINT "enrollments_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "enrollments" ADD CONSTRAINT "enrollments_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "invoices" ADD CONSTRAINT "invoices_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "invoices" ADD CONSTRAINT "invoices_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "ledger_entries" ADD CONSTRAINT "ledger_entries_seller_id_users_id_fk" FOREIGN KEY ("seller_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "order_items" ADD CONSTRAINT "order_items_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "orders" ADD CONSTRAINT "orders_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "orders" ADD CONSTRAINT "orders_coupon_id_coupons_id_fk" FOREIGN KEY ("coupon_id") REFERENCES "public"."coupons"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "payments" ADD CONSTRAINT "payments_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "payouts" ADD CONSTRAINT "payouts_seller_id_users_id_fk" FOREIGN KEY ("seller_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "refunds" ADD CONSTRAINT "refunds_order_id_orders_id_fk" FOREIGN KEY ("order_id") REFERENCES "public"."orders"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "refunds" ADD CONSTRAINT "refunds_requested_by_users_id_fk" FOREIGN KEY ("requested_by") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "answers" ADD CONSTRAINT "answers_question_id_questions_id_fk" FOREIGN KEY ("question_id") REFERENCES "public"."questions"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "answers" ADD CONSTRAINT "answers_author_id_users_id_fk" FOREIGN KEY ("author_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "bookmarks" ADD CONSTRAINT "bookmarks_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_request_votes" ADD CONSTRAINT "course_request_votes_request_id_course_requests_id_fk" FOREIGN KEY ("request_id") REFERENCES "public"."course_requests"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_request_votes" ADD CONSTRAINT "course_request_votes_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_requests" ADD CONSTRAINT "course_requests_requester_id_users_id_fk" FOREIGN KEY ("requester_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "exam_plans" ADD CONSTRAINT "exam_plans_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "flashcard_reviews" ADD CONSTRAINT "flashcard_reviews_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "lesson_progress" ADD CONSTRAINT "lesson_progress_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "mock_answers" ADD CONSTRAINT "mock_answers_attempt_id_mock_attempts_id_fk" FOREIGN KEY ("attempt_id") REFERENCES "public"."mock_attempts"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "mock_attempts" ADD CONSTRAINT "mock_attempts_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "notes" ADD CONSTRAINT "notes_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "questions" ADD CONSTRAINT "questions_author_id_users_id_fk" FOREIGN KEY ("author_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "reviews" ADD CONSTRAINT "reviews_student_id_users_id_fk" FOREIGN KEY ("student_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "wishlists" ADD CONSTRAINT "wishlists_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_announcements" ADD CONSTRAINT "classroom_announcements_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_courses" ADD CONSTRAINT "classroom_courses_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_coursework" ADD CONSTRAINT "classroom_coursework_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_feed_items" ADD CONSTRAINT "classroom_feed_items_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_materials" ADD CONSTRAINT "classroom_materials_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_push_registrations" ADD CONSTRAINT "classroom_push_registrations_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_submissions" ADD CONSTRAINT "classroom_submissions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "classroom_sync_runs" ADD CONSTRAINT "classroom_sync_runs_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "google_connections" ADD CONSTRAINT "google_connections_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "notification_preferences" ADD CONSTRAINT "notification_preferences_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "notifications" ADD CONSTRAINT "notifications_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "push_tokens" ADD CONSTRAINT "push_tokens_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "reports" ADD CONSTRAINT "reports_reporter_id_users_id_fk" FOREIGN KEY ("reporter_id") REFERENCES "public"."users"("id") ON DELETE no action ON UPDATE no action;
CREATE INDEX "auth_otps_target_idx" ON "auth_otps" USING btree ("target","purpose","created_at");
CREATE UNIQUE INDEX "seller_profiles_slug_uq" ON "seller_profiles" USING btree ("slug");
CREATE INDEX "seller_profiles_status_idx" ON "seller_profiles" USING btree ("verification_status");
CREATE UNIQUE INDEX "sessions_token_uq" ON "sessions" USING btree ("refresh_token_hash");
CREATE INDEX "sessions_user_idx" ON "sessions" USING btree ("user_id");
CREATE UNIQUE INDEX "users_phone_uq" ON "users" USING btree ("phone");
CREATE UNIQUE INDEX "users_email_uq" ON "users" USING btree ("email");
CREATE UNIQUE INDEX "users_google_sub_uq" ON "users" USING btree ("google_sub");
CREATE INDEX "users_last_active_idx" ON "users" USING btree ("last_active_at");
CREATE UNIQUE INDEX "coupon_redemptions_order_uq" ON "coupon_redemptions" USING btree ("coupon_id","order_id");
CREATE INDEX "coupon_redemptions_user_idx" ON "coupon_redemptions" USING btree ("coupon_id","user_id");
CREATE UNIQUE INDEX "coupons_code_uq" ON "coupons" USING btree ("code");
CREATE INDEX "coupons_seller_idx" ON "coupons" USING btree ("seller_id");
CREATE UNIQUE INDEX "enrollments_student_course_uq" ON "enrollments" USING btree ("student_id","course_id");
CREATE INDEX "enrollments_course_idx" ON "enrollments" USING btree ("course_id");
CREATE UNIQUE INDEX "invoices_number_uq" ON "invoices" USING btree ("number");
CREATE UNIQUE INDEX "invoices_order_uq" ON "invoices" USING btree ("order_id");
CREATE INDEX "ledger_seller_created_idx" ON "ledger_entries" USING btree ("seller_id","created_at");
CREATE UNIQUE INDEX "ledger_dedupe_uq" ON "ledger_entries" USING btree ("dedupe_key");
CREATE INDEX "order_items_order_idx" ON "order_items" USING btree ("order_id");
CREATE INDEX "orders_student_idx" ON "orders" USING btree ("student_id","created_at");
CREATE INDEX "orders_status_idx" ON "orders" USING btree ("status");
CREATE UNIQUE INDEX "orders_idem_uq" ON "orders" USING btree ("student_id","idempotency_key");
CREATE UNIQUE INDEX "payments_gateway_trx_uq" ON "payments" USING btree ("gateway","gateway_trx_id");
CREATE INDEX "payments_order_idx" ON "payments" USING btree ("order_id");
CREATE INDEX "payouts_seller_idx" ON "payouts" USING btree ("seller_id","created_at");
CREATE INDEX "payouts_status_idx" ON "payouts" USING btree ("status");
CREATE INDEX "refunds_status_idx" ON "refunds" USING btree ("status");
CREATE UNIQUE INDEX "refunds_open_order_uq" ON "refunds" USING btree ("order_id");
CREATE INDEX "answers_question_idx" ON "answers" USING btree ("question_id");
CREATE INDEX "bookmarks_student_idx" ON "bookmarks" USING btree ("student_id","created_at");
CREATE UNIQUE INDEX "course_request_votes_uq" ON "course_request_votes" USING btree ("request_id","user_id");
CREATE UNIQUE INDEX "course_requests_dedupe_uq" ON "course_requests" USING btree ("dedupe_key");
CREATE INDEX "course_requests_status_idx" ON "course_requests" USING btree ("status","vote_count");
CREATE INDEX "exam_plans_student_idx" ON "exam_plans" USING btree ("student_id","exam_date");
CREATE UNIQUE INDEX "flashcard_reviews_uq" ON "flashcard_reviews" USING btree ("student_id","flashcard_id");
CREATE UNIQUE INDEX "lesson_progress_uq" ON "lesson_progress" USING btree ("student_id","lesson_id");
CREATE INDEX "lesson_progress_course_idx" ON "lesson_progress" USING btree ("student_id","course_id");
CREATE UNIQUE INDEX "mock_answers_uq" ON "mock_answers" USING btree ("attempt_id","question_id");
CREATE INDEX "mock_attempts_student_idx" ON "mock_attempts" USING btree ("student_id","test_id");
CREATE INDEX "notes_student_idx" ON "notes" USING btree ("student_id","created_at");
CREATE INDEX "questions_lesson_idx" ON "questions" USING btree ("lesson_id","created_at");
CREATE INDEX "questions_seller_idx" ON "questions" USING btree ("seller_id","status");
CREATE UNIQUE INDEX "reviews_course_student_uq" ON "reviews" USING btree ("course_id","student_id");
CREATE INDEX "reviews_course_idx" ON "reviews" USING btree ("course_id","created_at");
CREATE UNIQUE INDEX "wishlists_uq" ON "wishlists" USING btree ("user_id","course_id");
CREATE UNIQUE INDEX "classroom_announcements_uq" ON "classroom_announcements" USING btree ("user_id","gc_announcement_id");
CREATE UNIQUE INDEX "classroom_courses_uq" ON "classroom_courses" USING btree ("user_id","gc_course_id");
CREATE UNIQUE INDEX "classroom_coursework_uq" ON "classroom_coursework" USING btree ("user_id","gc_coursework_id");
CREATE INDEX "classroom_coursework_due_idx" ON "classroom_coursework" USING btree ("user_id","due_at");
CREATE UNIQUE INDEX "classroom_feed_uq" ON "classroom_feed_items" USING btree ("user_id","kind","ref_id");
CREATE INDEX "classroom_feed_user_idx" ON "classroom_feed_items" USING btree ("user_id","occurred_at");
CREATE UNIQUE INDEX "classroom_materials_uq" ON "classroom_materials" USING btree ("user_id","gc_material_id");
CREATE UNIQUE INDEX "classroom_push_reg_uq" ON "classroom_push_registrations" USING btree ("registration_id");
CREATE INDEX "classroom_push_reg_exp_idx" ON "classroom_push_registrations" USING btree ("expires_at");
CREATE UNIQUE INDEX "classroom_submissions_uq" ON "classroom_submissions" USING btree ("user_id","gc_coursework_id");
CREATE INDEX "classroom_sync_runs_user_idx" ON "classroom_sync_runs" USING btree ("user_id","started_at");
CREATE UNIQUE INDEX "google_connections_user_uq" ON "google_connections" USING btree ("user_id");
CREATE INDEX "google_connections_status_idx" ON "google_connections" USING btree ("status","last_sync_at");
CREATE INDEX "audit_logs_entity_idx" ON "audit_logs" USING btree ("entity","entity_id");
CREATE INDEX "audit_logs_created_idx" ON "audit_logs" USING btree ("created_at");
CREATE INDEX "email_logs_created_idx" ON "email_logs" USING btree ("created_at");
CREATE INDEX "email_logs_status_idx" ON "email_logs" USING btree ("status");
CREATE UNIQUE INDEX "integration_settings_uq" ON "integration_settings" USING btree ("provider","key");
CREATE UNIQUE INDEX "notification_prefs_uq" ON "notification_preferences" USING btree ("user_id","type","channel");
CREATE UNIQUE INDEX "notifications_dedupe_uq" ON "notifications" USING btree ("dedupe_key");
CREATE INDEX "notifications_user_idx" ON "notifications" USING btree ("user_id","read_at","created_at");
CREATE UNIQUE INDEX "push_tokens_token_uq" ON "push_tokens" USING btree ("token");
CREATE INDEX "push_tokens_user_idx" ON "push_tokens" USING btree ("user_id");
CREATE INDEX "reports_status_idx" ON "reports" USING btree ("status","created_at");
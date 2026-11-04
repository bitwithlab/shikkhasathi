-- ShikkhaSathi content database — initial schema (migrated from Drizzle 0000_kind_moira_mactaggert.sql).
-- Existing databases are baselined at V1 (spring.flyway baseline-on-migrate) so no data is touched.

CREATE TABLE "departments" (
	"id" uuid PRIMARY KEY NOT NULL,
	"institution_id" uuid NOT NULL,
	"name" text NOT NULL,
	"name_bn" text,
	"short_name" text NOT NULL,
	"icon" text,
	"position" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "institution_requests" (
	"id" uuid PRIMARY KEY NOT NULL,
	"requested_by" uuid NOT NULL,
	"name" text NOT NULL,
	"type" text NOT NULL,
	"city" text,
	"note" text,
	"status" text DEFAULT 'pending' NOT NULL,
	"resolved_institution_id" uuid,
	"reviewed_by" uuid,
	"reviewed_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "institution_subjects" (
	"id" uuid PRIMARY KEY NOT NULL,
	"institution_id" uuid NOT NULL,
	"department_id" uuid,
	"program_id" uuid,
	"semester" integer,
	"subject_master_id" uuid NOT NULL,
	"course_code" text,
	"credits" integer,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "institutions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"name_bn" text,
	"short_name" text NOT NULL,
	"slug" text NOT NULL,
	"type" text NOT NULL,
	"city" text,
	"logo_url" text,
	"website" text,
	"aliases" text[] DEFAULT '{}' NOT NULL,
	"status" text DEFAULT 'approved' NOT NULL,
	"merged_into_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "programs" (
	"id" uuid PRIMARY KEY NOT NULL,
	"department_id" uuid NOT NULL,
	"name" text NOT NULL,
	"level" text DEFAULT 'undergraduate' NOT NULL,
	"semesters" integer DEFAULT 8 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "subject_masters" (
	"id" uuid PRIMARY KEY NOT NULL,
	"name" text NOT NULL,
	"name_bn" text,
	"slug" text NOT NULL,
	"aliases" text[] DEFAULT '{}' NOT NULL,
	"category" text DEFAULT 'general' NOT NULL,
	"status" text DEFAULT 'approved' NOT NULL,
	"proposed_by" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "bundle_items" (
	"id" uuid PRIMARY KEY NOT NULL,
	"bundle_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "bundles" (
	"id" uuid PRIMARY KEY NOT NULL,
	"seller_id" uuid NOT NULL,
	"title" text NOT NULL,
	"slug" text NOT NULL,
	"description" text,
	"price" bigint NOT NULL,
	"compare_at_price" bigint,
	"status" text DEFAULT 'published' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_chapters" (
	"id" uuid PRIMARY KEY NOT NULL,
	"section_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"title" text NOT NULL,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_revisions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"payload_json" jsonb NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	"reviewer_id" uuid,
	"notes" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_sections" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"title" text NOT NULL,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "course_targets" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"is_general" boolean DEFAULT false NOT NULL,
	"institution_id" uuid,
	"department_id" uuid,
	"program_id" uuid,
	"academic_level" text,
	"semester" integer,
	"subject_master_id" uuid,
	"institution_subject_id" uuid,
	"course_code" text,
	"batches" text[] DEFAULT '{}' NOT NULL,
	"sections" text[] DEFAULT '{}' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "courses" (
	"id" uuid PRIMARY KEY NOT NULL,
	"seller_id" uuid NOT NULL,
	"title" text NOT NULL,
	"slug" text NOT NULL,
	"short_description" text DEFAULT '' NOT NULL,
	"description" text DEFAULT '' NOT NULL,
	"learnings" text[] DEFAULT '{}' NOT NULL,
	"not_for" text[] DEFAULT '{}' NOT NULL,
	"faq" jsonb DEFAULT '[]'::jsonb NOT NULL,
	"cover_url" text,
	"cover_color" text,
	"intro_video_id" uuid,
	"language" text DEFAULT 'bn' NOT NULL,
	"exam_type" text DEFAULT 'midterm' NOT NULL,
	"course_type" text DEFAULT 'crash' NOT NULL,
	"price" bigint DEFAULT 0 NOT NULL,
	"compare_at_price" bigint,
	"access_type" text DEFAULT 'lifetime' NOT NULL,
	"access_days" integer,
	"access_until" timestamp with time zone,
	"status" text DEFAULT 'draft' NOT NULL,
	"review_note" text,
	"submitted_at" timestamp with time zone,
	"published_at" timestamp with time zone,
	"content_updated_at" timestamp with time zone,
	"needs_spot_review" boolean DEFAULT false NOT NULL,
	"rating_avg" numeric(3, 2) DEFAULT '0' NOT NULL,
	"rating_count" integer DEFAULT 0 NOT NULL,
	"enrollment_count" integer DEFAULT 0 NOT NULL,
	"enrollments_30d" integer DEFAULT 0 NOT NULL,
	"total_duration_sec" integer DEFAULT 0 NOT NULL,
	"lesson_count" integer DEFAULT 0 NOT NULL,
	"view_count" integer DEFAULT 0 NOT NULL,
	"seller_verified" boolean DEFAULT false NOT NULL,
	"seller_name" text DEFAULT '' NOT NULL,
	"deleted_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "documents" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid,
	"owner_id" uuid NOT NULL,
	"kind" text NOT NULL,
	"title" text NOT NULL,
	"year" integer,
	"storage_bucket" text,
	"storage_key" text,
	"external_url" text,
	"mime" text,
	"size" integer,
	"pages" integer,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "lessons" (
	"id" uuid PRIMARY KEY NOT NULL,
	"chapter_id" uuid NOT NULL,
	"course_id" uuid NOT NULL,
	"title" text NOT NULL,
	"type" text NOT NULL,
	"position" integer NOT NULL,
	"duration_sec" integer DEFAULT 0 NOT NULL,
	"video_asset_id" uuid,
	"document_id" uuid,
	"mock_test_id" uuid,
	"body" text,
	"is_preview" boolean DEFAULT false NOT NULL,
	"is_important" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "video_assets" (
	"id" uuid PRIMARY KEY NOT NULL,
	"owner_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"provider_video_id" text,
	"external_url" text,
	"title" text,
	"status" text DEFAULT 'uploading' NOT NULL,
	"duration_sec" integer DEFAULT 0 NOT NULL,
	"thumbnail_url" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "flashcards" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"chapter_id" uuid,
	"front" text NOT NULL,
	"back" text NOT NULL,
	"position" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "mock_options" (
	"id" uuid PRIMARY KEY NOT NULL,
	"question_id" uuid NOT NULL,
	"body" text NOT NULL,
	"is_correct" boolean DEFAULT false NOT NULL,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "mock_questions" (
	"id" uuid PRIMARY KEY NOT NULL,
	"test_id" uuid NOT NULL,
	"body" text NOT NULL,
	"explanation" text,
	"chapter_tag" text,
	"chapter_id" uuid,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE "mock_tests" (
	"id" uuid PRIMARY KEY NOT NULL,
	"course_id" uuid NOT NULL,
	"title" text NOT NULL,
	"description" text,
	"duration_sec" integer DEFAULT 1800 NOT NULL,
	"shuffle" boolean DEFAULT true NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE "departments" ADD CONSTRAINT "departments_institution_id_institutions_id_fk" FOREIGN KEY ("institution_id") REFERENCES "public"."institutions"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "institution_subjects" ADD CONSTRAINT "institution_subjects_institution_id_institutions_id_fk" FOREIGN KEY ("institution_id") REFERENCES "public"."institutions"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "institution_subjects" ADD CONSTRAINT "institution_subjects_department_id_departments_id_fk" FOREIGN KEY ("department_id") REFERENCES "public"."departments"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "institution_subjects" ADD CONSTRAINT "institution_subjects_program_id_programs_id_fk" FOREIGN KEY ("program_id") REFERENCES "public"."programs"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "institution_subjects" ADD CONSTRAINT "institution_subjects_subject_master_id_subject_masters_id_fk" FOREIGN KEY ("subject_master_id") REFERENCES "public"."subject_masters"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "programs" ADD CONSTRAINT "programs_department_id_departments_id_fk" FOREIGN KEY ("department_id") REFERENCES "public"."departments"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "bundle_items" ADD CONSTRAINT "bundle_items_bundle_id_bundles_id_fk" FOREIGN KEY ("bundle_id") REFERENCES "public"."bundles"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "bundle_items" ADD CONSTRAINT "bundle_items_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_chapters" ADD CONSTRAINT "course_chapters_section_id_course_sections_id_fk" FOREIGN KEY ("section_id") REFERENCES "public"."course_sections"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_chapters" ADD CONSTRAINT "course_chapters_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_revisions" ADD CONSTRAINT "course_revisions_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_sections" ADD CONSTRAINT "course_sections_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_institution_id_institutions_id_fk" FOREIGN KEY ("institution_id") REFERENCES "public"."institutions"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_department_id_departments_id_fk" FOREIGN KEY ("department_id") REFERENCES "public"."departments"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_program_id_programs_id_fk" FOREIGN KEY ("program_id") REFERENCES "public"."programs"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_subject_master_id_subject_masters_id_fk" FOREIGN KEY ("subject_master_id") REFERENCES "public"."subject_masters"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "course_targets" ADD CONSTRAINT "course_targets_institution_subject_id_institution_subjects_id_fk" FOREIGN KEY ("institution_subject_id") REFERENCES "public"."institution_subjects"("id") ON DELETE no action ON UPDATE no action;
ALTER TABLE "courses" ADD CONSTRAINT "courses_intro_video_id_video_assets_id_fk" FOREIGN KEY ("intro_video_id") REFERENCES "public"."video_assets"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "documents" ADD CONSTRAINT "documents_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "lessons" ADD CONSTRAINT "lessons_chapter_id_course_chapters_id_fk" FOREIGN KEY ("chapter_id") REFERENCES "public"."course_chapters"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "lessons" ADD CONSTRAINT "lessons_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "lessons" ADD CONSTRAINT "lessons_video_asset_id_video_assets_id_fk" FOREIGN KEY ("video_asset_id") REFERENCES "public"."video_assets"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "lessons" ADD CONSTRAINT "lessons_document_id_documents_id_fk" FOREIGN KEY ("document_id") REFERENCES "public"."documents"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "flashcards" ADD CONSTRAINT "flashcards_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "flashcards" ADD CONSTRAINT "flashcards_chapter_id_course_chapters_id_fk" FOREIGN KEY ("chapter_id") REFERENCES "public"."course_chapters"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "mock_options" ADD CONSTRAINT "mock_options_question_id_mock_questions_id_fk" FOREIGN KEY ("question_id") REFERENCES "public"."mock_questions"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "mock_questions" ADD CONSTRAINT "mock_questions_test_id_mock_tests_id_fk" FOREIGN KEY ("test_id") REFERENCES "public"."mock_tests"("id") ON DELETE cascade ON UPDATE no action;
ALTER TABLE "mock_questions" ADD CONSTRAINT "mock_questions_chapter_id_course_chapters_id_fk" FOREIGN KEY ("chapter_id") REFERENCES "public"."course_chapters"("id") ON DELETE set null ON UPDATE no action;
ALTER TABLE "mock_tests" ADD CONSTRAINT "mock_tests_course_id_courses_id_fk" FOREIGN KEY ("course_id") REFERENCES "public"."courses"("id") ON DELETE cascade ON UPDATE no action;
CREATE UNIQUE INDEX "departments_inst_short_uq" ON "departments" USING btree ("institution_id","short_name");
CREATE INDEX "institution_requests_status_idx" ON "institution_requests" USING btree ("status","created_at");
CREATE INDEX "institution_subjects_lookup_idx" ON "institution_subjects" USING btree ("institution_id","department_id","semester");
CREATE INDEX "institution_subjects_code_idx" ON "institution_subjects" USING btree ("course_code");
CREATE UNIQUE INDEX "institutions_slug_uq" ON "institutions" USING btree ("slug");
CREATE INDEX "institutions_status_idx" ON "institutions" USING btree ("status","type");
CREATE INDEX "programs_department_idx" ON "programs" USING btree ("department_id");
CREATE UNIQUE INDEX "subject_masters_slug_uq" ON "subject_masters" USING btree ("slug");
CREATE UNIQUE INDEX "bundle_items_uq" ON "bundle_items" USING btree ("bundle_id","course_id");
CREATE UNIQUE INDEX "bundles_slug_uq" ON "bundles" USING btree ("slug");
CREATE INDEX "course_chapters_section_idx" ON "course_chapters" USING btree ("section_id","position");
CREATE INDEX "course_revisions_course_idx" ON "course_revisions" USING btree ("course_id","status");
CREATE INDEX "course_sections_course_idx" ON "course_sections" USING btree ("course_id","position");
CREATE INDEX "course_targets_match_idx" ON "course_targets" USING btree ("institution_id","department_id","semester","subject_master_id");
CREATE INDEX "course_targets_code_idx" ON "course_targets" USING btree ("course_code");
CREATE INDEX "course_targets_course_idx" ON "course_targets" USING btree ("course_id");
CREATE UNIQUE INDEX "courses_slug_uq" ON "courses" USING btree ("slug");
CREATE INDEX "courses_status_published_idx" ON "courses" USING btree ("status","published_at");
CREATE INDEX "courses_seller_idx" ON "courses" USING btree ("seller_id");
CREATE INDEX "documents_course_idx" ON "documents" USING btree ("course_id","kind");
CREATE INDEX "lessons_chapter_idx" ON "lessons" USING btree ("chapter_id","position");
CREATE INDEX "lessons_course_idx" ON "lessons" USING btree ("course_id");
CREATE INDEX "video_assets_owner_idx" ON "video_assets" USING btree ("owner_id");
CREATE UNIQUE INDEX "video_assets_provider_uq" ON "video_assets" USING btree ("provider","provider_video_id");
CREATE INDEX "flashcards_course_idx" ON "flashcards" USING btree ("course_id");
CREATE INDEX "mock_options_question_idx" ON "mock_options" USING btree ("question_id");
CREATE INDEX "mock_questions_test_idx" ON "mock_questions" USING btree ("test_id","position");
CREATE INDEX "mock_tests_course_idx" ON "mock_tests" USING btree ("course_id");
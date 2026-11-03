import { boolean, index, integer, jsonb, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { sql } from 'drizzle-orm';
import { id, timestamps, ts } from '../../columns';

export const users = pgTable(
  'users',
  {
    id: id(),
    roles: text('roles').array().notNull().default(sql`ARRAY['student']::text[]`),
    name: text('name').notNull(),
    phone: text('phone'),
    email: text('email'),
    emailVerifiedAt: ts('email_verified_at'),
    phoneVerifiedAt: ts('phone_verified_at'),
    passwordHash: text('password_hash'),
    googleSub: text('google_sub'),
    avatarUrl: text('avatar_url'),
    locale: text('locale').notNull().default('bn'),
    theme: text('theme').notNull().default('system'),
    status: text('status').notNull().default('active'),
    onboardingCompletedAt: ts('onboarding_completed_at'),
    lastActiveAt: ts('last_active_at'),
    deletionRequestedAt: ts('deletion_requested_at'),
    deletedAt: ts('deleted_at'),
    ...timestamps,
  },
  (t) => [
    uniqueIndex('users_phone_uq').on(t.phone),
    uniqueIndex('users_email_uq').on(t.email),
    uniqueIndex('users_google_sub_uq').on(t.googleSub),
    index('users_last_active_idx').on(t.lastActiveAt),
  ],
);

export const authOtps = pgTable(
  'auth_otps',
  {
    id: id(),
    /** phone (+8801…) or email, depending on purpose */
    target: text('target').notNull(),
    codeHash: text('code_hash').notNull(),
    purpose: text('purpose').notNull(),
    expiresAt: ts('expires_at').notNull(),
    attempts: integer('attempts').notNull().default(0),
    consumedAt: ts('consumed_at'),
    ...timestamps,
  },
  (t) => [index('auth_otps_target_idx').on(t.target, t.purpose, t.createdAt)],
);

export const sessions = pgTable(
  'sessions',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    /** All rotations of one login share a family; reuse of an old token revokes the family. */
    familyId: uuid('family_id').notNull(),
    refreshTokenHash: text('refresh_token_hash').notNull(),
    deviceId: text('device_id'),
    deviceName: text('device_name'),
    platform: text('platform').notNull().default('web'),
    ip: text('ip'),
    userAgent: text('user_agent'),
    expiresAt: ts('expires_at').notNull(),
    lastSeenAt: ts('last_seen_at').notNull().defaultNow(),
    rotatedAt: ts('rotated_at'),
    revokedAt: ts('revoked_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('sessions_token_uq').on(t.refreshTokenHash), index('sessions_user_idx').on(t.userId)],
);

export const studentProfiles = pgTable('student_profiles', {
  userId: uuid('user_id')
    .primaryKey()
    .references(() => users.id, { onDelete: 'cascade' }),
  institutionId: uuid('institution_id'),
  departmentId: uuid('department_id'),
  programId: uuid('program_id'),
  semester: integer('semester'),
  batch: text('batch'),
  section: text('section'),
  lowDataMode: boolean('low_data_mode').notNull().default(false),
  ...timestamps,
});

export const sellerProfiles = pgTable(
  'seller_profiles',
  {
    userId: uuid('user_id')
      .primaryKey()
      .references(() => users.id, { onDelete: 'cascade' }),
    slug: text('slug').notNull(),
    displayName: text('display_name').notNull(),
    headline: text('headline'),
    bio: text('bio'),
    coverUrl: text('cover_url'),
    institutionId: uuid('institution_id'),
    verificationStatus: text('verification_status').notNull().default('none'),
    verificationMethod: text('verification_method'),
    verificationData: jsonb('verification_data').$type<Record<string, unknown>>(),
    verificationNote: text('verification_note'),
    verifiedAt: ts('verified_at'),
    commissionRate: integer('commission_rate'),
    payoutMethod: text('payout_method'),
    payoutAccountEnc: text('payout_account_enc'),
    payoutAccountMasked: text('payout_account_masked'),
    ...timestamps,
  },
  (t) => [uniqueIndex('seller_profiles_slug_uq').on(t.slug), index('seller_profiles_status_idx').on(t.verificationStatus)],
);

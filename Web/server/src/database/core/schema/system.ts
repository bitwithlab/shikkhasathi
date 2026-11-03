import { boolean, index, integer, jsonb, pgTable, text, uniqueIndex, uuid } from 'drizzle-orm/pg-core';
import { id, timestamps, ts } from '../../columns';
import { users } from './identity';

export const notifications = pgTable(
  'notifications',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    type: text('type').notNull(),
    title: text('title').notNull(),
    body: text('body').notNull(),
    /** deep link path, e.g. /classroom/assignments?id=… */
    link: text('link'),
    dataJson: jsonb('data_json').$type<Record<string, unknown>>(),
    dedupeKey: text('dedupe_key').notNull(),
    readAt: ts('read_at'),
    pushedAt: ts('pushed_at'),
    ...timestamps,
  },
  (t) => [uniqueIndex('notifications_dedupe_uq').on(t.dedupeKey), index('notifications_user_idx').on(t.userId, t.readAt, t.createdAt)],
);

export const notificationPreferences = pgTable(
  'notification_preferences',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    type: text('type').notNull(),
    channel: text('channel').notNull(),
    enabled: boolean('enabled').notNull(),
    ...timestamps,
  },
  (t) => [uniqueIndex('notification_prefs_uq').on(t.userId, t.type, t.channel)],
);

export const pushTokens = pgTable(
  'push_tokens',
  {
    id: id(),
    userId: uuid('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    platform: text('platform').notNull(), // android | ios | web
    token: text('token').notNull(),
    lastSeenAt: ts('last_seen_at').notNull().defaultNow(),
    ...timestamps,
  },
  (t) => [uniqueIndex('push_tokens_token_uq').on(t.token), index('push_tokens_user_idx').on(t.userId)],
);

export const reports = pgTable(
  'reports',
  {
    id: id(),
    reporterId: uuid('reporter_id')
      .notNull()
      .references(() => users.id),
    targetType: text('target_type').notNull(), // course | review | question | answer | user
    targetId: uuid('target_id').notNull(),
    reason: text('reason').notNull(),
    details: text('details'),
    status: text('status').notNull().default('open'),
    resolvedBy: uuid('resolved_by'),
    resolution: text('resolution'),
    ...timestamps,
  },
  (t) => [index('reports_status_idx').on(t.status, t.createdAt)],
);

export const auditLogs = pgTable(
  'audit_logs',
  {
    id: id(),
    actorId: uuid('actor_id'),
    action: text('action').notNull(),
    entity: text('entity').notNull(),
    entityId: text('entity_id'),
    beforeJson: jsonb('before_json'),
    afterJson: jsonb('after_json'),
    ip: text('ip'),
    ...timestamps,
  },
  (t) => [index('audit_logs_entity_idx').on(t.entity, t.entityId), index('audit_logs_created_idx').on(t.createdAt)],
);

export const settings = pgTable('settings', {
  key: text('key').primaryKey(),
  valueJson: jsonb('value_json').notNull(),
  ...timestamps,
});

export const integrationSettings = pgTable(
  'integration_settings',
  {
    id: id(),
    provider: text('provider').notNull(),
    key: text('key').notNull(),
    valueEncrypted: text('value_encrypted').notNull(),
    isSecret: boolean('is_secret').notNull().default(false),
    updatedBy: uuid('updated_by'),
    ...timestamps,
  },
  (t) => [uniqueIndex('integration_settings_uq').on(t.provider, t.key)],
);

export const integrationTests = pgTable('integration_tests', {
  provider: text('provider').primaryKey(),
  ok: boolean('ok').notNull(),
  message: text('message').notNull(),
  testedBy: uuid('tested_by'),
  ...timestamps,
});

export const emailLogs = pgTable(
  'email_logs',
  {
    id: id(),
    toEmail: text('to_email').notNull(),
    template: text('template').notNull(),
    locale: text('locale').notNull().default('bn'),
    subject: text('subject'),
    status: text('status').notNull().default('queued'),
    provider: text('provider'),
    providerMessageId: text('provider_message_id'),
    error: text('error'),
    attempts: integer('attempts').notNull().default(0),
    relatedEntity: text('related_entity'),
    sentAt: ts('sent_at'),
    ...timestamps,
  },
  (t) => [index('email_logs_created_idx').on(t.createdAt), index('email_logs_status_idx').on(t.status)],
);

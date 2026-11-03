import { z } from 'zod';
import { Ok, endpoint } from './common';
import { INTEGRATION_PROVIDERS } from '../types';

export type FieldType = 'text' | 'secret' | 'email' | 'number' | 'boolean' | 'select' | 'json' | 'url';

export interface IntegrationField {
  key: string;
  label: string;
  type: FieldType;
  /** Env var used as fallback when no Admin value is stored. */
  env?: string;
  options?: readonly string[];
  placeholder?: string;
  help?: string;
  defaultValue?: string;
}

export interface IntegrationDef {
  provider: (typeof INTEGRATION_PROVIDERS)[number];
  title: string;
  description: string;
  /** Field that must be set for the integration to count as configured. */
  requiredKeys: readonly string[];
  testable: boolean;
  fields: readonly IntegrationField[];
}

/** Catalog of every Admin-editable integration (spec §16.1). Shared so the admin UI renders forms from it. */
export const INTEGRATIONS: readonly IntegrationDef[] = [
  {
    provider: 'brevo',
    title: 'Email (Brevo)',
    description: 'ট্রানজ্যাকশনাল ইমেইল — OTP, ইনভয়েস, রিমাইন্ডার। app.brevo.com → SMTP & API থেকে কী নিন।',
    requiredKeys: ['api_key', 'sender_email'],
    testable: true,
    fields: [
      { key: 'api_key', label: 'Brevo API key (v3)', type: 'secret', env: 'BREVO_API_KEY', placeholder: 'xkeysib-…' },
      { key: 'sender_name', label: 'প্রেরকের নাম', type: 'text', env: 'BREVO_SENDER_NAME', defaultValue: 'ShikkhaSathi' },
      { key: 'sender_email', label: 'প্রেরকের ইমেইল', type: 'email', env: 'BREVO_SENDER_EMAIL', placeholder: 'no-reply@shikkhasathi.com', help: 'Brevo-তে ভেরিফাইড sender/domain হতে হবে' },
      { key: 'reply_to', label: 'Reply-to', type: 'email', env: 'BREVO_REPLY_TO', placeholder: 'support@shikkhasathi.com' },
      { key: 'smtp_host', label: 'SMTP host (fallback)', type: 'text', env: 'BREVO_SMTP_HOST', defaultValue: 'smtp-relay.brevo.com' },
      { key: 'smtp_port', label: 'SMTP port', type: 'number', env: 'BREVO_SMTP_PORT', defaultValue: '587' },
      { key: 'smtp_login', label: 'SMTP login', type: 'text', env: 'BREVO_SMTP_LOGIN' },
      { key: 'smtp_key', label: 'SMTP key', type: 'secret', env: 'BREVO_SMTP_KEY' },
    ],
  },
  {
    provider: 'payment',
    title: 'পেমেন্ট গেটওয়ে',
    description: 'bKash, Nagad, Rocket ও কার্ড — SSLCommerz হোস্টেড পেজ অথবা bKash tokenized।',
    requiredKeys: ['gateway'],
    testable: true,
    fields: [
      { key: 'gateway', label: 'সক্রিয় গেটওয়ে', type: 'select', env: 'PAYMENT_GATEWAY', options: ['sslcommerz', 'bkash', 'fake'], defaultValue: 'fake' },
      { key: 'sslcommerz_store_id', label: 'SSLCommerz store id', type: 'text', env: 'SSLCOMMERZ_STORE_ID' },
      { key: 'sslcommerz_store_password', label: 'SSLCommerz store password', type: 'secret', env: 'SSLCOMMERZ_STORE_PASSWORD' },
      { key: 'sslcommerz_sandbox', label: 'Sandbox mode', type: 'boolean', env: 'SSLCOMMERZ_SANDBOX', defaultValue: 'true' },
      { key: 'bkash_app_key', label: 'bKash app key', type: 'secret', env: 'BKASH_APP_KEY' },
      { key: 'bkash_app_secret', label: 'bKash app secret', type: 'secret', env: 'BKASH_APP_SECRET' },
      { key: 'bkash_username', label: 'bKash username', type: 'text', env: 'BKASH_USERNAME' },
      { key: 'bkash_password', label: 'bKash password', type: 'secret', env: 'BKASH_PASSWORD' },
      { key: 'bkash_sandbox', label: 'bKash sandbox', type: 'boolean', env: 'BKASH_SANDBOX', defaultValue: 'true' },
    ],
  },
  {
    provider: 'sms',
    title: 'SMS',
    description: 'OTP ও ঐচ্ছিক রিমাইন্ডার SMS।',
    requiredKeys: ['api_url', 'api_key'],
    testable: true,
    fields: [
      { key: 'provider', label: 'প্রোভাইডার', type: 'select', env: 'SMS_PROVIDER', options: ['http', 'fake'], defaultValue: 'fake' },
      { key: 'api_url', label: 'API URL', type: 'url', env: 'SMS_API_URL', placeholder: 'https://bulksmsbd.net/api/smsapi' },
      { key: 'api_key', label: 'API key', type: 'secret', env: 'SMS_API_KEY' },
      { key: 'sender_id', label: 'Sender ID', type: 'text', env: 'SMS_SENDER_ID' },
    ],
  },
  {
    provider: 'video',
    title: 'ভিডিও প্রোভাইডার',
    description: 'Bunny Stream (ডিফল্ট) — HLS, signed URL।',
    requiredKeys: ['library_id', 'api_key'],
    testable: true,
    fields: [
      { key: 'provider', label: 'প্রোভাইডার', type: 'select', env: 'VIDEO_PROVIDER', options: ['bunny', 'fake'], defaultValue: 'fake' },
      { key: 'library_id', label: 'Bunny library id', type: 'text', env: 'BUNNY_STREAM_LIBRARY_ID' },
      { key: 'api_key', label: 'Bunny Stream API key', type: 'secret', env: 'BUNNY_STREAM_API_KEY' },
      { key: 'token_key', label: 'Token authentication key', type: 'secret', env: 'BUNNY_TOKEN_KEY' },
      { key: 'cdn_host', label: 'CDN host', type: 'text', env: 'BUNNY_CDN_HOST', placeholder: 'vz-xxxx.b-cdn.net' },
      { key: 'webhook_secret', label: 'Webhook secret', type: 'secret', env: 'VIDEO_WEBHOOK_SECRET' },
    ],
  },
  {
    provider: 'google',
    title: 'Google OAuth + Classroom',
    description: 'Google সাইন-ইন এবং Classroom (read-only) সংযোগ, Pub/Sub push।',
    requiredKeys: ['client_id', 'client_secret'],
    testable: true,
    fields: [
      { key: 'client_id', label: 'Web client id', type: 'text', env: 'GOOGLE_CLIENT_ID' },
      { key: 'client_secret', label: 'Web client secret', type: 'secret', env: 'GOOGLE_CLIENT_SECRET' },
      { key: 'android_client_id', label: 'Android client id', type: 'text', env: 'GOOGLE_ANDROID_CLIENT_ID' },
      { key: 'ios_client_id', label: 'iOS client id', type: 'text', env: 'GOOGLE_IOS_CLIENT_ID' },
      { key: 'pubsub_topic', label: 'Pub/Sub topic', type: 'text', env: 'GOOGLE_PUBSUB_TOPIC', placeholder: 'projects/shikkhasathi/topics/classroom' },
      { key: 'pubsub_verification_token', label: 'Pub/Sub push verification token', type: 'secret', env: 'GOOGLE_PUBSUB_VERIFICATION_TOKEN' },
    ],
  },
  {
    provider: 'push',
    title: 'Push notification',
    description: 'FCM (Android/web), APNs (iOS), Web Push (VAPID)।',
    requiredKeys: ['fcm_service_account_json'],
    testable: true,
    fields: [
      { key: 'fcm_service_account_json', label: 'FCM service account JSON', type: 'secret', env: 'FCM_SERVICE_ACCOUNT_JSON' },
      { key: 'apns_key_id', label: 'APNs key id', type: 'text', env: 'APNS_KEY_ID' },
      { key: 'apns_team_id', label: 'APNs team id', type: 'text', env: 'APNS_TEAM_ID' },
      { key: 'apns_private_key', label: 'APNs private key (.p8)', type: 'secret', env: 'APNS_PRIVATE_KEY' },
      { key: 'web_push_public_key', label: 'Web Push public key', type: 'text', env: 'WEB_PUSH_PUBLIC_KEY' },
      { key: 'web_push_private_key', label: 'Web Push private key', type: 'secret', env: 'WEB_PUSH_PRIVATE_KEY' },
    ],
  },
  {
    provider: 'search',
    title: 'সার্চ (Meilisearch)',
    description: 'না থাকলে Postgres full-text fallback ব্যবহার হয়।',
    requiredKeys: ['host'],
    testable: true,
    fields: [
      { key: 'host', label: 'Meilisearch host', type: 'url', env: 'MEILI_HOST', placeholder: 'http://meilisearch:7700' },
      { key: 'master_key', label: 'Master / API key', type: 'secret', env: 'MEILI_MASTER_KEY' },
    ],
  },
  {
    provider: 'business',
    title: 'ব্যবসায়িক সেটিংস',
    description: 'কমিশন, রিফান্ড উইন্ডো, পেআউট ও ফিচার ফ্ল্যাগ।',
    requiredKeys: [],
    testable: false,
    fields: [
      { key: 'commission_percent', label: 'ডিফল্ট কমিশন (%)', type: 'number', env: 'COMMISSION_PERCENT', defaultValue: '20' },
      { key: 'refund_window_hours', label: 'রিফান্ড উইন্ডো (ঘণ্টা)', type: 'number', defaultValue: '48' },
      { key: 'refund_max_watch_percent', label: 'রিফান্ডের সর্বোচ্চ দেখা (%)', type: 'number', defaultValue: '20' },
      { key: 'ledger_hold_days', label: 'আয় হোল্ড (দিন)', type: 'number', defaultValue: '7' },
      { key: 'payout_min_taka', label: 'সর্বনিম্ন পেআউট (৳)', type: 'number', defaultValue: '500' },
      { key: 'vat_percent', label: 'VAT (%) — ইনভয়েসে', type: 'number', defaultValue: '0' },
      { key: 'feature_bundles', label: 'বান্ডেল চালু', type: 'boolean', defaultValue: 'true' },
      { key: 'feature_course_requests', label: 'কোর্স অনুরোধ বোর্ড চালু', type: 'boolean', defaultValue: 'true' },
      { key: 'feature_classroom', label: 'Google Classroom চালু', type: 'boolean', defaultValue: 'true' },
      { key: 'max_devices', label: 'সর্বোচ্চ ডিভাইস (প্লেব্যাক)', type: 'number', defaultValue: '3' },
    ],
  },
] as const;

export const IntegrationFieldValue = z.object({
  key: z.string(),
  /** Non-secret value, or masked `••••1234` for secrets (never the real secret). */
  value: z.string().nullable(),
  source: z.enum(['admin', 'env', 'default', 'unset']),
  isSecret: z.boolean(),
  updatedAt: z.string().nullable(),
});

export const IntegrationState = z.object({
  provider: z.enum(INTEGRATION_PROVIDERS),
  mode: z.enum(['configured', 'env', 'fake', 'disabled']),
  fields: z.array(IntegrationFieldValue),
  lastTest: z
    .object({ ok: z.boolean(), message: z.string(), at: z.string() })
    .nullable(),
});
export type IntegrationState = z.infer<typeof IntegrationState>;

export const ProviderParams = z.object({ provider: z.enum(INTEGRATION_PROVIDERS) });

export const UpdateIntegrationBody = z.object({
  /** key → new value. `null` clears the admin value (falls back to env). Secrets: empty string = unchanged. */
  values: z.record(z.string(), z.string().nullable()),
});

export const TestIntegrationBody = z.object({ to: z.string().optional() });
export const TestIntegrationResponse = z.object({ ok: z.boolean(), message: z.string() });

export const settings = {
  list: endpoint({ method: 'GET', path: '/admin/integrations', summary: 'All integrations with masked values', tag: 'admin', auth: 'admin', response: z.object({ items: z.array(IntegrationState) }) }),
  update: endpoint({ method: 'PUT', path: '/admin/integrations/:provider', summary: 'Save integration values (audit-logged)', tag: 'admin', auth: 'admin', params: ProviderParams, body: UpdateIntegrationBody, response: IntegrationState }),
  test: endpoint({ method: 'POST', path: '/admin/integrations/:provider/test', summary: 'Test connection / send test email', tag: 'admin', auth: 'admin', params: ProviderParams, body: TestIntegrationBody, response: TestIntegrationResponse }),
  publicConfig: endpoint({
    method: 'GET',
    path: '/config',
    summary: 'Public client config (Google client id, feature flags)',
    tag: 'system',
    auth: 'public',
    response: z.object({
      googleClientId: z.string().nullable(),
      webPushPublicKey: z.string().nullable(),
      features: z.object({ bundles: z.boolean(), courseRequests: z.boolean(), classroom: z.boolean() }),
      paymentGateway: z.string(),
    }),
  }),
};

export { Ok };

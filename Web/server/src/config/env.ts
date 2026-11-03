import { z } from 'zod';

/**
 * Bootstrap configuration — env only (CLAUDE.md §0B). Integration credentials are NOT here;
 * they come from modules/settings (Admin panel → env fallback → disabled).
 */
const EnvSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().default(3000),
  HOST: z.string().default('0.0.0.0'),
  APP_URL: z.url().default('http://localhost:3000'),
  API_URL: z.url().optional(),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),

  CORE_DATABASE_URL: z.string().default('postgres://postgres:postgres@localhost:5432/shikkhasathi_core'),
  CORE_DIRECT_URL: z.string().optional(),
  CONTENT_DATABASE_URL: z.string().default('postgres://postgres:postgres@localhost:5432/shikkhasathi_content'),
  CONTENT_DIRECT_URL: z.string().optional(),
  /** Set to true when CORE/CONTENT_DATABASE_URL point at the Supabase transaction pooler (port 6543). */
  DB_POOLER: z
    .string()
    .optional()
    .transform((v) => v === 'true' || v === '1'),
  DB_POOL_MAX: z.coerce.number().int().default(10),

  SUPABASE_CORE_URL: z.string().optional(),
  SUPABASE_CORE_SERVICE_ROLE_KEY: z.string().optional(),
  SUPABASE_CONTENT_URL: z.string().optional(),
  SUPABASE_CONTENT_SERVICE_ROLE_KEY: z.string().optional(),

  REDIS_URL: z.string().default('redis://localhost:6379'),
  /** 32-byte key, base64 or hex. Used for AES-256-GCM of integration secrets + Google tokens. */
  ENCRYPTION_KEY: z.string().default('dev-only-insecure-key-change-me-0000000000000000'),
  JWT_ACCESS_SECRET: z.string().min(16).default('dev-access-secret-change-me-please'),
  JWT_REFRESH_SECRET: z.string().min(16).default('dev-refresh-secret-change-me-please'),
  SENTRY_DSN: z.string().optional(),

  /** Directory used by the local storage adapter when Supabase Storage is not configured. */
  LOCAL_STORAGE_DIR: z.string().default('./storage-local'),
  CLIENT_DIST_DIR: z.string().default('../client/dist'),
  /** Disable the in-process worker (tests). */
  INLINE_JOBS: z
    .string()
    .optional()
    .transform((v) => v === 'true' || v === '1'),
});

export type Env = z.infer<typeof EnvSchema>;

function load(): Env {
  const parsed = EnvSchema.safeParse(process.env);
  if (!parsed.success) {
    console.error('Invalid environment:', z.prettifyError(parsed.error));
    throw new Error('Invalid environment');
  }
  const env = parsed.data;
  if (env.NODE_ENV === 'production') {
    const insecure = [
      env.ENCRYPTION_KEY.startsWith('dev-') && 'ENCRYPTION_KEY',
      env.JWT_ACCESS_SECRET.startsWith('dev-') && 'JWT_ACCESS_SECRET',
      env.JWT_REFRESH_SECRET.startsWith('dev-') && 'JWT_REFRESH_SECRET',
    ].filter(Boolean);
    if (insecure.length) throw new Error(`Refusing to start in production with default secrets: ${insecure.join(', ')}`);
  }
  return env;
}

export const env = load();
export const isProd = env.NODE_ENV === 'production';
export const isTest = env.NODE_ENV === 'test';

import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, stat } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { env, isProd } from '../../config/env';
import { signPurposeToken, verifyPurposeToken } from '../../common/jwt';

/** Buckets live with the data they belong to (CLAUDE.md §0B). */
export const BUCKETS = {
  invoices: { db: 'core', public: false },
  'verification-docs': { db: 'core', public: false },
  avatars: { db: 'core', public: false },
  'course-docs': { db: 'content', public: false },
  'course-covers': { db: 'content', public: true },
} as const;
export type Bucket = keyof typeof BUCKETS;

export interface ObjectStorage {
  readonly name: 'supabase' | 'local';
  /** Signed URL the client PUTs the file to. */
  signedUploadUrl(bucket: Bucket, key: string, mime: string): Promise<{ url: string; headers: Record<string, string> }>;
  /** Short-lived signed download URL (private buckets). */
  signedDownloadUrl(bucket: Bucket, key: string, ttlSec?: number, downloadName?: string): Promise<string>;
  publicUrl(bucket: Bucket, key: string): string;
  put(bucket: Bucket, key: string, body: Buffer, mime: string): Promise<void>;
  get(bucket: Bucket, key: string): Promise<Buffer>;
  exists(bucket: Bucket, key: string): Promise<boolean>;
}

/** Supabase Storage REST (service-role key, server-only). */
export class SupabaseStorage implements ObjectStorage {
  readonly name = 'supabase' as const;
  constructor(private readonly projects: { core: { url: string; key: string }; content: { url: string; key: string } }, private readonly fetchImpl: typeof fetch = fetch) {}
  private p(bucket: Bucket) {
    return this.projects[BUCKETS[bucket].db];
  }
  private headers(bucket: Bucket) {
    const k = this.p(bucket).key;
    return { authorization: `Bearer ${k}`, apikey: k };
  }
  async signedUploadUrl(bucket: Bucket, key: string) {
    const { url } = this.p(bucket);
    const res = await this.fetchImpl(`${url}/storage/v1/object/upload/sign/${bucket}/${key}`, { method: 'POST', headers: this.headers(bucket) });
    if (!res.ok) throw new Error(`storage sign upload ${res.status}`);
    const data = (await res.json()) as { url: string };
    return { url: `${url}/storage/v1${data.url}`, headers: {} };
  }
  async signedDownloadUrl(bucket: Bucket, key: string, ttlSec = 600, downloadName?: string) {
    const { url } = this.p(bucket);
    const res = await this.fetchImpl(`${url}/storage/v1/object/sign/${bucket}/${key}`, { method: 'POST', headers: { ...this.headers(bucket), 'content-type': 'application/json' }, body: JSON.stringify({ expiresIn: ttlSec, ...(downloadName ? { download: downloadName } : {}) }) });
    if (!res.ok) throw new Error(`storage sign ${res.status}`);
    const data = (await res.json()) as { signedURL: string };
    return `${url}/storage/v1${data.signedURL}`;
  }
  publicUrl(bucket: Bucket, key: string) {
    return `${this.p(bucket).url}/storage/v1/object/public/${bucket}/${key}`;
  }
  async put(bucket: Bucket, key: string, body: Buffer, mime: string) {
    const res = await this.fetchImpl(`${this.p(bucket).url}/storage/v1/object/${bucket}/${key}`, { method: 'POST', headers: { ...this.headers(bucket), 'content-type': mime, 'x-upsert': 'true' }, body: new Uint8Array(body) });
    if (!res.ok) throw new Error(`storage put ${res.status}`);
  }
  async get(bucket: Bucket, key: string) {
    const res = await this.fetchImpl(`${this.p(bucket).url}/storage/v1/object/${bucket}/${key}`, { headers: this.headers(bucket) });
    if (!res.ok) throw new Error(`storage get ${res.status}`);
    return Buffer.from(await res.arrayBuffer());
  }
  async exists(bucket: Bucket, key: string) {
    const res = await this.fetchImpl(`${this.p(bucket).url}/storage/v1/object/info/${bucket}/${key}`, { headers: this.headers(bucket) });
    return res.ok;
  }
}

/** Dev/test: files on disk under LOCAL_STORAGE_DIR, served by /api/v1/files/* with signed tokens. */
export class LocalStorage implements ObjectStorage {
  readonly name = 'local' as const;
  private path(bucket: Bucket, key: string) {
    const root = resolve(process.cwd(), env.LOCAL_STORAGE_DIR);
    const p = resolve(root, bucket, key);
    if (!p.startsWith(root)) throw new Error('bad key');
    return p;
  }
  async signedUploadUrl(bucket: Bucket, key: string, mime: string) {
    const token = await signPurposeToken('upload', { b: bucket, k: key, m: mime }, 3600);
    return { url: `${env.APP_URL}/api/v1/files/upload?token=${token}`, headers: { 'content-type': mime } };
  }
  async signedDownloadUrl(bucket: Bucket, key: string, ttlSec = 600, downloadName?: string) {
    const token = await signPurposeToken('download', { b: bucket, k: key, n: downloadName ?? null }, ttlSec);
    return `${env.APP_URL}/api/v1/files/download?token=${token}`;
  }
  publicUrl(bucket: Bucket, key: string) {
    return `${env.APP_URL}/api/v1/files/public/${bucket}/${key}`;
  }
  async put(bucket: Bucket, key: string, body: Buffer) {
    const p = this.path(bucket, key);
    await mkdir(dirname(p), { recursive: true });
    await writeFile(p, body);
  }
  async get(bucket: Bucket, key: string) {
    return readFile(this.path(bucket, key));
  }
  async exists(bucket: Bucket, key: string) {
    return stat(this.path(bucket, key)).then(() => true, () => false);
  }
  static verifyUpload(token: string) {
    return verifyPurposeToken<{ b: Bucket; k: string; m: string }>('upload', token);
  }
  static verifyDownload(token: string) {
    return verifyPurposeToken<{ b: Bucket; k: string; n: string | null }>('download', token);
  }
}

let instance: ObjectStorage | null = null;
export function storage(): ObjectStorage {
  if (instance) return instance;
  if (env.SUPABASE_CORE_URL && env.SUPABASE_CORE_SERVICE_ROLE_KEY && env.SUPABASE_CONTENT_URL && env.SUPABASE_CONTENT_SERVICE_ROLE_KEY) {
    instance = new SupabaseStorage({ core: { url: env.SUPABASE_CORE_URL, key: env.SUPABASE_CORE_SERVICE_ROLE_KEY }, content: { url: env.SUPABASE_CONTENT_URL, key: env.SUPABASE_CONTENT_SERVICE_ROLE_KEY } });
  } else {
    if (isProd) throw new Error('Supabase Storage env (SUPABASE_*_URL / SERVICE_ROLE_KEY) is required in production');
    instance = new LocalStorage();
  }
  return instance;
}

export function objectKey(prefix: string, filename: string) {
  const ext = (filename.split('.').pop() ?? 'bin').toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 5) || 'bin';
  const rand = createHash('sha256').update(`${Date.now()}-${Math.random()}`).digest('hex').slice(0, 16);
  return `${prefix}/${rand}.${ext}`;
}

export const EXT_BY_MIME: Record<string, string> = { 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx' };

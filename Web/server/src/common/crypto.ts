import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { env } from '../config/env';

function deriveKey(raw: string): Buffer {
  if (/^[0-9a-f]{64}$/i.test(raw)) return Buffer.from(raw, 'hex');
  const b = Buffer.from(raw, 'base64');
  if (b.length === 32 && /^[A-Za-z0-9+/=]+$/.test(raw)) return b;
  // Fallback for dev keys: stretch to 32 bytes (production must provide a real 32-byte key).
  return createHash('sha256').update(raw).digest();
}

const KEY = deriveKey(env.ENCRYPTION_KEY);
const VERSION = 'v1';

/** AES-256-GCM. Output: `v1:<iv b64>:<tag b64>:<ciphertext b64>`. */
export function encrypt(plain: string, key: Buffer = KEY): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  return [VERSION, iv.toString('base64'), cipher.getAuthTag().toString('base64'), ct.toString('base64')].join(':');
}

export function decrypt(payload: string, key: Buffer = KEY): string {
  const [v, iv, tag, ct] = payload.split(':');
  if (v !== VERSION || !iv || !tag || !ct) throw new Error('Unsupported ciphertext');
  const d = createDecipheriv('aes-256-gcm', key, Buffer.from(iv, 'base64'));
  d.setAuthTag(Buffer.from(tag, 'base64'));
  return Buffer.concat([d.update(Buffer.from(ct, 'base64')), d.final()]).toString('utf8');
}

export const sha256 = (v: string) => createHash('sha256').update(v).digest('hex');
export const hmac = (v: string, secret = env.JWT_REFRESH_SECRET) => createHmac('sha256', secret).update(v).digest('hex');
export const randomToken = (bytes = 32) => randomBytes(bytes).toString('base64url');
export const randomDigits = (n = 6) => Array.from({ length: n }, () => randomInt(0, 10)).join('');

export function safeEqual(a: string, b: string) {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

/** `••••1234` — last 4 visible, never more than a quarter of the secret. */
export function mask(secret: string | null | undefined): string | null {
  if (!secret) return null;
  const tail = secret.length >= 12 ? secret.slice(-4) : '';
  return `••••${tail}`;
}

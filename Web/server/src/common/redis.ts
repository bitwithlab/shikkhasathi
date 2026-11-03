import { Redis } from 'ioredis';
import { env } from '../config/env';

let client: Redis | null = null;

/** Shared Redis connection (cache, rate limits). BullMQ creates its own connections. */
export function redis(): Redis {
  if (!client) client = new Redis(env.REDIS_URL, { maxRetriesPerRequest: 2, lazyConnect: false });
  return client;
}

export function bullConnection() {
  const u = new URL(env.REDIS_URL);
  return {
    host: u.hostname,
    port: Number(u.port || 6379),
    username: u.username || undefined,
    password: u.password ? decodeURIComponent(u.password) : undefined,
    db: u.pathname && u.pathname.length > 1 ? Number(u.pathname.slice(1)) : 0,
    tls: u.protocol === 'rediss:' ? {} : undefined,
    maxRetriesPerRequest: null,
  };
}

export async function closeRedis() {
  if (client) {
    await client.quit().catch(() => {});
    client = null;
  }
}

/** Fixed-window counter. Returns remaining attempts; throws nothing. */
export async function hitLimit(key: string, max: number, windowSec: number) {
  const r = redis();
  const n = await r.incr(key);
  if (n === 1) await r.expire(key, windowSec);
  const ttl = await r.ttl(key);
  return { allowed: n <= max, count: n, retryAfterSec: ttl > 0 ? ttl : windowSec };
}

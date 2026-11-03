import { describe, expect, it } from 'vitest';
import { allEndpoints } from '@shikkhasathi/shared';
import { getApp, createUser } from './helpers';

const UUID = '0192f0c0-0000-7000-8000-000000000000';
const url = (path: string) => `/api/v1${path.replace(/:(\w+)/g, (_m, k: string) => (/(slug|code|provider|gateway)/i.test(k) ? 'x' : UUID))}`;

/** Every contract endpoint must enforce its declared auth level — no route can silently ship unprotected. */
describe('RBAC sweep over every endpoint', () => {
  it('auth-required endpoints reject anonymous requests with 401', async () => {
    const app = await getApp();
    const bad: string[] = [];
    for (const e of allEndpoints()) {
      if (e.auth === 'public' || e.auth === 'optional') continue;
      const r = await app.inject({ method: e.method, url: url(e.path), payload: ['POST', 'PUT', 'PATCH'].includes(e.method) ? {} : undefined });
      if (r.statusCode !== 401) bad.push(`${e.method} ${e.path} → ${r.statusCode}`);
    }
    expect(bad).toEqual([]);
  });

  it('admin endpoints reject students (403); seller endpoints reject non-sellers (403)', async () => {
    const app = await getApp();
    const student = await createUser({ name: 'Plain Student' });
    const bad: string[] = [];
    for (const e of allEndpoints()) {
      if (e.auth !== 'admin' && e.auth !== 'seller' && e.auth !== 'verified_seller') continue;
      const r = await app.inject({ method: e.method, url: url(e.path), headers: student.auth, payload: ['POST', 'PUT', 'PATCH'].includes(e.method) ? {} : undefined });
      // 422 would mean validation ran before RBAC — also a leak of the endpoint's shape
      if (r.statusCode !== 403) bad.push(`${e.method} ${e.path} → ${r.statusCode}`);
    }
    expect(bad).toEqual([]);
  });

  it('every /admin path is admin-only and every /seller path is seller-only by contract (except the application flow)', () => {
    // becoming a teacher: any signed-in user may apply, verify a university email and upload an ID card (service rejects other upload kinds)
    const APPLY = new Set(['GET /seller/profile', 'POST /seller/apply', 'POST /seller/verify-email/request', 'POST /seller/verify-email/confirm', 'POST /seller/uploads']);
    const wrong = allEndpoints().filter((e) => (e.path.startsWith('/admin/') && e.auth !== 'admin') || (e.path.startsWith('/seller/') && !['seller', 'verified_seller'].includes(e.auth) && !(APPLY.has(`${e.method} ${e.path}`) && e.auth === 'user')));
    expect(wrong.map((e) => `${e.method} ${e.path} (${e.auth})`)).toEqual([]);
  });
});

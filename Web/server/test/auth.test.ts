import { describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { coreDb } from '../src/database/client';
import { authOtps, sessions, users } from '../src/database/core/schema';
import { FakeSmsProvider } from '../src/services/sms';
import { FakeEmailProvider } from '../src/services/email';

const json = (r: { json: () => unknown }) => r.json() as Record<string, any>; // eslint-disable-line @typescript-eslint/no-explicit-any

describe('phone OTP', () => {
  it('normalises the phone, sends SMS, signs up a new user', async () => {
    const app = await getApp();
    const req = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '01712-345678' } });
    expect(req.statusCode).toBe(200);
    const code = json(req).devCode as string;
    expect(code).toMatch(/^\d{6}$/);
    expect(FakeSmsProvider.outbox.at(-1)!.to).toBe('+8801712345678');
    const [otp] = await coreDb.select().from(authOtps);
    expect(otp!.codeHash).not.toContain(code);

    const v = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '+8801712345678', code, platform: 'android' } });
    expect(v.statusCode).toBe(200);
    const body = json(v);
    expect(body.isNewUser).toBe(true);
    expect(body.user.phone).toBe('+8801712345678');
    expect(body.refreshToken).toBeTruthy();

    const me = await app.inject({ method: 'GET', url: '/api/v1/me', headers: { authorization: `Bearer ${body.accessToken}` } });
    expect(json(me).roles).toEqual(['student']);
  });

  it('rejects invalid numbers, wrong codes, reused codes', async () => {
    const app = await getApp();
    const bad = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '12345' } });
    expect(bad.statusCode).toBe(422);
    const req = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '01812345678' } });
    const code = json(req).devCode as string;
    const wrong = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '01812345678', code: code === '000000' ? '111111' : '000000' } });
    expect(wrong.statusCode).toBe(400);
    const ok = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '01812345678', code } });
    expect(ok.statusCode).toBe(200);
    const again = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '01812345678', code } });
    expect(again.statusCode).toBe(400);
  });

  it('enforces resend cooldown', async () => {
    const app = await getApp();
    await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '01912345678' } });
    const second = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '01912345678' } });
    expect(second.statusCode).toBe(429);
    expect(json(second).error.code).toBe('RATE_LIMITED');
  });

  it('locks a code after 5 wrong attempts', async () => {
    const app = await getApp();
    const req = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/request', payload: { phone: '01512345678' } });
    const code = json(req).devCode as string;
    const wrongCode = code === '999999' ? '888888' : '999999';
    for (let i = 0; i < 5; i++) await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '01512345678', code: wrongCode } });
    const r = await app.inject({ method: 'POST', url: '/api/v1/auth/otp/verify', payload: { phone: '01512345678', code } });
    expect(r.statusCode).toBe(429);
  });
});

describe('email + password', () => {
  it('registers with argon2id, sends welcome email, logs in, rejects bad password', async () => {
    const app = await getApp();
    const reg = await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'রহিম', email: 'Rahim@Example.com', password: 'correct horse 1', platform: 'android' } });
    expect(reg.statusCode).toBe(201);
    const [u] = await coreDb.select().from(users).where(eq(users.email, 'rahim@example.com'));
    expect(u!.passwordHash).toMatch(/^\$argon2id\$/);
    expect(FakeEmailProvider.outbox.at(-1)!.to).toBe('rahim@example.com');

    const dup = await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'XY', email: 'rahim@example.com', password: 'another pass 2' } });
    expect(dup.statusCode).toBe(409);

    const bad = await app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'rahim@example.com', password: 'nope-nope' } });
    expect(bad.statusCode).toBe(401);
    const good = await app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'rahim@example.com', password: 'correct horse 1', platform: 'ios' } });
    expect(good.statusCode).toBe(200);
    expect(json(good).user.hasPassword).toBe(true);
  });

  it('verifies email with the welcome code and resets password', async () => {
    const app = await getApp();
    const reg = await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'Karim', email: 'karim@example.com', password: 'password-123', platform: 'android' } });
    const code = FakeEmailProvider.outbox.at(-1)!.text.match(/\b(\d{6})\b/)![1]!;
    const v = await app.inject({ method: 'POST', url: '/api/v1/me/verify-email', headers: { authorization: `Bearer ${json(reg).accessToken}` }, payload: { code } });
    expect(json(v).emailVerified).toBe(true);

    await app.inject({ method: 'POST', url: '/api/v1/auth/email-otp', payload: { email: 'karim@example.com', purpose: 'password_reset' } });
    const reset = FakeEmailProvider.outbox.at(-1)!.text.match(/\b(\d{6})\b/)![1]!;
    const r = await app.inject({ method: 'POST', url: '/api/v1/auth/password-reset', payload: { email: 'karim@example.com', code: reset, password: 'brand-new-pass' } });
    expect(r.statusCode).toBe(200);
    const login = await app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 'karim@example.com', password: 'brand-new-pass' } });
    expect(login.statusCode).toBe(200);
  });

  it('does not reveal unknown emails on reset request', async () => {
    const app = await getApp();
    const r = await app.inject({ method: 'POST', url: '/api/v1/auth/email-otp', payload: { email: 'ghost@example.com', purpose: 'password_reset' } });
    expect(r.statusCode).toBe(200);
    expect(FakeEmailProvider.outbox).toHaveLength(0);
  });
});

describe('Google sign-in (fake verifier when no client id)', () => {
  it('creates then links by google sub', async () => {
    const app = await getApp();
    const a = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { idToken: 'fake:nadia@gmail.com:Nadia', platform: 'android' } });
    expect(json(a).isNewUser).toBe(true);
    const b = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { idToken: 'fake:nadia@gmail.com:Nadia', platform: 'android' } });
    expect(json(b).isNewUser).toBe(false);
    expect(json(b).user.id).toBe(json(a).user.id);
    const bad = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { idToken: 'not-a-google-token' } });
    expect(bad.statusCode).toBe(401);
  });
});

describe('refresh rotation', () => {
  it('rotates (mobile body token) and detects reuse after grace by revoking the family', async () => {
    const app = await getApp();
    const { refreshToken, user } = await createUser();
    const r1 = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken } });
    expect(r1.statusCode).toBe(200);
    const rt2 = json(r1).refreshToken as string;
    expect(rt2).not.toBe(refreshToken);

    // simulate old token replayed long after rotation
    await coreDb.update(sessions).set({ rotatedAt: new Date(Date.now() - 60_000) }).where(eq(sessions.userId, user.id));
    const replay = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken } });
    expect(replay.statusCode).toBe(401);
    const r3 = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: rt2 } });
    expect(r3.statusCode).toBe(401);
  });

  it('web flow: httpOnly cookie + CSRF double-submit', async () => {
    const app = await getApp();
    const reg = await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'Web', email: 'web@example.com', password: 'password-123' } });
    expect(json(reg).refreshToken).toBeUndefined();
    const cookies = reg.cookies as { name: string; value: string; httpOnly?: boolean }[];
    const rt = cookies.find((c) => c.name === 'ss_rt')!;
    const csrf = cookies.find((c) => c.name === 'ss_csrf')!;
    expect(rt.httpOnly).toBe(true);
    const noCsrf = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', cookies: { ss_rt: rt.value, ss_csrf: csrf.value }, payload: {} });
    expect(noCsrf.statusCode).toBe(400);
    const ok = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', cookies: { ss_rt: rt.value, ss_csrf: csrf.value }, headers: { 'x-csrf-token': csrf.value }, payload: {} });
    expect(ok.statusCode).toBe(200);
    expect(json(ok).accessToken).toBeTruthy();
  });

  it('lists and revokes sessions', async () => {
    const app = await getApp();
    const a = await createUser();
    const list = await app.inject({ method: 'GET', url: '/api/v1/me/sessions', headers: a.auth });
    const items = json(list).items as { id: string; current: boolean }[];
    expect(items).toHaveLength(1);
    expect(items[0]!.current).toBe(true);
    await app.inject({ method: 'DELETE', url: `/api/v1/me/sessions/${items[0]!.id}`, headers: a.auth });
    const r = await app.inject({ method: 'POST', url: '/api/v1/auth/refresh', payload: { refreshToken: a.refreshToken } });
    expect(r.statusCode).toBe(401);
  });
});

describe('RBAC + envelope', () => {
  it('returns the error envelope for 404 and bad tokens', async () => {
    const app = await getApp();
    const nf = await app.inject({ method: 'GET', url: '/api/v1/does-not-exist' });
    expect(nf.statusCode).toBe(404);
    expect(json(nf).error.code).toBe('NOT_FOUND');
    const bad = await app.inject({ method: 'GET', url: '/api/v1/me', headers: { authorization: 'Bearer garbage' } });
    expect(bad.statusCode).toBe(401);
  });
  it('suspended users cannot sign in', async () => {
    const app = await getApp();
    await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'Sumi', email: 's@example.com', password: 'password-123' } });
    await coreDb.update(users).set({ status: 'suspended' }).where(eq(users.email, 's@example.com'));
    const r = await app.inject({ method: 'POST', url: '/api/v1/auth/login', payload: { email: 's@example.com', password: 'password-123' } });
    expect(r.statusCode).toBe(403);
  });
  it('response contract strips private fields (no password hash in /me)', async () => {
    const app = await getApp();
    const reg = await app.inject({ method: 'POST', url: '/api/v1/auth/register', payload: { name: 'Polash', email: 'p@example.com', password: 'password-123' } });
    expect(reg.body).not.toContain('argon2');
  });
});

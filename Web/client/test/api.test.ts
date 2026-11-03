import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, contracts, tokenStore } from '@/lib/api';

afterEach(() => {
  vi.restoreAllMocks();
  tokenStore.set(null);
  document.cookie = 'ss_csrf=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
});

describe('api client', () => {
  it('maps the error envelope to ApiError with the server message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ error: { code: 'RATE_LIMITED', message: '৬০ সেকেন্ড পর আবার চেষ্টা করুন' } }), { status: 429 }));
    const err = await api(contracts.auth.otpRequest, { body: { phone: '01712345678' } }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.code).toBe('RATE_LIMITED');
    expect(err.message).toContain('৬০');
  });
  it('refreshes once on 401 using the cookie + CSRF header, then retries', async () => {
    document.cookie = 'ss_csrf=abc';
    tokenStore.set('old');
    const calls: { url: string; init: RequestInit }[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      calls.push({ url: String(url), init: init! });
      if (String(url).endsWith('/auth/refresh')) return new Response(JSON.stringify({ accessToken: 'new', accessTokenExpiresAt: '', user: { id: 'u' }, isNewUser: false }), { status: 200 });
      const auth = (init!.headers as Record<string, string>).authorization;
      return auth === 'Bearer new' ? new Response(JSON.stringify({ id: 'u' }), { status: 200 }) : new Response('{}', { status: 401 });
    });
    const me = await api(contracts.auth.me);
    expect(me).toEqual({ id: 'u' });
    const refresh = calls.find((c) => c.url.endsWith('/auth/refresh'))!;
    expect((refresh.init.headers as Record<string, string>)['x-csrf-token']).toBe('abc');
    expect(calls.filter((c) => c.url.endsWith('/me'))).toHaveLength(2);
  });
  it('builds paths and query strings', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"ok":true}', { status: 200 }));
    await api(contracts.auth.revokeSession, { params: { id: '0192f0c0-0000-7000-8000-000000000000' } });
    expect(String(spy.mock.calls[0]![0])).toBe('/api/v1/me/sessions/0192f0c0-0000-7000-8000-000000000000');
  });
});

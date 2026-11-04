import { describe, expect, it } from 'vitest';
import { z } from 'zod';
import { BdPhone, OtpVerifyBody, RegisterBody, allEndpoints, INTEGRATIONS } from '@/api';

describe('contracts', () => {
  it('normalises Bangladeshi phone numbers', () => {
    for (const v of ['01712345678', '+8801712345678', '8801712345678', '017-1234 5678']) expect(BdPhone.parse(v)).toBe('+8801712345678');
    for (const v of ['01212345678', '1712345678', '+880171234567']) expect(BdPhone.safeParse(v).success).toBe(false);
  });
  it('round-trips request bodies', () => {
    const body = { phone: '01812345678', code: '123456', platform: 'android' as const };
    expect(OtpVerifyBody.parse(OtpVerifyBody.parse(body))).toEqual({ ...body, phone: '+8801812345678' });
    expect(RegisterBody.parse({ name: 'রহিম', email: 'A@B.COM', password: 'password1' })).toMatchObject({ email: 'a@b.com', asSeller: false, platform: 'web' });
  });
  it('every endpoint is unique, versioned-relative and JSON-schema representable', () => {
    const keys = allEndpoints().map((e) => `${e.method} ${e.path}`);
    expect(new Set(keys).size).toBe(keys.length);
    for (const e of allEndpoints()) {
      expect(e.path.startsWith('/')).toBe(true);
      expect(e.path.startsWith('/api')).toBe(false);
      expect(() => z.toJSONSchema(e.response, { unrepresentable: 'any' })).not.toThrow();
    }
  });
  it('integration catalog marks secrets as secret fields', () => {
    for (const d of INTEGRATIONS) for (const f of d.fields) if (/key$|secret|password|_json$/.test(f.key) && !/public|_id$/.test(f.key)) expect(f.type, `${d.provider}.${f.key}`).toBe('secret');
  });
});

import { endpoints } from '@/api/contracts/endpoints.gen';
import { contracts } from '@/api/contracts';

describe('endpoints.gen.ts (zod-free client map)', () => {
  it('is in sync with the contracts — run `pnpm gen:endpoints` after changing contracts', () => {
    const shape = (c: Record<string, Record<string, { method: string; path: string; auth: string }>>) =>
      Object.fromEntries(Object.entries(c).map(([g, eps]) => [g, Object.fromEntries(Object.entries(eps).map(([k, e]) => [k, `${e.method} ${e.path} ${e.auth}`]))]));
    expect(shape(endpoints as never)).toEqual(shape(contracts as never));
  });
});

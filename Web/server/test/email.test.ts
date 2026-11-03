import { describe, expect, it, vi } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { coreDb } from '../src/database/client';
import { emailLogs } from '../src/database/core/schema';
import { BrevoApiProvider } from '../src/services/email/brevo-api';
import { FakeEmailProvider, renderEmail, sendEmail } from '../src/services/email';
import { EMAIL_TEMPLATES } from '@shikkhasathi/shared';

describe('email', () => {
  it('BrevoApiProvider posts to /v3/smtp/email with api-key header', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ messageId: '<abc@brevo>' }), { status: 201 }));
    const p = new BrevoApiProvider('xkeysib-1', fetchMock as unknown as typeof fetch);
    const r = await p.send({ to: 'a@b.com', subject: 'S', html: '<p>h</p>', text: 't' }, { name: 'ShikkhaSathi', email: 'no-reply@shikkhasathi.com', replyTo: 'support@shikkhasathi.com' });
    expect(r.messageId).toBe('<abc@brevo>');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('https://api.brevo.com/v3/smtp/email');
    expect((init.headers as Record<string, string>)['api-key']).toBe('xkeysib-1');
    const payload = JSON.parse(init.body as string);
    expect(payload.sender).toEqual({ name: 'ShikkhaSathi', email: 'no-reply@shikkhasathi.com' });
    expect(payload.replyTo).toEqual({ email: 'support@shikkhasathi.com' });
    expect(payload.textContent).toBe('t');
  });

  it('BrevoApiProvider surfaces API errors', async () => {
    const fetchMock = vi.fn(async () => new Response('{"code":"unauthorized"}', { status: 401 }));
    const p = new BrevoApiProvider('bad', fetchMock as unknown as typeof fetch);
    await expect(p.send({ to: 'a@b.com', subject: 'S', html: 'h', text: 't' }, { name: 'n', email: 'e@x.com' })).rejects.toThrow(/401/);
  });

  it('every template renders bn + en with a plain-text part and branding', () => {
    for (const t of EMAIL_TEMPLATES) {
      for (const l of ['bn', 'en'] as const) {
        const r = renderEmail(t, { name: 'Rahim', code: '123456', total: 29900, items: 'DS', invoiceNumber: 'SS-2026-000001', subject: 'Hi', body: 'Hello' }, l);
        expect(r.subject.length).toBeGreaterThan(0);
        expect(r.text.length).toBeGreaterThan(10);
        expect(r.html).toMatch(/শিক্ষাসাথী|ShikkhaSathi/);
      }
    }
  });

  it('escapes user data in templates', () => {
    const r = renderEmail('welcome', { name: '<script>x</script>' }, 'bn');
    expect(r.html).not.toContain('<script>x');
  });

  it('queues through email.send, logs status, never stores the OTP', async () => {
    await sendEmail({ to: 'student@example.com', template: 'email_otp', data: { code: '654321' } });
    const rows = await coreDb.select().from(emailLogs).where(eq(emailLogs.toEmail, 'student@example.com'));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.status).toBe('sent');
    expect(rows[0]!.provider).toBe('fake');
    expect(JSON.stringify(rows)).not.toContain('654321');
    expect(FakeEmailProvider.outbox.at(-1)!.text).toContain('654321');
  });

  it('admin "টেস্ট মেইল পাঠান" works with fake provider and records last test', async () => {
    const app = await getApp();
    const admin = await createUser({ roles: ['admin'], email: 'admin@shikkhasathi.com' });
    const r = await app.inject({ method: 'POST', url: '/api/v1/admin/integrations/brevo/test', headers: admin.auth, payload: { to: 'admin@shikkhasathi.com' } });
    expect(r.json().ok).toBe(true);
    const list = await app.inject({ method: 'GET', url: '/api/v1/admin/integrations', headers: admin.auth });
    expect(list.json().items.find((i: { provider: string }) => i.provider === 'brevo').lastTest.ok).toBe(true);
  });
});

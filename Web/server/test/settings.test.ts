import { describe, expect, it } from 'vitest';
import { eq } from 'drizzle-orm';
import { getApp, createUser } from './helpers';
import { coreDb } from '../src/database/client';
import { auditLogs, integrationSettings } from '../src/database/core/schema';
import { getIntegration, invalidateSettingsCache } from '../src/modules/settings/settings.service';

describe('settings / integrations', () => {
  it('requires admin', async () => {
    const app = await getApp();
    const student = await createUser();
    const r = await app.inject({ method: 'GET', url: '/api/v1/admin/integrations', headers: student.auth });
    expect(r.statusCode).toBe(403);
    const anon = await app.inject({ method: 'GET', url: '/api/v1/admin/integrations' });
    expect(anon.statusCode).toBe(401);
  });

  it('stores secrets encrypted, never returns them, audit-logs changes', async () => {
    const app = await getApp();
    const admin = await createUser({ roles: ['student', 'admin'] });
    const put = await app.inject({
      method: 'PUT',
      url: '/api/v1/admin/integrations/brevo',
      headers: admin.auth,
      payload: { values: { api_key: 'xkeysib-very-secret-9876', sender_email: 'no-reply@shikkhasathi.com' } },
    });
    expect(put.statusCode).toBe(200);
    const body = put.json();
    expect(JSON.stringify(body)).not.toContain('very-secret');
    expect(body.fields.find((f: { key: string }) => f.key === 'api_key').value).toBe('••••9876');
    expect(body.mode).toBe('configured');

    const [row] = await coreDb.select().from(integrationSettings).where(eq(integrationSettings.key, 'api_key'));
    expect(row!.valueEncrypted).not.toContain('very-secret');
    expect(row!.isSecret).toBe(true);
    expect((await getIntegration('brevo')).api_key).toBe('xkeysib-very-secret-9876');

    // empty secret = unchanged (write-only)
    await app.inject({ method: 'PUT', url: '/api/v1/admin/integrations/brevo', headers: admin.auth, payload: { values: { api_key: '' } } });
    expect((await getIntegration('brevo')).api_key).toBe('xkeysib-very-secret-9876');

    const logs = await coreDb.select().from(auditLogs).where(eq(auditLogs.action, 'integration.update'));
    expect(logs.length).toBe(2);
    expect(JSON.stringify(logs)).not.toContain('very-secret');
  });

  it('falls back to env, then default, and null clears admin value', async () => {
    const app = await getApp();
    const admin = await createUser({ roles: ['admin'] });
    process.env.BREVO_SENDER_NAME = 'From Env';
    expect((await getIntegration('brevo')).sender_name).toBe('From Env');
    await app.inject({ method: 'PUT', url: '/api/v1/admin/integrations/brevo', headers: admin.auth, payload: { values: { sender_name: 'Admin Name' } } });
    expect((await getIntegration('brevo')).sender_name).toBe('Admin Name');
    await app.inject({ method: 'PUT', url: '/api/v1/admin/integrations/brevo', headers: admin.auth, payload: { values: { sender_name: null } } });
    expect((await getIntegration('brevo')).sender_name).toBe('From Env');
    delete process.env.BREVO_SENDER_NAME;
    invalidateSettingsCache();
    const list = await app.inject({ method: 'GET', url: '/api/v1/admin/integrations', headers: admin.auth });
    const brevo = list.json().items.find((i: { provider: string }) => i.provider === 'brevo');
    expect(brevo.fields.find((f: { key: string }) => f.key === 'sender_name').source).toBe('default');
    expect(brevo.mode).toBe('fake');
  });

  it('validates field types', async () => {
    const app = await getApp();
    const admin = await createUser({ roles: ['admin'] });
    const r = await app.inject({ method: 'PUT', url: '/api/v1/admin/integrations/business', headers: admin.auth, payload: { values: { commission_percent: 'abc' } } });
    expect(r.statusCode).toBe(400);
    const r2 = await app.inject({ method: 'PUT', url: '/api/v1/admin/integrations/payment', headers: admin.auth, payload: { values: { gateway: 'paypal' } } });
    expect(r2.statusCode).toBe(400);
  });
});

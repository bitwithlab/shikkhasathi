import { isProd } from '../../config/env';
import { logger } from '../../common/logger';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';

export interface SmsProvider {
  readonly name: string;
  send(to: string, message: string): Promise<void>;
}

/** Generic HTTP SMS gateway (BulkSMSBD-style query API). */
export class HttpSmsProvider implements SmsProvider {
  readonly name = 'http';
  constructor(private readonly cfg: { apiUrl: string; apiKey: string; senderId?: string }, private readonly fetchImpl: typeof fetch = fetch) {}
  async send(to: string, message: string) {
    const url = new URL(this.cfg.apiUrl);
    url.searchParams.set('api_key', this.cfg.apiKey);
    url.searchParams.set('number', to.replace(/^\+/, ''));
    url.searchParams.set('message', message);
    if (this.cfg.senderId) url.searchParams.set('senderid', this.cfg.senderId);
    const res = await this.fetchImpl(url, { method: 'GET', signal: AbortSignal.timeout(10_000) });
    if (!res.ok) throw new Error(`SMS gateway ${res.status}`);
  }
}

/** Dev/test: records messages; prints only in development (never in production). */
export class FakeSmsProvider implements SmsProvider {
  readonly name = 'fake';
  static outbox: { to: string; message: string }[] = [];
  async send(to: string, message: string) {
    FakeSmsProvider.outbox.push({ to, message });
    if (FakeSmsProvider.outbox.length > 200) FakeSmsProvider.outbox.shift();
    if (process.env.NODE_ENV === 'development') logger.info({ to }, `[fake-sms] ${message}`);
  }
}

export async function smsProvider(): Promise<SmsProvider | null> {
  const c = await getIntegration('sms');
  if (c.provider === 'http' && c.api_url && c.api_key) return new HttpSmsProvider({ apiUrl: c.api_url, apiKey: c.api_key, senderId: c.sender_id });
  return isProd ? null : new FakeSmsProvider();
}

export async function isFakeSms() {
  return (await smsProvider())?.name === 'fake';
}

registerIntegrationTest('sms', async (c, { to }) => {
  if (!(c.provider === 'http' && c.api_url && c.api_key)) return { ok: false, message: 'SMS API URL ও key দিন' };
  if (!to) return { ok: false, message: 'টেস্ট SMS-এর জন্য নম্বর দিন' };
  await new HttpSmsProvider({ apiUrl: c.api_url, apiKey: c.api_key, senderId: c.sender_id }).send(to, 'ShikkhaSathi test SMS ✓');
  return { ok: true, message: `টেস্ট SMS পাঠানো হয়েছে → ${to}` };
});

import { SignJWT, importPKCS8 } from 'jose';
import { isProd } from '../../config/env';
import { logger } from '../../common/logger';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';

export interface PushMessage {
  title: string;
  body: string;
  link?: string | null;
  data?: Record<string, string>;
}

export interface PushProvider {
  readonly name: string;
  /** Returns tokens that are permanently invalid and should be deleted. */
  send(tokens: { platform: string; token: string }[], msg: PushMessage): Promise<{ invalid: string[] }>;
}

/** FCM HTTP v1 (Android + web). Uses the service-account JSON from Admin → Integrations → Push. */
export class FcmProvider implements PushProvider {
  readonly name = 'fcm';
  private cached: { token: string; exp: number } | null = null;
  constructor(private readonly sa: { project_id: string; client_email: string; private_key: string }, private readonly fetchImpl: typeof fetch = fetch) {}

  private async accessToken() {
    if (this.cached && this.cached.exp > Date.now() + 60_000) return this.cached.token;
    const key = await importPKCS8(this.sa.private_key, 'RS256');
    const now = Math.floor(Date.now() / 1000);
    const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
      .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
      .setIssuer(this.sa.client_email)
      .setAudience('https://oauth2.googleapis.com/token')
      .setIssuedAt(now)
      .setExpirationTime(now + 3600)
      .sign(key);
    const res = await this.fetchImpl('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }) });
    if (!res.ok) throw new Error(`FCM auth ${res.status}`);
    const data = (await res.json()) as { access_token: string; expires_in: number };
    this.cached = { token: data.access_token, exp: Date.now() + data.expires_in * 1000 };
    return data.access_token;
  }

  async send(tokens: { platform: string; token: string }[], msg: PushMessage) {
    const at = await this.accessToken();
    const invalid: string[] = [];
    for (const t of tokens.filter((x) => x.platform !== 'ios' || true)) {
      const res = await this.fetchImpl(`https://fcm.googleapis.com/v1/projects/${this.sa.project_id}/messages:send`, {
        method: 'POST',
        headers: { authorization: `Bearer ${at}`, 'content-type': 'application/json' },
        body: JSON.stringify({
          message: {
            token: t.token,
            notification: { title: msg.title, body: msg.body },
            data: { ...(msg.data ?? {}), ...(msg.link ? { link: msg.link } : {}) },
            android: { priority: 'high', notification: { channel_id: 'classroom', click_action: 'OPEN_LINK' } },
            webpush: msg.link ? { fcm_options: { link: msg.link } } : undefined,
          },
        }),
      });
      if (res.status === 404 || res.status === 400) invalid.push(t.token);
    }
    return { invalid };
  }
}

export class FakePushProvider implements PushProvider {
  readonly name = 'fake';
  static sent: { tokens: string[]; msg: PushMessage }[] = [];
  async send(tokens: { platform: string; token: string }[], msg: PushMessage) {
    FakePushProvider.sent.push({ tokens: tokens.map((t) => t.token), msg });
    if (FakePushProvider.sent.length > 200) FakePushProvider.sent.shift();
    if (process.env.NODE_ENV === 'development') logger.info({ n: tokens.length }, `[fake-push] ${msg.title}`);
    return { invalid: [] };
  }
}

export async function pushProvider(): Promise<PushProvider | null> {
  const c = await getIntegration('push');
  if (c.fcm_service_account_json) {
    try {
      return new FcmProvider(JSON.parse(c.fcm_service_account_json));
    } catch {
      logger.error('invalid FCM service account JSON');
    }
  }
  return isProd ? null : new FakePushProvider();
}

registerIntegrationTest('push', async (c) => {
  if (!c.fcm_service_account_json) return { ok: false, message: 'FCM service account JSON দিন' };
  const sa = JSON.parse(c.fcm_service_account_json);
  const p = new FcmProvider(sa);
  await (p as unknown as { accessToken: () => Promise<string> }).accessToken();
  return { ok: true, message: `FCM সংযোগ ঠিক আছে (${sa.project_id})` };
});

import type { EmailMessage, EmailProvider, EmailSender } from './provider';

/** Brevo transactional API: POST https://api.brevo.com/v3/smtp/email with `api-key` header. */
export class BrevoApiProvider implements EmailProvider {
  readonly name = 'brevo_api' as const;
  constructor(
    private readonly apiKey: string,
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly baseUrl = 'https://api.brevo.com/v3',
  ) {}

  async send(msg: EmailMessage, from: EmailSender) {
    const res = await this.fetchImpl(`${this.baseUrl}/smtp/email`, {
      method: 'POST',
      headers: { 'api-key': this.apiKey, 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({
        sender: { name: from.name, email: from.email },
        to: [{ email: msg.to, ...(msg.toName ? { name: msg.toName } : {}) }],
        ...(from.replyTo ? { replyTo: { email: from.replyTo } } : {}),
        subject: msg.subject,
        htmlContent: msg.html,
        textContent: msg.text,
        ...(msg.tags?.length ? { tags: msg.tags } : {}),
        ...(msg.attachments?.length ? { attachment: msg.attachments.map((a) => ({ name: a.name, content: a.contentBase64 })) } : {}),
      }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`Brevo API ${res.status}: ${body.slice(0, 200)}`);
    }
    const data = (await res.json().catch(() => ({}))) as { messageId?: string };
    return { messageId: data.messageId ?? null };
  }

  /** GET /account — used by the "test connection" button. */
  async ping() {
    const res = await this.fetchImpl(`${this.baseUrl}/account`, { headers: { 'api-key': this.apiKey, accept: 'application/json' }, signal: AbortSignal.timeout(10_000) });
    if (!res.ok) throw new Error(`Brevo API ${res.status}`);
    return (await res.json()) as { email?: string; companyName?: string };
  }
}

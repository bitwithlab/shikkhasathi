import { randomUUID } from 'node:crypto';
import type { EmailMessage, EmailProvider, EmailSender } from './provider';

export interface SentFake {
  to: string;
  subject: string;
  text: string;
  html: string;
  from: EmailSender;
}

/** Dev/test provider: keeps messages in memory (for tests) and logs the subject only. */
export class FakeEmailProvider implements EmailProvider {
  readonly name = 'fake' as const;
  static outbox: SentFake[] = [];
  async send(msg: EmailMessage, from: EmailSender) {
    FakeEmailProvider.outbox.push({ to: msg.to, subject: msg.subject, text: msg.text, html: msg.html, from });
    if (FakeEmailProvider.outbox.length > 200) FakeEmailProvider.outbox.shift();
    if (process.env.NODE_ENV !== 'test') console.info(`[fake-email] → ${msg.to}: ${msg.subject}`);
    return { messageId: `fake-${randomUUID()}` };
  }
}

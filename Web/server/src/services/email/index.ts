import { eq } from 'drizzle-orm';
import type { EmailTemplate, Locale } from '@shikkhasathi/shared';
import { coreDb } from '../../database/client';
import { emailLogs } from '../../database/core/schema';
import { defineJob, enqueue } from '../../common/jobs';
import { logger } from '../../common/logger';
import { isProd } from '../../config/env';
import { getIntegration, registerIntegrationTest } from '../../modules/settings/settings.service';
import { BrevoApiProvider } from './brevo-api';
import { BrevoSmtpProvider } from './brevo-smtp';
import { FakeEmailProvider } from './fake';
import type { EmailProvider, EmailSender } from './provider';
import { renderEmail } from './templates';

export { FakeEmailProvider } from './fake';
export { renderEmail } from './templates';

/** Templates whose data contains one-time codes — their job payload is never retained on failure. */
const SENSITIVE: EmailTemplate[] = ['email_otp', 'password_reset', 'welcome'];

interface EmailJob {
  logId: string;
  to: string;
  toName?: string;
  template: EmailTemplate;
  locale: Locale;
  data: Record<string, string | number | null | undefined>;
  attachments?: { name: string; contentBase64: string }[];
}

export async function resolveEmailProviders(cfg?: Record<string, string | undefined>) {
  const c = cfg ?? (await getIntegration('brevo'));
  const sender: EmailSender = {
    name: c.sender_name || 'ShikkhaSathi',
    email: c.sender_email || 'no-reply@shikkhasathi.com',
    ...(c.reply_to ? { replyTo: c.reply_to } : {}),
  };
  const chain: EmailProvider[] = [];
  if (c.api_key) chain.push(new BrevoApiProvider(c.api_key));
  if (c.smtp_login && c.smtp_key) {
    chain.push(new BrevoSmtpProvider({ host: c.smtp_host || 'smtp-relay.brevo.com', port: Number(c.smtp_port || 587), login: c.smtp_login, key: c.smtp_key }));
  }
  if (chain.length === 0 && !isProd) chain.push(new FakeEmailProvider());
  return { sender, chain };
}

/** Queue an email. Logged in email_logs as `queued`; never stores template data (may contain OTPs). */
export async function sendEmail(opts: {
  to: string;
  toName?: string;
  template: EmailTemplate;
  data?: EmailJob['data'];
  locale?: Locale;
  relatedEntity?: string;
  attachments?: EmailJob['attachments'];
}) {
  const locale = opts.locale ?? 'bn';
  const subject = renderEmail(opts.template, opts.data ?? {}, locale).subject;
  const [log] = await coreDb
    .insert(emailLogs)
    .values({ toEmail: opts.to, template: opts.template, locale, subject, status: 'queued', relatedEntity: opts.relatedEntity ?? null })
    .returning({ id: emailLogs.id });
  const job: EmailJob = { logId: log!.id, to: opts.to, toName: opts.toName, template: opts.template, locale, data: opts.data ?? {}, attachments: opts.attachments };
  await enqueue('email.send', job, SENSITIVE.includes(opts.template) ? { removeOnFail: true, removeOnComplete: true } : {});
  return log!.id;
}

export async function deliverEmail(job: EmailJob) {
  const rendered = renderEmail(job.template, job.data, job.locale);
  const { sender, chain } = await resolveEmailProviders();
  if (chain.length === 0) {
    await coreDb.update(emailLogs).set({ status: 'failed', error: 'Email provider not configured' }).where(eq(emailLogs.id, job.logId));
    throw new Error('Email provider not configured');
  }
  let lastErr: unknown;
  for (const provider of chain) {
    try {
      const { messageId } = await provider.send({ to: job.to, toName: job.toName, ...rendered, attachments: job.attachments, tags: [job.template] }, sender);
      await coreDb
        .update(emailLogs)
        .set({ status: 'sent', provider: provider.name, providerMessageId: messageId, error: null, sentAt: new Date() })
        .where(eq(emailLogs.id, job.logId));
      return { provider: provider.name, messageId };
    } catch (err) {
      lastErr = err;
      logger.warn({ provider: provider.name, template: job.template, err: (err as Error).message }, 'email provider failed');
    }
  }
  const [row] = await coreDb.select({ attempts: emailLogs.attempts }).from(emailLogs).where(eq(emailLogs.id, job.logId));
  await coreDb
    .update(emailLogs)
    .set({ status: 'failed', error: (lastErr as Error)?.message?.slice(0, 500) ?? 'unknown', attempts: (row?.attempts ?? 0) + 1 })
    .where(eq(emailLogs.id, job.logId));
  throw lastErr;
}

defineJob<EmailJob>('email.send', deliverEmail);

registerIntegrationTest('brevo', async (cfg, { to }) => {
  if (!to) return { ok: false, message: 'টেস্ট মেইলের জন্য প্রাপকের ইমেইল দরকার' };
  const { sender, chain } = await resolveEmailProviders(cfg);
  if (chain.length === 0) return { ok: false, message: 'Brevo API key বা SMTP তথ্য দেওয়া নেই' };
  const provider = chain[0]!;
  const rendered = renderEmail('test', { provider: provider.name }, 'bn');
  const [log] = await coreDb.insert(emailLogs).values({ toEmail: to, template: 'test', subject: rendered.subject, status: 'queued', provider: provider.name }).returning({ id: emailLogs.id });
  try {
    const { messageId } = await provider.send({ to, ...rendered, tags: ['test'] }, sender);
    await coreDb.update(emailLogs).set({ status: 'sent', providerMessageId: messageId, sentAt: new Date() }).where(eq(emailLogs.id, log!.id));
    return { ok: true, message: `টেস্ট মেইল পাঠানো হয়েছে (${provider.name}) → ${to}` };
  } catch (err) {
    await coreDb.update(emailLogs).set({ status: 'failed', error: (err as Error).message.slice(0, 500) }).where(eq(emailLogs.id, log!.id));
    return { ok: false, message: (err as Error).message.slice(0, 300) };
  }
});

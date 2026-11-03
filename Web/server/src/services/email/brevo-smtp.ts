import nodemailer, { type Transporter } from 'nodemailer';
import type { EmailMessage, EmailProvider, EmailSender } from './provider';

/** Fallback: Brevo SMTP relay (smtp-relay.brevo.com:587, STARTTLS). */
export class BrevoSmtpProvider implements EmailProvider {
  readonly name = 'brevo_smtp' as const;
  private readonly transport: Transporter;
  constructor(cfg: { host: string; port: number; login: string; key: string }) {
    this.transport = nodemailer.createTransport({
      host: cfg.host,
      port: cfg.port,
      secure: cfg.port === 465,
      requireTLS: cfg.port === 587,
      auth: { user: cfg.login, pass: cfg.key },
      connectionTimeout: 10_000,
    });
  }

  async send(msg: EmailMessage, from: EmailSender) {
    const info = await this.transport.sendMail({
      from: { name: from.name, address: from.email },
      to: msg.toName ? { name: msg.toName, address: msg.to } : msg.to,
      replyTo: from.replyTo,
      subject: msg.subject,
      html: msg.html,
      text: msg.text,
      attachments: msg.attachments?.map((a) => ({ filename: a.name, content: Buffer.from(a.contentBase64, 'base64') })),
    });
    return { messageId: info.messageId ?? null };
  }

  async verify() {
    await this.transport.verify();
  }
}

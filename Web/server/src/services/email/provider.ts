export interface EmailMessage {
  to: string;
  toName?: string;
  subject: string;
  html: string;
  text: string;
  attachments?: { name: string; contentBase64: string }[];
  tags?: string[];
}

export interface EmailSender {
  name: string;
  email: string;
  replyTo?: string;
}

export interface EmailProvider {
  readonly name: 'brevo_api' | 'brevo_smtp' | 'fake';
  send(msg: EmailMessage, from: EmailSender): Promise<{ messageId: string | null }>;
}

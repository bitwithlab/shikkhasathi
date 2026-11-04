package com.shikkhasathi.notification.email;

import com.shikkhasathi.notification.email.EmailMessage.EmailSender;

/** One email delivery backend: Brevo API, Brevo SMTP or the dev/test fake. */
public interface EmailProvider {
  /** {@code brevo_api} | {@code brevo_smtp} | {@code fake} (stored in email_logs.provider) */
  String name();

  /** Deliver; returns the provider message id (nullable). Throws on failure. */
  String send(EmailMessage msg, EmailSender from);
}

package com.shikkhasathi.notification.email;

import java.util.List;

/**
 * A rendered message ready for a provider.
 *
 * @param toName      nullable
 * @param attachments may be empty
 * @param tags        Brevo tags (template id), may be empty
 */
public record EmailMessage(String to, String toName, String subject, String html, String text, List<EmailAttachment> attachments, List<String> tags) {
  public EmailMessage {
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
    tags = tags == null ? List.of() : List.copyOf(tags);
  }

  /** Attachment: file name + base64 content (e.g. the invoice PDF). */
  public record EmailAttachment(String name, String contentBase64) {}

  /** From/reply-to identity; {@code replyTo} nullable. */
  public record EmailSender(String name, String email, String replyTo) {}

  /** Rendered template parts. */
  public record Rendered(String subject, String html, String text) {}
}

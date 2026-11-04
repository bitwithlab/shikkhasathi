package com.shikkhasathi.notification.service;

import com.shikkhasathi.notification.email.EmailTemplate;
import java.util.Map;
import java.util.UUID;

/**
 * Input for {@link NotificationService#notify}. Build with {@link #of} and the {@code with*} copies:
 * {@code NotifyInput.of(userId, NotificationTypes.ORDER_PAID, orderId, title, body).withLink("/my-courses").withEmail(EmailTemplate.ORDER_PAID, data)}.
 *
 * @param ref            unique per (user, type) — a repeat with the same ref is ignored (dedupe key {@code user:type:ref})
 * @param link           app-relative link (e.g. {@code /home}), nullable
 * @param data           extra JSON stored in {@code data_json}, nullable
 * @param emailTemplate  email sent when the email channel is on for this type (nullable = no email)
 * @param emailData      template data for {@code emailTemplate}
 * @param emailByDefault turn email on unless the user explicitly switched it off (admin broadcasts)
 */
public record NotifyInput(UUID userId, String type, String ref, String title, String body, String link, Map<String, Object> data,
                          EmailTemplate emailTemplate, Map<String, Object> emailData, boolean emailByDefault) {
  public static NotifyInput of(UUID userId, String type, String ref, String title, String body) {
    return new NotifyInput(userId, type, ref, title, body, null, null, null, null, false);
  }

  public NotifyInput withLink(String l) {
    return new NotifyInput(userId, type, ref, title, body, l, data, emailTemplate, emailData, emailByDefault);
  }

  public NotifyInput withData(Map<String, Object> d) {
    return new NotifyInput(userId, type, ref, title, body, link, d, emailTemplate, emailData, emailByDefault);
  }

  public NotifyInput withEmail(EmailTemplate t, Map<String, Object> d) {
    return new NotifyInput(userId, type, ref, title, body, link, data, t, d, emailByDefault);
  }

  public NotifyInput withEmailByDefault() {
    return new NotifyInput(userId, type, ref, title, body, link, data, emailTemplate, emailData, true);
  }

  /** {@code user:type:ref} */
  public String dedupeKey() {
    return userId + ":" + type + ":" + ref;
  }
}

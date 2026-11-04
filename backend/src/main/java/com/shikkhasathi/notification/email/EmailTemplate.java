package com.shikkhasathi.notification.email;

import java.util.Arrays;
import java.util.Optional;

/** Transactional email templates (bn + en). {@link #id()} is the name stored in {@code email_logs.template}. */
public enum EmailTemplate {
  WELCOME, EMAIL_OTP, PASSWORD_RESET, ORDER_PAID, REFUND, SELLER_APPLICATION_RESULT, COURSE_REVIEW_DECISION, PAYOUT_PAID, CLASSROOM_DEADLINE, ADMIN_BROADCAST, TEST;

  /** e.g. {@code email_otp} */
  public String id() {
    return name().toLowerCase();
  }

  /** Templates whose data contains one-time codes — their job payload is never retained on failure. */
  public boolean sensitive() {
    return this == EMAIL_OTP || this == PASSWORD_RESET || this == WELCOME;
  }

  public static Optional<EmailTemplate> of(String id) {
    return Arrays.stream(values()).filter(t -> t.id().equals(id)).findFirst();
  }
}

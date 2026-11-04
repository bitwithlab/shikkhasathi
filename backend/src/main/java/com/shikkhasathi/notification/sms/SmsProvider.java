package com.shikkhasathi.notification.sms;

/** An SMS gateway. */
public interface SmsProvider {
  /** {@code http} | {@code fake} */
  String name();

  /** Send one message; throws on gateway failure. Never log the message (it may hold an OTP). */
  void send(String to, String message);
}

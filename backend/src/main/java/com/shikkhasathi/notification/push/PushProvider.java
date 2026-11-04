package com.shikkhasathi.notification.push;

import java.util.List;
import java.util.Map;

/** A push delivery backend (FCM HTTP v1 for Android + web; fake in dev/tests). */
public interface PushProvider {
  /** A device token. {@code platform}: web | android | ios. */
  record Target(String platform, String token) {}

  /** Message; {@code link} is an absolute URL or null, {@code data} extra string fields. */
  record PushMessage(String title, String body, String link, Map<String, String> data) {
    public PushMessage(String title, String body, String link) {
      this(title, body, link, Map.of());
    }
  }

  /** {@code fcm} | {@code fake} */
  String name();

  /** Send to every target; returns tokens that are permanently invalid and should be deleted. */
  List<String> send(List<Target> targets, PushMessage msg);
}

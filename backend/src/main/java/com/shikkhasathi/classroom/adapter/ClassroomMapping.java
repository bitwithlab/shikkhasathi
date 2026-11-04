package com.shikkhasathi.classroom.adapter;

import com.shikkhasathi.classroom.adapter.ClassroomModels.Material;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Google REST → normalized shapes, and the minimal OAuth scopes. */
public final class ClassroomMapping {
  private ClassroomMapping() {}

  /** Minimal read-only scopes (CLAUDE.md §0A). Anything more must be logged in docs/DECISIONS.md. */
  public static final List<String> CLASSROOM_SCOPES = List.of(
      "openid",
      "email",
      "profile",
      "https://www.googleapis.com/auth/classroom.courses.readonly",
      "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
      "https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly",
      "https://www.googleapis.com/auth/classroom.announcements.readonly",
      "https://www.googleapis.com/auth/classroom.push-notifications");

  /** Text value of {@code n.key}, or null when missing / null. */
  static String str(JsonNode n, String key) {
    if (n == null) return null;
    JsonNode v = n.get(key);
    return v == null || v.isNull() ? null : v.asString();
  }

  static String strOr(JsonNode n, String key, String fallback) {
    String v = str(n, key);
    return v == null ? fallback : v;
  }

  /** {@code typeof v === 'string' && v ? new Date(v) : null} */
  static Instant date(JsonNode n, String key) {
    JsonNode v = n == null ? null : n.get(key);
    if (v == null || !v.isString() || v.asString().isEmpty()) return null;
    try {
      return Instant.parse(v.asString());
    } catch (RuntimeException e) {
      return null;
    }
  }

  static Double number(JsonNode n, String key) {
    JsonNode v = n == null ? null : n.get(key);
    return v != null && v.isNumber() ? v.asDouble() : null;
  }

  private static JsonNode obj(JsonNode n, String key) {
    JsonNode v = n == null ? null : n.get(key);
    return v == null || v.isNull() || v.isMissingNode() ? null : v;
  }

  /** Classroom material attachments → contract {@code Material[]} (drive, link, youtube, form; others dropped). */
  public static List<Material> mapMaterials(JsonNode list) {
    List<Material> out = new ArrayList<>();
    if (list == null || !list.isArray()) return out;
    for (JsonNode m : list) {
      JsonNode drive = obj(obj(m, "driveFile"), "driveFile");
      if (drive != null) {
        out.add(new Material("drive", strOr(drive, "title", "Drive file"), str(drive, "alternateLink"), str(drive, "thumbnailUrl"), null));
        continue;
      }
      JsonNode link = obj(m, "link");
      if (link != null) {
        out.add(new Material("link", str(link, "title") != null ? str(link, "title") : str(link, "url"), str(link, "url"), str(link, "thumbnailUrl"), null));
        continue;
      }
      JsonNode yt = obj(m, "youtubeVideo");
      if (yt != null) {
        out.add(new Material("youtube", strOr(yt, "title", "YouTube"), str(yt, "alternateLink"), str(yt, "thumbnailUrl"), null));
        continue;
      }
      JsonNode form = obj(m, "form");
      if (form != null) out.add(new Material("form", strOr(form, "title", "Google Form"), str(form, "formUrl"), str(form, "thumbnailUrl"), null));
    }
    return out;
  }

  /**
   * Classroom dueDate/dueTime are UTC. A missing time means end of day (23:59 UTC is how Classroom renders it);
   * a present but empty time means midnight.
   */
  public static Instant mapDue(JsonNode dueDate, JsonNode dueTime) {
    if (dueDate == null || dueDate.isNull() || dueDate.path("year").asInt(0) == 0) return null;
    boolean hasTime = dueTime != null && !dueTime.isNull() && !dueTime.isMissingNode();
    int h = hasTime && dueTime.hasNonNull("hours") ? dueTime.get("hours").asInt() : (hasTime ? 0 : 23);
    int mi = hasTime && dueTime.hasNonNull("minutes") ? dueTime.get("minutes").asInt() : (hasTime ? 0 : 59);
    return LocalDateTime.of(dueDate.path("year").asInt(), 1, 1, 0, 0)
        .plusMonths(dueDate.path("month").asInt(1) - 1L)
        .plusDays(dueDate.path("day").asInt(1) - 1L)
        .plusHours(h)
        .plusMinutes(mi)
        .toInstant(ZoneOffset.UTC);
  }
}

package com.shikkhasathi.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request/response records of the {@code notifications} contract group. */
public final class NotificationDtos {
  private NotificationDtos() {}

  static final String TYPE_RE = "classroom\\.assignment_new|classroom\\.material_new|classroom\\.announcement|classroom\\.graded|classroom\\.due_24h|classroom\\.due_2h|classroom\\.overdue|exam\\.daily_plan|course\\.qa_answered|seller\\.qa_new|order\\.paid|order\\.refunded|request\\.published|seller\\.course_decision|seller\\.payout_paid|seller\\.new_enrollment|seller\\.application_result|admin\\.broadcast";
  static final String CHANNEL_RE = "in_app|push|email|sms";

  /** One notification in the center. {@code createdAt} is ISO-8601 with millis. */
  public record NotificationItem(UUID id, String type, String title, String body, String link, String createdAt, boolean read) {}

  public record NotificationPage(List<NotificationItem> items, String nextCursor) {}

  public record UnreadCount(long count) {}

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }

  /** type × channel → enabled */
  public record Preference(@NotNull @Pattern(regexp = TYPE_RE) String type, @NotNull @Pattern(regexp = CHANNEL_RE) String channel, @NotNull Boolean enabled) {
    public Preference(String type, String channel, boolean enabled) {
      this(type, channel, Boolean.valueOf(enabled));
    }
  }

  public record Preferences(List<Preference> items) {}

  public record UpdatePreferences(@NotNull @Size(max = 200) List<@Valid @NotNull Preference> items) {}

  public record MarkRead(@Size(max = 200) List<@NotNull UUID> ids, Boolean all) {}

  public record RegisterPush(@NotNull @Pattern(regexp = "web|android|ios") String platform, @NotNull @Size(min = 10, max = 4096) String token) {}

  public record UnregisterPush(@NotNull @Size(min = 10, max = 4096) String token) {}
}

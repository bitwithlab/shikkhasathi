package com.shikkhasathi.classroom.dto;

import com.shikkhasathi.assignment.dto.AssignmentDtos.Assignment;
import com.shikkhasathi.classroom.adapter.ClassroomModels.Material;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request/response records of the {@code classroom} and {@code webhooks} contract groups. */
public final class ClassroomDtos {
  private ClassroomDtos() {}

  public record Ok(boolean ok) {
    public static final Ok OK = new Ok(true);
  }

  /** status = active | needs_reauth | workspace_blocked | error | syncing (null when not connected) */
  public record ClassroomStatus(boolean connected, String status, String email, String lastSyncAt, String lastFullSyncAt, String nextManualSyncAt, String errorCode,
                                long courseCount, boolean isFake) {}

  public record ConnectUrl(String url, boolean fake) {}

  public record ConnectBody(@NotNull @Size(min = 4) String code, @Pattern(regexp = "web|android|ios") String platform, String redirectUri) {}

  public record ClassCourse(String id, String name, String section, String room, List<String> teacherNames, String color, String alternateLink, long unreadCount,
                            long pendingCount, String nextDueAt) {}

  public record CourseList(List<ClassCourse> items) {}

  /** kind = assignment | material | announcement */
  public record StreamItem(String id, String kind, String title, String body, String author, List<Material> materials, String postedAt, String alternateLink,
                           Assignment assignment) {}

  public record Stream(ClassCourse course, List<StreamItem> items) {}

  public record FeedGrade(Number assigned, Number max) {}

  /** kind = assignment | material | announcement | graded */
  public record FeedItem(UUID id, String kind, String refId, String courseId, String courseName, String courseColor, String title, String body, List<Material> materials,
                         FeedGrade grade, String alternateLink, String occurredAt, boolean read) {}

  public record FeedPage(List<FeedItem> items, String nextCursor) {}

  public record FeedReadBody(@Size(max = 200) List<@NotNull UUID> ids, Boolean all) {}

  /** Pub/Sub push envelope. */
  public record PubSubPush(@NotNull @Valid PubSubMessage message, String subscription) {}

  public record PubSubMessage(String data, Map<String, String> attributes, String messageId) {}
}

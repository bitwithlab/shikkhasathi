package com.shikkhasathi.student.dto;

import com.shikkhasathi.classroom.dto.ClassroomDtos.ClassroomStatus;
import com.shikkhasathi.classroom.dto.ClassroomDtos.FeedItem;
import java.util.List;
import java.util.UUID;

/** Contract {@code Dashboard} (GET /dashboard): per-widget sections that resolve and fail independently. */
public final class DashboardDtos {
  private DashboardDtos() {}

  /** One widget: {@code data} (nullable) and {@code error} ("unavailable" when it failed, else null). */
  public record Section<T>(T data, String error) {
    public static <T> Section<T> ok(T data) {
      return new Section<>(data, null);
    }

    public static <T> Section<T> failed() {
      return new Section<>(null, "unavailable");
    }
  }

  public record Header(String name, String institutionShort, String departmentShort, Integer semester, long unreadNotifications, ClassroomStatus classroom) {}

  public record FeedWidget(List<FeedItem> items, long unread) {}

  public record ExamWidget(UUID planId, String subjectName, String courseCode, String examType, String examDate, long daysLeft, int readiness) {}

  public record WeekDay(String date, long deadlines, long exams) {}

  public record Dashboard(Header header, Section<?> dueSoon, Section<FeedWidget> feed, Section<ExamWidget> exam, Section<Object> continueLearning, Section<List<WeekDay>> week,
                          Section<Object> recommendations, String generatedAt) {}
}

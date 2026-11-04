package com.shikkhasathi.assignment.dto;

import com.shikkhasathi.classroom.adapter.ClassroomModels.Material;
import java.util.List;

/** Assignment read models of the {@code classroom} contract group (Assignment, CalendarEvent, DueGroup). */
public final class AssignmentDtos {
  private AssignmentDtos() {}

  /**
   * Contract {@code Assignment}. {@code id} is the Classroom coursework id; status = pending | turned_in | graded | missing.
   * Numbers are integral when whole (JSON {@code 20}, not {@code 20.0}).
   */
  public record Assignment(String id, String courseId, String courseName, String courseColor, String title, String description, String workType, String dueAt,
                           Number maxPoints, String status, boolean late, Number grade, List<Material> materials, String alternateLink, String postedAt) {}

  public record AssignmentList(List<Assignment> items) {}

  /** kind = assignment | exam */
  public record CalendarEvent(String id, String kind, String title, String at, String courseName, String color, String status, String link) {}

  public record CalendarList(List<CalendarEvent> items) {}

  /** key = overdue | today | tomorrow | this_week */
  public record DueGroup(String key, List<Assignment> items) {}

  public record DueSoon(List<DueGroup> groups, int total) {}
}

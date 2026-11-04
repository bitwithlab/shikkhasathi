package com.shikkhasathi.assignment.service;

import static com.shikkhasathi.classroom.service.ClassroomText.*;

import com.shikkhasathi.assignment.dto.AssignmentDtos.*;
import com.shikkhasathi.classroom.adapter.ClassroomModels.Material;
import com.shikkhasathi.classroom.repository.ClassroomRepository;
import com.shikkhasathi.classroom.repository.ClassroomRepository.CourseRow;
import com.shikkhasathi.classroom.repository.ClassroomRepository.CourseworkRow;
import com.shikkhasathi.classroom.repository.ClassroomRepository.SubmissionRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.student.repository.ExamPlanQueries;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Assignment views over the Classroom cache: list, detail, calendar and the dashboard's due-soon groups. */
@Service
public class AssignmentService {
  private final ClassroomRepository repo;
  private final ExamPlanQueries exams;
  private final JsonMapper json;

  public AssignmentService(ClassroomRepository repo, ExamPlanQueries exams, JsonMapper json) {
    this.repo = repo;
    this.exams = exams;
    this.json = json;
  }

  /** Pending · Turned in · Graded (returned) · Missing (past due, not turned in). */
  public static String assignmentStatus(String subState, Instant dueAt, Instant now) {
    if ("RETURNED".equals(subState)) return "graded";
    if ("TURNED_IN".equals(subState)) return "turned_in";
    if (dueAt != null && dueAt.isBefore(now)) return "missing";
    return "pending";
  }

  /** materials_json → Material[] (anything but an array is empty). */
  public List<Material> materials(String raw) {
    if (raw == null) return List.of();
    JsonNode n = json.readTree(raw);
    if (!n.isArray()) return List.of();
    List<Material> out = new ArrayList<>();
    for (JsonNode m : n) out.add(json.treeToValue(m, Material.class));
    return out;
  }

  /** gc course id → course, ordered by name. */
  public Map<String, CourseRow> coursesMap(UUID userId) {
    Map<String, CourseRow> m = new LinkedHashMap<>();
    for (var c : repo.courses(userId)) m.put(c.gcCourseId(), c);
    return m;
  }

  public Assignment toAssignment(CourseworkRow cw, SubmissionRow sub, CourseRow course, Instant now) {
    return new Assignment(
        cw.gcCourseworkId(),
        cw.gcCourseId(),
        course == null ? "" : course.name(),
        course == null ? COLORS.get(0) : course.color(),
        cw.title(),
        cw.description(),
        cw.workType(),
        JsonConfig.iso(cw.dueAt()),
        num(cw.maxPoints()),
        assignmentStatus(sub == null ? null : sub.state(), cw.dueAt(), now),
        sub != null && sub.late(),
        sub == null ? null : num(sub.assignedGrade()),
        materials(cw.materialsJson()),
        sub != null && sub.alternateLink() != null ? sub.alternateLink() : cw.alternateLink(),
        JsonConfig.iso(cw.createdAtGc()));
  }

  /** Every assignment of the user (optionally one course), with status, ordered by due date. */
  @Transactional(readOnly = true)
  public List<Assignment> all(UUID userId, String courseId, Instant now) {
    var courses = coursesMap(userId);
    return repo.assignmentRows(userId, courseId, null, null, null, false).stream().map(r -> toAssignment(r.cw(), r.sub(), courses.get(r.cw().gcCourseId()), now)).toList();
  }

  /** status = pending | turned_in | graded | missing | all | upcoming (pending + missing). */
  @Transactional(readOnly = true)
  public AssignmentList list(UUID userId, String status, String courseId, Instant from, Instant to) {
    var rows = repo.assignmentRows(userId, courseId, null, from, to, false);
    var courses = coursesMap(userId);
    Instant now = Instant.now();
    var items = rows.stream().map(r -> toAssignment(r.cw(), r.sub(), courses.get(r.cw().gcCourseId()), now));
    if ("upcoming".equals(status)) items = items.filter(a -> a.status().equals("pending") || a.status().equals("missing"));
    else if (!"all".equals(status)) items = items.filter(a -> a.status().equals(status));
    return new AssignmentList(items.toList());
  }

  @Transactional(readOnly = true)
  public Assignment get(UUID userId, String id) {
    var rows = repo.assignmentRows(userId, null, id, null, null, false);
    if (rows.isEmpty()) throw ApiException.notFound("অ্যাসাইনমেন্টটি পাওয়া যায়নি।");
    var r = rows.get(0);
    return toAssignment(r.cw(), r.sub(), coursesMap(userId).get(r.cw().gcCourseId()), Instant.now());
  }

  /** Deadlines + exams in [from, to], ordered by time. */
  @Transactional(readOnly = true)
  public CalendarList calendar(UUID userId, Instant from, Instant to) {
    var rows = repo.assignmentRows(userId, null, null, from, to, true);
    var courses = coursesMap(userId);
    Instant now = Instant.now();
    List<CalendarEvent> items = new ArrayList<>();
    for (var r : rows) {
      var a = toAssignment(r.cw(), r.sub(), courses.get(r.cw().gcCourseId()), now);
      items.add(new CalendarEvent(a.id(), "assignment", a.title(), a.dueAt(), a.courseName(), a.courseColor(), a.status(), "/classroom/assignments?id=" + uri(a.id())));
    }
    for (var e : exams.activeBetween(userId, from, to, true)) {
      String type = "final".equals(e.examType()) ? "Final" : "midterm".equals(e.examType()) ? "Midterm" : e.examType();
      items.add(new CalendarEvent(e.id().toString(), "exam", e.subjectName() + " " + type, JsonConfig.iso(e.examDate()), e.courseCode(), "#F59E0B", null, "/exam-mode/" + e.id()));
    }
    items.sort(Comparator.comparing(CalendarEvent::at));
    return new CalendarList(items);
  }

  /** Dashboard "সামনের অ্যাসাইনমেন্ট": overdue / today / tomorrow / this week (Asia/Dhaka days), empty groups dropped. */
  @Transactional(readOnly = true)
  public DueSoon dueSoonGroups(UUID userId, Instant now) {
    Instant weekEnd = now.plusMillis(7 * 86_400_000L);
    var rows = repo.assignmentRows(userId, null, null, now.minusMillis(7 * 86_400_000L), weekEnd, true);
    var courses = coursesMap(userId);
    var list = rows.stream().map(r -> toAssignment(r.cw(), r.sub(), courses.get(r.cw().gcCourseId()), now))
        .filter(a -> a.status().equals("pending") || a.status().equals("missing") || (a.status().equals("turned_in") && Instant.parse(a.dueAt()).isAfter(now)))
        .toList();
    LocalDate today = dhakaDay(now);
    LocalDate tomorrow = dhakaDay(now.plusMillis(86_400_000L));
    Map<String, List<Assignment>> groups = new LinkedHashMap<>();
    for (String k : List.of("overdue", "today", "tomorrow", "this_week")) groups.put(k, new ArrayList<>());
    for (var a : list) {
      LocalDate due = dhakaDay(Instant.parse(a.dueAt()));
      if (a.status().equals("missing")) groups.get("overdue").add(a);
      else if (due.equals(today)) groups.get("today").add(a);
      else if (due.equals(tomorrow)) groups.get("tomorrow").add(a);
      else groups.get("this_week").add(a);
    }
    return new DueSoon(groups.entrySet().stream().filter(e -> !e.getValue().isEmpty()).map(e -> new DueGroup(e.getKey(), e.getValue())).toList(), list.size());
  }
}

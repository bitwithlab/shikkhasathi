package com.shikkhasathi.exam.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Exam study-plan generator (spec §6.9) — pure and deterministic.
 * Distributes remaining lessons of linked courses (⭐ important first, curriculum order otherwise)
 * across the days until the exam, balancing minutes per day. The last day before the exam is revision
 * (mock tests + flashcards + important lessons recap) when there are ≥ 2 days left.
 */
public final class Planner {
  private Planner() {}

  /**
   * @param doneBefore completed before today (Dhaka) — excluded from the plan
   * @param doneToday completed today — stays in today's list, ticked
   */
  public record PlanLesson(String id, String courseId, String title, String chapterTitle, int durationSec, String type, boolean isImportant, boolean doneBefore, boolean doneToday) {}

  public record PlanMock(String id, String title, int questionCount, boolean attempted) {}

  public record Flash(int total, int known) {}

  public sealed interface GenItem permits LessonItem, MockItem, FlashcardsItem, RevisionItem {
    boolean done();
  }

  public record LessonItem(PlanLesson lesson, boolean done) implements GenItem {}

  public record MockItem(PlanMock mock, boolean done) implements GenItem {}

  public record FlashcardsItem(int count, boolean done) implements GenItem {}

  public record RevisionItem(boolean done) implements GenItem {}

  public static final class GenDay {
    public final String date;
    public List<GenItem> items = new ArrayList<>();
    public int totalMinutes;
    public final boolean isRevision;

    GenDay(String date, boolean isRevision) {
      this.date = date;
      this.isRevision = isRevision;
    }
  }

  public record Input(String today, String examDay, List<PlanLesson> lessons, List<PlanMock> mocks, Flash flashcards) {}

  /** Text/PDF lessons have no duration — budget 10 minutes of reading. */
  public static int effectiveMinutes(int durationSec, String type) {
    return durationSec > 0 ? (int) Math.max(1, Math.round(durationSec / 60.0)) : 10;
  }

  static String addDays(String ymd, int n) {
    return LocalDate.parse(ymd).plusDays(n).toString();
  }

  public static int daysBetween(String a, String b) {
    return (int) ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b));
  }

  public static List<GenDay> generatePlan(Input input) {
    String today = input.today();
    int span = daysBetween(today, input.examDay()); // days strictly before the exam day
    if (span <= 0) return List.of();
    List<PlanLesson> pending = input.lessons().stream().filter(l -> !l.doneBefore()).toList();
    // ⭐ first, then curriculum order (stable)
    List<PlanLesson> ordered = new ArrayList<>();
    for (var l : pending) if (l.isImportant()) ordered.add(l);
    for (var l : pending) if (!l.isImportant()) ordered.add(l);
    boolean hasRevisionDay = span >= 2;
    int studyDays = hasRevisionDay ? span - 1 : span;
    List<GenDay> days = new ArrayList<>();
    for (int i = 0; i < span; i++) days.add(new GenDay(addDays(today, i), hasRevisionDay && i == span - 1));

    // balanced greedy fill: each day gets ≈ total / studyDays minutes; a day always takes at least one lesson
    int total = 0;
    for (var l : ordered) total += effectiveMinutes(l.durationSec(), l.type());
    double target = studyDays != 0 ? (double) total / studyDays : total;
    int d = 0;
    int acc = 0;
    for (var l : ordered) {
      int m = effectiveMinutes(l.durationSec(), l.type());
      GenDay day = days.get(d);
      // move to the next study day once today's budget is reached (never past the last study day)
      if (!day.items.isEmpty() && acc + m / 2.0 > target * (d + 1) && d < studyDays - 1) d++;
      GenDay into = days.get(d);
      into.items.add(new LessonItem(l, l.doneToday()));
      into.totalMinutes += m;
      acc += m;
    }

    // revision day (or the only day): important recap + mocks + flashcards
    GenDay last = days.get(days.size() - 1);
    if (hasRevisionDay) last.items.add(new RevisionItem(false));
    List<PlanMock> untried = input.mocks().stream().filter(m -> !m.attempted()).toList();
    for (var m : untried.isEmpty() ? input.mocks().stream().limit(1).toList() : untried) last.items.add(new MockItem(m, m.attempted()));
    Flash fl = input.flashcards();
    if (fl.total() > 0) last.items.add(new FlashcardsItem(fl.total(), fl.known() >= fl.total()));
    // with many days left, put a first mock midway so students get early feedback
    if (span >= 6 && untried.size() > 1) {
      GenDay mid = days.get(studyDays / 2);
      PlanMock m = untried.get(0);
      mid.items.add(new MockItem(m, m.attempted()));
      last.items = new ArrayList<>(last.items.stream().filter(x -> !(x instanceof MockItem mi && mi.mock().id().equals(m.id()))).toList());
    }
    return days;
  }

  public record Readiness(int total, int lessons, Integer mock, Integer flashcards) {}

  /**
   * Readiness (0–100): lessons 60 % · mock score 30 % · flashcards 10 %.
   * Components without content (no mock tests / no flashcards) are dropped and the weights renormalised.
   */
  public static Readiness readiness(int lessonsDone, int lessonsTotal, Integer mockScore, int flashKnown, int flashTotal) {
    List<int[]> parts = new ArrayList<>();
    int lessons = lessonsTotal != 0 ? (int) Math.round((double) lessonsDone / lessonsTotal * 100) : 0;
    parts.add(new int[] {lessons, 60});
    if (mockScore != null) parts.add(new int[] {mockScore, 30});
    Integer flash = flashTotal != 0 ? (int) Math.round((double) flashKnown / flashTotal * 100) : null;
    if (flash != null) parts.add(new int[] {flash, 10});
    int w = 0;
    double sum = 0;
    for (var p : parts) {
      w += p[1];
      sum += (double) p[0] * p[1];
    }
    int total = (int) Math.round(sum / w);
    return new Readiness(Math.max(0, Math.min(100, total)), lessons, mockScore, flash);
  }
}

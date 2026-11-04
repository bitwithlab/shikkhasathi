package com.shikkhasathi.exam;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.exam.service.Planner;
import com.shikkhasathi.exam.service.Planner.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Port of the previous API's planner.test.ts (pure functions, no Spring context). */
class PlannerTest {
  static PlanLesson L(int i) {
    return new PlanLesson("l" + i, "c", "Lesson " + i, "Ch", 600, "video", false, false, false);
  }

  static PlanLesson L(int i, boolean important, boolean doneBefore, boolean doneToday, int durationSec, String type) {
    return new PlanLesson("l" + i, "c", "Lesson " + i, "Ch", durationSec, type, important, doneBefore, doneToday);
  }

  static final Flash NO_FLASH = new Flash(0, 0);

  @Test
  void spreadsRemainingLessonsOverStudyDaysKeepsLastDayForRevisionImportantFirst() {
    List<PlanLesson> lessons = IntStream.range(0, 10).mapToObj(i -> L(i, i == 7 || i == 8, false, false, 600, "video")).toList();
    var days = Planner.generatePlan(new Input("2026-11-03", "2026-11-09", lessons, List.of(new PlanMock("m1", "Mock 1", 20, false)), new Flash(30, 5)));
    assertThat(days).hasSize(6);
    assertThat(days.stream().map(d -> d.date).toList()).containsExactly("2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07", "2026-11-08");
    var rev = days.get(days.size() - 1);
    assertThat(rev.isRevision).isTrue();
    assertThat(rev.items).anyMatch(i -> i instanceof RevisionItem);
    assertThat(rev.items).anyMatch(i -> i instanceof MockItem);
    assertThat(rev.items).anyMatch(i -> i instanceof FlashcardsItem);
    assertThat(rev.items).noneMatch(i -> i instanceof LessonItem);
    var first = days.get(0).items.stream().filter(i -> i instanceof LessonItem).map(i -> (LessonItem) i).toList();
    assertThat(first.get(0).lesson().id()).isEqualTo("l7");
    long planned = days.stream().flatMap(d -> d.items.stream()).filter(i -> i instanceof LessonItem).count();
    assertThat(planned).isEqualTo(10);
    var mins = days.subList(0, 5).stream().mapToInt(d -> d.totalMinutes).toArray();
    int max = IntStream.of(mins).max().getAsInt();
    int min = IntStream.of(mins).min().getAsInt();
    assertThat(max - min).isLessThanOrEqualTo(10);
  }

  @Test
  void excludesLessonsDoneBeforeTodayKeepsTodaysCompletedOnesTicked() {
    var days = Planner.generatePlan(new Input("2026-11-03", "2026-11-05",
        List.of(L(1, false, true, false, 600, "video"), L(2, false, false, true, 600, "video"), L(3)), List.of(), NO_FLASH));
    List<GenItem> all = new ArrayList<>();
    days.forEach(d -> all.addAll(d.items));
    assertThat(all).noneMatch(i -> i instanceof LessonItem l && l.lesson().id().equals("l1"));
    assertThat(days.get(0).items.get(0)).isInstanceOf(LessonItem.class);
    assertThat(days.get(0).items.get(0).done()).isTrue();
  }

  @Test
  void examTomorrowSingleDayWithEverythingExamTodayOrPastNoPlan() {
    var one = Planner.generatePlan(new Input("2026-11-03", "2026-11-04", List.of(L(1), L(2)), List.of(new PlanMock("m", "M", 5, true)), NO_FLASH));
    assertThat(one).hasSize(1);
    assertThat(one.get(0).isRevision).isFalse();
    assertThat(one.get(0).items.stream().filter(i -> i instanceof LessonItem)).hasSize(2);
    assertThat(Planner.generatePlan(new Input("2026-11-04", "2026-11-04", List.of(L(1)), List.of(), NO_FLASH))).isEmpty();
  }

  @Test
  void textLessonsGetATenMinuteReadingBudget() {
    var days = Planner.generatePlan(new Input("2026-11-03", "2026-11-04", List.of(L(1, false, false, false, 0, "text")), List.of(), NO_FLASH));
    assertThat(days.get(0).totalMinutes).isEqualTo(10);
  }

  @Test
  void readinessWeights603010AndRenormalisesMissingParts() {
    assertThat(Planner.readiness(5, 10, 80, 10, 10).total()).isEqualTo((int) Math.round(50 * 0.6 + 80 * 0.3 + 100 * 0.1));
    assertThat(Planner.readiness(5, 10, null, 0, 0).total()).isEqualTo(50);
    var r = Planner.readiness(10, 10, null, 0, 10);
    assertThat(r.total()).isEqualTo((int) Math.round((100 * 60 + 0 * 10) / 70.0));
    assertThat(r.mock()).isNull();
    assertThat(r.flashcards()).isEqualTo(0);
    assertThat(Planner.readiness(0, 0, null, 0, 0).total()).isEqualTo(0);
  }
}

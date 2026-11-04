package com.shikkhasathi.search;

import static com.shikkhasathi.search.service.Ranking.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.shikkhasathi.search.service.QueryExpansion;
import com.shikkhasathi.search.service.QueryExpansion.Interpretation;
import com.shikkhasathi.search.service.Ranking.Exam;
import com.shikkhasathi.search.service.Ranking.RankCourse;
import com.shikkhasathi.search.service.Ranking.RankStudent;
import com.shikkhasathi.search.service.Ranking.RankTarget;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure ranking (spec §6.5) and query expansion — no Spring context needed. */
class RankingTest {
  static final UUID MU = UUID.randomUUID(), CSE = UUID.randomUUID(), DS = UUID.randomUUID(), ALGO = UUID.randomUUID(), NSU = UUID.randomUUID(), X = UUID.randomUUID();
  static final RankStudent STUDENT = new RankStudent(MU, CSE, 4, "56", List.of(DS), List.of());

  static RankTarget t(boolean general, UUID inst, UUID dept, Integer sem, UUID subj, List<String> batches) {
    return new RankTarget(general, inst, dept, sem, subj, batches);
  }

  static RankCourse course(String examType, double ratingAvg, int ratingCount, int e30, boolean verified, List<RankTarget> targets) {
    return new RankCourse(examType, ratingAvg, ratingCount, e30, verified, Instant.parse("2026-11-01T00:00:00Z"), targets);
  }

  static final List<RankTarget> OWN = List.of(t(false, MU, CSE, 4, DS, List.of("56")));

  static RankCourse course() {
    return course("midterm", 4.5, 50, 20, true, OWN);
  }

  @Test
  void matchWeights100_80_50_30_10() {
    assertThat(matchWeight(course().targets(), STUDENT)).isEqualTo(100);
    assertThat(matchWeight(List.of(t(false, MU, CSE, 4, DS, List.of())), STUDENT)).isEqualTo(80);
    assertThat(matchWeight(List.of(t(false, NSU, X, 4, DS, List.of())), STUDENT)).isEqualTo(50);
    assertThat(matchWeight(List.of(t(true, null, null, null, ALGO, List.of())), STUDENT)).isEqualTo(30);
    assertThat(matchWeight(List.of(t(false, NSU, null, null, ALGO, List.of())), STUDENT)).isEqualTo(10);
    assertThat(matchWeight(List.of(t(false, NSU, null, null, ALGO, List.of()), t(false, MU, CSE, 4, DS, List.of())), STUDENT)).isEqualTo(80);
  }

  @Test
  void bayesianRatingPullsSmallSamplesTowardThePrior() {
    assertThat(bayesianRating(5, 1)).isCloseTo((4 * 10 + 5) / 11.0, within(1e-5));
    assertThat(bayesianRating(5, 1000)).isGreaterThan(4.98);
    assertThat(bayesianRating(0, 0)).isEqualTo(4);
  }

  @Test
  void examProximityBoostOnlyForMatchingSubjectAndExamTypeWithin14Days() {
    Instant now = Instant.parse("2026-11-03T00:00:00Z");
    var exam = new Exam(DS, "midterm", Instant.parse("2026-11-06T00:00:00Z"));
    var s = new RankStudent(MU, CSE, 4, "56", List.of(DS), List.of(exam));
    assertThat(examProximityBoost(course(), s, now)).isCloseTo(40 * (1 - 3 / 15.0), within(1e-5));
    assertThat(examProximityBoost(course("final", 4.5, 50, 20, true, OWN), s, now)).isEqualTo(0);
    var far = new RankStudent(MU, CSE, 4, "56", List.of(DS), List.of(new Exam(DS, "midterm", Instant.parse("2026-11-30T00:00:00Z"))));
    assertThat(examProximityBoost(course(), far, now)).isEqualTo(0);
  }

  @Test
  void popularityFreshnessVerifiedBonus() {
    assertThat(popularity(0)).isEqualTo(0);
    assertThat(popularity(Math.E - 1)).isCloseTo(5, within(1e-5));
    Instant now = Instant.parse("2026-11-03T00:00:00Z");
    assertThat(freshness(now, now)).isEqualTo(5);
    assertThat(freshness(Instant.parse("2026-08-01T00:00:00Z"), now)).isEqualTo(0);
    double a = rankScore(course(), STUDENT, now);
    double b = rankScore(course("midterm", 4.5, 50, 20, false, OWN), STUDENT, now);
    assertThat(a - b).isCloseTo(5, within(1e-5));
  }

  @Test
  void ownSyllabusCourseOutranksAMorePopularOtherUniversityCourse() {
    Instant now = Instant.parse("2026-11-03T00:00:00Z");
    double own = rankScore(course("midterm", 4.2, 50, 10, true, OWN), STUDENT, now);
    double other = rankScore(course("midterm", 5, 500, 400, true, List.of(t(false, NSU, X, null, DS, List.of()))), STUDENT, now);
    assertThat(own).isGreaterThan(other);
  }

  @Test
  void compatibilityBadge() {
    assertThat(compatibility(course().targets(), STUDENT)).isEqualTo("match");
    assertThat(compatibility(List.of(t(false, NSU, X, null, null, List.of())), STUDENT)).isEqualTo("mismatch");
    assertThat(compatibility(List.of(t(true, null, null, null, null, List.of())), STUDENT)).isEqualTo("general");
    assertThat(compatibility(List.of(t(false, MU, CSE, null, null, List.of())), null)).isEqualTo("general");
  }

  @Test
  void banglaEnglishAndAbbreviationMapToOneSubject() {
    for (String q : List.of("DS", "ডাটা স্ট্রাকচার", "data structure")) assertThat(QueryExpansion.expand(q).terms()).contains("data structures");
    assertThat(QueryExpansion.expand("DS").interpreted().get(0)).isEqualTo(new Interpretation("ds", "Data Structures"));
  }

  @Test
  void extractsCourseCodesAndExamWords() {
    var e = QueryExpansion.expand("cse 2203 midterm");
    assertThat(e.code()).isEqualTo("CSE2203");
    assertThat(e.examType()).isEqualTo("midterm");
    assertThat(QueryExpansion.normalizeCode("CSE-2203")).isEqualTo(QueryExpansion.normalizeCode("cse2203"));
  }
}

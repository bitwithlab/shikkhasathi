package com.shikkhasathi.search.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Course ranking (spec §6.5) — pure and deterministic.
 *
 * <pre>
 * score = match_weight              100 own inst+dept+semester+batch · 80 inst+dept+semester · 50 same subject other inst · 30 general · 10 related
 *       + 4 × rating_bayesian       0–5
 *       + exam_proximity_boost      up to +40 when the student's exam for that subject is ≤ 14 days and the course exam type matches
 *       + log(1 + enrollments_30d) × 5
 *       + 5 if seller verified
 *       + freshness                 up to +5 for courses updated in the last 60 days
 * </pre>
 */
public final class Ranking {
  public static final double BAYES_PRIOR = 4.0;
  public static final double BAYES_WEIGHT = 10;
  private static final double DAY_MS = 86_400_000d;

  private Ranking() {}

  public record RankTarget(boolean isGeneral, UUID institutionId, UUID departmentId, Integer semester, UUID subjectMasterId, List<String> batches) {
    public RankTarget {
      batches = batches == null ? List.of() : batches;
    }
  }

  public record RankCourse(String examType, double ratingAvg, int ratingCount, int enrollments30d, boolean sellerVerified, Instant updatedAt, List<RankTarget> targets) {}

  public record Exam(UUID subjectMasterId, String examType, Instant examDate) {}

  /**
   * @param subjectIds subjects the student is studying (their institution's subjects for their semester), for "related"
   */
  public record RankStudent(UUID institutionId, UUID departmentId, Integer semester, String batch, List<UUID> subjectIds, List<Exam> exams) {
    public RankStudent {
      subjectIds = subjectIds == null ? List.of() : subjectIds;
      exams = exams == null ? List.of() : exams;
    }
  }

  public static double bayesianRating(double avg, int count) {
    return bayesianRating(avg, count, BAYES_PRIOR, BAYES_WEIGHT);
  }

  public static double bayesianRating(double avg, int count, double prior, double weight) {
    if (count <= 0) return prior;
    return (prior * weight + avg * count) / (weight + count);
  }

  public static int matchWeight(List<RankTarget> targets, RankStudent s) {
    if (s == null) return targets.stream().anyMatch(RankTarget::isGeneral) ? 30 : 10;
    int best = 0;
    for (RankTarget t : targets) {
      boolean sameInstDept = s.institutionId() != null && Objects.equals(t.institutionId(), s.institutionId()) && s.departmentId() != null && Objects.equals(t.departmentId(), s.departmentId());
      boolean sameSubject = t.subjectMasterId() != null && s.subjectIds().contains(t.subjectMasterId());
      int w;
      if (sameInstDept && s.semester() != null && Objects.equals(t.semester(), s.semester())) w = s.batch() != null && !s.batch().isEmpty() && t.batches().contains(s.batch()) ? 100 : 80;
      else if (sameInstDept) w = 60; // own programme, another semester
      else if (sameSubject) w = 50; // same subject, other institution (or general)
      else if (t.isGeneral()) w = 30;
      else w = 10;
      best = Math.max(best, w);
    }
    return best;
  }

  public static double examProximityBoost(RankCourse c, RankStudent s, Instant now) {
    if (s == null) return 0;
    double best = 0;
    Set<UUID> subjects = new HashSet<>();
    for (RankTarget t : c.targets()) if (t.subjectMasterId() != null) subjects.add(t.subjectMasterId());
    for (Exam e : s.exams()) {
      if (e.subjectMasterId() == null || !subjects.contains(e.subjectMasterId())) continue;
      if (!Objects.equals(e.examType(), c.examType())) continue;
      double days = (e.examDate().toEpochMilli() - now.toEpochMilli()) / DAY_MS;
      if (days < 0 || days > 14) continue;
      best = Math.max(best, 40 * (1 - days / 15));
    }
    return best;
  }

  public static double popularity(double enrollments30d) {
    return Math.log(1 + Math.max(0, enrollments30d)) * 5;
  }

  public static double freshness(Instant updatedAt, Instant now) {
    double days = (now.toEpochMilli() - updatedAt.toEpochMilli()) / DAY_MS;
    return days < 0 ? 5 : Math.max(0, 5 * (1 - days / 60));
  }

  public static double rankScore(RankCourse c, RankStudent s, Instant now) {
    return matchWeight(c.targets(), s)
        + 4 * bayesianRating(c.ratingAvg(), c.ratingCount())
        + examProximityBoost(c, s, now)
        + popularity(c.enrollments30d())
        + (c.sellerVerified() ? 5 : 0)
        + freshness(c.updatedAt(), now);
  }

  public static double rankScore(RankCourse c, RankStudent s) {
    return rankScore(c, s, Instant.now());
  }

  /** Compatibility badge: match when a target matches the student's institution + department. */
  public static String compatibility(List<RankTarget> targets, RankStudent s) {
    if (!targets.isEmpty() && targets.stream().allMatch(RankTarget::isGeneral)) return "general";
    // unknown student (guest / no academic profile): never claim a mismatch we can't know
    if (s == null || s.institutionId() == null) return "general";
    if (targets.stream().anyMatch(t -> Objects.equals(t.institutionId(), s.institutionId()) && (s.departmentId() == null || t.departmentId() == null || t.departmentId().equals(s.departmentId())))) return "match";
    return targets.stream().anyMatch(RankTarget::isGeneral) ? "general" : "mismatch";
  }
}

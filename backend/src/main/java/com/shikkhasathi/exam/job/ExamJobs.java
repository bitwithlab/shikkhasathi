package com.shikkhasathi.exam.job;

import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.exam.service.ExamService;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Recurring exam jobs (scheduled in {@code common.jobs.JobSchedules}). Both are idempotent. */
public final class ExamJobs {
  private ExamJobs() {}

  /** {@code exam.replan} (01:00 UTC): refresh readiness of upcoming plans, archive plans 2 days after the exam. */
  @Component
  public static class Replan implements JobHandler<Map> {
    private final ExamService svc;

    public Replan(ExamService svc) {
      this.svc = svc;
    }

    @Override
    public String name() {
      return JobNames.EXAM_REPLAN;
    }

    @Override
    public Class<Map> payloadType() {
      return Map.class;
    }

    @Override
    public void handle(Map payload) {
      svc.replanAll();
    }
  }

  /** {@code exam.dailyPlan} (08:00 Asia/Dhaka): one "আজকের প্ল্যান" notification per plan per day (deduped by ref). */
  @Component
  public static class DailyPlan implements JobHandler<Map> {
    private final ExamService svc;

    public DailyPlan(ExamService svc) {
      this.svc = svc;
    }

    @Override
    public String name() {
      return JobNames.EXAM_DAILY_PLAN;
    }

    @Override
    public Class<Map> payloadType() {
      return Map.class;
    }

    @Override
    public void handle(Map payload) {
      svc.dailyPlanNudges(Instant.now());
    }
  }
}

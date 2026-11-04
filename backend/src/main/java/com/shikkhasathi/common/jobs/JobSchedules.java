package com.shikkhasathi.common.jobs;

import com.shikkhasathi.config.AppProperties;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recurring schedules (spec §9) — same cadence as the previous worker. Only the worker role enqueues them;
 * the minute-bucketed jobId keeps several worker replicas from double-scheduling.
 */
@Component
public class JobSchedules {
  private final JobQueue q;
  private final AppProperties props;

  public JobSchedules(JobQueue q, AppProperties props) {
    this.q = q;
    this.props = props;
  }

  private void tick(String name) {
    // Like the previous worker: a schedule only runs when some module handles that job (ledger.release and
    // cleanup have no handler yet, so they are skipped instead of piling up in Redis).
    if (!props.runsWorker() || q.inline() || !q.hasHandler(name)) return;
    q.enqueue(name, Map.of(), EnqueueOptions.jobId("schedule-" + (System.currentTimeMillis() / 60_000)).withDedupeSeconds(120));
  }

  @Scheduled(fixedRate = 5 * 60_000, initialDelay = 30_000)
  void deadlineReminders() { tick(JobNames.REMINDERS_DEADLINES); }

  @Scheduled(fixedRate = 15 * 60_000, initialDelay = 60_000)
  void classroomPoll() { tick(JobNames.CLASSROOM_POLL); }

  @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
  void renewRegistrations() { tick(JobNames.CLASSROOM_RENEW_REGISTRATIONS); }

  @Scheduled(fixedRate = 60 * 60_000, initialDelay = 120_000)
  void reconcilePayments() { tick(JobNames.PAYMENTS_RECONCILE); }

  @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
  void ledgerRelease() { tick(JobNames.LEDGER_RELEASE); }

  @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
  void examReplan() { tick(JobNames.EXAM_REPLAN); }

  /** 08:00 Asia/Dhaka */
  @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
  void examDailyPlan() { tick(JobNames.EXAM_DAILY_PLAN); }

  @Scheduled(cron = "0 45 3 * * *", zone = "UTC")
  void cleanup() { tick(JobNames.CLEANUP); }
}

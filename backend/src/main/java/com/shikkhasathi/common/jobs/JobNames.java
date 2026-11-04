package com.shikkhasathi.common.jobs;

import java.util.List;

/** Job types (spec §9) — same names as the previous BullMQ queues. */
public final class JobNames {
  public static final String EMAIL_SEND = "email.send";
  public static final String CLASSROOM_FULL_SYNC = "classroom.fullSync";
  public static final String CLASSROOM_INCREMENTAL_SYNC = "classroom.incrementalSync";
  public static final String CLASSROOM_PUBSUB_CONSUME = "classroom.pubsubConsume";
  public static final String CLASSROOM_RENEW_REGISTRATIONS = "classroom.renewRegistrations";
  public static final String CLASSROOM_POLL = "classroom.poll";
  public static final String REMINDERS_DEADLINES = "reminders.deadlines";
  public static final String NOTIFICATIONS_DISPATCH = "notifications.dispatch";
  public static final String SEARCH_INDEX_COURSE = "search.indexCourse";
  public static final String PAYMENTS_RECONCILE = "payments.reconcile";
  public static final String PAYMENTS_FULFIL = "payments.fulfil";
  public static final String LEDGER_RELEASE = "ledger.release";
  public static final String VIDEO_PROCESSED = "video.processed";
  public static final String EXAM_REPLAN = "exam.replan";
  public static final String EXAM_DAILY_PLAN = "exam.dailyPlan";
  public static final String INVOICE_GENERATE = "invoice.generate";
  public static final String CLEANUP = "cleanup";

  public static final List<String> ALL = List.of(EMAIL_SEND, CLASSROOM_FULL_SYNC, CLASSROOM_INCREMENTAL_SYNC, CLASSROOM_PUBSUB_CONSUME, CLASSROOM_RENEW_REGISTRATIONS, CLASSROOM_POLL, REMINDERS_DEADLINES, NOTIFICATIONS_DISPATCH, SEARCH_INDEX_COURSE, PAYMENTS_RECONCILE, PAYMENTS_FULFIL, LEDGER_RELEASE, VIDEO_PROCESSED, EXAM_REPLAN, EXAM_DAILY_PLAN, INVOICE_GENERATE, CLEANUP);

  private JobNames() {}
}

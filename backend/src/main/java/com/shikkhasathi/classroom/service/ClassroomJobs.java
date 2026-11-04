package com.shikkhasathi.classroom.service;

import com.shikkhasathi.classroom.dto.ClassroomDtos.PubSubMessage;
import com.shikkhasathi.classroom.service.ClassroomSyncService.RenewJob;
import com.shikkhasathi.classroom.service.ClassroomSyncService.SyncJob;
import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Job handlers of the Classroom module (same queue names as before). */
public final class ClassroomJobs {
  private ClassroomJobs() {}

  /** {@code classroom.fullSync} (trigger defaults to connect) */
  @Component
  public static class FullSync implements JobHandler<SyncJob> {
    private final ClassroomSyncService sync;

    public FullSync(ClassroomSyncService sync) {
      this.sync = sync;
    }

    @Override public String name() { return JobNames.CLASSROOM_FULL_SYNC; }
    @Override public Class<SyncJob> payloadType() { return SyncJob.class; }

    @Override
    public void handle(SyncJob j) {
      sync.runSync(new SyncJob(j.userId(), j.trigger() == null ? "connect" : j.trigger(), j.courseId()));
    }
  }

  /** {@code classroom.incrementalSync} */
  @Component
  public static class IncrementalSync implements JobHandler<SyncJob> {
    private final ClassroomSyncService sync;

    public IncrementalSync(ClassroomSyncService sync) {
      this.sync = sync;
    }

    @Override public String name() { return JobNames.CLASSROOM_INCREMENTAL_SYNC; }
    @Override public Class<SyncJob> payloadType() { return SyncJob.class; }

    @Override
    public void handle(SyncJob j) {
      sync.runSync(j);
    }
  }

  /** {@code classroom.renewRegistrations} (daily 03:15 UTC for everyone; after a full sync for one user) */
  @Component
  public static class RenewRegistrations implements JobHandler<RenewJob> {
    private final ClassroomSyncService sync;

    public RenewRegistrations(ClassroomSyncService sync) {
      this.sync = sync;
    }

    @Override public String name() { return JobNames.CLASSROOM_RENEW_REGISTRATIONS; }
    @Override public Class<RenewJob> payloadType() { return RenewJob.class; }

    @Override
    public void handle(RenewJob j) {
      sync.renewRegistrations(j);
    }
  }

  /** {@code classroom.pubsubConsume} */
  @Component
  public static class PubsubConsume implements JobHandler<PubSubMessage> {
    private final ClassroomSyncService sync;

    public PubsubConsume(ClassroomSyncService sync) {
      this.sync = sync;
    }

    @Override public String name() { return JobNames.CLASSROOM_PUBSUB_CONSUME; }
    @Override public Class<PubSubMessage> payloadType() { return PubSubMessage.class; }

    @Override
    public void handle(PubSubMessage m) {
      sync.consumePubsub(m);
    }
  }

  /** {@code classroom.poll} (every 15 min) */
  @Component
  public static class Poll implements JobHandler<Map> {
    private final ClassroomSyncService sync;

    public Poll(ClassroomSyncService sync) {
      this.sync = sync;
    }

    @Override public String name() { return JobNames.CLASSROOM_POLL; }
    @Override public Class<Map> payloadType() { return Map.class; }

    @Override
    public void handle(Map ignored) {
      sync.pollActive();
    }
  }

  /** {@code reminders.deadlines} (every 5 min) */
  @Component
  public static class DeadlineReminders implements JobHandler<Map> {
    private final ClassroomReminderService reminders;

    public DeadlineReminders(ClassroomReminderService reminders) {
      this.reminders = reminders;
    }

    @Override public String name() { return JobNames.REMINDERS_DEADLINES; }
    @Override public Class<Map> payloadType() { return Map.class; }

    @Override
    public void handle(Map ignored) {
      reminders.runDeadlineReminders(Instant.now());
    }
  }
}

package com.shikkhasathi.notification.service;

import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import org.springframework.stereotype.Component;

/** {@code notifications.dispatch}: push one notification (idempotent via {@code pushed_at}). */
@Component
public class NotificationDispatchJob implements JobHandler<NotificationService.DispatchJob> {
  private final NotificationService notifications;

  public NotificationDispatchJob(NotificationService notifications) {
    this.notifications = notifications;
  }

  @Override
  public String name() {
    return JobNames.NOTIFICATIONS_DISPATCH;
  }

  @Override
  public Class<NotificationService.DispatchJob> payloadType() {
    return NotificationService.DispatchJob.class;
  }

  @Override
  public void handle(NotificationService.DispatchJob payload) {
    notifications.dispatchPush(payload.notificationId());
  }
}

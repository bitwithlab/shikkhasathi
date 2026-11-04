package com.shikkhasathi.notification.email;

import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import org.springframework.stereotype.Component;

/** {@code email.send}: delivers a queued email (retried with backoff by the job queue). */
@Component
public class EmailSendJob implements JobHandler<EmailService.EmailJob> {
  private final EmailService email;

  public EmailSendJob(EmailService email) {
    this.email = email;
  }

  @Override
  public String name() {
    return JobNames.EMAIL_SEND;
  }

  @Override
  public Class<EmailService.EmailJob> payloadType() {
    return EmailService.EmailJob.class;
  }

  @Override
  public void handle(EmailService.EmailJob payload) {
    email.deliver(payload);
  }
}

package com.shikkhasathi.notification.email;

import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.notification.email.EmailMessage.EmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev/test provider: keeps the last 200 messages in memory (for tests) and logs the subject only. */
@Component
public class FakeEmailProvider implements EmailProvider, ResettableFake {
  private static final Logger log = LoggerFactory.getLogger(FakeEmailProvider.class);

  /** A message captured by the fake. */
  public record Sent(String to, String subject, String text, String html, EmailSender from) {}

  private final List<Sent> outbox = new ArrayList<>();
  private final boolean quiet;

  public FakeEmailProvider(AppProperties props) {
    this.quiet = props.isTest();
  }

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public synchronized String send(EmailMessage msg, EmailSender from) {
    outbox.add(new Sent(msg.to(), msg.subject(), msg.text(), msg.html(), from));
    if (outbox.size() > 200) outbox.remove(0);
    if (!quiet) log.info("[fake-email] → {}: {}", msg.to(), msg.subject());
    return "fake-" + UUID.randomUUID();
  }

  /** Snapshot of captured messages, oldest first. */
  public synchronized List<Sent> outbox() {
    return List.copyOf(outbox);
  }

  /** Most recent message, or null. */
  public synchronized Sent last() {
    return outbox.isEmpty() ? null : outbox.get(outbox.size() - 1);
  }

  @Override
  public synchronized void reset() {
    outbox.clear();
  }
}

package com.shikkhasathi.notification.sms;

import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev/test: records the last 200 messages; prints them only in development (never in production). */
@Component
public class FakeSmsProvider implements SmsProvider, ResettableFake {
  private static final Logger log = LoggerFactory.getLogger(FakeSmsProvider.class);

  /** A captured SMS. */
  public record Sent(String to, String message) {}

  private final List<Sent> outbox = new ArrayList<>();
  private final boolean dev;

  public FakeSmsProvider(AppProperties props) {
    this.dev = "development".equals(props.env());
  }

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public synchronized void send(String to, String message) {
    outbox.add(new Sent(to, message));
    if (outbox.size() > 200) outbox.remove(0);
    if (dev) log.info("[fake-sms] to={} {}", to, message);
  }

  /** Snapshot, oldest first. */
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

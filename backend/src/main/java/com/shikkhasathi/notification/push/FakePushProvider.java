package com.shikkhasathi.notification.push;

import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev/test push: records the last 200 sends. */
@Component
public class FakePushProvider implements PushProvider, ResettableFake {
  private static final Logger log = LoggerFactory.getLogger(FakePushProvider.class);

  /** A captured send. */
  public record Sent(List<String> tokens, PushMessage msg) {}

  private final List<Sent> sent = new ArrayList<>();
  private final boolean dev;

  public FakePushProvider(AppProperties props) {
    this.dev = "development".equals(props.env());
  }

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public synchronized List<String> send(List<Target> targets, PushMessage msg) {
    sent.add(new Sent(targets.stream().map(Target::token).toList(), msg));
    if (sent.size() > 200) sent.remove(0);
    if (dev) log.info("[fake-push] n={} {}", targets.size(), msg.title());
    return List.of();
  }

  /** Snapshot, oldest first. */
  public synchronized List<Sent> sent() {
    return List.copyOf(sent);
  }

  @Override
  public synchronized void reset() {
    sent.clear();
  }
}

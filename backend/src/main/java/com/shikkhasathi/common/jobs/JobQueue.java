package com.shikkhasathi.common.jobs;

import com.shikkhasathi.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Durable Redis-backed job queue (replaces BullMQ). Each job type is a Redis list {@code ss:q:<name>};
 * delayed/retrying jobs sit in the sorted set {@code ss:q:delayed} until due; exhausted jobs go to
 * {@code ss:q:failed:<name>}. Workers run on virtual threads when this process has the worker role.
 * In inline mode (tests / INLINE_JOBS) jobs execute synchronously in the caller's thread.
 */
@Component
public class JobQueue {
  private static final Logger log = LoggerFactory.getLogger(JobQueue.class);
  private static final String P = "ss:q:";
  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final AppProperties props;
  private final List<JobHandler<?>> handlerList;
  private final Map<String, JobHandler<?>> handlers = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> active = new ConcurrentHashMap<>();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final List<Thread> threads = new CopyOnWriteArrayList<>();
  /** inline-mode log (tests can assert what was enqueued) */
  private final List<Map.Entry<String, Object>> inlineLog = new CopyOnWriteArrayList<>();

  public JobQueue(StringRedisTemplate redis, JsonMapper json, AppProperties props, @Lazy List<JobHandler<?>> handlerList) {
    this.redis = redis;
    this.json = json;
    this.props = props;
    this.handlerList = handlerList;
  }

  record Envelope(String name, String jobId, int attempt, int maxAttempts, boolean sensitive, Object payload) {}

  private void index() {
    if (handlers.isEmpty()) for (var h : handlerList) handlers.put(h.name(), h);
  }

  public boolean inline() {
    return props.jobsInline();
  }

  public List<Map.Entry<String, Object>> inlineLog() {
    return inlineLog;
  }

  public void enqueue(String name, Object payload) {
    enqueue(name, payload, EnqueueOptions.DEFAULT);
  }

  public void enqueue(String name, Object payload, EnqueueOptions o) {
    index();
    JobHandler<?> h = handlers.get(name);
    if (inline()) {
      inlineLog.add(Map.entry(name, payload));
      if (h == null || o.delayMs() > 0) return; // delayed jobs are not run inline (same as before)
      try {
        run(h, json.writeValueAsString(payload));
      } catch (Exception e) {
        log.error("inline job {} failed", name, e);
        if (o.attempts() != null && o.attempts() == 1) throw e instanceof RuntimeException r ? r : new IllegalStateException(e);
      }
      return;
    }
    if (o.jobId() != null) {
      Boolean fresh = redis.opsForValue().setIfAbsent(P + "id:" + name + ":" + o.jobId(), "1", Duration.ofSeconds(Math.max(1, o.dedupeSeconds())));
      if (!Boolean.TRUE.equals(fresh)) return;
    }
    int max = o.attempts() != null ? o.attempts() : h != null ? h.attempts() : 5;
    String env = json.writeValueAsString(new Envelope(name, o.jobId(), 1, max, o.sensitive(), payload));
    if (o.delayMs() > 0) redis.opsForZSet().add(P + "delayed", env, System.currentTimeMillis() + o.delayMs());
    else redis.opsForList().leftPush(P + name, env);
  }

  @SuppressWarnings("unchecked")
  private <T> void run(JobHandler<T> h, String payloadJson) throws Exception {
    T payload = json.readValue(payloadJson, h.payloadType());
    h.handle(payload);
  }

  // ---------------------------------------------------------------- worker

  /** Started once the context is ready (handlers depend on this bean, so not in @PostConstruct). */
  @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
  void start() {
    if (inline() || !props.runsWorker()) return;
    index();
    running.set(true);
    for (var h : handlers.values()) {
      for (int i = 0; i < h.concurrency(); i++) {
        threads.add(Thread.ofVirtual().name("job-" + h.name() + "-" + i).start(() -> loop(h)));
      }
    }
    log.info("job workers started for {} queues", handlers.size());
  }

  @PreDestroy
  void stop() {
    running.set(false);
    threads.forEach(Thread::interrupt);
  }

  private void loop(JobHandler<?> h) {
    while (running.get()) {
      String raw;
      try {
        raw = redis.opsForList().rightPop(P + h.name(), Duration.ofSeconds(2));
      } catch (RuntimeException e) {
        if (!running.get()) return;
        sleep(1000);
        continue;
      }
      if (raw == null) continue;
      Envelope env;
      try {
        env = json.readValue(raw, Envelope.class);
      } catch (RuntimeException e) {
        log.error("dropping malformed job on {}", h.name());
        continue;
      }
      active.computeIfAbsent(h.name(), k -> new AtomicInteger()).incrementAndGet();
      try {
        run(h, json.writeValueAsString(env.payload()));
      } catch (Exception e) {
        log.warn("job {} (id={}) failed attempt {}/{}: {}", h.name(), env.jobId(), env.attempt(), env.maxAttempts(), e.toString());
        retry(env);
      } finally {
        active.get(h.name()).decrementAndGet();
      }
    }
  }

  private void retry(Envelope env) {
    if (env.attempt() >= env.maxAttempts()) {
      if (!env.sensitive()) {
        redis.opsForList().leftPush(P + "failed:" + env.name(), json.writeValueAsString(env));
        redis.opsForList().trim(P + "failed:" + env.name(), 0, 4999);
      }
      return;
    }
    long backoff = 5000L * (1L << (env.attempt() - 1)); // exponential, 5 s base (same as before)
    var next = new Envelope(env.name(), env.jobId(), env.attempt() + 1, env.maxAttempts(), env.sensitive(), env.payload());
    redis.opsForZSet().add(P + "delayed", json.writeValueAsString(next), System.currentTimeMillis() + backoff);
  }

  /** Moves due delayed/retry jobs onto their queues. */
  @Scheduled(fixedDelay = 1000)
  void promoteDelayed() {
    if (!running.get()) return;
    var due = redis.opsForZSet().rangeByScore(P + "delayed", 0, System.currentTimeMillis(), 0, 200);
    if (due == null) return;
    for (String raw : due) {
      Long removed = redis.opsForZSet().remove(P + "delayed", raw);
      if (removed == null || removed == 0) continue; // another instance took it
      String name = json.readValue(raw, Envelope.class).name();
      redis.opsForList().leftPush(P + name, raw);
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ---------------------------------------------------------------- stats (Admin → System health)

  public record QueueStat(String name, long waiting, long active, long failed, long delayed) {}

  public List<QueueStat> stats() {
    if (inline()) return List.of();
    index();
    var delayed = Optional.ofNullable(redis.opsForZSet().range(P + "delayed", 0, -1)).orElse(Set.of());
    Map<String, Long> delayedBy = new HashMap<>();
    for (String raw : delayed) {
      try {
        delayedBy.merge(json.readValue(raw, Envelope.class).name(), 1L, Long::sum);
      } catch (RuntimeException ignored) {
        // skip
      }
    }
    return JobNames.ALL.stream()
        .map(n -> new QueueStat(n,
            Optional.ofNullable(redis.opsForList().size(P + n)).orElse(0L),
            active.getOrDefault(n, new AtomicInteger()).get(),
            Optional.ofNullable(redis.opsForList().size(P + "failed:" + n)).orElse(0L),
            delayedBy.getOrDefault(n, 0L)))
        .toList();
  }
}

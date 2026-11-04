package com.shikkhasathi.common.jobs;

/**
 * @param jobId    de-duplication key: a second enqueue with the same id within {@code dedupeSeconds} is dropped
 * @param delayMs  run no earlier than now + delay (not run in inline/test mode — same as before)
 * @param attempts override the handler's max attempts
 * @param sensitive payload contains one-time codes: never kept in the failed list
 */
public record EnqueueOptions(String jobId, long delayMs, Integer attempts, int dedupeSeconds, boolean sensitive) {
  public static final EnqueueOptions DEFAULT = new EnqueueOptions(null, 0, null, 3600, false);

  public static EnqueueOptions jobId(String id) {
    return new EnqueueOptions(id, 0, null, 3600, false);
  }

  public static EnqueueOptions delay(long ms) {
    return new EnqueueOptions(null, ms, null, 3600, false);
  }

  public EnqueueOptions withJobId(String id) { return new EnqueueOptions(id, delayMs, attempts, dedupeSeconds, sensitive); }
  public EnqueueOptions withDelay(long ms) { return new EnqueueOptions(jobId, ms, attempts, dedupeSeconds, sensitive); }
  public EnqueueOptions withAttempts(int n) { return new EnqueueOptions(jobId, delayMs, n, dedupeSeconds, sensitive); }
  public EnqueueOptions withDedupeSeconds(int s) { return new EnqueueOptions(jobId, delayMs, attempts, s, sensitive); }
  public EnqueueOptions asSensitive() { return new EnqueueOptions(jobId, delayMs, attempts, dedupeSeconds, true); }
}

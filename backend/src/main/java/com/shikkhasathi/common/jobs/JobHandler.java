package com.shikkhasathi.common.jobs;

/**
 * A background job type (replaces the previous BullMQ queues). Implement as a Spring bean; {@link JobQueue}
 * discovers all handlers. Handlers must be idempotent — jobs are retried with exponential backoff.
 */
public interface JobHandler<T> {
  /** queue name, e.g. {@code payments.fulfil} (same names as before) */
  String name();

  Class<T> payloadType();

  void handle(T payload) throws Exception;

  /** max attempts (default 5, same as before) */
  default int attempts() {
    return 5;
  }

  /** worker threads for this queue */
  default int concurrency() {
    return 3;
  }
}

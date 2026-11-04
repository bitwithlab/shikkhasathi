package com.shikkhasathi.file.service;

import java.util.Map;

/**
 * Object storage (CLAUDE.md §0B): Supabase Storage in production ({@link SupabaseStorage}), local disk in dev/tests
 * ({@link LocalStorage}). Private files are only ever handed out as short-lived signed URLs issued by the API.
 * Inject this interface; key helpers are in {@link StorageKeys}.
 */
public interface StorageService {
  /** Where the client PUTs the file, plus headers it must send. */
  record UploadTarget(String url, Map<String, String> headers) {}

  /** {@code supabase} or {@code local} */
  String name();

  /** Signed URL the client PUTs the file to. */
  UploadTarget signedUploadUrl(Bucket bucket, String key, String mime);

  /** Short-lived signed download URL (private buckets); {@code downloadName} sets Content-Disposition (nullable). */
  String signedDownloadUrl(Bucket bucket, String key, long ttlSec, String downloadName);

  /** Signed download URL valid for 10 minutes. */
  default String signedDownloadUrl(Bucket bucket, String key) {
    return signedDownloadUrl(bucket, key, 600, null);
  }

  /** Permanent URL for a public bucket. */
  String publicUrl(Bucket bucket, String key);

  /** Server-side upload (upsert). */
  void put(Bucket bucket, String key, byte[] body, String mime);

  /** Server-side download; throws {@link StorageException} when missing. */
  byte[] get(Bucket bucket, String key);

  boolean exists(Bucket bucket, String key);

  /** Storage backend failure. */
  class StorageException extends RuntimeException {
    public StorageException(String message) {
      super(message);
    }

    public StorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

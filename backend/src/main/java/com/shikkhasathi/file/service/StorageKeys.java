package com.shikkhasathi.file.service;

import com.shikkhasathi.common.Crypto;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Object-key helpers shared by every module that stores files. */
public final class StorageKeys {
  /** File extension by accepted upload MIME type. */
  public static final Map<String, String> EXT_BY_MIME = Map.of(
      "application/pdf", "pdf",
      "image/jpeg", "jpg",
      "image/png", "png",
      "image/webp", "webp",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

  /** Content type by extension (local file serving). */
  public static final Map<String, String> MIME_BY_EXT = Map.of(
      "pdf", "application/pdf",
      "jpg", "image/jpeg",
      "png", "image/png",
      "webp", "image/webp",
      "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

  private StorageKeys() {}

  /** {@code <prefix>/<16 hex random>.<ext>} with the extension taken (sanitised, ≤5 chars) from {@code filename}. */
  public static String objectKey(String prefix, String filename) {
    String f = filename == null ? "" : filename;
    int dot = f.lastIndexOf('.');
    String raw = dot >= 0 ? f.substring(dot + 1) : f; // JS split('.').pop(): whole name when there is no dot
    String ext = raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (ext.length() > 5) ext = ext.substring(0, 5);
    if (ext.isEmpty()) ext = "bin";
    String rand = Crypto.sha256(System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextDouble()).substring(0, 16);
    return prefix + "/" + rand + "." + ext;
  }

  /** Content type for a key by its extension, {@code application/octet-stream} when unknown. */
  public static String mimeFor(String key) {
    int slash = key.lastIndexOf('/');
    String name = key.substring(slash + 1);
    int dot = name.lastIndexOf('.');
    if (dot <= 0) return "application/octet-stream";
    return MIME_BY_EXT.getOrDefault(name.substring(dot + 1), "application/octet-stream");
  }
}

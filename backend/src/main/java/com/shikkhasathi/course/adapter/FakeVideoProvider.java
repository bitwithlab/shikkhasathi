package com.shikkhasathi.course.adapter;

import com.shikkhasathi.common.Crypto;
import java.util.Map;

/** Dev/demo: no real upload; plays a public sample HLS stream. */
public class FakeVideoProvider implements VideoProvider {
  public static final String SAMPLE = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

  @Override
  public String name() {
    return "fake";
  }

  @Override
  public UploadSlot createUpload(String title) {
    String sha1;
    try {
      sha1 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest((title + System.currentTimeMillis()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      sha1 = Crypto.sha256(title + System.nanoTime());
    }
    return new UploadSlot("fake-" + sha1.substring(0, 12), "", Map.of());
  }

  @Override
  public String playbackUrl(String providerVideoId, long ttlSec) {
    return SAMPLE;
  }

  @Override
  public VideoStatus status(String providerVideoId) {
    return new VideoStatus("ready", 600);
  }
}

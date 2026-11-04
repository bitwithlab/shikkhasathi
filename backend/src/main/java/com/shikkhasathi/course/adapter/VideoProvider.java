package com.shikkhasathi.course.adapter;

import java.util.Map;

/** Video hosting (Bunny Stream by default; fake in dev). Playback URLs are issued only by the API to enrolled users. */
public interface VideoProvider {
  /** A created video slot with resumable (TUS) upload instructions; {@code uploadUrl} is empty for the fake. */
  record UploadSlot(String providerVideoId, String uploadUrl, Map<String, String> headers) {}

  /** {@code status}: processing | ready | failed */
  record VideoStatus(String status, int durationSec) {}

  /** {@code bunny} | {@code fake} */
  String name();

  /** Create a video slot; returns provider id + TUS upload headers. */
  UploadSlot createUpload(String title);

  /** Short-lived HLS playback URL. */
  String playbackUrl(String providerVideoId, long ttlSec);

  /** Processing state + duration. */
  VideoStatus status(String providerVideoId);
}

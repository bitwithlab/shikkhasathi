package com.shikkhasathi.course.adapter;

import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Bunny Stream: TUS upload with signed headers; token-authenticated HLS via the pull zone (directory token). */
public class BunnyStreamProvider implements VideoProvider {
  /** {@code tokenKey}/{@code cdnHost} nullable (no token auth / default {@code vz-<library>.b-cdn.net}). */
  public record Config(String libraryId, String apiKey, String tokenKey, String cdnHost) {}

  private final Config cfg;
  private final HttpTransport http;
  private final JsonMapper json;

  public BunnyStreamProvider(Config cfg, HttpTransport http, JsonMapper json) {
    this.cfg = cfg;
    this.http = http;
    this.json = json;
  }

  @Override
  public String name() {
    return "bunny";
  }

  @Override
  public UploadSlot createUpload(String title) {
    var res = http.send(Request.post("https://video.bunnycdn.com/library/" + cfg.libraryId() + "/videos", json.writeValueAsString(Map.of("title", title)))
        .header("AccessKey", cfg.apiKey()).header("content-type", "application/json").header("accept", "application/json"));
    if (!res.ok()) throw new IllegalStateException("Bunny create " + res.status());
    String guid = json.readTree(res.text()).path("guid").asString();
    long expires = System.currentTimeMillis() / 1000 + 24 * 3600;
    String signature = Crypto.sha256(cfg.libraryId() + cfg.apiKey() + expires + guid);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("AuthorizationSignature", signature);
    headers.put("AuthorizationExpire", String.valueOf(expires));
    headers.put("VideoId", guid);
    headers.put("LibraryId", cfg.libraryId());
    return new UploadSlot(guid, "https://video.bunnycdn.com/tusupload", headers);
  }

  @Override
  public String playbackUrl(String id, long ttlSec) {
    String host = cfg.cdnHost() == null || cfg.cdnHost().isEmpty() ? "vz-" + cfg.libraryId() + ".b-cdn.net" : cfg.cdnHost();
    String path = "/" + id + "/playlist.m3u8";
    if (cfg.tokenKey() == null || cfg.tokenKey().isEmpty()) return "https://" + host + path;
    long expires = System.currentTimeMillis() / 1000 + ttlSec;
    return "https://" + host + "/bcdn_token=" + token(cfg.tokenKey(), id, expires) + "&expires=" + expires + "&token_path=" + URLEncoder.encode("/" + id + "/", StandardCharsets.UTF_8).replace("+", "%20") + path;
  }

  /** Bunny CDN directory token: base64url(sha256(key + path + expires + "token_path=" + path)) without padding. */
  static String token(String tokenKey, String id, long expires) {
    String tokenPath = "/" + id + "/";
    return Base64.getUrlEncoder().withoutPadding().encodeToString(Crypto.sha256Bytes(tokenKey + tokenPath + expires + "token_path=" + tokenPath));
  }

  @Override
  public VideoStatus status(String id) {
    var res = http.send(Request.get("https://video.bunnycdn.com/library/" + cfg.libraryId() + "/videos/" + id).header("AccessKey", cfg.apiKey()).header("accept", "application/json"));
    if (!res.ok()) return new VideoStatus("failed", 0);
    JsonNode v = json.readTree(res.text());
    int st = v.path("status").asInt(-1);
    // 0 created, 1 uploaded, 2 processing, 3 transcoding, 4 finished, 5 error
    return new VideoStatus(st == 4 ? "ready" : st == 5 ? "failed" : "processing", v.path("length").asInt(0));
  }
}

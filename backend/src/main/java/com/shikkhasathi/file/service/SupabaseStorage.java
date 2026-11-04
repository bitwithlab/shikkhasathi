package com.shikkhasathi.file.service;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Supabase Storage REST with the service-role keys (server-only; never sent to clients). */
public class SupabaseStorage implements StorageService {
  /** One Supabase project: base URL + service-role key. */
  public record Project(String url, String serviceRoleKey) {}

  private final Project core;
  private final Project content;
  private final HttpTransport http;
  private final JsonMapper json;

  public SupabaseStorage(Project core, Project content, HttpTransport http, JsonMapper json) {
    this.core = new Project(trim(core.url()), core.serviceRoleKey());
    this.content = new Project(trim(content.url()), content.serviceRoleKey());
    this.http = http;
    this.json = json;
  }

  private static String trim(String u) {
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }

  private Project p(Bucket b) {
    return "core".equals(b.db()) ? core : content;
  }

  private Request auth(Bucket b, Request r) {
    String k = p(b).serviceRoleKey();
    return r.header("authorization", "Bearer " + k).header("apikey", k);
  }

  @Override
  public String name() {
    return "supabase";
  }

  @Override
  public UploadTarget signedUploadUrl(Bucket bucket, String key, String mime) {
    String url = p(bucket).url();
    var res = http.send(auth(bucket, Request.post(url + "/storage/v1/object/upload/sign/" + bucket.id() + "/" + key, (byte[]) null)));
    if (!res.ok()) throw new StorageException("storage sign upload " + res.status());
    return new UploadTarget(url + "/storage/v1" + json.readTree(res.text()).path("url").asString(), Map.of());
  }

  @Override
  public String signedDownloadUrl(Bucket bucket, String key, long ttlSec, String downloadName) {
    String url = p(bucket).url();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("expiresIn", ttlSec);
    if (downloadName != null && !downloadName.isEmpty()) body.put("download", downloadName);
    var res = http.send(auth(bucket, Request.post(url + "/storage/v1/object/sign/" + bucket.id() + "/" + key, json.writeValueAsString(body))).header("content-type", "application/json"));
    if (!res.ok()) throw new StorageException("storage sign " + res.status());
    return url + "/storage/v1" + json.readTree(res.text()).path("signedURL").asString();
  }

  @Override
  public String publicUrl(Bucket bucket, String key) {
    return p(bucket).url() + "/storage/v1/object/public/" + bucket.id() + "/" + key;
  }

  @Override
  public void put(Bucket bucket, String key, byte[] body, String mime) {
    var res = http.send(auth(bucket, Request.post(p(bucket).url() + "/storage/v1/object/" + bucket.id() + "/" + key, body)).header("content-type", mime).header("x-upsert", "true").timeout(java.time.Duration.ofSeconds(60)));
    if (!res.ok()) throw new StorageException("storage put " + res.status());
  }

  @Override
  public byte[] get(Bucket bucket, String key) {
    var res = http.send(auth(bucket, Request.get(p(bucket).url() + "/storage/v1/object/" + bucket.id() + "/" + key)).timeout(java.time.Duration.ofSeconds(60)));
    if (!res.ok()) throw new StorageException("storage get " + res.status());
    return res.body();
  }

  @Override
  public boolean exists(Bucket bucket, String key) {
    try {
      return http.send(auth(bucket, Request.get(p(bucket).url() + "/storage/v1/object/info/" + bucket.id() + "/" + key))).ok();
    } catch (HttpTransport.HttpException e) {
      return false;
    }
  }
}

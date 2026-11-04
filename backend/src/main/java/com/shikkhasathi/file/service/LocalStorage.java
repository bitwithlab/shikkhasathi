package com.shikkhasathi.file.service;

import com.shikkhasathi.security.JwtService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Dev/test storage: files on disk under {@code ss.local-storage-dir}, served by {@code /api/v1/files/*} with signed
 * purpose tokens ({@code upload}: b,k,m — {@code download}: b,k,n), exactly like the previous API.
 */
public class LocalStorage implements StorageService {
  /** Verified upload token claims. */
  public record UploadClaims(Bucket bucket, String key, String mime) {}

  /** Verified download token claims; {@code downloadName} may be null. */
  public record DownloadClaims(Bucket bucket, String key, String downloadName) {}

  private final Path root;
  private final String appUrl;
  private final JwtService jwt;

  public LocalStorage(Path root, String appUrl, JwtService jwt) {
    this.root = root.toAbsolutePath().normalize();
    this.appUrl = appUrl;
    this.jwt = jwt;
  }

  /** Resolve a key inside the bucket folder; rejects path traversal. */
  Path path(Bucket bucket, String key) {
    Path p = root.resolve(bucket.id()).resolve(key).normalize();
    if (!p.startsWith(root)) throw new StorageException("bad key");
    return p;
  }

  @Override
  public String name() {
    return "local";
  }

  @Override
  public UploadTarget signedUploadUrl(Bucket bucket, String key, String mime) {
    String token = jwt.signPurpose("upload", Map.of("b", bucket.id(), "k", key, "m", mime), 3600);
    return new UploadTarget(appUrl + "/api/v1/files/upload?token=" + token, Map.of("content-type", mime));
  }

  @Override
  public String signedDownloadUrl(Bucket bucket, String key, long ttlSec, String downloadName) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("b", bucket.id());
    claims.put("k", key);
    claims.put("n", downloadName); // null → claim omitted, read back as null
    String token = jwt.signPurpose("download", claims, ttlSec);
    return appUrl + "/api/v1/files/download?token=" + token;
  }

  @Override
  public String publicUrl(Bucket bucket, String key) {
    return appUrl + "/api/v1/files/public/" + bucket.id() + "/" + key;
  }

  @Override
  public void put(Bucket bucket, String key, byte[] body, String mime) {
    Path p = path(bucket, key);
    try {
      Files.createDirectories(p.getParent());
      Files.write(p, body);
    } catch (IOException e) {
      throw new StorageException("local put failed", e);
    }
  }

  @Override
  public byte[] get(Bucket bucket, String key) {
    try {
      return Files.readAllBytes(path(bucket, key));
    } catch (IOException e) {
      throw new StorageException("not found", e);
    }
  }

  @Override
  public boolean exists(Bucket bucket, String key) {
    try {
      return Files.isRegularFile(path(bucket, key));
    } catch (StorageException e) {
      return false;
    }
  }

  /** Verify an upload token (401 when expired/invalid). */
  public UploadClaims verifyUpload(String token) {
    var c = jwt.verifyPurpose("upload", token == null ? "" : token);
    return new UploadClaims(bucketClaim(c), (String) c.get("k"), (String) c.get("m"));
  }

  /** Verify a download token (401 when expired/invalid). */
  public DownloadClaims verifyDownload(String token) {
    var c = jwt.verifyPurpose("download", token == null ? "" : token);
    return new DownloadClaims(bucketClaim(c), (String) c.get("k"), c.get("n") instanceof String s ? s : null);
  }

  private static Bucket bucketClaim(Map<String, Object> c) {
    return Bucket.of(String.valueOf(c.get("b"))).orElseThrow(() -> com.shikkhasathi.common.ApiException.unauthorized("লিংকটির মেয়াদ শেষ বা অবৈধ।"));
  }
}

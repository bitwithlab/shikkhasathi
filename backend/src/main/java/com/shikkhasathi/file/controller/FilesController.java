package com.shikkhasathi.file.controller;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.LocalStorage;
import com.shikkhasathi.file.service.StorageKeys;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.security.Auth;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Local storage endpoints (dev/test only — production uses Supabase Storage signed URLs directly). */
@RestController
@RequestMapping("/api/v1/files")
@Auth(Auth.Level.PUBLIC)
public class FilesController {
  private final StorageService storage;

  public FilesController(StorageService storage) {
    this.storage = storage;
  }

  private LocalStorage local() {
    if (!(storage instanceof LocalStorage l)) throw ApiException.notFound();
    return l;
  }

  @PutMapping("/upload")
  public Map<String, Object> upload(@RequestParam(required = false) String token, @RequestBody(required = false) byte[] body) {
    LocalStorage s = local();
    var t = s.verifyUpload(token);
    if (body == null) throw ApiException.badRequest("ফাইল পাওয়া যায়নি।");
    s.put(t.bucket(), t.key(), body, t.mime());
    return Map.of("ok", true);
  }

  @GetMapping("/download")
  public ResponseEntity<byte[]> download(@RequestParam(required = false) String token) {
    LocalStorage s = local();
    var t = s.verifyDownload(token);
    byte[] buf = read(s, t.bucket(), t.key());
    var r = ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, StorageKeys.mimeFor(t.key())).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
    if (t.downloadName() != null && !t.downloadName().isEmpty()) r = r.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.downloadName().replaceAll("[^A-Za-z0-9_.-]", "_") + "\"");
    return r.body(buf);
  }

  @GetMapping("/public/{bucket}/{*key}")
  public ResponseEntity<byte[]> publicFile(@PathVariable String bucket, @PathVariable String key) {
    String k = key.startsWith("/") ? key.substring(1) : key;
    Bucket b = Bucket.of(bucket).filter(Bucket::isPublic).orElseThrow(ApiException::notFound);
    byte[] buf = read(local(), b, k);
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, StorageKeys.mimeFor(k)).header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400").body(buf);
  }

  private static byte[] read(LocalStorage s, Bucket b, String key) {
    try {
      return s.get(b, key);
    } catch (StorageService.StorageException e) {
      throw ApiException.notFound();
    }
  }
}

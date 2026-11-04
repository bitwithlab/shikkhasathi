package com.shikkhasathi.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.file.service.*;
import com.shikkhasathi.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class StorageTest extends IntegrationTest {
  @Autowired StorageService storage;

  private static String path(String url) {
    return url.replaceFirst("^https?://[^/]+", "");
  }

  @Test
  void localSignedUploadAndDownloadRoundTrip() {
    assertThat(storage.name()).isEqualTo("local");
    String key = StorageKeys.objectKey("docs/test", "Lecture Notes.PDF");
    assertThat(key).matches("docs/test/[0-9a-f]{16}\\.pdf");
    var up = storage.signedUploadUrl(Bucket.COURSE_DOCS, key, "application/pdf");
    assertThat(up.headers()).containsEntry("content-type", "application/pdf");
    byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
    var r = call(HttpMethod.PUT, path(up.url()), pdf, null, Map.of("Content-Type", "application/pdf"));
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.at("/ok").asBoolean()).isTrue();
    assertThat(storage.exists(Bucket.COURSE_DOCS, key)).isTrue();
    assertThat(storage.exists(Bucket.COURSE_DOCS, "docs/test/missing.pdf")).isFalse();

    var dl = get(path(storage.signedDownloadUrl(Bucket.COURSE_DOCS, key, 600, "নোট CSE-2203.pdf")));
    assertThat(dl.status()).isEqualTo(200);
    assertThat(dl.header("content-type")).isEqualTo("application/pdf");
    assertThat(dl.header("cache-control")).isEqualTo("private, max-age=300");
    assertThat(dl.header("content-disposition")).isEqualTo("attachment; filename=\"____CSE-2203.pdf\"");
    assertThat(dl.body()).isEqualTo("%PDF-1.4 test");

    // a download token can't be used to upload, garbage tokens are rejected
    String dlToken = storage.signedDownloadUrl(Bucket.COURSE_DOCS, key).split("token=")[1];
    assertThat(call(HttpMethod.PUT, "/files/upload?token=" + dlToken, pdf, null, Map.of("Content-Type", "application/pdf")).status()).isEqualTo(401);
    assertThat(get("/files/download?token=nope").status()).isEqualTo(401);
    assertThat(get("/files/download").status()).isEqualTo(401);
  }

  @Test
  void publicRouteServesOnlyPublicBuckets() {
    storage.put(Bucket.COURSE_COVERS, "covers/a/b.png", new byte[] {1, 2, 3}, "image/png");
    storage.put(Bucket.INVOICES, "inv/x.pdf", new byte[] {1}, "application/pdf");
    var r = get(path(storage.publicUrl(Bucket.COURSE_COVERS, "covers/a/b.png")));
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.header("content-type")).isEqualTo("image/png");
    assertThat(r.header("cache-control")).isEqualTo("public, max-age=86400");
    assertThat(get("/files/public/invoices/inv/x.pdf").status()).isEqualTo(404);
    assertThat(get("/files/public/nope/x.png").status()).isEqualTo(404);
    assertThat(get("/files/public/course-covers/covers/missing.png").status()).isEqualTo(404);
    assertThat(storage.get(Bucket.INVOICES, "inv/x.pdf")).containsExactly(1);
  }

  @Test
  void rejectsPathTraversal() {
    assertThat(storage.exists(Bucket.COURSE_DOCS, "../../etc/passwd")).isFalse();
    assertThat(get("/files/public/course-covers/../../../etc/passwd").status()).isIn(400, 404);
  }

  @Test
  void supabaseStorageCallsTheRestApiWithTheServiceRoleKey() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var s = new SupabaseStorage(new SupabaseStorage.Project("https://core.supabase.co/", "core-key"), new SupabaseStorage.Project("https://content.supabase.co", "content-key"), r -> {
      calls.add(r);
      if (r.url().contains("/upload/sign/")) return HttpTransport.Response.of(200, "{\"url\":\"/object/upload/sign/course-docs/k.pdf?token=t\"}");
      if (r.url().contains("/object/sign/")) return HttpTransport.Response.of(200, "{\"signedURL\":\"/object/sign/invoices/i.pdf?token=t\"}");
      return HttpTransport.Response.of(200, "{}");
    }, json);
    assertThat(s.signedUploadUrl(Bucket.COURSE_DOCS, "k.pdf", "application/pdf").url()).isEqualTo("https://content.supabase.co/storage/v1/object/upload/sign/course-docs/k.pdf?token=t");
    assertThat(calls.get(0).headerValue("apikey")).isEqualTo("content-key");
    assertThat(s.signedDownloadUrl(Bucket.INVOICES, "i.pdf", 60, "SS-1.pdf")).isEqualTo("https://core.supabase.co/storage/v1/object/sign/invoices/i.pdf?token=t");
    assertThat(calls.get(1).headerValue("authorization")).isEqualTo("Bearer core-key");
    assertThat(calls.get(1).bodyText()).isEqualTo("{\"expiresIn\":60,\"download\":\"SS-1.pdf\"}");
    s.put(Bucket.COURSE_COVERS, "c.png", new byte[] {1}, "image/png");
    assertThat(calls.get(2).url()).isEqualTo("https://content.supabase.co/storage/v1/object/course-covers/c.png");
    assertThat(calls.get(2).headerValue("x-upsert")).isEqualTo("true");
    assertThat(s.publicUrl(Bucket.COURSE_COVERS, "c.png")).isEqualTo("https://content.supabase.co/storage/v1/object/public/course-covers/c.png");
  }
}

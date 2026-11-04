package com.shikkhasathi.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.course.adapter.BunnyStreamProvider;
import com.shikkhasathi.course.adapter.FakeVideoProvider;
import com.shikkhasathi.course.adapter.VideoService;
import com.shikkhasathi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class VideoAdapterTest extends IntegrationTest {
  @Autowired VideoService video;

  @Test
  void fakeOutsideProduction() {
    var p = video.provider();
    assertThat(p.name()).isEqualTo("fake");
    assertThat(p.createUpload("Lecture 1").providerVideoId()).matches("fake-[0-9a-f]{12}");
    assertThat(p.playbackUrl("x", 60)).isEqualTo(FakeVideoProvider.SAMPLE);
    assertThat(p.status("x")).isEqualTo(new com.shikkhasathi.course.adapter.VideoProvider.VideoStatus("ready", 600));
  }

  @Test
  void bunnyUploadSignatureTokenUrlAndStatus() {
    List<HttpTransport.Request> calls = new ArrayList<>();
    var p = new BunnyStreamProvider(new BunnyStreamProvider.Config("123", "APIKEY", "tokkey", null), r -> {
      calls.add(r);
      if (r.method().equals("POST")) return HttpTransport.Response.of(200, "{\"guid\":\"vid-1\"}");
      return HttpTransport.Response.of(200, "{\"status\":4,\"length\":754}");
    }, json);
    var slot = p.createUpload("Lecture 1");
    assertThat(slot.providerVideoId()).isEqualTo("vid-1");
    assertThat(slot.uploadUrl()).isEqualTo("https://video.bunnycdn.com/tusupload");
    long exp = Long.parseLong(slot.headers().get("AuthorizationExpire"));
    assertThat(slot.headers().get("AuthorizationSignature")).isEqualTo(com.shikkhasathi.common.Crypto.sha256("123APIKEY" + exp + "vid-1"));
    assertThat(calls.get(0).headerValue("AccessKey")).isEqualTo("APIKEY");
    String url = p.playbackUrl("vid-1", 3600);
    assertThat(url).matches("https://vz-123\\.b-cdn\\.net/bcdn_token=[A-Za-z0-9_-]+&expires=\\d+&token_path=%2Fvid-1%2F/vid-1/playlist\\.m3u8");
    assertThat(p.status("vid-1")).isEqualTo(new com.shikkhasathi.course.adapter.VideoProvider.VideoStatus("ready", 754));
    assertThat(new BunnyStreamProvider(new BunnyStreamProvider.Config("123", "k", null, "cdn.example.com"), r -> null, json).playbackUrl("v", 60)).isEqualTo("https://cdn.example.com/v/playlist.m3u8");
  }

  @Test
  void bunnyTokenMatchesThePreviousApi() throws Exception {
    var m = BunnyStreamProvider.class.getDeclaredMethod("token", String.class, String.class, long.class);
    m.setAccessible(true);
    // base64url(sha256("tokkey/vid-1/1800000000token_path=/vid-1/")) computed independently
    assertThat(m.invoke(null, "tokkey", "vid-1", 1800000000L)).isEqualTo("6IuKjuf5Md4DuYtzc-m1r4oOFs_MdEcVpQ5sNNw7UvU");
  }

  @Test
  void selectsBunnyWhenConfigured() {
    var admin = createUser("Admin", "admin");
    put("/admin/integrations/video", Map.of("values", Map.of("provider", "bunny", "library_id", "123", "api_key", "k")), admin).expect(200);
    assertThat(video.provider().name()).isEqualTo("bunny");
  }
}

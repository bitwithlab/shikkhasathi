package com.shikkhasathi.course.adapter;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** The active {@link VideoProvider} from the {@code video} integration (Bunny when configured, fake outside production). */
@Service
public class VideoService {
  private final IntegrationService integrations;
  private final HttpTransport http;
  private final JsonMapper json;
  private final AppProperties props;

  public VideoService(IntegrationService integrations, HttpTransport http, JsonMapper json, AppProperties props) {
    this.integrations = integrations;
    this.http = http;
    this.json = json;
    this.props = props;
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("video", (c, opts) -> {
      if (blank(c.get("library_id")) || blank(c.get("api_key"))) return new IntegrationService.TestResult(false, "Library id ও API key দিন");
      var res = http.send(Request.get("https://video.bunnycdn.com/library/" + c.get("library_id") + "/videos?page=1&itemsPerPage=1").header("AccessKey", c.get("api_key")).header("accept", "application/json"));
      return res.ok() ? new IntegrationService.TestResult(true, "Bunny Stream সংযোগ ঠিক আছে") : new IntegrationService.TestResult(false, "Bunny Stream: HTTP " + res.status());
    });
  }

  /** Bunny when configured; fake outside production; 503 INTEGRATION_DISABLED in production otherwise. */
  public VideoProvider provider() {
    var c = integrations.get("video");
    if ("bunny".equals(c.get("provider")) && !blank(c.get("library_id")) && !blank(c.get("api_key")))
      return new BunnyStreamProvider(new BunnyStreamProvider.Config(c.get("library_id"), c.get("api_key"), c.get("token_key"), c.get("cdn_host")), http, json);
    if (props.isProd()) throw new ApiException(ErrorCode.INTEGRATION_DISABLED, "Video provider not configured");
    return new FakeVideoProvider();
  }

  /** Webhook secret for video-processed callbacks (nullable). */
  public String webhookSecret() {
    return integrations.get("video").get("webhook_secret");
  }

  private static boolean blank(String s) {
    return s == null || s.isEmpty();
  }
}

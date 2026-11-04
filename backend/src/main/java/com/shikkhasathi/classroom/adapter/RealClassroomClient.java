package com.shikkhasathi.classroom.adapter;

import static com.shikkhasathi.classroom.adapter.ClassroomMapping.*;

import com.shikkhasathi.classroom.adapter.ClassroomModels.*;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.http.HttpTransport.Request;
import com.shikkhasathi.common.http.HttpTransport.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/** Google Classroom REST v1 + OAuth token endpoint, through {@link HttpTransport}. */
public class RealClassroomClient implements GoogleClassroomClient {
  private static final String API = "https://classroom.googleapis.com/v1";
  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final Pattern QUOTA = Pattern.compile("rateLimitExceeded|RESOURCE_EXHAUSTED|quota", Pattern.CASE_INSENSITIVE);
  private static final Pattern WORKSPACE = Pattern.compile("ClassroomApiDisabled|CannotAccess|admin|Workspace|disabled for your domain|PERMISSION_DENIED", Pattern.CASE_INSENSITIVE);

  public record Config(String clientId, String clientSecret, List<String> scopes) {}

  private final Config cfg;
  private final HttpTransport http;
  private final JsonMapper json;

  public RealClassroomClient(Config cfg, HttpTransport http, JsonMapper json) {
    this.cfg = cfg;
    this.http = http;
    this.json = json;
  }

  @Override
  public boolean fake() {
    return false;
  }

  @Override
  public String authUrl(String state, String codeChallenge, String redirectUri, String loginHint) {
    Map<String, String> q = new LinkedHashMap<>();
    q.put("client_id", cfg.clientId());
    q.put("redirect_uri", redirectUri);
    q.put("response_type", "code");
    q.put("scope", String.join(" ", cfg.scopes()));
    q.put("access_type", "offline");
    q.put("prompt", "consent");
    q.put("include_granted_scopes", "true");
    q.put("state", state);
    q.put("code_challenge", codeChallenge);
    q.put("code_challenge_method", "S256");
    if (loginHint != null && !loginHint.isEmpty()) q.put("login_hint", loginHint);
    return HttpTransport.withQuery("https://accounts.google.com/o/oauth2/v2/auth", q);
  }

  private JsonNode parse(Response res) {
    try {
      JsonNode n = json.readTree(res.text());
      return n == null || n.isMissingNode() ? JsonNodeFactory.instance.objectNode() : n;
    } catch (RuntimeException e) {
      return JsonNodeFactory.instance.objectNode();
    }
  }

  private JsonNode token(Map<String, String> body) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_id", cfg.clientId());
    form.put("client_secret", cfg.clientSecret());
    form.putAll(body);
    Response res = http.send(Request.post(TOKEN_URL, HttpTransport.form(form)).header("content-type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(15)));
    JsonNode data = parse(res);
    if (!res.ok()) {
      String err = str(data, "error");
      if ("invalid_grant".equals(err)) throw new ClassroomError(ClassroomError.Kind.REAUTH, "invalid_grant", res.status());
      throw new ClassroomError(ClassroomError.Kind.OTHER, "token " + res.status() + " " + (err == null ? "" : err), res.status());
    }
    return data;
  }

  private static Instant expiry(JsonNode data) {
    double sec = data.hasNonNull("expires_in") ? data.get("expires_in").asDouble(3600) : 3600;
    return Instant.now().plusMillis((long) ((sec - 60) * 1000));
  }

  @Override
  public TokenSet exchangeCode(String code, String redirectUri, String codeVerifier) {
    Map<String, String> b = new LinkedHashMap<>();
    b.put("grant_type", "authorization_code");
    b.put("code", code);
    b.put("redirect_uri", redirectUri != null && !redirectUri.isEmpty() ? redirectUri : "");
    if (codeVerifier != null && !codeVerifier.isEmpty()) b.put("code_verifier", codeVerifier);
    JsonNode data = token(b);
    JsonNode claims = JsonNodeFactory.instance.objectNode();
    String idToken = str(data, "id_token");
    if (idToken != null) {
      String[] parts = idToken.split("\\.");
      if (parts.length > 1) claims = json.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }
    String scope = strOr(data, "scope", "");
    return new TokenSet(str(data, "refresh_token"), str(data, "access_token"), expiry(data),
        Arrays.stream(scope.split(" ")).filter(s -> !s.isEmpty()).toList(), strOr(claims, "sub", ""), strOr(claims, "email", ""));
  }

  @Override
  public AccessToken refresh(String refreshToken) {
    Map<String, String> b = new LinkedHashMap<>();
    b.put("grant_type", "refresh_token");
    b.put("refresh_token", refreshToken);
    JsonNode data = token(b);
    return new AccessToken(str(data, "access_token"), expiry(data));
  }

  @Override
  public void revoke(String token) {
    try {
      http.send(Request.post(HttpTransport.withQuery("https://oauth2.googleapis.com/revoke", Map.of("token", token)), (byte[]) null).timeout(Duration.ofSeconds(10)));
    } catch (RuntimeException ignored) {
      // best effort
    }
  }

  private List<JsonNode> get(String at, String path, Map<String, String> params) {
    List<JsonNode> out = new ArrayList<>();
    String pageToken = null;
    for (int i = 0; i < 20; i++) {
      Map<String, String> q = new LinkedHashMap<>(params);
      q.put("pageSize", "100");
      if (pageToken != null) q.put("pageToken", pageToken);
      Response res = http.send(Request.get(HttpTransport.withQuery(API + path, q)).header("authorization", "Bearer " + at).timeout(Duration.ofSeconds(20)));
      if (!res.ok()) throw toError(res);
      JsonNode data = parse(res);
      for (var e : data.properties()) {
        if (e.getValue().isArray()) {
          e.getValue().forEach(out::add);
          break;
        }
      }
      pageToken = str(data, "nextPageToken");
      if (pageToken == null || pageToken.isEmpty()) break;
    }
    return out;
  }

  private ClassroomError toError(Response res) {
    JsonNode body = parse(res);
    JsonNode error = body.path("error");
    String msg = error.hasNonNull("message") ? error.get("message").asString() : "HTTP " + res.status();
    JsonNode detail = error.hasNonNull("details") ? error.get("details") : error.hasNonNull("errors") ? error.get("errors") : null;
    String reason = detail == null ? "\"\"" : json.writeValueAsString(detail);
    String hay = reason + msg;
    if (res.status() == 401) return new ClassroomError(ClassroomError.Kind.REAUTH, msg, 401);
    if (res.status() == 429 || QUOTA.matcher(hay).find()) return new ClassroomError(ClassroomError.Kind.QUOTA, msg, res.status());
    if (res.status() == 403 && WORKSPACE.matcher(hay).find()) return new ClassroomError(ClassroomError.Kind.WORKSPACE, msg, 403);
    if (res.status() == 404) return new ClassroomError(ClassroomError.Kind.NOT_FOUND, msg, 404);
    return new ClassroomError(ClassroomError.Kind.OTHER, msg, res.status());
  }

  private static Map<String, String> params(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
    return m;
  }

  @Override
  public List<GcCourse> listCourses(String at) {
    // Teacher names need classroom.rosters.readonly, which we deliberately don't request (ADR-014).
    return get(at, "/courses", params("studentId", "me", "courseStates", "ACTIVE")).stream()
        .map(c -> new GcCourse(str(c, "id"), str(c, "name"), str(c, "section"), str(c, "room"), str(c, "descriptionHeading"), str(c, "alternateLink"),
            List.of(), strOr(c, "courseState", "ACTIVE"), date(c, "updateTime")))
        .toList();
  }

  @Override
  public List<GcCourseWork> listCourseWork(String at, String courseId) {
    return get(at, "/courses/" + courseId + "/courseWork", params("courseWorkStates", "PUBLISHED", "orderBy", "updateTime desc")).stream()
        .map(w -> new GcCourseWork(str(w, "id"), courseId, strOr(w, "title", ""), str(w, "description"), strOr(w, "workType", "ASSIGNMENT"),
            mapDue(w.get("dueDate"), w.get("dueTime")), number(w, "maxPoints"), mapMaterials(w.get("materials")), str(w, "alternateLink"),
            strOr(w, "state", "PUBLISHED"), date(w, "creationTime"), date(w, "updateTime")))
        .toList();
  }

  @Override
  public List<GcSubmission> listSubmissions(String at, String courseId) {
    return get(at, "/courses/" + courseId + "/courseWork/-/studentSubmissions", params("userId", "me")).stream()
        .map(s -> new GcSubmission(str(s, "id"), courseId, str(s, "courseWorkId"), strOr(s, "state", "NEW"), s.path("late").asBoolean(false),
            number(s, "assignedGrade"), str(s, "alternateLink"), date(s, "updateTime")))
        .toList();
  }

  @Override
  public List<GcMaterialPost> listMaterials(String at, String courseId) {
    return get(at, "/courses/" + courseId + "/courseWorkMaterials", params("courseWorkMaterialStates", "PUBLISHED")).stream()
        .map(m -> new GcMaterialPost(str(m, "id"), courseId, strOr(m, "title", ""), str(m, "description"), mapMaterials(m.get("materials")),
            str(m, "alternateLink"), date(m, "creationTime"), date(m, "updateTime")))
        .toList();
  }

  @Override
  public List<GcAnnouncement> listAnnouncements(String at, String courseId) {
    return get(at, "/courses/" + courseId + "/announcements", params("announcementStates", "PUBLISHED", "orderBy", "updateTime desc")).stream()
        .map(a -> new GcAnnouncement(str(a, "id"), courseId, strOr(a, "text", ""), null, mapMaterials(a.get("materials")), str(a, "alternateLink"),
            date(a, "creationTime"), date(a, "updateTime")))
        .toList();
  }

  @Override
  public Registration registerCourseWorkPush(String at, String courseId, String topicName) {
    var body = Map.of("feed", Map.of("feedType", "COURSE_WORK_CHANGES", "courseWorkChangesInfo", Map.of("courseId", courseId)), "cloudPubsubTopic", Map.of("topicName", topicName));
    Response res = http.send(Request.post(API + "/registrations", json.writeValueAsString(body)).header("authorization", "Bearer " + at)
        .header("content-type", "application/json").timeout(Duration.ofSeconds(15)));
    if (!res.ok()) throw toError(res);
    JsonNode data = parse(res);
    return new Registration(str(data, "registrationId"), Instant.parse(str(data, "expiryTime")));
  }

  @Override
  public void deleteRegistration(String at, String registrationId) {
    try {
      http.send(Request.of("DELETE", API + "/registrations/" + registrationId, null).header("authorization", "Bearer " + at).timeout(Duration.ofSeconds(10)));
    } catch (RuntimeException ignored) {
      // best effort
    }
  }
}

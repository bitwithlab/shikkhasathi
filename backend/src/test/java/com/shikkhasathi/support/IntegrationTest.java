package com.shikkhasathi.support;

import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.Ids;
import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.config.seed.DevSeeder;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.security.AuthUser;
import com.shikkhasathi.security.JwtService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for integration tests: real Postgres (two test DBs, Flyway-migrated) + Redis db 15, a clean slate before
 * every test, request helpers, users with tokens and the contract oracle ({@link Contracts}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
  static {
    TestDatabases.ensure();
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JsonMapper json;
  @Autowired @Qualifier("coreJdbc") protected NamedParameterJdbcTemplate coreJdbc;
  @Autowired @Qualifier("contentJdbc") protected NamedParameterJdbcTemplate contentJdbc;
  @Autowired protected JwtService jwt;
  @Autowired protected Crypto crypto;
  @Autowired protected DevSeeder seeder;
  @Autowired protected IntegrationService integrations;
  @Autowired protected RedisConnectionFactory redisFactory;
  @Autowired(required = false) protected List<ResettableFake> fakes = List.of();

  @BeforeEach
  void cleanSlate() {
    truncate(coreJdbc);
    truncate(contentJdbc);
    try (var c = redisFactory.getConnection()) {
      c.serverCommands().flushDb();
    }
    integrations.invalidate(null);
    fakes.forEach(ResettableFake::reset);
  }

  private static void truncate(NamedParameterJdbcTemplate jdbc) {
    var tables = jdbc.queryForList("select tablename from pg_tables where schemaname = 'public' and tablename <> 'flyway_schema_history'", Map.of(), String.class);
    if (!tables.isEmpty()) jdbc.getJdbcTemplate().execute("TRUNCATE " + String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList()) + " RESTART IDENTITY CASCADE");
  }

  /** Load the demo dataset (same data as local development). */
  protected void seed() {
    seeder.seed();
  }

  // ---------------------------------------------------------------- users

  protected TestUser createUser(String name, List<String> roles, String email, String phone) {
    UUID id = Ids.uuidv7();
    coreJdbc.update("insert into users (id, name, roles, email, phone) values (:id, :name, cast(:roles as text[]), :email, :phone)",
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("roles", "{" + String.join(",", roles) + "}").addValue("email", email).addValue("phone", phone));
    return login(id, name, email, phone, roles);
  }

  protected TestUser createUser(String name) {
    return createUser(name, List.of("student"), null, null);
  }

  protected TestUser createUser(String name, String... roles) {
    return createUser(name, List.of(roles), null, null);
  }

  /** Issue a session + access token for an existing user (e.g. a seeded one). */
  protected TestUser login(UUID id, String name, String email, String phone, List<String> roles) {
    UUID sid = Ids.uuidv7();
    coreJdbc.update("insert into sessions (id, user_id, family_id, refresh_token_hash, platform, expires_at) values (:id, :u, :f, :h, 'android', :exp)",
        Map.of("id", sid, "u", id, "f", Ids.uuidv7(), "h", crypto.hmac(Crypto.randomToken(48)), "exp", Timestamp.from(Instant.now().plusSeconds(JwtService.REFRESH_TTL_SEC))));
    String token = jwt.signAccess(new AuthUser(id, roles, sid)).token();
    return new TestUser(id, name, email, phone, roles, sid, token);
  }

  /** Log in a seeded user by email. */
  protected TestUser loginAs(String email) {
    var row = coreJdbc.queryForMap("select id, name, email, phone, array_to_string(roles, ',') as roles from users where email = :e", Map.of("e", email));
    return login((UUID) row.get("id"), (String) row.get("name"), (String) row.get("email"), (String) row.get("phone"), List.of(((String) row.get("roles")).split(",")));
  }

  // ---------------------------------------------------------------- requests

  protected Resp get(String path, TestUser u) { return call(HttpMethod.GET, path, null, u, Map.of()); }
  protected Resp get(String path) { return call(HttpMethod.GET, path, null, null, Map.of()); }
  protected Resp post(String path, Object body, TestUser u) { return call(HttpMethod.POST, path, body, u, Map.of()); }
  protected Resp post(String path, Object body) { return call(HttpMethod.POST, path, body, null, Map.of()); }
  protected Resp put(String path, Object body, TestUser u) { return call(HttpMethod.PUT, path, body, u, Map.of()); }
  protected Resp patch(String path, Object body, TestUser u) { return call(HttpMethod.PATCH, path, body, u, Map.of()); }
  protected Resp delete(String path, TestUser u) { return call(HttpMethod.DELETE, path, null, u, Map.of()); }

  /** Generic call; {@code path} is relative to /api/v1 unless it starts with "/api" or "http". */
  protected Resp call(HttpMethod method, String path, Object body, TestUser u, Map<String, String> headers) {
    String url = path.startsWith("/api") ? path : "/api/v1" + path;
    MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(method, url);
    if (u != null) b.header("Authorization", u.bearer());
    headers.forEach(b::header);
    if (body instanceof String s) b.contentType(headers.getOrDefault("Content-Type", MediaType.APPLICATION_JSON_VALUE)).content(s);
    else if (body instanceof byte[] bytes) b.contentType(headers.getOrDefault("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE)).content(bytes);
    else if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    try {
      var res = mvc.perform(b).andReturn().getResponse();
      Map<String, String> h = new HashMap<>();
      for (String n : res.getHeaderNames()) h.put(n.toLowerCase(), res.getHeader(n));
      res.setCharacterEncoding("UTF-8");
      return new Resp(res.getStatus(), res.getContentAsString(), h);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Call + validate against the contract (status and response schema). */
  protected Resp contract(String endpoint, Resp r) {
    return Contracts.check(endpoint, r);
  }
}

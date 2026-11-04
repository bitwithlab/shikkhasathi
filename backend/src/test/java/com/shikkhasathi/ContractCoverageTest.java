package com.shikkhasathi;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.support.Contracts;
import com.shikkhasathi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Definition of done for the migration: every endpoint of the API contract (179, exported from the website's
 * contracts) exists in this backend and enforces its auth level before validation.
 */
class ContractCoverageTest extends IntegrationTest {
  private static final String UUID = "0192f0c0-0000-7000-8000-000000000000";

  private static String url(String path) {
    return path.replaceAll(":(\\w*(slug|code|provider|gateway)\\w*)", "x").replaceAll(":\\w+", UUID);
  }

  private static boolean hasBody(String m) {
    return m.equals("POST") || m.equals("PUT") || m.equals("PATCH");
  }

  @Test
  void authRequiredEndpointsRejectAnonymousCallers() {
    List<String> bad = new ArrayList<>();
    for (var e : Contracts.endpoints().entrySet()) {
      String auth = (String) e.getValue().get("auth");
      if (auth.equals("public") || auth.equals("optional")) continue;
      String m = (String) e.getValue().get("method");
      var r = call(HttpMethod.valueOf(m), url((String) e.getValue().get("path")), hasBody(m) ? "{}" : null, null, Map.of());
      if (r.status() != 401) bad.add(e.getKey() + " " + m + " " + e.getValue().get("path") + " → " + r.status());
    }
    assertThat(bad).as("endpoints that don't return 401 to anonymous callers (404 = not ported)").isEmpty();
  }

  @Test
  void adminAndSellerEndpointsRejectStudents() {
    var student = createUser("Plain Student");
    List<String> bad = new ArrayList<>();
    for (var e : Contracts.endpoints().entrySet()) {
      String auth = (String) e.getValue().get("auth");
      if (!auth.equals("admin") && !auth.equals("seller") && !auth.equals("verified_seller")) continue;
      String m = (String) e.getValue().get("method");
      var r = call(HttpMethod.valueOf(m), url((String) e.getValue().get("path")), hasBody(m) ? "{}" : null, student, Map.of());
      if (r.status() != 403) bad.add(e.getKey() + " " + m + " " + e.getValue().get("path") + " → " + r.status());
    }
    assertThat(bad).as("admin/seller endpoints must return 403 to students before validation").isEmpty();
  }

  @Test
  void publicEndpointsExist() {
    List<String> missing = new ArrayList<>();
    for (var e : Contracts.endpoints().entrySet()) {
      String auth = (String) e.getValue().get("auth");
      if (!auth.equals("public") && !auth.equals("optional")) continue;
      String m = (String) e.getValue().get("method");
      var r = call(HttpMethod.valueOf(m), url((String) e.getValue().get("path")), hasBody(m) ? "{}" : null, null, Map.of());
      // 404 with our envelope is fine for unknown ids; Spring's "no handler" is NOT_FOUND too, so also check the path is mapped
      if (r.status() == 404 && r.body().contains("\"NOT_FOUND\"") && !mapped(m, (String) e.getValue().get("path"))) missing.add(e.getKey() + " " + m + " " + e.getValue().get("path"));
    }
    assertThat(missing).as("public endpoints not implemented").isEmpty();
  }

  @org.springframework.beans.factory.annotation.Autowired
  @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

  private boolean mapped(String method, String path) {
    String spring = "/api/v1" + path.replaceAll(":(\\w+)", "{$1}");
    return mappings.getHandlerMethods().keySet().stream().anyMatch(info ->
        info.getMethodsCondition().getMethods().stream().anyMatch(rm -> rm.name().equals(method))
            && info.getPathPatternsCondition() != null
            && info.getPathPatternsCondition().getPatternValues().stream().anyMatch(p -> p.replaceAll("\\{[^}]+}", "{}").equals(spring.replaceAll("\\{[^}]+}", "{}"))));
  }
}

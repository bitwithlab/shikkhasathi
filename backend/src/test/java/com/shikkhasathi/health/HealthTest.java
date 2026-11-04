package com.shikkhasathi.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class HealthTest extends IntegrationTest {
  @Test
  void liveness() {
    var r = contract("health.get", get("/health"));
    assertThat(r.at("/status").asString()).isEqualTo("ok");
    assertThat(r.at("/time").asString()).endsWith("Z");
  }

  @Test
  void systemHealthShowsBothDatabasesRedisAndIntegrations() {
    get("/admin/system-health", createUser("S")).expect(403);
    var r = contract("health.system", get("/admin/system-health", createUser("Admin", "admin")));
    for (int i = 0; i < 2; i++) {
      assertThat(r.at("/databases/" + i + "/connected").asBoolean()).isTrue();
      assertThat(r.at("/databases/" + i + "/rlsOk").asBoolean()).as("RLS on every table").isTrue();
      assertThat(r.at("/databases/" + i + "/migration").asString()).startsWith("V1");
    }
    assertThat(r.at("/databases/0/name").asString()).isEqualTo("core");
    assertThat(r.at("/redis/connected").asBoolean()).isTrue();
    assertThat(r.at("/integrations").size()).isEqualTo(8);
    assertThat(r.at("/integrations/0/mode").asString()).isEqualTo("fake");
  }
}

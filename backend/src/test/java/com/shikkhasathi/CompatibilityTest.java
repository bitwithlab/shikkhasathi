package com.shikkhasathi;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Proves the new backend reads data written by the previous (Node) API: values below were produced by the
 * previous implementation with the dev secrets.
 */
class CompatibilityTest extends IntegrationTest {
  @Autowired PasswordEncoder passwords;

  @Test
  void decryptsSecretsEncryptedByThePreviousApi() {
    assertThat(crypto.decrypt("v1:PTE3VSn53TIQdqTB:r/GbU0z5pwQawzhoZ7MHig==:lq6D1LEIrTMI/xqSIaSzVuYA1g==")).isEqualTo("xkeysib-secret-1234");
    assertThat(crypto.decrypt(crypto.encrypt("round trip ✓ বাংলা"))).isEqualTo("round trip ✓ বাংলা");
    assertThat(Crypto.mask("xkeysib-secret-1234")).isEqualTo("••••1234");
  }

  @Test
  void hmacMatchesRefreshTokenHashes() {
    assertThat(crypto.hmac("refresh-token-abc")).isEqualTo("1e9abc5f398e38f1a2bdccdf320e31a22da8f35bc607e951766b2f2a12de11ba");
  }

  @Test
  void verifiesAccessTokensSignedByThePreviousApi() throws Exception {
    var jwtObj = SignedJWT.parse("eyJhbGciOiJIUzI1NiJ9.eyJyb2xlcyI6WyJzdHVkZW50Iiwic2VsbGVyIl0sInNpZCI6IjAxOTJmMGMwLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDBhYSIsInN1YiI6IjAxOTJmMGMwLTAwMDAtNzAwMC04MDAwLTAwMDAwMDAwMDAwMSIsImlzcyI6InNoaWtraGFzYXRoaSIsImF1ZCI6InNoaWtraGFzYXRoaS1hcGkiLCJpYXQiOjE3OTM3NzIyNjIsImV4cCI6MTc5Mzc3MzE2Mn0.CRBFxhnrkhVmduU9cPk1irPORZMhttBMws4mXh8Nan4");
    byte[] k = "dev-access-secret-change-me-please".getBytes(StandardCharsets.UTF_8);
    assertThat(jwtObj.verify(new MACVerifier(k.length >= 32 ? k : Arrays.copyOf(k, 32)))).isTrue();
    var c = jwtObj.getJWTClaimsSet();
    assertThat(c.getSubject()).isEqualTo("0192f0c0-0000-7000-8000-000000000001");
    assertThat(c.getStringClaim("sid")).isEqualTo("0192f0c0-0000-7000-8000-0000000000aa");
    assertThat(c.getStringListClaim("roles")).containsExactly("student", "seller");
    assertThat(c.getIssuer()).isEqualTo("shikkhasathi");
    assertThat(c.getAudience()).containsExactly("shikkhasathi-api");
  }

  @Test
  void cursorsAreEncodedTheSameWay() {
    assertThat(Cursor.encode("2026-11-03T19:16:07.010Z", "0192f0c0-0000-7000-8000-000000000001")).isEqualTo("WyIyMDI2LTExLTAzVDE5OjE2OjA3LjAxMFoiLCIwMTkyZjBjMC0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDEiXQ");
    var v = Cursor.decode("WyIyMDI2LTExLTAzVDE5OjE2OjA3LjAxMFoiLCIwMTkyZjBjMC0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDEiXQ");
    assertThat(v.sort()).isEqualTo("2026-11-03T19:16:07.010Z");
    assertThat(v.id()).isEqualTo("0192f0c0-0000-7000-8000-000000000001");
  }

  @Test
  void verifiesArgon2idPasswordHashesFromThePreviousApi() {
    assertThat(passwords.matches("shikkhasathi123", "$argon2id$v=19$m=65536,t=3,p=4$YI3HNHtBodusXCwEf2c9hQ$DQ0qoP+WMcG+3IC/XAvfxMd05NWsKvg41MxlqvfFx8w")).isTrue();
    assertThat(passwords.matches("wrong", "$argon2id$v=19$m=65536,t=3,p=4$YI3HNHtBodusXCwEf2c9hQ$DQ0qoP+WMcG+3IC/XAvfxMd05NWsKvg41MxlqvfFx8w")).isFalse();
  }

  @Test
  void demoSeedLoadsAndSeededPasswordsVerify() {
    seed();
    Integer users = coreJdbc.queryForObject("select count(*) from users", Map.of(), Integer.class);
    Integer courses = contentJdbc.queryForObject("select count(*) from courses", Map.of(), Integer.class);
    assertThat(users).isGreaterThanOrEqualTo(10);
    assertThat(courses).isEqualTo(13);
    String hash = coreJdbc.queryForObject("select password_hash from users where email = 'sadman@demo.shikkhasathi.com'", Map.of(), String.class);
    assertThat(passwords.matches("shikkhasathi123", hash)).isTrue();
    // timestamps were shifted to "now": the demo exam plan is still in the future
    Boolean future = coreJdbc.queryForObject("select bool_and(exam_date > now()) from exam_plans", Map.of(), Boolean.class);
    assertThat(future).isTrue();
  }

  @Test
  void bothDatabasesHaveRlsOnEveryTable() {
    for (var jdbc : List.of(coreJdbc, contentJdbc)) {
      var missing = jdbc.queryForList("select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace where n.nspname = 'public' and c.relkind in ('r','p') and not c.relrowsecurity and c.relname <> 'flyway_schema_history'", Map.of(), String.class);
      assertThat(missing).as("tables without RLS").isEmpty();
      assertThat(jdbc.queryForList("select policyname from pg_policies where schemaname = 'public'", Map.of(), String.class)).isEmpty();
    }
  }
}

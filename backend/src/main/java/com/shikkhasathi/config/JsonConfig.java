package com.shikkhasathi.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * JSON exactly like the previous API: nulls are written (contracts use {@code .nullable()}, keys must exist) and
 * instants are ISO-8601 UTC with millisecond precision ({@code 2026-11-03T19:16:07.010Z}, JS toISOString()).
 */
@Configuration
public class JsonConfig {
  public static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  public static String iso(Instant i) {
    return i == null ? null : ISO_MILLIS.format(i);
  }

  @Bean
  public JsonMapperBuilderCustomizer ssJson() {
    SimpleModule m = new SimpleModule("ss-instant");
    m.addSerializer(Instant.class, new ValueSerializer<Instant>() {
      @Override
      public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(ISO_MILLIS.format(value));
      }
    });
    return b -> b.addModule(m);
  }
}

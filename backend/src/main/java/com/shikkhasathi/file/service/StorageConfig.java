package com.shikkhasathi.file.service;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.security.JwtService;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Picks the storage backend: Supabase when all four SUPABASE_* env vars are set, else local disk (not allowed in production). */
@Configuration
public class StorageConfig {
  @Bean
  public StorageService storageService(AppProperties props, JwtService jwt, HttpTransport http, JsonMapper json) {
    if (!blank(props.supabaseCoreUrl()) && !blank(props.supabaseCoreServiceRoleKey()) && !blank(props.supabaseContentUrl()) && !blank(props.supabaseContentServiceRoleKey())) {
      return new SupabaseStorage(
          new SupabaseStorage.Project(props.supabaseCoreUrl(), props.supabaseCoreServiceRoleKey()),
          new SupabaseStorage.Project(props.supabaseContentUrl(), props.supabaseContentServiceRoleKey()),
          http, json);
    }
    if (props.isProd()) throw new IllegalStateException("Supabase Storage env (SUPABASE_*_URL / SERVICE_ROLE_KEY) is required in production");
    return new LocalStorage(Path.of(blank(props.localStorageDir()) ? "./storage-local" : props.localStorageDir()), props.appUrl(), jwt);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}

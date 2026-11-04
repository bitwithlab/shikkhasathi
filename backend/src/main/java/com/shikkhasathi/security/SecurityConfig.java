package com.shikkhasathi.security;

import com.shikkhasathi.common.ApiExceptionHandler;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless API security. Authentication = bearer access token (JwtService); authorization per route = {@link Auth}
 * levels enforced in {@link AuthInterceptor} before validation. Refresh tokens live in an httpOnly cookie on web
 * with a CSRF double-submit check in the auth module (same as before).
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {
  private final AuthInterceptor interceptor;
  private final AppProperties props;

  public SecurityConfig(AuthInterceptor interceptor, AppProperties props) {
    this.interceptor = interceptor;
    this.props = props;
  }

  @Bean
  public SecurityFilterChain api(HttpSecurity http, JsonMapper json) throws Exception {
    http.csrf(c -> c.disable())
        .cors(c -> c.configurationSource(cors()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(b -> b.disable())
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        .headers(h -> h
            .contentTypeOptions(c -> {})
            .frameOptions(f -> f.deny())
            .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=(), payment=(self)")))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
          var body = ApiExceptionHandler.envelope(ErrorCode.UNAUTHORIZED, null, null);
          res.setStatus(401);
          res.setContentType(MediaType.APPLICATION_JSON_VALUE);
          res.setCharacterEncoding("UTF-8");
          json.writeValue(res.getOutputStream(), body.getBody());
        }));
    return http.build();
  }

  /** Argon2id — verifies the PHC strings written by the previous API (node argon2) and writes new ones. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  CorsConfigurationSource cors() {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(props.isProd() ? List.of(props.appUrl()) : List.of(props.appUrl(), "http://localhost:5173", "http://localhost:4173"));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-Token", "Accept-Language"));
    c.setAllowCredentials(true);
    var src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/api/**", c);
    return src;
  }

  @Override
  public void addInterceptors(InterceptorRegistry r) {
    r.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new AuthUserArgumentResolver());
  }
}

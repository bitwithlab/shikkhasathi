package com.shikkhasathi.config;

import com.shikkhasathi.common.Crypto;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CoreBeans {
  private final AppProperties props;

  public CoreBeans(AppProperties props) {
    this.props = props;
  }

  /** Refuse to start in production with the dev defaults (same guard as before). */
  @PostConstruct
  void guardSecrets() {
    if (!props.isProd()) return;
    StringBuilder bad = new StringBuilder();
    if (props.encryptionKey().startsWith("dev-")) bad.append("SS_ENCRYPTION_KEY ");
    if (props.jwtAccessSecret().startsWith("dev-")) bad.append("SS_JWT_ACCESS_SECRET ");
    if (props.jwtRefreshSecret().startsWith("dev-")) bad.append("SS_JWT_REFRESH_SECRET ");
    if (!bad.isEmpty()) throw new IllegalStateException("Refusing to start in production with default secrets: " + bad);
  }

  @Bean
  public Crypto crypto() {
    return new Crypto(props.encryptionKey(), props.jwtRefreshSecret());
  }

  /** Injectable clock (tests can fix time). */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}

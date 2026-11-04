package com.shikkhasathi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata (served at /api/v1/openapi.json, committed to docs/api/openapi.json). */
@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI shikkhaSathiOpenApi() {
    return new OpenAPI()
        .info(new Info().title("ShikkhaSathi API").version("v1").description("শিক্ষাসাথী — REST API for the website and the mobile app. Errors: {\"error\":{code,message,details}}; lists use cursor pagination."))
        .servers(List.of(new Server().url("https://shikkhasathi.com").description("Production")))
        .components(new Components().addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
  }
}

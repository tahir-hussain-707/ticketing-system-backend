package com.frequency.ticketing.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows a browser-hosted frontend on a different origin (e.g. a local Vite dev server) to call
 * the {@code /api/v1/**} endpoints. Spring Boot has no CORS policy by default — without this,
 * every cross-origin browser request is blocked before it reaches a controller. Allowed origins
 * come from config (`app.cors.allowed-origins`), not a wildcard, so production deployments set
 * their real frontend origin(s) via the {@code CORS_ALLOWED_ORIGINS} environment variable.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  private final List<String> allowedOrigins;

  public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
    this.allowedOrigins = List.of(allowedOrigins.split("\\s*,\\s*"));
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/v1/**")
        .allowedOrigins(allowedOrigins.toArray(new String[0]))
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // 005-auth-rag-chatbot: session-cookie auth requires credentialed CORS (research.md
        // "CORS and CSRF"); allowedOrigins stays a real allow-list, never a wildcard.
        .allowCredentials(true)
        .maxAge(3600);
  }
}

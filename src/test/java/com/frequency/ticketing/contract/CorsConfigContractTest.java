package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

/**
 * A browser-hosted frontend on an allowed origin (app.cors.allowed-origins, default includes
 * http://localhost:5173) must be able to call /api/v1/** cross-origin; any other origin must
 * not receive an Access-Control-Allow-Origin header.
 */
class CorsConfigContractTest extends AbstractIntegrationTest {

  @Test
  void preflightFromAllowedOriginIsGranted() {
    RequestEntity<Void> preflight =
        RequestEntity.options(baseUrl("/tickets"))
            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .build();

    ResponseEntity<Void> response = restTemplate.exchange(preflight, Void.class);

    assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .isEqualTo("http://localhost:5173");
  }

  @Test
  void preflightFromDisallowedOriginGetsNoAllowOriginHeader() {
    RequestEntity<Void> preflight =
        RequestEntity.options(baseUrl("/tickets"))
            .header(HttpHeaders.ORIGIN, "http://evil.example.com")
            .header("Access-Control-Request-Method", "POST")
            .build();

    ResponseEntity<Void> response = restTemplate.exchange(preflight, Void.class);

    assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
  }
}

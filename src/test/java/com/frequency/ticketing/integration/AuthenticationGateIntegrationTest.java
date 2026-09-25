package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-006 (amended): every API this feature exposes rejects an unauthenticated caller, with the
 * single exception of login itself (spec.md User Story 1, Scenario 8).
 */
class AuthenticationGateIntegrationTest extends AbstractIntegrationTest {

  private void assertUnauthenticated(HttpMethod method, String path) {
    ResponseEntity<ApiError> response =
        restTemplate.exchange(baseUrl(path), method, null, ApiError.class);
    assertThat(response.getStatusCode())
        .as("%s %s should be 401 without authentication", method, path)
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void everyGatedEndpointRejectsAnUnauthenticatedCaller() {
    UUID randomId = UUID.randomUUID();
    assertUnauthenticated(HttpMethod.POST, "/tickets");
    assertUnauthenticated(HttpMethod.GET, "/tickets");
    assertUnauthenticated(HttpMethod.GET, "/tickets/" + randomId);
    assertUnauthenticated(HttpMethod.PATCH, "/tickets/" + randomId);
    assertUnauthenticated(HttpMethod.POST, "/tickets/" + randomId + "/transitions");
    assertUnauthenticated(HttpMethod.PATCH, "/tickets/" + randomId + "/assignee");
    assertUnauthenticated(HttpMethod.POST, "/tickets/" + randomId + "/comments");
    assertUnauthenticated(HttpMethod.GET, "/tickets/" + randomId + "/comments");
    assertUnauthenticated(HttpMethod.POST, "/chatbot/messages");
    assertUnauthenticated(HttpMethod.POST, "/chatbot/conversations/" + randomId + "/end");
  }

  @Test
  void loginItselfDoesNotRequireAuthentication() {
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(
            baseUrl("/auth/login"),
            new com.frequency.ticketing.web.dto.LoginRequest("nobody@example.test", "whatever"),
            ApiError.class);

    // Reaches the login handler (401 invalid credentials), never a 401 "not authenticated" from
    // the security filter chain itself — proving login is genuinely permitAll.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.INVALID_CREDENTIALS.name());
  }
}

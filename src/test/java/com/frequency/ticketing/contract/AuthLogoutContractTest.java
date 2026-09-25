package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Matches contracts/auth-api.yaml exactly (spec 005 FR-008). */
class AuthLogoutContractTest extends AbstractIntegrationTest {

  @Test
  void logoutWhenAuthenticatedReturns204() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<Void> response =
        restTemplate.exchange(baseUrl("/auth/logout"), HttpMethod.POST, authEntity(auth), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void logoutWithNoSessionReturns401() {
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(baseUrl("/auth/logout"), null, ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}

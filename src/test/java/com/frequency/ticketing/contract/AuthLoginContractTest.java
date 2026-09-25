package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.LoginRequest;
import com.frequency.ticketing.web.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Matches contracts/auth-api.yaml exactly (spec 005 FR-001, FR-003, FR-004). */
class AuthLoginContractTest extends AbstractIntegrationTest {

  @Test
  void validCredentialsReturn200WithUserSummary() {
    seed(TestUser.GENERAL_1);

    ResponseEntity<LoginResponse> response =
        restTemplate.postForEntity(
            baseUrl("/auth/login"), new LoginRequest(TestUser.GENERAL_1.email, "test-password"), LoginResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().email()).isEqualTo(TestUser.GENERAL_1.email);
    assertThat(response.getBody().role()).isEqualTo(TestUser.GENERAL_1.role);
    assertThat(response.getHeaders().get(org.springframework.http.HttpHeaders.SET_COOKIE)).isNotEmpty();
  }

  @Test
  void wrongPasswordReturns401GenericError() {
    seed(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(
            baseUrl("/auth/login"), new LoginRequest(TestUser.GENERAL_1.email, "wrong-password"), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.INVALID_CREDENTIALS.name());
  }

  @Test
  void unregisteredEmailReturnsSameGenericError() {
    ResponseEntity<ApiError> wrongPassword =
        restTemplate.postForEntity(
            baseUrl("/auth/login"),
            new LoginRequest("nobody-" + java.util.UUID.randomUUID() + "@example.test", "anything"),
            ApiError.class);

    assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(wrongPassword.getBody().code()).isEqualTo(ApiError.Code.INVALID_CREDENTIALS.name());
  }

  @Test
  void missingPasswordReturns400ValidationFailed() {
    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(
            baseUrl("/auth/login"), new LoginRequest("someone@example.test", ""), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }
}

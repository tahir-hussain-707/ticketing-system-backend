package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Full login → act → logout → act-rejected flow (spec 005 User Story 1, Scenarios 1, 4, 6, 7). */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

  @Test
  void loginEnablesActionsAndLogoutRevokesThem() {
    TicketCreateRequest createRequest = new TicketCreateRequest("Auth flow", "desc", TicketPriority.LOW);

    // Unauthenticated: rejected, nothing created.
    ResponseEntity<ApiError> beforeLogin =
        restTemplate.postForEntity(baseUrl("/tickets"), createRequest, ApiError.class);
    assertThat(beforeLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // After login: succeeds.
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    ResponseEntity<TicketResponse> afterLogin =
        restTemplate.exchange(
            baseUrl("/tickets"), HttpMethod.POST, authEntity(createRequest, auth), TicketResponse.class);
    assertThat(afterLogin.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // After logout: rejected again, using the now-invalidated cookie.
    restTemplate.exchange(baseUrl("/auth/logout"), HttpMethod.POST, authEntity(auth), Void.class);
    ResponseEntity<ApiError> afterLogout =
        restTemplate.exchange(
            baseUrl("/tickets"), HttpMethod.POST, authEntity(createRequest, auth), ApiError.class);
    assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}

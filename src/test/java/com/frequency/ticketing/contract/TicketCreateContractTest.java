package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketCreateContractTest extends AbstractIntegrationTest {

  @Test
  void createTicketReturns201WithOpenStatus() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    var request = new TicketCreateRequest("Printer offline", "3rd floor printer unreachable", TicketPriority.HIGH);

    ResponseEntity<TicketResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets"), HttpMethod.POST, authEntity(request, auth), TicketResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    TicketResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.id()).isNotNull();
    assertThat(body.title()).isEqualTo("Printer offline");
    assertThat(body.priority()).isEqualTo(TicketPriority.HIGH);
    assertThat(body.status()).isEqualTo(TicketStatus.OPEN);
  }

  @Test
  void createTicketWithBlankTitleReturns400ValidationFailed() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    var request = new TicketCreateRequest("", "description", TicketPriority.LOW);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets"), HttpMethod.POST, authEntity(request, auth), ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }

  @Test
  void createTicketWithoutAuthenticationReturns401() {
    var request = new TicketCreateRequest("Printer offline", "desc", TicketPriority.LOW);

    ResponseEntity<ApiError> response =
        restTemplate.postForEntity(baseUrl("/tickets"), request, ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}

package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketTransitionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TicketTransitionContractTest extends AbstractIntegrationTest {

  @Test
  void legalTransitionReturns200() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Transition me", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    ResponseEntity<TicketResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/transitions"),
            HttpMethod.POST,
            authEntity(new TicketTransitionRequest(TicketStatus.IN_PROGRESS), auth),
            TicketResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(TicketStatus.IN_PROGRESS);
  }

  @Test
  void illegalTransitionReturns409InvalidTransition() {
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Illegal move", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody();

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/transitions"),
            HttpMethod.POST,
            authEntity(new TicketTransitionRequest(TicketStatus.CLOSED), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.INVALID_TRANSITION.name());
  }
}

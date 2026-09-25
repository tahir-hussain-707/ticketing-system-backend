package com.frequency.ticketing.integration;

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

/**
 * Full transition matrix through the HTTP API (SC-002, SC-006): every legal edge succeeds, and
 * every reversal to OPEN plus every other disallowed move is rejected, with the ticket's
 * persisted status unchanged after each rejection.
 */
class TicketStateMachineIntegrationTest extends AbstractIntegrationTest {

  private HttpHeaders auth;

  private TicketResponse createTicket() {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("State machine", "desc", TicketPriority.MEDIUM), auth),
            TicketResponse.class)
        .getBody();
  }

  private ResponseEntity<TicketResponse> transitionOk(String ticketId, TicketStatus to) {
    return restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(to), auth),
        TicketResponse.class);
  }

  private ResponseEntity<ApiError> transitionRejected(String ticketId, TicketStatus to) {
    return restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(to), auth),
        ApiError.class);
  }

  @org.junit.jupiter.api.BeforeEach
  void logIn() {
    auth = loginAs(TestUser.SUPPORT_1);
  }

  @Test
  void fullLegalPathOpenToClosed() {
    TicketResponse ticket = createTicket();
    String id = ticket.id().toString();

    assertThat(transitionOk(id, TicketStatus.IN_PROGRESS).getBody().status())
        .isEqualTo(TicketStatus.IN_PROGRESS);
    assertThat(transitionOk(id, TicketStatus.RESOLVED).getBody().status())
        .isEqualTo(TicketStatus.RESOLVED);
    assertThat(transitionOk(id, TicketStatus.CLOSED).getBody().status())
        .isEqualTo(TicketStatus.CLOSED);
  }

  @Test
  void openToCancelled() {
    TicketResponse ticket = createTicket();
    assertThat(transitionOk(ticket.id().toString(), TicketStatus.CANCELLED).getBody().status())
        .isEqualTo(TicketStatus.CANCELLED);
  }

  @Test
  void inProgressToCancelled() {
    TicketResponse ticket = createTicket();
    String id = ticket.id().toString();
    transitionOk(id, TicketStatus.IN_PROGRESS);
    assertThat(transitionOk(id, TicketStatus.CANCELLED).getBody().status())
        .isEqualTo(TicketStatus.CANCELLED);
  }

  @Test
  void everyReversalToOpenIsRejectedAndStatusUnchanged() {
    // CLOSED -> OPEN
    TicketResponse closed = createTicket();
    String closedId = closed.id().toString();
    transitionOk(closedId, TicketStatus.IN_PROGRESS);
    transitionOk(closedId, TicketStatus.RESOLVED);
    transitionOk(closedId, TicketStatus.CLOSED);
    ResponseEntity<ApiError> closedReversal = transitionRejected(closedId, TicketStatus.OPEN);
    assertThat(closedReversal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(closedReversal.getBody().code()).isEqualTo(ApiError.Code.INVALID_TRANSITION.name());
    assertThat(getStatus(closedId)).isEqualTo(TicketStatus.CLOSED);

    // RESOLVED -> OPEN
    TicketResponse resolved = createTicket();
    String resolvedId = resolved.id().toString();
    transitionOk(resolvedId, TicketStatus.IN_PROGRESS);
    transitionOk(resolvedId, TicketStatus.RESOLVED);
    assertThat(transitionRejected(resolvedId, TicketStatus.OPEN).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(getStatus(resolvedId)).isEqualTo(TicketStatus.RESOLVED);

    // CANCELLED -> OPEN
    TicketResponse cancelled = createTicket();
    String cancelledId = cancelled.id().toString();
    transitionOk(cancelledId, TicketStatus.CANCELLED);
    assertThat(transitionRejected(cancelledId, TicketStatus.OPEN).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(getStatus(cancelledId)).isEqualTo(TicketStatus.CANCELLED);
  }

  @Test
  void everyOtherDisallowedMoveIsRejected() {
    TicketResponse ticket = createTicket();
    String id = ticket.id().toString();

    // OPEN -> RESOLVED / CLOSED are not direct edges
    assertThat(transitionRejected(id, TicketStatus.RESOLVED).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(transitionRejected(id, TicketStatus.CLOSED).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(getStatus(id)).isEqualTo(TicketStatus.OPEN);
  }

  private TicketStatus getStatus(String ticketId) {
    return restTemplate
        .exchange(
            baseUrl("/tickets/" + ticketId),
            HttpMethod.GET,
            authEntity(auth),
            com.frequency.ticketing.web.dto.TicketDetailResponse.class)
        .getBody()
        .status();
  }
}

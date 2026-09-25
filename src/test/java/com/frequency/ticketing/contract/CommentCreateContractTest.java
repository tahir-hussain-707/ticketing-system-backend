package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CommentCreateContractTest extends AbstractIntegrationTest {

  private UUID createTicket(HttpHeaders auth) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("Comment target", "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }

  @Test
  void addCommentReturns201() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth);

    ResponseEntity<CommentResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("Escalated to facilities."), auth),
            CommentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().ticketId()).isEqualTo(ticketId);
    assertThat(response.getBody().content()).isEqualTo("Escalated to facilities.");
    assertThat(response.getBody().authorName()).isNotBlank();
  }

  @Test
  void emptyContentReturns400ValidationFailed() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest(""), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.VALIDATION_FAILED.name());
  }

  @Test
  void unknownTicketReturns404NotFound() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + UUID.randomUUID() + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("orphan comment"), auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.TICKET_NOT_FOUND.name());
  }

  @Test
  void commentByUnrelatedUserReturns403() {
    HttpHeaders creatorAuth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(creatorAuth);

    HttpHeaders otherAuth = loginAs(TestUser.GENERAL_2);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("not my ticket"), otherAuth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}

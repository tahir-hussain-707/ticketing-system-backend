package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CommentListContractTest extends AbstractIntegrationTest {

  private UUID createTicket(HttpHeaders auth) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("Comment list contract", "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }

  @Test
  void listCommentsReturns200WithPageShapeAndTimestamps() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest("first"), auth),
        CommentResponse.class);

    ResponseEntity<CommentPage> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId + "/comments"), HttpMethod.GET, authEntity(auth), CommentPage.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    CommentPage body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.content()).hasSize(1);
    assertThat(body.content().get(0).content()).isEqualTo("first");
    assertThat(body.content().get(0).createdAt()).isNotNull();
    assertThat(body.totalElements()).isEqualTo(1);
  }

  @Test
  void listCommentsForUnknownTicketReturns404NotFound() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);

    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + UUID.randomUUID() + "/comments"),
            HttpMethod.GET,
            authEntity(auth),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.TICKET_NOT_FOUND.name());
  }
}

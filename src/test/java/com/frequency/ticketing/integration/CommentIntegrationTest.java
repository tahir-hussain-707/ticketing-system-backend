package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Comments returned in ascending createdAt order with content intact (FR-005). */
class CommentIntegrationTest extends AbstractIntegrationTest {

  @Test
  void threeCommentsReturnedInOrderOnTicketDetail() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Comment history", "desc", TicketPriority.LOW), auth),
                TicketResponse.class)
            .getBody()
            .id();

    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest("first"), auth),
        CommentResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest("second"), auth),
        CommentResponse.class);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest("third"), auth),
        CommentResponse.class);

    TicketDetailResponse detail =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + ticketId), HttpMethod.GET, authEntity(auth), TicketDetailResponse.class)
            .getBody();

    assertThat(detail.comments()).hasSize(3);
    assertThat(detail.comments().stream().map(CommentResponse::content).toList())
        .containsExactly("first", "second", "third");
    detail.comments().forEach(c -> assertThat(c.ticketId()).isEqualTo(ticketId));
  }
}

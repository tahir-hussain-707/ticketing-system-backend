package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Ordering + timestamps (FR-002, FR-002a), empty-list-not-error (FR-003), and pagination
 * (FR-005) for the standalone comment-list endpoint.
 */
class CommentListIntegrationTest extends AbstractIntegrationTest {

  private UUID createTicket(HttpHeaders auth, String title) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest(title, "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }

  private void addComment(HttpHeaders auth, UUID ticketId, String content) {
    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/comments"),
        HttpMethod.POST,
        authEntity(new CommentCreateRequest(content), auth),
        CommentResponse.class);
  }

  @Test
  void threeCommentsListedOldestToNewestWithContentAndTimestamp() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth, "Ordering test");
    addComment(auth, ticketId, "first");
    addComment(auth, ticketId, "second");
    addComment(auth, ticketId, "third");

    CommentPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + ticketId + "/comments"), HttpMethod.GET, authEntity(auth), CommentPage.class)
            .getBody();

    assertThat(page.content()).extracting(CommentResponse::content).containsExactly("first", "second", "third");
    page.content().forEach(c -> assertThat(c.createdAt()).isNotNull());
  }

  @Test
  void ticketWithNoCommentsReturnsEmptyListNotError() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth, "No comments");

    CommentPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + ticketId + "/comments"), HttpMethod.GET, authEntity(auth), CommentPage.class)
            .getBody();

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void pagesThroughCommentsCorrectly() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(auth, "Pagination test");
    addComment(auth, ticketId, "c1");
    addComment(auth, ticketId, "c2");
    addComment(auth, ticketId, "c3");

    CommentPage firstPage =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + ticketId + "/comments?page=0&size=2"),
                HttpMethod.GET,
                authEntity(auth),
                CommentPage.class)
            .getBody();
    CommentPage secondPage =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + ticketId + "/comments?page=1&size=2"),
                HttpMethod.GET,
                authEntity(auth),
                CommentPage.class)
            .getBody();

    assertThat(firstPage.content()).hasSize(2);
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(secondPage.content()).hasSize(1);
    assertThat(secondPage.content().get(0).content()).isEqualTo("c3");
  }
}

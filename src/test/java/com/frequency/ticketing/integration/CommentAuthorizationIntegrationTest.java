package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentResponse;
import com.frequency.ticketing.web.dto.ReassignRequest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-014/FR-015 (spec 005 User Story 5): creator or assignee may comment and is named; anyone
 * else is rejected. Uses ADMIN reassignment to pin a known assignee, rather than relying on
 * auto-assignment's outcome, so the test doesn't depend on which SUPPORT user happens to be
 * least-loaded when it runs.
 */
class CommentAuthorizationIntegrationTest extends AbstractIntegrationTest {

  @Test
  void creatorAndAssigneeCanCommentNamedUnrelatedUserCannot() {
    HttpHeaders creator = loginAs(TestUser.GENERAL_1);
    TicketResponse created =
        restTemplate
            .exchange(
                baseUrl("/tickets"),
                HttpMethod.POST,
                authEntity(new TicketCreateRequest("Comment auth", "desc", TicketPriority.LOW), creator),
                TicketResponse.class)
            .getBody();

    HttpHeaders admin = loginAs(TestUser.ADMIN);
    restTemplate.exchange(
        baseUrl("/tickets/" + created.id() + "/assignee"),
        HttpMethod.PATCH,
        authEntity(new ReassignRequest(idOf(TestUser.SUPPORT_1)), admin),
        TicketResponse.class);

    // Creator comments — succeeds, named.
    ResponseEntity<CommentResponse> creatorComment =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("from the creator"), creator),
            CommentResponse.class);
    assertThat(creatorComment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(creatorComment.getBody().authorName()).isNotBlank();

    // Assignee comments — succeeds, named.
    HttpHeaders assignee = loginAs(TestUser.SUPPORT_1);
    ResponseEntity<CommentResponse> assigneeComment =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("from the assignee"), assignee),
            CommentResponse.class);
    assertThat(assigneeComment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(assigneeComment.getBody().authorName()).isNotBlank();

    // Unrelated user — rejected.
    HttpHeaders unrelated = loginAs(TestUser.GENERAL_2);
    ResponseEntity<ApiError> unrelatedComment =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/comments"),
            HttpMethod.POST,
            authEntity(new CommentCreateRequest("not my ticket"), unrelated),
            ApiError.class);
    assertThat(unrelatedComment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // Retrieval (as SUPPORT, permitted by FR-037) shows both names.
    TicketDetailResponse detail =
        restTemplate
            .exchange(
                baseUrl("/tickets/" + created.id()), HttpMethod.GET, authEntity(assignee), TicketDetailResponse.class)
            .getBody();
    assertThat(detail.comments()).hasSize(2);
    detail.comments().forEach(c -> assertThat(c.authorName()).isNotBlank());
  }
}

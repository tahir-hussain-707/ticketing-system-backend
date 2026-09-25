package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-037: single-ticket/comment view restricted to creator, assignee, or SUPPORT/ADMIN (spec 005
 * User Story 1, Scenario 9).
 */
class TicketViewAuthorizationIntegrationTest extends AbstractIntegrationTest {

  private UUID createTicket(HttpHeaders auth) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("View auth", "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }

  @Test
  void creatorCanViewTicketAndComments() {
    HttpHeaders creator = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(creator);

    assertThat(
            restTemplate
                .exchange(
                    baseUrl("/tickets/" + ticketId), HttpMethod.GET, authEntity(creator), TicketDetailResponse.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .exchange(
                    baseUrl("/tickets/" + ticketId + "/comments"),
                    HttpMethod.GET,
                    authEntity(creator),
                    CommentPage.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void unrelatedGeneralUserCannotViewTicketOrComments() {
    HttpHeaders creator = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(creator);
    HttpHeaders unrelated = loginAs(TestUser.GENERAL_2);

    ResponseEntity<ApiError> ticketResponse =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId), HttpMethod.GET, authEntity(unrelated), ApiError.class);
    ResponseEntity<ApiError> commentsResponse =
        restTemplate.exchange(
            baseUrl("/tickets/" + ticketId + "/comments"), HttpMethod.GET, authEntity(unrelated), ApiError.class);

    assertThat(ticketResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(commentsResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void supportAndAdminCanViewAnyTicket() {
    HttpHeaders creator = loginAs(TestUser.GENERAL_1);
    UUID ticketId = createTicket(creator);

    HttpHeaders support = loginAs(TestUser.SUPPORT_2);
    HttpHeaders admin = loginAs(TestUser.ADMIN);

    assertThat(
            restTemplate
                .exchange(baseUrl("/tickets/" + ticketId), HttpMethod.GET, authEntity(support), TicketDetailResponse.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .exchange(baseUrl("/tickets/" + ticketId), HttpMethod.GET, authEntity(admin), TicketDetailResponse.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}

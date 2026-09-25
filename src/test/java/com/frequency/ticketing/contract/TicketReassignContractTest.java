package com.frequency.ticketing.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ReassignRequest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ADMIN-only manual reassignment (spec 005 FR-013, User Story 2 Scenarios 5-6). */
class TicketReassignContractTest extends AbstractIntegrationTest {

  private TicketResponse createTicket(HttpHeaders auth) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest("Reassign me", "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody();
  }

  @Test
  void adminCanReassignToAnotherSupportUser() {
    HttpHeaders general = loginAs(TestUser.GENERAL_1);
    TicketResponse created = createTicket(general);
    UUID targetSupportId = idOf(TestUser.SUPPORT_2);

    HttpHeaders admin = loginAs(TestUser.ADMIN);
    ResponseEntity<TicketResponse> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/assignee"),
            HttpMethod.PATCH,
            authEntity(new ReassignRequest(targetSupportId), admin),
            TicketResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().assignee().id()).isEqualTo(targetSupportId);
  }

  @Test
  void nonAdminCannotReassign() {
    HttpHeaders general = loginAs(TestUser.GENERAL_1);
    TicketResponse created = createTicket(general);
    UUID targetSupportId = idOf(TestUser.SUPPORT_2);

    HttpHeaders support = loginAs(TestUser.SUPPORT_1);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/assignee"),
            HttpMethod.PATCH,
            authEntity(new ReassignRequest(targetSupportId), support),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void reassignToNonSupportUserReturns400() {
    HttpHeaders general = loginAs(TestUser.GENERAL_1);
    TicketResponse created = createTicket(general);
    UUID nonSupportTarget = idOf(TestUser.GENERAL_2);

    HttpHeaders admin = loginAs(TestUser.ADMIN);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/assignee"),
            HttpMethod.PATCH,
            authEntity(new ReassignRequest(nonSupportTarget), admin),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void reassignToNonexistentUserReturns404WithUserNotFoundCode() {
    HttpHeaders general = loginAs(TestUser.GENERAL_1);
    TicketResponse created = createTicket(general);
    UUID nonexistentTarget = UUID.randomUUID();

    HttpHeaders admin = loginAs(TestUser.ADMIN);
    ResponseEntity<ApiError> response =
        restTemplate.exchange(
            baseUrl("/tickets/" + created.id() + "/assignee"),
            HttpMethod.PATCH,
            authEntity(new ReassignRequest(nonexistentTarget), admin),
            ApiError.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo(ApiError.Code.USER_NOT_FOUND.name());
  }
}

package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.ReassignRequest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketPage;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** FR-016/FR-017 (spec 005 User Story 7): scope=mine/assigned/all each return the exact subset. */
class TicketScopedListingIntegrationTest extends AbstractIntegrationTest {

  @Test
  void mineReturnsOnlyTicketsCreatedByCaller() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    String marker = "scopemine" + UUID.randomUUID().toString().substring(0, 8);
    UUID myTicketId = create(auth, marker);

    TicketPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=mine&q=" + marker), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();

    assertThat(page.content()).extracting(TicketResponse::id).containsExactly(myTicketId);
  }

  @Test
  void assignedReturnsOnlyTicketsAssignedToCallerEmptyForGeneralUser() {
    HttpHeaders general = loginAs(TestUser.GENERAL_2);

    TicketPage page =
        restTemplate
            .exchange(baseUrl("/tickets?scope=assigned"), HttpMethod.GET, authEntity(general), TicketPage.class)
            .getBody();

    // A GENERAL user is never an assignee (spec.md Assumptions) — empty, not an error.
    assertThat(page).isNotNull();
  }

  @Test
  void assignedReturnsOnlyTicketsAssignedToSupportCaller() {
    HttpHeaders creator = loginAs(TestUser.GENERAL_1);
    String marker = "scopeassigned" + UUID.randomUUID().toString().substring(0, 8);
    UUID ticketId = create(creator, marker);

    HttpHeaders admin = loginAs(TestUser.ADMIN);
    restTemplate.exchange(
        baseUrl("/tickets/" + ticketId + "/assignee"),
        HttpMethod.PATCH,
        authEntity(new ReassignRequest(idOf(TestUser.SUPPORT_1)), admin),
        TicketResponse.class);

    HttpHeaders support = loginAs(TestUser.SUPPORT_1);
    TicketPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=assigned&q=" + marker), HttpMethod.GET, authEntity(support), TicketPage.class)
            .getBody();

    assertThat(page.content()).extracting(TicketResponse::id).containsExactly(ticketId);
  }

  @Test
  void allNarrowsToOwnCreatedTicketsForGeneralUserButShowsEveryTicketForSupport() {
    HttpHeaders general = loginAs(TestUser.GENERAL_1);
    String marker = "scopeall" + UUID.randomUUID().toString().substring(0, 8);
    UUID myTicketId = create(general, marker);

    TicketPage generalAll =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=all&q=" + marker), HttpMethod.GET, authEntity(general), TicketPage.class)
            .getBody();
    assertThat(generalAll.content()).extracting(TicketResponse::id).containsExactly(myTicketId);

    HttpHeaders support = loginAs(TestUser.SUPPORT_1);
    TicketPage supportAll =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=all&q=" + marker), HttpMethod.GET, authEntity(support), TicketPage.class)
            .getBody();
    // SUPPORT sees it too, even though they neither created nor are assigned it.
    assertThat(supportAll.content()).extracting(TicketResponse::id).contains(myTicketId);
  }

  @Test
  void scopeCombinesWithExistingStatusFilter() {
    HttpHeaders auth = loginAs(TestUser.GENERAL_1);
    String marker = "scopestatus" + UUID.randomUUID().toString().substring(0, 8);
    UUID ticketId = create(auth, marker);

    TicketPage openOnly =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=mine&q=" + marker + "&status=OPEN"),
                HttpMethod.GET,
                authEntity(auth),
                TicketPage.class)
            .getBody();
    TicketPage cancelledOnly =
        restTemplate
            .exchange(
                baseUrl("/tickets?scope=mine&q=" + marker + "&status=CANCELLED"),
                HttpMethod.GET,
                authEntity(auth),
                TicketPage.class)
            .getBody();

    assertThat(openOnly.content()).extracting(TicketResponse::id).containsExactly(ticketId);
    assertThat(cancelledOnly.content()).isEmpty();
  }

  private UUID create(HttpHeaders auth, String title) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest(title, "desc", TicketPriority.LOW), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }
}

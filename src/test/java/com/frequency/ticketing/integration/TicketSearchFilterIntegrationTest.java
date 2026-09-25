package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketPage;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketTransitionRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Keyword match, empty-result (not error), status filter, and combined filter (FR-006, FR-007). */
class TicketSearchFilterIntegrationTest extends AbstractIntegrationTest {

  // scope=all as SUPPORT sees every ticket regardless of who created it (FR-016 Assumptions).
  private HttpHeaders auth() {
    return loginAs(TestUser.SUPPORT_1);
  }

  private UUID create(HttpHeaders auth, String title, String description) {
    return restTemplate
        .exchange(
            baseUrl("/tickets"),
            HttpMethod.POST,
            authEntity(new TicketCreateRequest(title, description, TicketPriority.MEDIUM), auth),
            TicketResponse.class)
        .getBody()
        .id();
  }

  @Test
  void keywordMatchesTitleOrDescription() {
    HttpHeaders auth = auth();
    String unique = "zx" + UUID.randomUUID().toString().substring(0, 8);
    create(auth, unique + " printer issue", "unrelated description");
    create(auth, "unrelated title", "mentions " + unique + " somewhere");
    create(auth, "completely unrelated", "nothing matches here");

    TicketPage page =
        restTemplate
            .exchange(baseUrl("/tickets?q=" + unique + "&scope=all"), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();

    assertThat(page.content()).hasSize(2);
  }

  @Test
  void nonMatchingKeywordReturnsEmptyListNotError() {
    HttpHeaders auth = auth();
    String noMatch = "zzzznomatch" + UUID.randomUUID();

    TicketPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets?q=" + noMatch + "&scope=all"), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isZero();
  }

  @Test
  void statusFilterReturnsOnlyMatchingStatus() {
    HttpHeaders auth = auth();
    String marker = "statusfilter" + UUID.randomUUID().toString().substring(0, 8);
    UUID openId = create(auth, marker + " open ticket", "d");
    UUID cancelledId = create(auth, marker + " cancelled ticket", "d");
    restTemplate.exchange(
        baseUrl("/tickets/" + cancelledId + "/transitions"),
        HttpMethod.POST,
        authEntity(new TicketTransitionRequest(TicketStatus.CANCELLED), auth),
        TicketResponse.class);

    TicketPage cancelledPage =
        restTemplate
            .exchange(
                baseUrl("/tickets?q=" + marker + "&status=CANCELLED&scope=all"),
                HttpMethod.GET,
                authEntity(auth),
                TicketPage.class)
            .getBody();
    TicketPage openPage =
        restTemplate
            .exchange(
                baseUrl("/tickets?q=" + marker + "&status=OPEN&scope=all"),
                HttpMethod.GET,
                authEntity(auth),
                TicketPage.class)
            .getBody();

    assertThat(cancelledPage.content()).extracting(TicketResponse::id).containsExactly(cancelledId);
    assertThat(openPage.content()).extracting(TicketResponse::id).containsExactly(openId);
  }
}

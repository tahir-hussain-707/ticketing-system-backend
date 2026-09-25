package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.support.AbstractIntegrationTest;
import com.frequency.ticketing.web.dto.TicketPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds >=10,000 tickets directly via JDBC (bypassing the HTTP/JPA layers so setup time isn't
 * confused with the behavior under test), then asserts keyword search and status filter each
 * return correct results in under 2 seconds (SC-004).
 */
class TicketSearchPerformanceIntegrationTest extends AbstractIntegrationTest {

  private static final int SEED_COUNT = 10_000;
  private static final String NEEDLE = "perfneedle";
  private static boolean seeded = false;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void resetSeedFlag() {
    seeded = false;
  }

  private void seedIfNeeded() {
    if (seeded) {
      return;
    }
    UUID createdById = idOf(TestUser.SUPPORT_1);
    Instant now = Instant.now();
    List<Object[]> rows = new ArrayList<>(SEED_COUNT);
    for (int i = 0; i < SEED_COUNT; i++) {
      String title = (i == 0) ? NEEDLE + " ticket" : "bulk ticket " + i;
      rows.add(
          new Object[] {
            UUID.randomUUID().toString(),
            title,
            "seeded description " + i,
            "MEDIUM",
            "OPEN",
            createdById.toString(),
            null,
            0L,
            now,
            now
          });
    }
    jdbcTemplate.batchUpdate(
        "INSERT INTO tickets (id, title, description, priority, status, created_by_id, assignee_id, version, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows);
    seeded = true;
  }

  @Test
  void keywordSearchUnder10kTicketsCompletesUnder2Seconds() {
    seedIfNeeded();
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    long start = System.nanoTime();
    TicketPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets?q=" + NEEDLE + "&scope=all"), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(page.content()).isNotEmpty();
    assertThat(page.content().get(0).title()).contains(NEEDLE);
    assertThat(elapsedMs).isLessThan(2000);
  }

  @Test
  void statusFilterUnder10kTicketsCompletesUnder2Seconds() {
    seedIfNeeded();
    HttpHeaders auth = loginAs(TestUser.SUPPORT_1);

    long start = System.nanoTime();
    TicketPage page =
        restTemplate
            .exchange(
                baseUrl("/tickets?status=OPEN&scope=all"), HttpMethod.GET, authEntity(auth), TicketPage.class)
            .getBody();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(page.totalElements()).isGreaterThanOrEqualTo(SEED_COUNT);
    assertThat(elapsedMs).isLessThan(2000);
  }
}

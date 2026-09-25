package com.frequency.ticketing.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression test for the V5 deploy failure: migrating a database that already has {@code
 * comments} rows (from before {@code author_id} existed) must not fail with a NOT NULL
 * violation, and must backfill those rows with the system user, mirroring V4's pattern.
 */
@Testcontainers
class CommentAuthorBackfillMigrationTest {

  private static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000001";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }

  @Test
  void migrationBackfillsPreExistingCommentsInsteadOfFailing() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("4"))
        .load()
        .migrate();

    UUID ticketId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    try (Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      insertLegacyTicketAndComment(connection, ticketId, commentId);
    }

    Flyway latest =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();

    assertThatCode(latest::migrate).doesNotThrowAnyException();

    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement =
            connection.prepareStatement("SELECT author_id FROM comments WHERE id = ?")) {
      statement.setObject(1, commentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("author_id")).isEqualTo(SYSTEM_USER_ID);
      }
    }
  }

  /**
   * Regression test for a second V5 deploy failure: if the {@code flyway_schema_history} row
   * for V5 is ever deleted (e.g. a manual "undo" attempt) while {@code comments.author_id}
   * already exists on the database, Flyway re-runs the script from scratch. The {@code ADD
   * COLUMN} must not fail with "column already exists" in that case.
   */
  @Test
  void migrationIsSafeToRerunAfterHistoryRowIsDeleted() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement deleteHistoryRow =
            connection.prepareStatement("DELETE FROM flyway_schema_history WHERE version = '5'")) {
      deleteHistoryRow.executeUpdate();
    }

    Flyway rerun =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .load();

    assertThatCode(rerun::migrate).doesNotThrowAnyException();
  }

  private void insertLegacyTicketAndComment(Connection connection, UUID ticketId, UUID commentId)
      throws Exception {
    Timestamp now = Timestamp.from(Instant.now());
    try (PreparedStatement ticket =
        connection.prepareStatement(
            "INSERT INTO tickets (id, title, description, priority, status, version, created_at,"
                + " updated_at, created_by_id) VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?)")) {
      ticket.setObject(1, ticketId);
      ticket.setString(2, "Legacy ticket");
      ticket.setString(3, "Created before author_id existed");
      ticket.setString(4, "HIGH");
      ticket.setString(5, "OPEN");
      ticket.setTimestamp(6, now);
      ticket.setTimestamp(7, now);
      ticket.setObject(8, UUID.fromString(SYSTEM_USER_ID));
      ticket.executeUpdate();
    }
    try (PreparedStatement comment =
        connection.prepareStatement(
            "INSERT INTO comments (id, ticket_id, content, created_at) VALUES (?, ?, ?, ?)")) {
      comment.setObject(1, commentId);
      comment.setObject(2, ticketId);
      comment.setString(3, "Legacy comment with no author");
      comment.setTimestamp(4, now);
      comment.executeUpdate();
    }
  }
}

package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  /**
   * Keyword search (case-insensitive substring on title/description, backed by the pg_trgm GIN
   * indexes from V1__init_schema.sql) combined with an optional exact status filter, and an
   * optional ownership scope (spec 005 FR-016, FR-017): {@code createdById}/{@code assigneeId}
   * are mutually exclusive — pass whichever one the caller's requested scope needs, or both
   * {@code null} for "all". A native query is used (rather than HQL {@code ilike}) so
   * PostgreSQL's own {@code ILIKE} operator is what actually executes, guaranteeing the pg_trgm
   * indexes apply.
   */
  @Query(
      value =
          "SELECT * FROM tickets t WHERE "
              + "(CAST(:keyword AS varchar) IS NULL OR t.title ILIKE CONCAT('%', CAST(:keyword AS varchar), '%') "
              + "OR t.description ILIKE CONCAT('%', CAST(:keyword AS varchar), '%')) "
              + "AND (CAST(:status AS varchar) IS NULL OR t.status = CAST(:status AS varchar)) "
              + "AND (CAST(:createdById AS uuid) IS NULL OR t.created_by_id = CAST(:createdById AS uuid)) "
              + "AND (CAST(:assigneeId AS uuid) IS NULL OR t.assignee_id = CAST(:assigneeId AS uuid))",
      countQuery =
          "SELECT count(*) FROM tickets t WHERE "
              + "(CAST(:keyword AS varchar) IS NULL OR t.title ILIKE CONCAT('%', CAST(:keyword AS varchar), '%') "
              + "OR t.description ILIKE CONCAT('%', CAST(:keyword AS varchar), '%')) "
              + "AND (CAST(:status AS varchar) IS NULL OR t.status = CAST(:status AS varchar)) "
              + "AND (CAST(:createdById AS uuid) IS NULL OR t.created_by_id = CAST(:createdById AS uuid)) "
              + "AND (CAST(:assigneeId AS uuid) IS NULL OR t.assignee_id = CAST(:assigneeId AS uuid))",
      nativeQuery = true)
  Page<Ticket> searchByStatusNameScoped(
      @Param("keyword") String keyword,
      @Param("status") String status,
      @Param("createdById") UUID createdById,
      @Param("assigneeId") UUID assigneeId,
      Pageable pageable);

  default Page<Ticket> search(
      String keyword, TicketStatus status, UUID createdById, UUID assigneeId, Pageable pageable) {
    return searchByStatusNameScoped(
        keyword, status == null ? null : status.name(), createdById, assigneeId, pageable);
  }
}

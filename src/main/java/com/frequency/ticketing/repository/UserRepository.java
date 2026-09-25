package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailIgnoreCase(String email);

  /**
   * Every {@code SUPPORT} user ordered by their current {@code OPEN}/{@code IN_PROGRESS} ticket
   * count ascending, then by id ascending for a deterministic tie-break (spec 005 FR-010,
   * FR-011). The caller takes the first row, if any (FR-012: none exist is a valid outcome).
   * Backed by {@code idx_tickets_assignee_id_status} (V4 migration).
   */
  @Query(
      value =
          "SELECT u.* FROM users u LEFT JOIN tickets t "
              + "ON t.assignee_id = u.id AND t.status IN ('OPEN', 'IN_PROGRESS') "
              + "WHERE u.role = 'SUPPORT' "
              + "GROUP BY u.id "
              + "ORDER BY COUNT(t.id) ASC, u.id ASC",
      nativeQuery = true)
  List<User> findSupportUsersByWorkloadAscending();
}

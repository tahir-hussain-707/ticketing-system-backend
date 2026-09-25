package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.comment.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

  /**
   * Paginated variant backing GET /api/v1/tickets/{ticketId}/comments (feature
   * 002-list-comments), backed by the composite (ticket_id, created_at) index from
   * V2__comments_list_index.sql.
   */
  Page<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId, Pageable pageable);
}

package com.frequency.ticketing.domain.comment;

import com.frequency.ticketing.domain.exception.ForbiddenActionException;
import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketService;
import com.frequency.ticketing.domain.user.CurrentUser;
import com.frequency.ticketing.repository.CommentRepository;
import com.frequency.ticketing.web.dto.CommentMapper;
import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.CommentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns comment creation and retrieval. One local {@code @Transactional} unit of work per
 * operation (constitution Design Patterns: Atomicity — no cross-service call here, so SAGA does
 * not apply).
 */
@Service
public class CommentService {

  private final CommentRepository commentRepository;
  private final TicketService ticketService;
  private final CommentMapper commentMapper;
  private final CurrentUser currentUser;

  public CommentService(
      CommentRepository commentRepository,
      TicketService ticketService,
      CommentMapper commentMapper,
      CurrentUser currentUser) {
    this.commentRepository = commentRepository;
    this.ticketService = ticketService;
    this.commentMapper = commentMapper;
    this.currentUser = currentUser;
  }

  /**
   * Only the ticket's creator or its currently assigned {@code SUPPORT} user may comment (spec
   * 005 FR-014) — a narrower rule than view authorization (FR-037), which also allows any
   * {@code SUPPORT}/{@code ADMIN} user. The comment is attributed to the caller (FR-015).
   */
  @Transactional
  public Comment addComment(UUID ticketId, String content) {
    Ticket ticket = ticketService.getById(ticketId);
    UUID callerId = currentUser.id();
    boolean allowed =
        callerId.equals(ticket.getCreatedById()) || callerId.equals(ticket.getAssigneeId());
    if (!allowed) {
      throw new ForbiddenActionException(
          "Only the ticket's creator or its assignee may comment (FR-014)");
    }
    return commentRepository.save(new Comment(ticketId, content, callerId));
  }

  /**
   * Standalone, paginated comment listing for a ticket (feature 002-list-comments, FR-001,
   * FR-005), gated by the same view authorization as the ticket itself (spec 005 FR-037).
   */
  @Transactional(readOnly = true)
  public CommentPage listByTicket(UUID ticketId, Pageable pageable) {
    ticketService.assertViewable(ticketId);
    Page<Comment> commentPage = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId, pageable);
    List<CommentResponse> content = commentMapper.toResponseList(commentPage.getContent());
    return CommentPage.from(new PageImpl<>(content, pageable, commentPage.getTotalElements()));
  }
}

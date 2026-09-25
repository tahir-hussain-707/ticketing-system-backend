package com.frequency.ticketing.domain.ticket;

import com.frequency.ticketing.domain.comment.Comment;
import com.frequency.ticketing.domain.exception.ForbiddenActionException;
import com.frequency.ticketing.domain.exception.InvalidAssignmentException;
import com.frequency.ticketing.domain.exception.TicketNotFoundException;
import com.frequency.ticketing.domain.exception.UserNotFoundException;
import com.frequency.ticketing.domain.user.CurrentUser;
import com.frequency.ticketing.domain.user.User;
import com.frequency.ticketing.domain.user.UserRole;
import com.frequency.ticketing.repository.CommentRepository;
import com.frequency.ticketing.repository.TicketRepository;
import com.frequency.ticketing.repository.UserRepository;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketMapper;
import com.frequency.ticketing.web.dto.TicketResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns every ticket read/write. {@link #applyTransition} is the ONLY method that changes a
 * ticket's status (FR-013) — it delegates to {@link TicketStatusTransitionPolicy}, the single
 * domain component that decides transition legality (constitution Principle II). Every method
 * is one local {@code @Transactional} unit of work (constitution Design Patterns: Atomicity —
 * this feature calls no other service, so SAGA does not apply).
 */
@Service
public class TicketService {

  private static final Logger log = LoggerFactory.getLogger(TicketService.class);

  private final TicketRepository ticketRepository;
  private final CommentRepository commentRepository;
  private final TicketStatusTransitionPolicy transitionPolicy;
  private final TicketMapper ticketMapper;
  private final CurrentUser currentUser;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  public TicketService(
      TicketRepository ticketRepository,
      CommentRepository commentRepository,
      TicketStatusTransitionPolicy transitionPolicy,
      TicketMapper ticketMapper,
      CurrentUser currentUser,
      UserRepository userRepository,
      ApplicationEventPublisher eventPublisher) {
    this.ticketRepository = ticketRepository;
    this.commentRepository = commentRepository;
    this.transitionPolicy = transitionPolicy;
    this.ticketMapper = ticketMapper;
    this.currentUser = currentUser;
    this.userRepository = userRepository;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Auto-assigns to the least-loaded {@code SUPPORT} user (FR-009, FR-010), or leaves the ticket
   * unassigned when none exists (FR-012) — accepted concurrency trade-off documented in
   * research.md "Auto-assignment algorithm".
   */
  @Transactional
  public Ticket create(String title, String description, TicketPriority priority, UUID createdById) {
    TicketPriority effectivePriority = priority != null ? priority : TicketPriority.MEDIUM;
    Ticket ticket = new Ticket(title, description, effectivePriority, createdById);
    List<User> candidates = userRepository.findSupportUsersByWorkloadAscending();
    if (!candidates.isEmpty()) {
      UUID assigneeId = candidates.get(0).getId();
      ticket.assignTo(assigneeId);
      log.info("ticket_auto_assigned ticketId={} assigneeId={}", ticket.getId(), assigneeId);
    } else {
      log.info("ticket_auto_assign_skipped ticketId={} reason=no_support_user", ticket.getId());
    }
    return ticketRepository.save(ticket);
  }

  /** {@code ADMIN}-only manual reassignment after auto-assignment (FR-013). */
  @Transactional
  public Ticket reassign(UUID ticketId, UUID assigneeId) {
    Ticket ticket = getById(ticketId);
    User target =
        userRepository.findById(assigneeId).orElseThrow(() -> new UserNotFoundException(assigneeId));
    if (target.getRole() != UserRole.SUPPORT) {
      throw new InvalidAssignmentException("Reassignment target must be a SUPPORT user");
    }
    ticket.assignTo(assigneeId);
    return ticket;
  }

  @Transactional(readOnly = true)
  public Ticket getById(UUID id) {
    return ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public TicketDetailResponse getDetailById(UUID id) {
    Ticket ticket = assertViewable(id);
    List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(id);
    return ticketMapper.toDetailResponse(ticket, comments);
  }

  /**
   * FR-037: a single ticket (and, by extension, its comments) may only be viewed by its creator,
   * its current assignee, or a {@code SUPPORT}/{@code ADMIN} user. Used by {@link
   * #getDetailById} and by {@code TicketCommentController}'s comment-list endpoint, which shares
   * the same authorization rule against the ticket its comments belong to.
   */
  @Transactional(readOnly = true)
  public Ticket assertViewable(UUID id) {
    Ticket ticket = getById(id);
    UUID callerId = currentUser.id();
    boolean allowed =
        callerId.equals(ticket.getCreatedById())
            || callerId.equals(ticket.getAssigneeId())
            || currentUser.role() == UserRole.SUPPORT
            || currentUser.role() == UserRole.ADMIN;
    if (!allowed) {
      throw new ForbiddenActionException(
          "You are not permitted to view this ticket (FR-037)");
    }
    return ticket;
  }

  @Transactional(readOnly = true)
  public Page<TicketResponse> list(Pageable pageable) {
    return search(null, null, TicketScope.ALL, pageable);
  }

  /**
   * {@code scope} resolves against the caller (FR-016): {@code MINE}/{@code ASSIGNED} filter by
   * the caller's id; {@code ALL} means every ticket for {@code SUPPORT}/{@code ADMIN}, but is
   * narrowed to the caller's own created tickets for a {@code GENERAL} caller, since they are
   * never an assignee (research.md "Listing scope semantics").
   */
  @Transactional(readOnly = true)
  public Page<TicketResponse> search(
      String keyword, TicketStatus status, TicketScope scope, Pageable pageable) {
    UUID callerId = currentUser.id();
    UUID createdById = null;
    UUID assigneeId = null;
    switch (scope) {
      case MINE -> createdById = callerId;
      case ASSIGNED -> assigneeId = callerId;
      case ALL -> {
        if (currentUser.role() == UserRole.GENERAL) {
          createdById = callerId;
        }
      }
    }
    Page<Ticket> ticketPage = ticketRepository.search(keyword, status, createdById, assigneeId, pageable);
    List<TicketResponse> content = ticketMapper.toResponseList(ticketPage.getContent());
    return new PageImpl<>(content, pageable, ticketPage.getTotalElements());
  }

  @Transactional
  public Ticket update(UUID id, String title, String description, TicketPriority priority) {
    Ticket ticket = getById(id);
    ticket.updateFields(title, description, priority);
    return ticket;
  }

  @Transactional
  public Ticket applyTransition(UUID id, TicketStatus newStatus) {
    Ticket ticket = getById(id);
    TicketStatus previous = ticket.getStatus();
    transitionPolicy.transition(ticket, newStatus);
    log.info(
        "ticket_transition ticketId={} from={} to={} actor={}",
        ticket.getId(),
        previous,
        ticket.getStatus(),
        currentUser.id());
    if (ticket.getStatus() == TicketStatus.RESOLVED) {
      eventPublisher.publishEvent(new TicketResolvedEvent(ticket.getId()));
    }
    return ticket;
  }
}

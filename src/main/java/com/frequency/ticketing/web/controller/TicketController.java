package com.frequency.ticketing.web.controller;

import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketScope;
import com.frequency.ticketing.domain.ticket.TicketService;
import com.frequency.ticketing.domain.ticket.TicketStatus;
import com.frequency.ticketing.domain.user.CurrentUser;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ReassignRequest;
import com.frequency.ticketing.web.dto.TicketCreateRequest;
import com.frequency.ticketing.web.dto.TicketDetailResponse;
import com.frequency.ticketing.web.dto.TicketMapper;
import com.frequency.ticketing.web.dto.TicketPage;
import com.frequency.ticketing.web.dto.TicketResponse;
import com.frequency.ticketing.web.dto.TicketTransitionRequest;
import com.frequency.ticketing.web.dto.TicketUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matches contracts/ticket-ownership-api.yaml exactly. Constructor injection only. Every endpoint
 * except login (spec 005 FR-006) now requires authentication via {@code SecurityConfig}'s
 * default-deny filter chain.
 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Ticket lifecycle: create, view, update, and status transitions")
public class TicketController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final TicketService ticketService;
  private final TicketMapper ticketMapper;
  private final CurrentUser currentUser;

  public TicketController(
      TicketService ticketService, TicketMapper ticketMapper, CurrentUser currentUser) {
    this.ticketService = ticketService;
    this.ticketMapper = ticketMapper;
    this.currentUser = currentUser;
  }

  @PostMapping
  @Operation(
      summary = "Create a ticket",
      description =
          "Creates a ticket with status OPEN, auto-assigned to the least-loaded SUPPORT user "
              + "(FR-001, FR-009, FR-010, FR-012). Requires authentication (FR-006).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Ticket created",
        content = @Content(schema = @Schema(implementation = TicketResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
    Ticket ticket =
        ticketService.create(
            request.title(), request.description(), request.priority(), currentUser.id());
    return ResponseEntity.status(HttpStatus.CREATED).body(ticketMapper.toResponse(ticket));
  }

  @GetMapping
  @Operation(
      summary = "List tickets",
      description =
          "Lists tickets, optionally filtered by keyword/status and scoped by ownership "
              + "(FR-016, FR-017). Requires authentication.")
  @ApiResponse(
      responseCode = "200",
      description = "Page of tickets matching the given filters",
      content = @Content(schema = @Schema(implementation = TicketPage.class)))
  public TicketPage list(
      @Parameter(description = "Keyword matched against title and description") @RequestParam(required = false)
          String q,
      @Parameter(description = "Filter by exact ticket status") @RequestParam(required = false)
          TicketStatus status,
      @Parameter(description = "MINE, ASSIGNED, or ALL (default: all)")
          @RequestParam(required = false)
          TicketScope scope,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(Math.max(page, 0), effectiveSize);
    TicketScope effectiveScope = scope != null ? scope : TicketScope.ALL;
    Page<TicketResponse> result = ticketService.search(q, status, effectiveScope, pageable);
    return TicketPage.from(result);
  }

  @GetMapping(value = "/{ticketId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get ticket details",
      description =
          "Full ticket record including comments (FR-003). Requires authentication and view "
              + "authorization: creator, assignee, or SUPPORT/ADMIN only (FR-037).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = TicketDetailResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Not the creator, assignee, or SUPPORT/ADMIN",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public TicketDetailResponse getById(@PathVariable UUID ticketId) {
    return ticketService.getDetailById(ticketId);
  }

  @PatchMapping("/{ticketId}")
  @Operation(
      summary = "Update ticket fields",
      description =
          "Updates title/description/priority (FR-004). Never accepts status — status changes "
              + "only via POST .../transitions (FR-013 of spec 001). Never accepts assignee — "
              + "reassignment only via PATCH .../assignee (FR-013 of spec 005).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = TicketResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Concurrent update conflict",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public TicketResponse update(
      @PathVariable UUID ticketId, @Valid @RequestBody TicketUpdateRequest request) {
    Ticket ticket =
        ticketService.update(ticketId, request.title(), request.description(), request.priority());
    return ticketMapper.toResponse(ticket);
  }

  @PostMapping("/{ticketId}/transitions")
  @Operation(
      summary = "Transition ticket status",
      description = "Applies a status transition per the allowed state machine (FR-009, FR-010).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = TicketResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Transition not allowed from the current status, or a concurrent update conflict",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public TicketResponse transition(
      @PathVariable UUID ticketId, @Valid @RequestBody TicketTransitionRequest request) {
    Ticket ticket = ticketService.applyTransition(ticketId, request.status());
    return ticketMapper.toResponse(ticket);
  }

  @PatchMapping("/{ticketId}/assignee")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Manually reassign a ticket",
      description = "ADMIN only (spec 005 FR-013).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = TicketResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Target user is not a SUPPORT user",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Caller is not ADMIN",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket or target user not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public TicketResponse reassign(
      @PathVariable UUID ticketId, @Valid @RequestBody ReassignRequest request) {
    Ticket ticket = ticketService.reassign(ticketId, request.assigneeId());
    return ticketMapper.toResponse(ticket);
  }
}

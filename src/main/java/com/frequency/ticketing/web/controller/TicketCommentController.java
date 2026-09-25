package com.frequency.ticketing.web.controller;

import com.frequency.ticketing.domain.comment.Comment;
import com.frequency.ticketing.domain.comment.CommentService;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.CommentCreateRequest;
import com.frequency.ticketing.web.dto.CommentMapper;
import com.frequency.ticketing.web.dto.CommentPage;
import com.frequency.ticketing.web.dto.CommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matches contracts/tickets-api.yaml (001) and contracts/comments-list-api.yaml (002) exactly.
 * Constructor injection only.
 */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
@Tag(name = "Ticket Comments", description = "Adding and listing comments on a ticket (FR-005; 002-list-comments)")
public class TicketCommentController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CommentService commentService;
  private final CommentMapper commentMapper;

  public TicketCommentController(CommentService commentService, CommentMapper commentMapper) {
    this.commentService = commentService;
    this.commentMapper = commentMapper;
  }

  @PostMapping
  @Operation(summary = "Add a comment", description = "Adds a timestamped comment to an existing ticket (FR-005).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Comment created",
        content = @Content(schema = @Schema(implementation = CommentResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed (e.g. empty content)",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public ResponseEntity<CommentResponse> addComment(
      @PathVariable UUID ticketId, @Valid @RequestBody CommentCreateRequest request) {
    Comment comment = commentService.addComment(ticketId, request.content());
    return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toResponse(comment));
  }

  @GetMapping
  @Operation(
      summary = "List a ticket's comments",
      description =
          "Retrieves a ticket's comments as their own paginated, oldest-to-newest list, "
              + "independent of the ticket's other fields (002-list-comments FR-001, FR-002, FR-005).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Page of comments; content is [] when the ticket has no comments (FR-003)",
        content = @Content(schema = @Schema(implementation = CommentPage.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public CommentPage listComments(
      @PathVariable UUID ticketId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int effectiveSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(Math.max(page, 0), effectiveSize);
    return commentService.listByTicket(ticketId, pageable);
  }
}

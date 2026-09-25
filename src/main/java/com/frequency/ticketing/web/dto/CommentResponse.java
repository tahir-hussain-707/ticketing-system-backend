package com.frequency.ticketing.web.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code authorName} is the comment creator's name, resolved from their user record (FR-015). */
public record CommentResponse(
    UUID id, UUID ticketId, String content, String authorName, Instant createdAt) {}

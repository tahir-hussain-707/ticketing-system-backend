package com.frequency.ticketing.web.dto;

import java.util.UUID;

/** Replaces the old free-text {@code assignee} string wherever a user is referenced. */
public record UserSummary(UUID id, String name) {}

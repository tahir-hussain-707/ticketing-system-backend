package com.frequency.ticketing.domain.ai;

import java.util.UUID;

/**
 * One piece of grounding context passed to {@link ResolutionLlmClient} — a ticket reference and
 * its resolution content only, never internal-only ticket data (spec 005 FR-025).
 */
public record GroundingSource(UUID ticketId, String resolutionContent) {}

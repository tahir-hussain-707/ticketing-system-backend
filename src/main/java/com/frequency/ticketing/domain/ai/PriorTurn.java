package com.frequency.ticketing.domain.ai;

/**
 * A prior turn's query/response text, passed to {@link EmbeddingClient}/{@link
 * ResolutionLlmClient} so a follow-up is interpreted in light of what came before it (spec 005
 * FR-033/FR-034).
 */
public record PriorTurn(String query, String response) {}

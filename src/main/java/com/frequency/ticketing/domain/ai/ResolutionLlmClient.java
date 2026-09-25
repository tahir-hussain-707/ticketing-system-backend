package com.frequency.ticketing.domain.ai;

import java.util.List;

/**
 * Generates a grounded, customer-safe resolution answer (spec 005 FR-024, FR-031, FR-032).
 * Production wiring calls Anthropic's Claude Messages API (research.md "Response generation");
 * tests substitute a deterministic stub.
 */
public interface ResolutionLlmClient {

  /**
   * @param query the caller's current query
   * @param priorTurns the conversation's earlier turns, oldest first, empty for a new
   *     conversation (FR-033/FR-034)
   * @param sources the matched tickets' resolution content to ground the answer in
   */
  String generate(String query, List<PriorTurn> priorTurns, List<GroundingSource> sources);
}

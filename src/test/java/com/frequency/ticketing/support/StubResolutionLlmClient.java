package com.frequency.ticketing.support;

import com.frequency.ticketing.domain.ai.GroundingSource;
import com.frequency.ticketing.domain.ai.PriorTurn;
import com.frequency.ticketing.domain.ai.ResolutionLlmClient;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic canned response for tests (research.md "Testing the AI integration") — echoes
 * the grounding content and prior-turn count so tests can assert both without a real LLM call.
 */
@Component
@Profile("test")
public class StubResolutionLlmClient implements ResolutionLlmClient {

  @Override
  public String generate(String query, List<PriorTurn> priorTurns, List<GroundingSource> sources) {
    String groundedOn =
        sources.stream().map(s -> s.resolutionContent()).collect(Collectors.joining(" | "));
    return "Based on similar past tickets: "
        + groundedOn
        + " (priorTurns="
        + priorTurns.size()
        + ")";
  }
}

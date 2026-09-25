package com.frequency.ticketing.support;

import com.frequency.ticketing.domain.ai.EmbeddingClient;
import com.frequency.ticketing.domain.ai.GroundingSource;
import com.frequency.ticketing.domain.ai.PriorTurn;
import com.frequency.ticketing.domain.ai.ResolutionLlmClient;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A failing variant of the AI clients, {@code @Import}ed only by the test verifying FR-030's 503
 * path (T063) — {@code @Primary} overrides {@link StubEmbeddingClient}/
 * {@link StubResolutionLlmClient} for that test class only.
 */
@TestConfiguration
public class FailingAiTestClients {

  @Bean
  @Primary
  public EmbeddingClient failingEmbeddingClient() {
    return text -> {
      throw new RuntimeException("simulated embedding provider outage");
    };
  }

  @Bean
  @Primary
  public ResolutionLlmClient failingResolutionLlmClient() {
    return new ResolutionLlmClient() {
      @Override
      public String generate(String query, List<PriorTurn> priorTurns, List<GroundingSource> sources) {
        throw new RuntimeException("simulated generation provider outage");
      }
    };
  }
}

package com.frequency.ticketing.domain.ai.impl;

import com.frequency.ticketing.config.AiProviderProperties;
import com.frequency.ticketing.domain.ai.GroundingSource;
import com.frequency.ticketing.domain.ai.PriorTurn;
import com.frequency.ticketing.domain.ai.ResolutionLlmClient;
import com.frequency.ticketing.domain.exception.AiServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls a local Ollama instance's {@code /api/chat} endpoint (research.md "Response generation",
 * amended to Ollama — local, no API key, no per-call cost). The system prompt constrains the
 * model to answer only from the supplied grounding context, never quote internal comment text
 * verbatim (FR-031), and never include names/contact/internal identifiers (FR-032). No automatic
 * retry — a user-visible retry is preferable to a silently doubled-cost/slow one.
 */
@Component
@Profile("!test")
public class HttpResolutionLlmClient implements ResolutionLlmClient {

  private static final String SYSTEM_PROMPT =
      """
      You are a customer support resolution assistant. Answer the user's question using ONLY the
      resolution context provided below, which comes from past resolved support tickets.

      Rules you MUST follow:
      - Never quote the provided resolution text verbatim; reword it in your own words.
      - Never include any person's name, email address, phone number, or internal account/user
        identifier, even if one appears in the provided context.
      - Answer only using the provided context. Do not use outside knowledge or guess.
      - Refer to a source by its ticket reference number only (e.g. "ticket <id>"), never by any
        other internal detail.
      """;

  private final RestClient restClient;
  private final AiProviderProperties config;

  public HttpResolutionLlmClient(AiProviderProperties config) {
    this.config = config;
    this.restClient = RestClient.builder().baseUrl(config.getBaseUrl()).build();
  }

  @Override
  @SuppressWarnings("unchecked")
  public String generate(String query, List<PriorTurn> priorTurns, List<GroundingSource> sources) {
    try {
      List<Map<String, String>> messages = new ArrayList<>();
      messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
      for (PriorTurn turn : priorTurns) {
        messages.add(Map.of("role", "user", "content", turn.query()));
        messages.add(Map.of("role", "assistant", "content", turn.response()));
      }
      messages.add(Map.of("role", "user", "content", buildUserContent(query, sources)));

      Map<String, Object> response =
          restClient
              .post()
              .uri("/api/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("model", config.getChatModel(), "messages", messages, "stream", false))
              .retrieve()
              .body(Map.class);

      Map<String, Object> message = (Map<String, Object>) response.get("message");
      return (String) message.get("content");
    } catch (RuntimeException ex) {
      throw new AiServiceUnavailableException("Resolution generation request failed", ex);
    }
  }

  private String buildUserContent(String query, List<GroundingSource> sources) {
    StringBuilder sb = new StringBuilder();
    sb.append("Resolution context from past resolved tickets:\n\n");
    for (GroundingSource source : sources) {
      sb.append("- ticket ").append(source.ticketId()).append(": ")
          .append(source.resolutionContent()).append('\n');
    }
    sb.append("\nUser question: ").append(query);
    return sb.toString();
  }
}

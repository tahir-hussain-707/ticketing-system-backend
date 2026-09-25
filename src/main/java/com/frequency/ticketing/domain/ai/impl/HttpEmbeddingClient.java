package com.frequency.ticketing.domain.ai.impl;

import com.frequency.ticketing.config.AiProviderProperties;
import com.frequency.ticketing.domain.ai.EmbeddingClient;
import com.frequency.ticketing.domain.exception.AiServiceUnavailableException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls a local Ollama instance's {@code /api/embed} endpoint (research.md "Embedding
 * generation", amended to Ollama — local, no API key, no per-call cost). One bounded retry, since
 * this call is idempotent and cheap. Excluded from the {@code test} profile — see
 * {@code support.StubEmbeddingClient}.
 */
@Component
@Profile("!test")
public class HttpEmbeddingClient implements EmbeddingClient {

  private final RestClient restClient;
  private final AiProviderProperties config;

  public HttpEmbeddingClient(AiProviderProperties config) {
    this.config = config;
    this.restClient = RestClient.builder().baseUrl(config.getBaseUrl()).build();
  }

  @Override
  @SuppressWarnings("unchecked")
  public float[] embed(String text) {
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Map<String, Object> response =
            restClient
                .post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", config.getEmbeddingModel(), "input", text))
                .retrieve()
                .body(Map.class);
        List<List<Number>> embeddings = (List<List<Number>>) response.get("embeddings");
        List<Number> embedding = embeddings.get(0);
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
          result[i] = embedding.get(i).floatValue();
        }
        return result;
      } catch (RuntimeException ex) {
        lastFailure = ex;
      }
    }
    throw new AiServiceUnavailableException("Embedding request failed", lastFailure);
  }
}

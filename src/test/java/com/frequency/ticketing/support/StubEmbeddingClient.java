package com.frequency.ticketing.support;

import com.frequency.ticketing.domain.ai.EmbeddingClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic feature-hashing "embedding" for tests (research.md "Testing the AI integration")
 * — never calls a real external API. Two texts sharing vocabulary land closer together in cosine
 * distance than two texts that don't, which is all the chatbot's retrieval tests need.
 */
@Component
@Profile("test")
public class StubEmbeddingClient implements EmbeddingClient {

  private static final int DIMENSIONS = 1024;

  @Override
  public float[] embed(String text) {
    float[] vector = new float[DIMENSIONS];
    for (String word : text.toLowerCase().split("\\W+")) {
      if (word.isBlank()) {
        continue;
      }
      int index = Math.floorMod(word.hashCode(), DIMENSIONS);
      vector[index] += 1.0f;
    }
    normalize(vector);
    return vector;
  }

  private void normalize(float[] vector) {
    double sumSquares = 0;
    for (float v : vector) {
      sumSquares += (double) v * v;
    }
    if (sumSquares == 0) {
      return;
    }
    float norm = (float) Math.sqrt(sumSquares);
    for (int i = 0; i < vector.length; i++) {
      vector[i] /= norm;
    }
  }
}

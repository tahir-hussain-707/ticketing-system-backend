package com.frequency.ticketing.domain.ai;

/**
 * Generates a semantic embedding for a piece of text (spec 005 FR-019). Production wiring calls
 * Voyage AI's embeddings endpoint (research.md "Embedding generation"); tests substitute a
 * deterministic stub.
 */
public interface EmbeddingClient {

  float[] embed(String text);
}

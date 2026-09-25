package com.frequency.ticketing.domain.knowledgebase;

/**
 * One nearest-neighbor result: {@code similarity} is cosine similarity (1 - cosine distance,
 * pgvector's {@code <=>} operator), higher is more similar — research.md "Retrieval & grounding".
 */
public record SimilarityMatch(KnowledgeBaseEntry entry, double similarity) {}

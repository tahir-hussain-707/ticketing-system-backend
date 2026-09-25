package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.knowledgebase.KnowledgeBaseEntry;
import com.frequency.ticketing.domain.knowledgebase.SimilarityMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeBaseEntryRepository extends JpaRepository<KnowledgeBaseEntry, UUID> {

  Optional<KnowledgeBaseEntry> findByTicketId(UUID ticketId);

  /**
   * Atomic upsert keyed on {@code ticket_id}'s unique constraint (V7 migration), so two
   * concurrent {@code onTicketResolved} events for the same ticket (e.g. reopened and
   * re-resolved twice in quick succession) can never both observe "no existing entry" and both
   * attempt an insert — the DB itself serializes the conflict instead of one racing insert
   * throwing {@code DataIntegrityViolationException}. On conflict, the existing row's id is kept
   * (not present in the SET clause) and everything else is refreshed.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO knowledge_base_entries "
              + "(id, ticket_id, embedding, resolution_content, has_resolution_content, created_at, updated_at) "
              + "VALUES (:id, :ticketId, CAST(:embedding AS vector), :resolutionContent, :hasResolutionContent, now(), now()) "
              + "ON CONFLICT (ticket_id) DO UPDATE SET "
              + "embedding = EXCLUDED.embedding, "
              + "resolution_content = EXCLUDED.resolution_content, "
              + "has_resolution_content = EXCLUDED.has_resolution_content, "
              + "updated_at = now()",
      nativeQuery = true)
  void upsert(
      @Param("id") UUID id,
      @Param("ticketId") UUID ticketId,
      @Param("embedding") String embedding,
      @Param("resolutionContent") String resolutionContent,
      @Param("hasResolutionContent") boolean hasResolutionContent);

  /**
   * Cosine-distance nearest-neighbor search (FR-023), backed by V7's HNSW index, filtered to
   * entries with resolution content (FR-027). Returns raw {@code (id, distance)} rows rather than
   * full entities because a native query mixing entity columns with a computed distance column
   * doesn't map cleanly through JPA's entity result mapping.
   */
  @Query(
      value =
          "SELECT id, (embedding <=> CAST(:queryVector AS vector)) AS distance "
              + "FROM knowledge_base_entries WHERE has_resolution_content = true "
              + "ORDER BY distance ASC LIMIT :limit",
      nativeQuery = true)
  List<Object[]> findNearestRaw(@Param("queryVector") String queryVector, @Param("limit") int limit);

  /** Converts a raw embedding array into the pgvector text literal a native query can cast. */
  static String toVectorLiteral(float[] embedding) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(embedding[i]);
    }
    return sb.append(']').toString();
  }

  default List<SimilarityMatch> findNearest(float[] queryEmbedding, int limit) {
    List<Object[]> rows = findNearestRaw(toVectorLiteral(queryEmbedding), limit);
    List<UUID> orderedIds = rows.stream().map(row -> (UUID) row[0]).toList();
    Map<UUID, Double> distanceById =
        rows.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    row -> (UUID) row[0], row -> ((Number) row[1]).doubleValue()));
    Map<UUID, KnowledgeBaseEntry> entryById =
        findAllById(orderedIds).stream()
            .collect(java.util.stream.Collectors.toMap(KnowledgeBaseEntry::getId, e -> e));
    List<SimilarityMatch> matches = new ArrayList<>(orderedIds.size());
    for (UUID id : orderedIds) {
      KnowledgeBaseEntry entry = entryById.get(id);
      if (entry != null) {
        matches.add(new SimilarityMatch(entry, 1.0 - distanceById.get(id)));
      }
    }
    return matches;
  }
}

package com.frequency.ticketing.domain.knowledgebase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Type;

/**
 * One retrievable representation of a resolved ticket, used to ground chatbot answers (spec 005
 * Key Entities: Knowledge Base Entry). At most one entry per ticket.
 */
@Entity
@Table(name = "knowledge_base_entries")
public class KnowledgeBaseEntry {

  @Id private UUID id;

  @Column(name = "ticket_id", nullable = false, unique = true)
  private UUID ticketId;

  @Type(VectorType.class)
  @Column(nullable = false, columnDefinition = "vector(1024)")
  private float[] embedding;

  @Column(name = "resolution_content", columnDefinition = "TEXT")
  private String resolutionContent;

  @Column(name = "has_resolution_content", nullable = false)
  private boolean hasResolutionContent;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected KnowledgeBaseEntry() {
    // JPA
  }

  public KnowledgeBaseEntry(
      UUID ticketId, float[] embedding, String resolutionContent, boolean hasResolutionContent) {
    this.id = UUID.randomUUID();
    this.ticketId = ticketId;
    this.embedding = embedding;
    this.resolutionContent = resolutionContent;
    this.hasResolutionContent = hasResolutionContent;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Refreshes the embedding/resolution content when a late comment arrives (data-model.md). */
  public void refresh(float[] embedding, String resolutionContent, boolean hasResolutionContent) {
    this.embedding = embedding;
    this.resolutionContent = resolutionContent;
    this.hasResolutionContent = hasResolutionContent;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public String getResolutionContent() {
    return resolutionContent;
  }

  public boolean isHasResolutionContent() {
    return hasResolutionContent;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

package com.frequency.ticketing.domain.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An ongoing exchange between one logged-in user and the chatbot (spec 005 Key Entities). Whether
 * it is still "open" is derived, not stored — see {@code ChatbotService} (research.md
 * "Conversation model").
 */
@Entity
@Table(name = "chatbot_conversations")
public class ChatbotConversation {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "explicitly_ended_at")
  private Instant explicitlyEndedAt;

  protected ChatbotConversation() {
    // JPA
  }

  public ChatbotConversation(UUID userId) {
    this.id = UUID.randomUUID();
    this.userId = userId;
  }

  @PrePersist
  void onCreate() {
    this.startedAt = Instant.now();
  }

  public void end() {
    this.explicitlyEndedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getExplicitlyEndedAt() {
    return explicitlyEndedAt;
  }
}

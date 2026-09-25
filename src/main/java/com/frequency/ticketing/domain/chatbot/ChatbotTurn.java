package com.frequency.ticketing.domain.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One query+response pair within a conversation (spec 005 Key Entities: Chatbot Query/Response). */
@Entity
@Table(name = "chatbot_turns")
public class ChatbotTurn {

  @Id private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
  private String queryText;

  @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
  private String responseText;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "source_ticket_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] sourceTicketIds;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ChatbotTurn() {
    // JPA
  }

  public ChatbotTurn(
      UUID conversationId, String queryText, String responseText, List<UUID> sourceTicketIds) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.queryText = queryText;
    this.responseText = responseText;
    this.sourceTicketIds = sourceTicketIds.toArray(new UUID[0]);
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public String getQueryText() {
    return queryText;
  }

  public String getResponseText() {
    return responseText;
  }

  public List<UUID> getSourceTicketIds() {
    return List.of(sourceTicketIds);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package com.frequency.ticketing.domain.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A timestamped note attached to exactly one ticket. Comments are never edited or deleted. */
@Entity
@Table(name = "comments")
public class Comment {

  @Id private UUID id;

  @Column(name = "ticket_id", nullable = false)
  private UUID ticketId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Comment() {
    // JPA
  }

  public Comment(UUID ticketId, String content, UUID authorId) {
    this.id = UUID.randomUUID();
    this.ticketId = ticketId;
    this.content = content;
    this.authorId = authorId;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public String getContent() {
    return content;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.chatbot.ChatbotConversation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatbotConversationRepository extends JpaRepository<ChatbotConversation, UUID> {

  /**
   * Just the caller's single most recent conversation — used to resume-or-start (research.md
   * "Conversation model"), so this must not load and discard the caller's whole history (which
   * grows across the 90-day retention window) just to keep the first row.
   */
  Optional<ChatbotConversation> findTopByUserIdOrderByStartedAtDesc(UUID userId);

  Optional<ChatbotConversation> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Conversations whose effective end (explicit end time, or its last turn's timestamp, or its
   * own start time if it has no turns) is before {@code cutoff} — backs
   * {@code ConversationRetentionJob} (FR-035).
   */
  @Query(
      "SELECT c FROM ChatbotConversation c WHERE COALESCE("
          + "c.explicitlyEndedAt, "
          + "(SELECT MAX(t.createdAt) FROM ChatbotTurn t WHERE t.conversationId = c.id), "
          + "c.startedAt) < :cutoff")
  List<ChatbotConversation> findWithEffectiveEndBefore(@Param("cutoff") Instant cutoff);
}

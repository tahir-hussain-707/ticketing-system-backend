package com.frequency.ticketing.repository;

import com.frequency.ticketing.domain.chatbot.ChatbotTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotTurnRepository extends JpaRepository<ChatbotTurn, UUID> {

  List<ChatbotTurn> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

  Optional<ChatbotTurn> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}

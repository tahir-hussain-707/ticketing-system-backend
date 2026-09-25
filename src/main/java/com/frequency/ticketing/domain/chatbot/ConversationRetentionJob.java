package com.frequency.ticketing.domain.chatbot;

import com.frequency.ticketing.config.ChatbotProperties;
import com.frequency.ticketing.repository.ChatbotConversationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily cleanup of chatbot conversations past their retention window (spec 005 FR-035). Plain
 * {@code @Scheduled} — no new dependency or external scheduler needed for this.
 */
@Component
public class ConversationRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);

  private final ChatbotConversationRepository conversationRepository;
  private final ChatbotProperties chatbotProperties;

  public ConversationRetentionJob(
      ChatbotConversationRepository conversationRepository, ChatbotProperties chatbotProperties) {
    this.conversationRepository = conversationRepository;
    this.chatbotProperties = chatbotProperties;
  }

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void deleteExpiredConversations() {
    Instant cutoff =
        Instant.now().minus(chatbotProperties.getRetentionDays(), ChronoUnit.DAYS);
    List<ChatbotConversation> expired = conversationRepository.findWithEffectiveEndBefore(cutoff);
    if (!expired.isEmpty()) {
      conversationRepository.deleteAll(expired);
      log.info("chatbot_conversation_retention_deleted count={}", expired.size());
    }
  }
}

package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.frequency.ticketing.domain.chatbot.ChatbotConversation;
import com.frequency.ticketing.domain.chatbot.ConversationRetentionJob;
import com.frequency.ticketing.repository.ChatbotConversationRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** FR-035 (spec 005): conversations past the 90-day retention window are deleted. */
class ConversationRetentionJobIntegrationTest extends AbstractIntegrationTest {

  @Autowired private ChatbotConversationRepository conversationRepository;
  @Autowired private ConversationRetentionJob retentionJob;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void deletesConversationsPastRetentionKeepsRecentOnes() {
    UUID userId = idOf(TestUser.GENERAL_1);

    ChatbotConversation old = conversationRepository.save(new ChatbotConversation(userId));
    Instant longAgo = Instant.now().minus(200, ChronoUnit.DAYS);
    jdbcTemplate.update(
        "UPDATE chatbot_conversations SET started_at = ?, explicitly_ended_at = ? WHERE id = ?",
        java.sql.Timestamp.from(longAgo),
        java.sql.Timestamp.from(longAgo),
        old.getId());

    ChatbotConversation recent = conversationRepository.save(new ChatbotConversation(userId));

    retentionJob.deleteExpiredConversations();

    assertThat(conversationRepository.findById(old.getId())).isEmpty();
    assertThat(conversationRepository.findById(recent.getId())).isPresent();
  }
}

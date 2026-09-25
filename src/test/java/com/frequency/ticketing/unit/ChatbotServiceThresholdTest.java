package com.frequency.ticketing.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.frequency.ticketing.config.ChatbotProperties;
import com.frequency.ticketing.domain.ai.EmbeddingClient;
import com.frequency.ticketing.domain.ai.ResolutionLlmClient;
import com.frequency.ticketing.domain.chatbot.ChatbotResult;
import com.frequency.ticketing.domain.chatbot.ChatbotService;
import com.frequency.ticketing.domain.knowledgebase.KnowledgeBaseEntry;
import com.frequency.ticketing.domain.knowledgebase.SimilarityMatch;
import com.frequency.ticketing.domain.user.CurrentUser;
import com.frequency.ticketing.repository.ChatbotConversationRepository;
import com.frequency.ticketing.repository.ChatbotTurnRepository;
import com.frequency.ticketing.repository.KnowledgeBaseEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Isolates FR-028's confidence decision (research.md "Retrieval & grounding") with mocked
 * collaborators — no database, no network call.
 */
@ExtendWith(MockitoExtension.class)
class ChatbotServiceThresholdTest {

  @Mock private ChatbotConversationRepository conversationRepository;
  @Mock private ChatbotTurnRepository turnRepository;
  @Mock private KnowledgeBaseEntryRepository knowledgeBaseEntryRepository;
  @Mock private EmbeddingClient embeddingClient;
  @Mock private ResolutionLlmClient resolutionLlmClient;
  @Mock private CurrentUser currentUser;

  private ChatbotService chatbotService;
  private final UUID callerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ChatbotProperties properties = new ChatbotProperties();
    properties.setSimilarityThreshold(0.75);
    chatbotService =
        new ChatbotService(
            conversationRepository,
            turnRepository,
            knowledgeBaseEntryRepository,
            embeddingClient,
            resolutionLlmClient,
            properties,
            currentUser);

    when(currentUser.id()).thenReturn(callerId);
    when(conversationRepository.findTopByUserIdOrderByStartedAtDesc(callerId))
        .thenReturn(Optional.empty());
    when(conversationRepository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(turnRepository.findByConversationIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
    when(embeddingClient.embed(any())).thenReturn(new float[] {1f});
  }

  @Test
  void aboveThresholdCallsLlmAndReportsConfident() {
    KnowledgeBaseEntry entry = new KnowledgeBaseEntry(UUID.randomUUID(), new float[] {1f}, "fix", true);
    when(knowledgeBaseEntryRepository.findNearest(any(), anyIntSafe()))
        .thenReturn(List.of(new SimilarityMatch(entry, 0.9)));
    when(resolutionLlmClient.generate(any(), any(), any())).thenReturn("grounded answer");

    ChatbotResult result = chatbotService.handleQuery("printer offline", null);

    assertThat(result.confidentMatch()).isTrue();
    assertThat(result.responseText()).isEqualTo("grounded answer");
    verify(resolutionLlmClient).generate(any(), any(), any());
  }

  @Test
  void belowThresholdSkipsLlmAndReportsNoMatch() {
    KnowledgeBaseEntry entry = new KnowledgeBaseEntry(UUID.randomUUID(), new float[] {1f}, "fix", true);
    when(knowledgeBaseEntryRepository.findNearest(any(), anyIntSafe()))
        .thenReturn(List.of(new SimilarityMatch(entry, 0.2)));

    ChatbotResult result = chatbotService.handleQuery("printer offline", null);

    assertThat(result.confidentMatch()).isFalse();
    assertThat(result.sourceTicketIds()).isEmpty();
    verify(resolutionLlmClient, never()).generate(any(), any(), any());
  }

  @Test
  void noMatchesAtAllSkipsLlmAndReportsNoMatch() {
    when(knowledgeBaseEntryRepository.findNearest(any(), anyIntSafe())).thenReturn(List.of());

    ChatbotResult result = chatbotService.handleQuery("anything", null);

    assertThat(result.confidentMatch()).isFalse();
    verify(resolutionLlmClient, never()).generate(any(), any(), any());
  }

  private int anyIntSafe() {
    return org.mockito.ArgumentMatchers.anyInt();
  }
}

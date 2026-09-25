package com.frequency.ticketing.domain.chatbot;

import com.frequency.ticketing.config.ChatbotProperties;
import com.frequency.ticketing.domain.ai.EmbeddingClient;
import com.frequency.ticketing.domain.ai.GroundingSource;
import com.frequency.ticketing.domain.ai.PriorTurn;
import com.frequency.ticketing.domain.ai.ResolutionLlmClient;
import com.frequency.ticketing.domain.exception.ChatbotConversationNotFoundException;
import com.frequency.ticketing.domain.knowledgebase.SimilarityMatch;
import com.frequency.ticketing.domain.user.CurrentUser;
import com.frequency.ticketing.repository.ChatbotConversationRepository;
import com.frequency.ticketing.repository.ChatbotTurnRepository;
import com.frequency.ticketing.repository.KnowledgeBaseEntryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retrieval + grounding + no-match handling + conversation lifecycle (spec 005 FR-021–FR-036).
 * One local {@code @Transactional} unit of work per query (constitution Design Patterns:
 * Atomicity — the embedding/generation calls are outbound HTTP, not a cross-service call in the
 * SAGA sense, per plan.md's Constitution Check).
 */
@Service
public class ChatbotService {

  private static final String NO_MATCH_RESPONSE =
      "I couldn't find a past resolution I'm confident matches your issue. Please raise a "
          + "ticket so a support agent can help you directly.";

  private final ChatbotConversationRepository conversationRepository;
  private final ChatbotTurnRepository turnRepository;
  private final KnowledgeBaseEntryRepository knowledgeBaseEntryRepository;
  private final EmbeddingClient embeddingClient;
  private final ResolutionLlmClient resolutionLlmClient;
  private final ChatbotProperties chatbotProperties;
  private final CurrentUser currentUser;

  public ChatbotService(
      ChatbotConversationRepository conversationRepository,
      ChatbotTurnRepository turnRepository,
      KnowledgeBaseEntryRepository knowledgeBaseEntryRepository,
      EmbeddingClient embeddingClient,
      ResolutionLlmClient resolutionLlmClient,
      ChatbotProperties chatbotProperties,
      CurrentUser currentUser) {
    this.conversationRepository = conversationRepository;
    this.turnRepository = turnRepository;
    this.knowledgeBaseEntryRepository = knowledgeBaseEntryRepository;
    this.embeddingClient = embeddingClient;
    this.resolutionLlmClient = resolutionLlmClient;
    this.chatbotProperties = chatbotProperties;
    this.currentUser = currentUser;
  }

  @Transactional
  public ChatbotResult handleQuery(String query, UUID requestedConversationId) {
    UUID callerId = currentUser.id();
    ChatbotConversation conversation = resolveConversation(callerId, requestedConversationId);

    List<ChatbotTurn> priorTurnEntities =
        turnRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
    List<PriorTurn> priorTurns =
        priorTurnEntities.stream()
            .map(t -> new PriorTurn(t.getQueryText(), t.getResponseText()))
            .toList();

    // FR-033/FR-034: a follow-up is embedded together with what came before it, not in isolation.
    StringBuilder embeddingInput = new StringBuilder();
    for (PriorTurn turn : priorTurns) {
      embeddingInput.append(turn.query()).append('\n').append(turn.response()).append('\n');
    }
    embeddingInput.append(query);
    float[] queryEmbedding = embeddingClient.embed(embeddingInput.toString());

    List<SimilarityMatch> matches =
        knowledgeBaseEntryRepository.findNearest(
            queryEmbedding, chatbotProperties.getRetrievalLimit());

    List<SimilarityMatch> confidentMatches =
        matches.stream()
            .filter(m -> m.similarity() >= chatbotProperties.getSimilarityThreshold())
            .toList();

    String responseText;
    List<UUID> sourceTicketIds;
    boolean confident = !confidentMatches.isEmpty();
    if (confident) {
      List<GroundingSource> sources =
          confidentMatches.stream()
              .map(m -> new GroundingSource(m.entry().getTicketId(), m.entry().getResolutionContent()))
              .toList();
      responseText = resolutionLlmClient.generate(query, priorTurns, sources);
      sourceTicketIds = confidentMatches.stream().map(m -> m.entry().getTicketId()).toList();
    } else {
      // FR-028: below threshold, tell the user honestly rather than fabricating — and skip the
      // LLM call entirely (research.md "Retrieval & grounding").
      responseText = NO_MATCH_RESPONSE;
      sourceTicketIds = List.of();
    }

    turnRepository.save(new ChatbotTurn(conversation.getId(), query, responseText, sourceTicketIds));
    return new ChatbotResult(conversation.getId(), responseText, sourceTicketIds, confident);
  }

  @Transactional
  public void endConversation(UUID conversationId) {
    ChatbotConversation conversation =
        conversationRepository
            .findByIdAndUserId(conversationId, currentUser.id())
            .orElseThrow(() -> new ChatbotConversationNotFoundException(conversationId));
    if (conversation.getExplicitlyEndedAt() != null) {
      throw new ChatbotConversationNotFoundException(conversationId);
    }
    conversation.end();
  }

  private ChatbotConversation resolveConversation(UUID callerId, UUID requestedConversationId) {
    if (requestedConversationId != null) {
      ChatbotConversation conversation =
          conversationRepository
              .findByIdAndUserId(requestedConversationId, callerId)
              .orElseThrow(() -> new ChatbotConversationNotFoundException(requestedConversationId));
      if (conversation.getExplicitlyEndedAt() != null) {
        throw new ChatbotConversationNotFoundException(requestedConversationId);
      }
      return conversation;
    }

    // No conversationId supplied: resume the caller's most recent still-open conversation, or
    // start a new one (research.md "Conversation model" — the 30-minute boundary is evaluated
    // lazily here, not by a background job).
    Optional<ChatbotConversation> mostRecent =
        conversationRepository.findTopByUserIdOrderByStartedAtDesc(callerId);
    if (mostRecent.isPresent() && isOpen(mostRecent.get())) {
      return mostRecent.get();
    }
    return conversationRepository.save(new ChatbotConversation(callerId));
  }

  private boolean isOpen(ChatbotConversation conversation) {
    if (conversation.getExplicitlyEndedAt() != null) {
      return false;
    }
    Instant lastActivity =
        turnRepository
            .findTopByConversationIdOrderByCreatedAtDesc(conversation.getId())
            .map(ChatbotTurn::getCreatedAt)
            .orElse(conversation.getStartedAt());
    Instant cutoff = Instant.now().minus(chatbotProperties.getInactivityMinutes(), ChronoUnit.MINUTES);
    return lastActivity.isAfter(cutoff);
  }
}

package com.frequency.ticketing.web.controller;

import com.frequency.ticketing.domain.chatbot.ChatbotResult;
import com.frequency.ticketing.domain.chatbot.ChatbotService;
import com.frequency.ticketing.web.dto.ApiError;
import com.frequency.ticketing.web.dto.ChatbotQueryRequest;
import com.frequency.ticketing.web.dto.ChatbotTurnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Matches contracts/chatbot-api.yaml exactly. Every endpoint requires authentication (FR-007). */
@RestController
@RequestMapping("/api/v1/chatbot")
@Tag(name = "Chatbot", description = "RAG resolution chatbot (spec 005)")
public class ChatbotController {

  private final ChatbotService chatbotService;

  public ChatbotController(ChatbotService chatbotService) {
    this.chatbotService = chatbotService;
  }

  @PostMapping("/messages")
  @Operation(
      summary = "Send a chatbot query",
      description = "FR-021-FR-025, FR-028, FR-033/FR-034.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ChatbotTurnResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Blank/empty query",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "conversationId supplied but not found/not owned/already ended",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Embedding or response-generation service unavailable",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public ChatbotTurnResponse sendMessage(@Valid @RequestBody ChatbotQueryRequest request) {
    ChatbotResult result = chatbotService.handleQuery(request.query(), request.conversationId());
    return ChatbotTurnResponse.from(result);
  }

  @PostMapping("/conversations/{conversationId}/end")
  @Operation(summary = "Explicitly end a conversation", description = "FR-036.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Conversation ended"),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiError.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Not found, not owned by the caller, or already ended",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  public ResponseEntity<Void> endConversation(@PathVariable UUID conversationId) {
    chatbotService.endConversation(conversationId);
    return ResponseEntity.noContent().build();
  }
}

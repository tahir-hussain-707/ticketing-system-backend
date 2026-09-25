package com.frequency.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Local Ollama endpoint config for both embeddings and resolution generation (research.md
 * "Embedding generation" / "Response generation", amended). No API key — Ollama runs locally.
 */
@Component
@ConfigurationProperties(prefix = "app.ai.ollama")
public class AiProviderProperties {

  private String baseUrl = "http://localhost:11434";
  private String embeddingModel = "mxbai-embed-large";
  private String chatModel = "llama3.1";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public String getChatModel() {
    return chatModel;
  }

  public void setChatModel(String chatModel) {
    this.chatModel = chatModel;
  }
}

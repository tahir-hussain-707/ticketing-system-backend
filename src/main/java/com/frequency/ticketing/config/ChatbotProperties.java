package com.frequency.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable chatbot parameters (spec 005). Defaults match the spec's own clarified values; none
 * require a code change to adjust.
 */
@Component
@ConfigurationProperties(prefix = "app.chatbot")
public class ChatbotProperties {

  /** Minimum cosine similarity (0-1) for a match to be treated as "confident" (FR-028). */
  private double similarityThreshold = 0.75;

  /** How long a chatbot conversation is retained after it ends (FR-035). */
  private int retentionDays = 90;

  /** Inactivity window after which an open conversation is treated as ended (FR-036). */
  private int inactivityMinutes = 30;

  /** How many candidate knowledge base entries to retrieve per query. */
  private int retrievalLimit = 5;

  public double getSimilarityThreshold() {
    return similarityThreshold;
  }

  public void setSimilarityThreshold(double similarityThreshold) {
    this.similarityThreshold = similarityThreshold;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  public int getInactivityMinutes() {
    return inactivityMinutes;
  }

  public void setInactivityMinutes(int inactivityMinutes) {
    this.inactivityMinutes = inactivityMinutes;
  }

  public int getRetrievalLimit() {
    return retrievalLimit;
  }

  public void setRetrievalLimit(int retrievalLimit) {
    this.retrievalLimit = retrievalLimit;
  }
}

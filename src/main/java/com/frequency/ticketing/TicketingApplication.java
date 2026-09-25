package com.frequency.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync: KnowledgeBaseService's post-commit indexing (spec 005 FR-018).
// @EnableScheduling: ConversationRetentionJob's daily cleanup (spec 005 FR-035).
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TicketingApplication {

  public static void main(String[] args) {
    SpringApplication.run(TicketingApplication.class, args);
  }
}

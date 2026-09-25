package com.frequency.ticketing.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Without an explicit {@link Executor} bean, {@code @EnableAsync} (see
 * {@link com.frequency.ticketing.TicketingApplication}) falls back to
 * {@code SimpleAsyncTaskExecutor}, which spawns a brand-new, unbounded thread per {@code @Async}
 * invocation — a burst of tickets transitioning to {@code RESOLVED} would fire one
 * {@code KnowledgeBaseService.onTicketResolved} task each with no pooling, queueing, or
 * backpressure. This bean gives {@code @Async} a bounded pool with a queue instead.
 */
@Configuration
public class AsyncConfig {

  @Bean
  public Executor applicationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("ticketing-async-");
    executor.initialize();
    return executor;
  }
}

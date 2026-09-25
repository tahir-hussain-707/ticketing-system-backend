package com.frequency.ticketing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.frequency.ticketing.domain.ticket.Ticket;
import com.frequency.ticketing.domain.ticket.TicketPriority;
import com.frequency.ticketing.repository.TicketRepository;
import com.frequency.ticketing.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Optimistic-lock conflict handling (constitution Principle IV; spec Edge Cases: concurrent
 * update; SC-005). Loads the same ticket into two separate persistence contexts, saves the
 * first (its version advances), then saves the stale second copy — the second save must be
 * rejected and the persisted state must match only the first, successful update.
 */
class TicketConcurrencyIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TicketRepository ticketRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void staleUpdateIsRejectedWithOptimisticLockException() {
    UUID createdById = idOf(TestUser.GENERAL_1);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    Ticket saved =
        tx.execute(
            status ->
                ticketRepository.save(
                    new Ticket("Concurrent", "desc", TicketPriority.LOW, createdById)));

    // Two independent reads of the same row (separate persistence contexts / transactions).
    Ticket copyOne = tx.execute(status -> ticketRepository.findById(saved.getId()).orElseThrow());
    Ticket copyTwo = tx.execute(status -> ticketRepository.findById(saved.getId()).orElseThrow());

    copyOne.updateFields("First writer", null, null);
    tx.executeWithoutResult(status -> ticketRepository.saveAndFlush(copyOne));

    copyTwo.updateFields("Second writer (stale)", null, null);
    assertThatThrownBy(() -> tx.executeWithoutResult(status -> ticketRepository.saveAndFlush(copyTwo)))
        .isInstanceOf(OptimisticLockingFailureException.class);

    Ticket finalState =
        tx.execute(status -> ticketRepository.findById(saved.getId()).orElseThrow());
    assertThat(finalState.getTitle()).isEqualTo("First writer");
  }
}

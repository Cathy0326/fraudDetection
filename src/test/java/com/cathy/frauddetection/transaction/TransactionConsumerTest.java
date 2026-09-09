package com.cathy.frauddetection.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cathy.frauddetection.rules.RuleEvaluator;
import com.cathy.frauddetection.rules.RuleHit;
import com.cathy.frauddetection.rules.RuleResult;
import com.cathy.frauddetection.velocity.VelocityService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

// No Spring context: the constructor is plain dependency injection, so
// MockitoExtension can wire three fake collaborators into it directly.
// This runs in milliseconds, same tier as the pure-logic tests.
@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private RuleEvaluator ruleEvaluator;

    @Mock
    private VelocityService velocityService;

    private TransactionConsumer consumer;
    private TransactionMetrics metrics;

    @BeforeEach
    void setUpMetrics(){
        metrics = new TransactionMetrics(new SimpleMeterRegistry(),"simple");
    }

    // Constructed manually in each test, not via @InjectMocks, so the three
    // mocks above are visible by name when reading a failing test — @InjectMocks
    // would hide which mock plugs into which constructor position.
    private TransactionEvent eventOf(Transaction transaction) {
        consumer = new TransactionConsumer(repository, ruleEvaluator, velocityService,metrics);
        when(repository.findById(1L)).thenReturn(Optional.of(transaction));
        return new TransactionEvent(1L, transaction.getTransactionRef(), transaction.getAccountId(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getDestinationCountry(),
                transaction.getOccurredAt());
    }

    private Transaction pendingTransaction() {
        return new Transaction("TX-TEST", "ACC-TEST", new BigDecimal("500.00"),
                "EUR", "IE", "TRANSFER", Instant.now(), TransactionStatus.PENDING);
    }

    // The one test that matters most: recordAndCount must fire only after the
    // idempotency guard has decided this transaction is unprocessed. If this
    // ever fires before the guard, a duplicate Kafka delivery would inflate
    // the velocity count silently — no exception, no failed row, just a
    // customer wrongly flagged for velocity days later.
    @Test
    void recordsVelocityOnlyAfterIdempotencyGuardPasses() {
        Transaction transaction = pendingTransaction();
        TransactionEvent event = eventOf(transaction);
        when(velocityService.recordAndCount(transaction.getAccountId())).thenReturn(1L);
        when(ruleEvaluator.evaluate(any(), anyLong()))
                .thenReturn(RuleResult.from(List.of()));

        consumer.consume(event);

        InOrder order = Mockito.inOrder(velocityService, ruleEvaluator);
        order.verify(velocityService).recordAndCount(transaction.getAccountId());
        order.verify(ruleEvaluator).evaluate(any(), anyLong());
    }

    // The other half of the same guarantee: an already-processed transaction
    // must short-circuit before either side effect. This is the scenario the
    // idempotency guard exists to prevent — testing only the happy path would
    // never catch a guard that got moved, weakened, or accidentally removed.
    @Test
    void alreadyProcessedTransactionSkipsVelocityAndEvaluation() {
        Transaction transaction = new Transaction("TX-DUP", "ACC-TEST", new BigDecimal("500.00"),
                "EUR", "IE", "TRANSFER", Instant.now(), TransactionStatus.PROCESSED);
        TransactionEvent event = eventOf(transaction);

        consumer.consume(event);

        verify(velocityService, never()).recordAndCount(any());
        verify(ruleEvaluator, never()).evaluate(any(), anyLong());
    }

    // The ifPresentOrElse's other branch. If the row genuinely doesn't exist
    // (should not happen given the producer-before-publish ordering, but the
    // code defends against it), neither collaborator should be touched.
    @Test
    void missingRowSkipsVelocityAndEvaluation() {
        consumer = new TransactionConsumer(repository, ruleEvaluator, velocityService,metrics);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        TransactionEvent event = new TransactionEvent(99L, "TX-MISSING", "ACC-TEST",
                new BigDecimal("500.00"), "EUR", "IE", Instant.now());

        consumer.consume(event);

        verify(velocityService, never()).recordAndCount(any());
        verify(ruleEvaluator, never()).evaluate(any(), anyLong());
    }

    // Confirms the entity is actually mutated with the evaluator's output,
    // not just that the evaluator was called. Calling a mock and using its
    // result are two different failure modes.
    @Test
    void appliesRiskAssessmentFromEvaluatorResult() {
        Transaction transaction = pendingTransaction();
        TransactionEvent event = eventOf(transaction);
        when(velocityService.recordAndCount(transaction.getAccountId())).thenReturn(1L);
        RuleResult result = RuleResult.from(List.of(new RuleHit("AMOUNT_THRESHOLD", 40)));
        when(ruleEvaluator.evaluate(any(), anyLong())).thenReturn(result);

        consumer.consume(event);

        org.junit.jupiter.api.Assertions.assertEquals(TransactionStatus.PROCESSED, transaction.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals((short)40, transaction.getRiskScore());
        org.junit.jupiter.api.Assertions.assertEquals(Decision.REVIEW, transaction.getDecision());
    }
}
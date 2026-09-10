package com.cathy.frauddetection.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cathy.frauddetection.rules.RuleHit;
import com.cathy.frauddetection.transaction.Decision;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// No Spring: the only collaborator is the repository, and every branch here is
// plain logic. Same tier as the pure-logic rules tests.
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Captor
    private ArgumentCaptor<Alert> alertCaptor;

    private AlertService alertService;

    // In @BeforeEach, not a field initializer: field initializers run before
    // Mockito finishes injecting, so the mock would be null at construction.
    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository);
    }

    private static final List<RuleHit> TWO_HITS = List.of(
            new RuleHit("AMOUNT_THRESHOLD", 40),
            new RuleHit("HIGH_RISK_COUNTRY", 40));

    // The APPROVE short-circuit lives here, so this is where it gets tested —
    // TransactionConsumerTest can only assert that this method was called.
    @Test
    void approvedTransactionCreatesNoAlert() {
        alertService.createIfNeeded(1L, 0, Decision.APPROVE, List.of());

        verify(alertRepository, never()).save(any());
        // Not even the existence check: APPROVE returns before any I/O.
        verify(alertRepository, never()).existsByTransactionId(anyLong());
    }

    // The duplicate guard. Without it a redelivered Kafka message would hit the
    // unique constraint, throw, and retry forever (no DLQ).
    @Test
    void existingAlertIsNotRecreated() {
        when(alertRepository.existsByTransactionId(1L)).thenReturn(true);

        alertService.createIfNeeded(1L, 80, Decision.BLOCK, TWO_HITS);

        verify(alertRepository, never()).save(any());
    }

    // Captures the saved entity instead of verify(save(any())): calling save and
    // saving the right thing are two different failure modes.
    @Test
    void savesSnapshotOfTheEvaluation() {
        when(alertRepository.existsByTransactionId(1L)).thenReturn(false);

        alertService.createIfNeeded(1L, 80, Decision.BLOCK, TWO_HITS);

        verify(alertRepository).save(alertCaptor.capture());
        Alert saved = alertCaptor.getValue();
        assertEquals(1L, saved.getTransactionId());
        assertEquals((short) 80, saved.getRiskScore());
        assertEquals(Decision.BLOCK, saved.getDecision());
        // Joined here, split back in AlertResponse. Order follows the hit list.
        assertEquals("AMOUNT_THRESHOLD,HIGH_RISK_COUNTRY", saved.getTriggeredRules());
        // Born OPEN and unreviewed — the constructor decides this, not the caller.
        assertEquals(AlertStatus.OPEN, saved.getStatus());
        assertNull(saved.getReviewedAt());
    }

    // REVIEW is the other non-approved decision. Without this the CHECK
    // constraint's two allowed values would only ever see one of them in tests.
    @Test
    void reviewDecisionAlsoCreatesAnAlert() {
        when(alertRepository.existsByTransactionId(2L)).thenReturn(false);

        alertService.createIfNeeded(2L, 40, Decision.REVIEW,
                List.of(new RuleHit("AMOUNT_THRESHOLD", 40)));

        verify(alertRepository).save(alertCaptor.capture());
        assertEquals(Decision.REVIEW, alertCaptor.getValue().getDecision());
    }

    @Test
    void reviewMarksTheAlertAndSetsTheTimestamp() {
        Alert alert = new Alert(1L, 80, Decision.BLOCK, "AMOUNT_THRESHOLD");
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));

        Alert reviewed = alertService.review(7L, AlertStatus.CONFIRMED);

        assertEquals(AlertStatus.CONFIRMED, reviewed.getStatus());
        // Both fields move together — the Java side of ck_alerts_reviewed_at.
        org.junit.jupiter.api.Assertions.assertNotNull(reviewed.getReviewedAt());
        // No save(): the entity is managed, so dirty checking flushes it.
        verify(alertRepository, never()).save(any());
    }

    @Test
    void reviewingAMissingAlertThrows() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class,
                () -> alertService.review(999L, AlertStatus.CONFIRMED));
    }
}
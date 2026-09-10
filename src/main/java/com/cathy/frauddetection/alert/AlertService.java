package com.cathy.frauddetection.alert;

import com.cathy.frauddetection.rules.RuleHit;
import com.cathy.frauddetection.transaction.Decision;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Creates an alert for a non-approved transaction. No @Transactional:
     * this always runs inside the consumer's transaction, so the alert and the
     * transaction's score commit or roll back together.
     */
    public void createIfNeeded(Long transactionId, int riskScore,
                               Decision decision, List<RuleHit> hits) {
        if (decision == Decision.APPROVE) {
            return;
        }
        // Guard before insert: the unique constraint would throw, and a throwing
        // consumer retries forever (no DLQ).
        if (alertRepository.existsByTransactionId(transactionId)) {
            return;
        }
        alertRepository.save(
                new Alert(transactionId, riskScore, decision, joinRuleCodes(hits)));
    }

    @Transactional(readOnly = true)
    public Page<Alert> findByStatus(AlertStatus status, Pageable pageable) {
        return alertRepository.findByStatus(status, pageable);
    }

    /**
     * Records a review outcome. No save(): the entity is managed here, so
     * Hibernate's dirty checking flushes the change at commit.
     *
     * Known gap: no optimistic locking, so concurrent reviews are last-write-wins.
     */
    @Transactional
    public Alert review(Long alertId, AlertStatus outcome) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        alert.review(outcome);
        return alert;
    }

    // Persistence format is this layer's concern, not RuleResult's.
    private String joinRuleCodes(List<RuleHit> hits) {
        return hits.stream()
                .map(RuleHit::ruleCode)
                .collect(Collectors.joining(Alert.RULE_SEPARATOR));
    }
}
package com.cathy.frauddetection.alert;

import com.cathy.frauddetection.transaction.Decision;
import java.time.Instant;
import java.util.List;

/**
 * API shape of an alert. Separate from the entity so the two can evolve apart:
 * returning the entity would make every new column a contract change.
 *
 * <p>reviewedAt stays in the JSON as null when unreviewed. Omitting the key
 * would leave the client unable to tell "not reviewed" from "no such field".
 */
record AlertResponse(
        Long id,
        Long transactionId,
        Short riskScore,
        Decision decision,
        List<String> triggeredRules,
        AlertStatus status,
        Instant createdAt,
        Instant reviewedAt) {
    // Comma joining is a storage choice. The API exposes the structure, not the
    // encoding, so the client never has to know about the separator.
    static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getTransactionId(),
                alert.getRiskScore(),
                alert.getDecision(),
                splitRuleCodes(alert.getTriggeredRules()),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getReviewedAt());
    }

    // An alert always has at least one hit (decision != APPROVE implies a match),
    // but "".split(",") yields [""] rather than an empty array, so guard anyway.
    private static List<String> splitRuleCodes(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split(Alert.RULE_SEPARATOR));
    }
}
package com.cathy.frauddetection.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kafka wire contract. Deliberately separate from the Transaction entity:
 * consumers must not be coupled to the database schema.
 */
public record TransactionEvent(
        Long transactionId,
        String transactionRef,
        String accountId,
        BigDecimal amount,
        String currency,
        String destinationCountry,
        Instant occurredAt
) {
}
package com.cathy.frauddetection.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of a transaction.
 *
 * Deliberately separate from the Transaction entity. The entity is free to grow
 * fields without changing this contract, and this contract can drop fields
 * without touching the persistence mapping.
 *
 * riskScore and decision are null for transactions the consumer has not scored
 * yet. That null is serialised, not omitted: a missing key would leave the
 * client unable to tell "not scored" from "field not in this API version".
 */
record TransactionResponse(
        Long id,
        String transactionRef,
        String accountId,
        BigDecimal amount,
        String currency,
        String destinationCountry,
        String transactionType,
        Instant occurredAt,
        TransactionStatus status,
        Short riskScore,
        Decision decision,
        Instant createdAt) {

    // Static factory on the DTO, not a toResponse() on the entity: the API type
    // is allowed to know the domain type, never the other way round.
    static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionRef(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDestinationCountry(),
                transaction.getTransactionType(),
                transaction.getOccurredAt(),
                transaction.getStatus(),
                transaction.getRiskScore(),
                transaction.getDecision(),
                transaction.getCreatedAt());
    }
}
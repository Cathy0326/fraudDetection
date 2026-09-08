package com.cathy.frauddetection.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Search filters for GET /api/v1/transactions.
 *
 * Every component is optional. A null component means "do not filter on this
 * field" — it never means "match rows where this column is null".
 *
 * No validation here: this record is constructed by Spring's data binder, and
 * an exception thrown during binding is wrapped by the framework, not by us.
 * The min/max ordering check lives in TransactionService, where we own the
 * exception type and the status code it maps to.
 */
record TransactionSearchCriteria(

        String accountId,

        String destinationCountry,

        // Enum, not String: Spring converts the query parameter by enum constant
        // name and rejects anything else with 400 before our code runs.
        // The match is case-sensitive — ?status=processed fails, PROCESSED works.
        TransactionStatus status,

        Decision decision,

        BigDecimal minAmount,

        BigDecimal maxAmount,

        // Short, not Integer: the entity field is Short, and the Criteria API
        // compares Path<Short> against Short. A widened type breaks the match.
        Short minRiskScore,

        Short maxRiskScore,

        // Filters occurred_at (when the transaction happened), not created_at
        // (when we stored it). These are different clocks owned by different
        // parties; the analyst asks about the first one.
        // Expects ISO-8601 instants, e.g. 2026-09-07T10:00:00Z.
        Instant occurredFrom,

        Instant occurredTo) {
}
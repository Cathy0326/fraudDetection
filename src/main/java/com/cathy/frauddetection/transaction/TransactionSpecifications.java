package com.cathy.frauddetection.transaction;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates search criteria into a JPA Specification.
 *
 * Only fields that are actually set produce a predicate. A null predicate is
 * never created and never enters the chain, so nothing downstream has to treat
 * null as a "skip me" signal.
 *
 * The string attribute names below are ENTITY FIELD names (accountId), not
 * database column names (account_id). They are unchecked at compile time — a
 * typo fails when the query runs, not when it compiles. The fix is the JPA
 * static metamodel (hibernate-jpamodelgen), deliberately not added here.
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> from(TransactionSearchCriteria criteria) {
        List<Specification<Transaction>> filters = new ArrayList<>();

        if (criteria.accountId() != null) {
            filters.add((root, query, cb) ->
                    cb.equal(root.get("accountId"), criteria.accountId()));
        }

        // Exact match, no case normalisation. Stored values are uppercase because
        // TransactionRequest rejects anything else at the boundary; normalising
        // here would hide the day that guarantee breaks.
        if (criteria.destinationCountry() != null) {
            filters.add((root, query, cb) ->
                    cb.equal(root.get("destinationCountry"), criteria.destinationCountry()));
        }

        if (criteria.status() != null) {
            filters.add((root, query, cb) ->
                    cb.equal(root.get("status"), criteria.status()));
        }

        if (criteria.decision() != null) {
            filters.add((root, query, cb) ->
                    cb.equal(root.get("decision"), criteria.decision()));
        }

        if (criteria.minAmount() != null) {
            filters.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
        }

        if (criteria.maxAmount() != null) {
            filters.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
        }

        // risk_score is nullable: NULL means "never scored". Rows with NULL are
        // dropped by these comparisons because NULL >= 0 evaluates to UNKNOWN,
        // and WHERE keeps only TRUE. That is the behaviour we want, but it comes
        // from SQL's three-valued logic, not from anything written here.
        if (criteria.minRiskScore() != null) {
            filters.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("riskScore"), criteria.minRiskScore()));
        }

        if (criteria.maxRiskScore() != null) {
            filters.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("riskScore"), criteria.maxRiskScore()));
        }

        if (criteria.occurredFrom() != null) {
            filters.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("occurredAt"), criteria.occurredFrom()));
        }

        if (criteria.occurredTo() != null) {
            filters.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("occurredAt"), criteria.occurredTo()));
        }

        // An empty filter list is a valid request meaning "return everything".
        // Returning an always-true Specification keeps the return type honest:
        // this method never hands back null, so no caller has to check for it.
        return filters.stream()
                .reduce(Specification::and)
                .orElseGet(TransactionSpecifications::matchAll);
    }

    private static Specification<Transaction> matchAll() {
        return (root, query, cb) -> cb.conjunction();
    }
}
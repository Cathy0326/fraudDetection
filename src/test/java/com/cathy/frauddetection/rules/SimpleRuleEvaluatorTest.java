package com.cathy.frauddetection.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cathy.frauddetection.transaction.Transaction;
import com.cathy.frauddetection.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Package-private on purpose: SimpleRuleEvaluator and its constructor are
// package-private, so this test can only live in the same package.
class SimpleRuleEvaluatorTest {

    // velocityLimit is passed directly, not read from application.yml.
    // The test's expectations should not shift if someone tunes the
    // production config value.
    private static final long VELOCITY_LIMIT = 3;

    private final SimpleRuleEvaluator evaluator = new SimpleRuleEvaluator(VELOCITY_LIMIT);

    // Builds a transaction where only amount and country vary between tests;
    // every other field is a fixed, uninteresting default. Reading a test
    // then shows only the value that test actually cares about.
    private Transaction transactionOf(BigDecimal amount, String country) {
        return new Transaction(
                "TX-TEST", "ACC-TEST", amount, "EUR", country, "TRANSFER",
                Instant.now(), TransactionStatus.PENDING);
    }

    @Test
    void noRulesMatchForOrdinaryTransaction() {
        Transaction transaction = transactionOf(new BigDecimal("500.00"), "IE");

        RuleResult result = evaluator.evaluate(transaction, 0);

        assertEquals(0, result.riskScore());
        assertTrue(result.hits().isEmpty());
    }

    // compareTo is strictly greater than: the threshold value itself must NOT
    // trigger the rule. Testing only 500 (clearly under) and 50000 (clearly
    // over) would never catch ">" silently becoming ">=".
    @ParameterizedTest
    @CsvSource({
            "10000.00, false",   // exactly at threshold: must not trigger
            "10000.01, true"     // one cent over: must trigger
    })
    void amountThresholdIsStrictlyGreaterThan(String amount, boolean shouldTrigger) {
        Transaction transaction = transactionOf(new BigDecimal(amount), "IE");

        RuleResult result = evaluator.evaluate(transaction, 0);

        assertEquals(shouldTrigger ? 40 : 0, result.riskScore());
    }

    @ParameterizedTest
    @CsvSource({"IR", "KP", "SY", "CU"})
    void highRiskCountryTriggersRule(String country) {
        Transaction transaction = transactionOf(new BigDecimal("500.00"), country);

        RuleResult result = evaluator.evaluate(transaction, 0);

        assertEquals(40, result.riskScore());
        assertEquals("HIGH_RISK_COUNTRY", result.hits().get(0).ruleCode());
    }

    @Test
    void ordinaryCountryDoesNotTriggerRule() {
        Transaction transaction = transactionOf(new BigDecimal("500.00"), "IE");

        RuleResult result = evaluator.evaluate(transaction, 0);

        assertTrue(result.hits().isEmpty());
    }

    // count > limit: the limit-th transaction is still allowed. Testing only
    // a value well past the limit would miss ">" silently becoming ">=", which
    // would fire one transaction earlier than the design intends.
    @ParameterizedTest
    @CsvSource({
            "3, false",   // count equals limit: still within the allowed window
            "4, true"     // first transaction past the limit
    })
    void velocityLimitIsStrictlyGreaterThan(long velocityCount, boolean shouldTrigger) {
        Transaction transaction = transactionOf(new BigDecimal("500.00"), "IE");

        RuleResult result = evaluator.evaluate(transaction, velocityCount);

        assertEquals(shouldTrigger ? 40 : 0, result.riskScore());
    }

    // The three rules must accumulate independently, not short-circuit or
    // overwrite each other. This is the scenario Phase 4 verified by hand
    // (0/0/0/100 across four manual transactions); this test locks it in as
    // an automated regression instead of a one-time manual observation.
    @Test
    void allThreeRulesAccumulateWhenAllMatch() {
        Transaction transaction = transactionOf(new BigDecimal("50000.00"), "IR");

        RuleResult result = evaluator.evaluate(transaction, 4);

        assertEquals(100, result.riskScore()); // 120 unclamped, clamped to 100
        assertEquals(3, result.hits().size());
    }
}
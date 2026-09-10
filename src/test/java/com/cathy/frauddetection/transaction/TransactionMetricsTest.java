package com.cathy.frauddetection.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.cathy.frauddetection.rules.RuleHit;
import com.cathy.frauddetection.rules.RuleResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the metric names and tag keys.
 *
 * <p>These are plain strings the compiler cannot check, and once Prometheus has
 * scraped them they are an external contract: renaming breaks historical series
 * that do not live in this repository. Names are asserted in Micrometer form
 * (dots); the translation to Prometheus form (underscores plus a _total suffix)
 * is the library's guarantee, not ours.
 *
 * <p>No Spring context: the constructor takes a registry and a string, so this
 * runs in milliseconds. That Spring actually injects a MeterRegistry is covered
 * by the running endpoint, not here.
 */
class TransactionMetricsTest {

    private MeterRegistry registry;
    private TransactionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TransactionMetrics(registry, "simple");
    }

    // The one test that cannot be replaced by any other: nothing is recorded,
    // so a counter that shows up here can only have been pre-registered. Had we
    // incremented APPROVE first, lazy registration would look identical.
    @Test
    void allDecisionCountersExistBeforeAnyTransactionIsProcessed() {
        for (Decision decision : Decision.values()) {
            assertThat(registry.find("fraud.decisions").tag("decision", decision.name()).counter())
                    .as("counter for %s must exist so 'never happened' differs from 'never instrumented'", decision)
                    .isNotNull()
                    .extracting(c -> c.count())
                    .isEqualTo(0.0);
        }
    }

    @Test
    void countDecisionIncrementsOnlyTheMatchingBand() {
        metrics.countDecision(Decision.BLOCK);

        assertThat(registry.get("fraud.decisions").tag("decision", "BLOCK").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("fraud.decisions").tag("decision", "APPROVE").counter().count()).isEqualTo(0.0);
    }

    // The engine tag comes from a property. Its value must match whichever
    // evaluator Spring wired, and nothing enforces that link — so at minimum
    // pin that the tag key is "engine" and the value is carried through.
    @Test
    void evaluationTimerCarriesTheEngineTag() {
        metrics.timeEvaluation(() -> RuleResult.from(List.of()));

        assertThat(registry.get("fraud.evaluation.duration").tag("engine", "simple").timer().count())
                .isEqualTo(1L);
    }

    // timeEvaluation is a pass-through: it must return what the supplier
    // returned, not merely time it. Timing a call and using its result are two
    // different failure modes.
    @Test
    void timeEvaluationReturnsTheSuppliersResult() {
        RuleResult expected = RuleResult.from(List.of(new RuleHit("AMOUNT_THRESHOLD", 40)));

        RuleResult actual = metrics.timeEvaluation(() -> expected);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void ruleHitsAreCountedPerRuleCode() {
        metrics.countRuleHits(List.of(
                new RuleHit("AMOUNT_THRESHOLD", 40),
                new RuleHit("HIGH_RISK_COUNTRY", 40)));

        assertThat(registry.get("fraud.rule.hits").tag("rule", "AMOUNT_THRESHOLD").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("fraud.rule.hits").tag("rule", "HIGH_RISK_COUNTRY").counter().count()).isEqualTo(1.0);
    }

    // Rule codes are not pre-registered because Drools takes them from the DRL
    // at runtime — the same reason RuleHit.ruleCode is a String, not an enum.
    // This pins the cost of that choice: an unfired rule is absent, not zero.
    @Test
    void unfiredRuleHasNoCounterAtAll() {
        assertThat(registry.find("fraud.rule.hits").tag("rule", "VELOCITY_LIMIT").counter()).isNull();
    }

    @Test
    void emptyHitListRecordsNothing() {
        metrics.countRuleHits(List.of());

        assertThat(registry.find("fraud.rule.hits").counters()).isEmpty();
    }
}
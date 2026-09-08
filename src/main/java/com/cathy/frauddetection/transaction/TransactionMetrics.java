package com.cathy.frauddetection.transaction;

import com.cathy.frauddetection.rules.RuleHit;
import com.cathy.frauddetection.rules.RuleResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Owns every metric emitted from the transaction pipeline.
 *
 * <p>Instrumentation lives here rather than inside the two RuleEvaluator
 * implementations: a metric name is an invariant that must hold exactly once,
 * and two implementations maintaining it separately can drift with nothing
 * to detect the drift.
 */
@Component
class TransactionMetrics {

    // Metric names are an external contract once Prometheus has scraped them:
    // renaming breaks historical series that do not live in this repository.
    private static final String DECISIONS = "fraud.decisions";
    private static final String EVALUATION_DURATION = "fraud.evaluation.duration";
    private static final String RULE_HITS = "fraud.rule.hits";

    private static final String TAG_DECISION = "decision";
    private static final String TAG_ENGINE = "engine";
    private static final String TAG_RULE = "rule";

    private final MeterRegistry registry;
    private final Timer evaluationTimer;
    private final Map<Decision, Counter> decisionCounters = new EnumMap<>(Decision.class);

    // Default must stay "simple" to match matchIfMissing=true on
    // SimpleRuleEvaluator. Known gap: a mis-cased value such as "DROOLS" fails
    // the @ConditionalOnProperty match but is still recorded as the tag here.
    TransactionMetrics(MeterRegistry registry,
                       @Value("${fraud.rules.engine:simple}") String engine) {
        this.registry = registry;

        this.evaluationTimer = Timer.builder(EVALUATION_DURATION)
                .description("Time spent inside RuleEvaluator.evaluate")
                .tag(TAG_ENGINE, engine)
                // Client-side percentiles. Correct for one instance; across
                // instances only server-side histograms can be aggregated.
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Registered up front so an unused band still reports 0. A counter that
        // has never been incremented is absent from the endpoint, and "never
        // happened" would then look identical to "never instrumented".
        for (Decision decision : Decision.values()) {
            decisionCounters.put(decision, Counter.builder(DECISIONS)
                    .description("Transactions per decision band")
                    .tag(TAG_DECISION, decision.name())
                    .register(registry));
        }
    }

    /**
     * Times the evaluation and returns its result.
     * Wrapping the call rather than exposing the Timer means a caller cannot
     * start a measurement and forget to stop it.
     */
    RuleResult timeEvaluation(Supplier<RuleResult> evaluation) {
        return evaluationTimer.record(evaluation);
    }

    void countDecision(Decision decision) {
        decisionCounters.get(decision).increment();
    }

    /**
     * Rule codes cannot be pre-registered: Drools takes them from the DRL at
     * runtime, which is the same reason RuleHit.ruleCode is a String and not an
     * enum. Cost, stated honestly: a rule that never fires never appears here.
     */
    void countRuleHits(List<RuleHit> hits) {
        for (RuleHit hit : hits) {
            // registry.counter caches by name plus tags, so this is a lookup
            // after the first call, not a re-registration.
            registry.counter(RULE_HITS, TAG_RULE, hit.ruleCode()).increment();
        }
    }
}

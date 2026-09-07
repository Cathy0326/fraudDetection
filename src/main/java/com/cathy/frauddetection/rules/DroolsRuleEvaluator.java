package com.cathy.frauddetection.rules;

import com.cathy.frauddetection.transaction.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// No matchIfMissing: an absent property must fall through to the simple engine.
@ConditionalOnProperty(name = "fraud.rules.engine", havingValue = "drools")
@Component
class DroolsRuleEvaluator implements RuleEvaluator {

    // Must match the global declared in fraud-rules.drl character for character.
    private static final String GLOBAL_HITS = "hits";

    private final KieContainer kieContainer;

    DroolsRuleEvaluator(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    @Override
    public RuleResult evaluate(Transaction transaction) {
        List<RuleHit> hits = new ArrayList<>();

        // A new session per evaluation. Working memory is stateful and not
        // thread safe: a reused session would still hold the previous
        // transaction, and would match it again on the next call.
        KieSession session = kieContainer.newKieSession();
        try {
            session.setGlobal(GLOBAL_HITS, hits);
            session.insert(transaction);
            session.fireAllRules();
        } finally {
            // finally, not after fireAllRules: a rule consequence can throw,
            // and a skipped dispose leaks working memory. KieSession is not
            // AutoCloseable, so try-with-resources is not available.
            session.dispose();
        }

        // Same three-way split as SimpleRuleEvaluator: the DRL decides what
        // matched, RuleResult.from decides the score.
        return RuleResult.from(hits);
    }
}
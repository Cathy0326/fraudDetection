package com.cathy.frauddetection.rules;

import com.cathy.frauddetection.transaction.Transaction;

/**
 * Strategy for scoring a transaction against fraud rules.
 *
 * <p>Two implementations are planned: SimpleRuleEvaluator (Phase 2, rules
 * hand-written in Java) and DroolsRuleEvaluator (Phase 3, rules in DRL).
 * The interface exists so the consumer does not change when the second
 * one arrives.
 */
public interface RuleEvaluator {

    /**
     * Scores a transaction and reports which rules matched.
     *
     * <p>Implementations report hits; RuleResult.from computes and clamps
     * the score, and Decision.fromRiskScore derives the decision. Neither
     * is this method's job, so two implementations cannot disagree about
     * either mapping.
     *
     * @param transaction the transaction to score, never null
     * @return the score and the rules that matched, never null
     */
    RuleResult evaluate(Transaction transaction);
}
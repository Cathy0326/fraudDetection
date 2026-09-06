package com.cathy.frauddetection.rules;

import java.util.List;

/**
 * Outcome of evaluating one transaction: the rules that matched and the
 * resulting risk score.
 *
 * <p>The score is computed here rather than by each evaluator, so that
 * SimpleRuleEvaluator and DroolsRuleEvaluator cannot produce different
 * scores from the same set of hits.
 */
public record RuleResult(int riskScore, List<RuleHit> hits) {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    public RuleResult {
        // List.copyOf makes the record genuinely immutable and rejects nulls.
        // Without it the caller keeps a reference to a mutable list.
        hits = List.copyOf(hits);
        if (riskScore < MIN_SCORE || riskScore > MAX_SCORE) {
            throw new IllegalArgumentException("Risk score out of range: " + riskScore);
        }
    }

    /**
     * Sums the weights of the matched rules and clamps to 0-100.
     * No hits means a score of zero, which maps to APPROVE.
     */
    public static RuleResult from(List<RuleHit> hits) {
        int total = hits.stream().mapToInt(RuleHit::weight).sum();
        return new RuleResult(Math.clamp(total, MIN_SCORE, MAX_SCORE), hits);
    }
}
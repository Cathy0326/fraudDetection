package com.cathy.frauddetection.transaction;

/**
 * Outcome of rule evaluation. Distinct from TransactionStatus: status is
 * lifecycle (has it been processed), decision is verdict (what did we decide).
 */
public enum Decision {

    APPROVE,
    REVIEW,
    BLOCK;

    // Lower bounds, inclusive. Kept here rather than in an evaluator so that
    // Phase 3's DroolsRuleEvaluator maps scores the same way without copying
    // the thresholds.
    private static final int REVIEW_THRESHOLD = 40;
    private static final int BLOCK_THRESHOLD = 80;

    /**
     * Maps a clamped risk score to a decision.
     *
     * @param riskScore score in the range 0-100, as enforced by
     *                  ck_transactions_risk_score_range
     * @throws IllegalArgumentException if the score is outside 0-100
     */
    public static Decision fromRiskScore(int riskScore) {
        // Fail loudly rather than silently bucketing an impossible score.
        // The database rejects out-of-range values too; this catches them
        // before the round-trip and points at the evaluator that produced it.
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("Risk score out of range: " + riskScore);
        }
        // Chained comparisons: the bands cannot overlap or leave a gap.
        if (riskScore < REVIEW_THRESHOLD) {
            return APPROVE;
        }
        if (riskScore < BLOCK_THRESHOLD) {
            return REVIEW;
        }
        return BLOCK;
    }
}
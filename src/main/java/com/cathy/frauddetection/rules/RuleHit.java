package com.cathy.frauddetection.rules;

/**
 * One rule that matched during evaluation.
 *
 * <p>ruleCode is a String rather than an enum on purpose: Phase 3 generates
 * Drools rules from database rows at runtime, so the set of codes is not
 * known at compile time. An enum would force a redeploy per new rule.
 */
public record RuleHit(String ruleCode, int weight) {

    public RuleHit {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        // Rules only add risk in this system; a negative weight would mean
        // a rule that vouches for a transaction, which is out of scope.
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive: " + weight);
        }
    }
}
package com.cathy.frauddetection.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleResultTest {

    @Test
    void fromSumsWeightsOfAllHits() {
        List<RuleHit> hits = List.of(
                new RuleHit("AMOUNT_THRESHOLD", 40),
                new RuleHit("HIGH_RISK_COUNTRY", 40));

        RuleResult result = RuleResult.from(hits);

        assertEquals(80, result.riskScore());
    }

    @Test
    void fromReturnsZeroForNoHits() {
        RuleResult result = RuleResult.from(List.of());

        assertEquals(0, result.riskScore());
    }

    // This is the one score that was never reachable before Phase 4's third
    // rule existed: two rules max out at 80, which is still a valid score.
    // Only three hits (120 unclamped) forces Math.clamp to actually do
    // something instead of being a no-op ceiling that's never approached.
    @Test
    void fromClampsToMaxWhenAllThreeRulesHit() {
        List<RuleHit> hits = List.of(
                new RuleHit("AMOUNT_THRESHOLD", 40),
                new RuleHit("HIGH_RISK_COUNTRY", 40),
                new RuleHit("VELOCITY_LIMIT", 40));

        RuleResult result = RuleResult.from(hits);

        assertEquals(100, result.riskScore());
    }

    @Test
    void constructorRejectsScoreAboveMax() {
        assertThrows(IllegalArgumentException.class,
                () -> new RuleResult(101, List.of()));
    }

    @Test
    void constructorRejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new RuleResult(-1, List.of()));
    }

    // List.copyOf takes a snapshot at construction time. Mutating the
    // original list afterward must not be visible through the record — if it
    // were, the record's "immutability" would be an illusion tied to caller
    // discipline instead of an actual guarantee.
    @Test
    void hitsIsDefensivelyCopiedFromCallerList() {
        List<RuleHit> mutableHits = new ArrayList<>();
        mutableHits.add(new RuleHit("AMOUNT_THRESHOLD", 40));

        RuleResult result = new RuleResult(40, mutableHits);
        mutableHits.add(new RuleHit("HIGH_RISK_COUNTRY", 40));

        assertEquals(1, result.hits().size());
    }

    // The other half of the same guarantee: the list handed back to the
    // caller must itself resist modification, not just the copy step.
    @Test
    void hitsListIsUnmodifiable() {
        RuleResult result = new RuleResult(40, List.of(new RuleHit("AMOUNT_THRESHOLD", 40)));

        assertThrows(UnsupportedOperationException.class,
                () -> result.hits().add(new RuleHit("HIGH_RISK_COUNTRY", 40)));
    }
}
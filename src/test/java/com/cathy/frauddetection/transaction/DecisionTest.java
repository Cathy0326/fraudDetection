package com.cathy.frauddetection.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DecisionTest {

    // Six rows, three bands. Each band gets a low value and its exact
    // boundary, because "< 40" and "<= 40" are easy to swap by accident and
    // only the boundary value itself would catch it.
    @ParameterizedTest
    @CsvSource({
            "0, APPROVE",
            "39, APPROVE",
            "40, REVIEW",   // REVIEW_THRESHOLD: first score that is REVIEW, not APPROVE
            "79, REVIEW",
            "80, BLOCK",    // BLOCK_THRESHOLD: first score that is BLOCK, not REVIEW
            "100, BLOCK"
    })
    void mapsRiskScoreToCorrectBand(int riskScore, Decision expected) {
        assertEquals(expected, Decision.fromRiskScore(riskScore));
    }

    @ParameterizedTest
    @CsvSource({"-1", "101"})
    void rejectsScoreOutsideValidRange(int invalidScore) {
        assertThrows(IllegalArgumentException.class, () -> Decision.fromRiskScore(invalidScore));
    }
}
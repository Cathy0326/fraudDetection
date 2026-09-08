package com.cathy.frauddetection.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleHitTest {

    @Test
    void constructorAcceptsValidCodeAndPositiveWeight() {
        RuleHit hit = assertDoesNotThrow(() -> new RuleHit("AMOUNT_THRESHOLD", 40));
        assertEquals("AMOUNT_THRESHOLD", hit.ruleCode());
        assertEquals(40, hit.weight());
    }

    // null and "" and "   " all fail the same isBlank() check, but they are
    // three different ways a caller could get this wrong. Testing only one
    // would leave the other two unverified.
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void constructorRejectsBlankRuleCode(String blankCode) {
        assertThrows(IllegalArgumentException.class, () -> new RuleHit(blankCode, 40));
    }

    @Test
    void constructorRejectsNullRuleCode() {
        assertThrows(IllegalArgumentException.class, () -> new RuleHit(null, 40));
    }

    // 0 and -1 are both invalid, for the same reason ("must be positive"),
    // but they exercise different branches of a <= 0 check: the equality
    // case and the strictly-negative case.
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructorRejectsNonPositiveWeight(int invalidWeight) {
        assertThrows(IllegalArgumentException.class,
                () -> new RuleHit("AMOUNT_THRESHOLD", invalidWeight));
    }
}
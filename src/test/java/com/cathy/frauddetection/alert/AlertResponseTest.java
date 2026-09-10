package com.cathy.frauddetection.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cathy.frauddetection.transaction.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

// Pure JUnit: the mapping is a static function of one entity.
class AlertResponseTest {

    // The API exposes the structure; the comma is a storage detail the client
    // must never have to know about.
    @Test
    void splitsJoinedRuleCodesIntoAList() {
        Alert alert = new Alert(1L, 80, Decision.BLOCK,
                "AMOUNT_THRESHOLD,HIGH_RISK_COUNTRY,VELOCITY_LIMIT");

        AlertResponse response = AlertResponse.from(alert);

        assertEquals(
                List.of("AMOUNT_THRESHOLD", "HIGH_RISK_COUNTRY", "VELOCITY_LIMIT"),
                response.triggeredRules());
    }

    @Test
    void singleRuleCodeBecomesASingleElementList() {
        Alert alert = new Alert(1L, 40, Decision.REVIEW, "AMOUNT_THRESHOLD");

        assertEquals(List.of("AMOUNT_THRESHOLD"),
                AlertResponse.from(alert).triggeredRules());
    }

    // The reason splitRuleCodes has a guard at all: "".split(",") yields [""],
    // a one-element array holding an empty string — not an empty array. Without
    // the guard the API would render one blank rule tag and never fail loudly.
    @Test
    void blankRuleCodesBecomeAnEmptyList() {
        Alert alert = new Alert(1L, 40, Decision.REVIEW, "");

        assertTrue(AlertResponse.from(alert).triggeredRules().isEmpty());
    }

    // An unreviewed alert keeps the key with a null value: omitting it would
    // leave the client unable to tell "not reviewed" from "no such field".
    @Test
    void unreviewedAlertHasNullTimestamp() {
        Alert alert = new Alert(1L, 80, Decision.BLOCK, "AMOUNT_THRESHOLD");

        AlertResponse response = AlertResponse.from(alert);

        assertEquals(AlertStatus.OPEN, response.status());
        assertNull(response.reviewedAt());
    }

    @Test
    void reviewedAlertCarriesTheOutcomeAndTimestamp() {
        Alert alert = new Alert(1L, 80, Decision.BLOCK, "AMOUNT_THRESHOLD");
        alert.review(AlertStatus.FALSE_POSITIVE);

        AlertResponse response = AlertResponse.from(alert);

        assertEquals(AlertStatus.FALSE_POSITIVE, response.status());
        org.junit.jupiter.api.Assertions.assertNotNull(response.reviewedAt());
    }

    // Every snapshot field must survive the mapping. A field silently dropped
    // here is invisible in the entity tests and only shows up in the UI.
    @Test
    void copiesEverySnapshotField() {
        Alert alert = new Alert(42L, 80, Decision.BLOCK, "AMOUNT_THRESHOLD");

        AlertResponse response = AlertResponse.from(alert);

        assertEquals(42L, response.transactionId());
        assertEquals((short) 80, response.riskScore());
        assertEquals(Decision.BLOCK, response.decision());
        // id and createdAt are database-owned, so they are null in a pure test.
        assertNull(response.id());
        assertNull(response.createdAt());
    }
}
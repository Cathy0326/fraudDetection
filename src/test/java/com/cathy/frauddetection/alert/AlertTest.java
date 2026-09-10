package com.cathy.frauddetection.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cathy.frauddetection.transaction.Decision;
import java.time.Instant;
import org.junit.jupiter.api.Test;

// Pure JUnit: this entity has no collaborators. createdAt stays null throughout
// because the column is insertable = false — the database owns that value.
class AlertTest {

    private static Alert newAlert() {
        return new Alert(1L, 80, Decision.BLOCK, "AMOUNT_THRESHOLD,HIGH_RISK_COUNTRY");
    }

    // status is not a constructor parameter, so "born already reviewed" is not
    // a state a caller can build. The database CHECK is the second line, but it
    // only fires at flush, pointing at the transaction synchronizer.
    @Test
    void newAlertIsOpenAndUnreviewed() {
        Alert alert = newAlert();

        assertEquals(AlertStatus.OPEN, alert.getStatus());
        assertNull(alert.getReviewedAt());
    }

    // Constructor takes int, field is Short: the narrowing happens once, inside.
    @Test
    void constructorKeepsTheSnapshotValues() {
        Alert alert = newAlert();

        assertEquals(1L, alert.getTransactionId());
        assertEquals((short) 80, alert.getRiskScore());
        assertEquals(Decision.BLOCK, alert.getDecision());
        assertEquals("AMOUNT_THRESHOLD,HIGH_RISK_COUNTRY", alert.getTriggeredRules());
    }

    // Both fields must move together — the Java side of ck_alerts_reviewed_at.
    // Asserting only the status would pass an implementation that forgets the
    // timestamp and then fails at flush time instead.
    @Test
    void confirmingSetsStatusAndTimestampTogether() {
        Alert alert = newAlert();
        Instant before = Instant.now();

        alert.review(AlertStatus.CONFIRMED);

        assertEquals(AlertStatus.CONFIRMED, alert.getStatus());
        assertNotNull(alert.getReviewedAt());
        assertTrue(!alert.getReviewedAt().isBefore(before));
    }

    // The other terminal state. One test per outcome, because a broken switch
    // could handle one and not the other.
    @Test
    void markingFalsePositiveSetsStatusAndTimestampTogether() {
        Alert alert = newAlert();

        alert.review(AlertStatus.FALSE_POSITIVE);

        assertEquals(AlertStatus.FALSE_POSITIVE, alert.getStatus());
        assertNotNull(alert.getReviewedAt());
    }

    // OPEN is a valid AlertStatus but not a review outcome: reviewing into OPEN
    // would write OPEN together with a timestamp, which the CHECK rejects.
    @Test
    void reviewingIntoOpenIsRejected() {
        Alert alert = newAlert();

        assertThrows(IllegalArgumentException.class,
                () -> alert.review(AlertStatus.OPEN));
    }

    @Test
    void reviewingWithNullIsRejected() {
        Alert alert = newAlert();

        assertThrows(IllegalArgumentException.class, () -> alert.review(null));
    }

    // A rejected review must leave the entity untouched. Without this, a check
    // placed after the assignments would still pass every test above.
    @Test
    void rejectedReviewLeavesTheAlertUnchanged() {
        Alert alert = newAlert();

        assertThrows(IllegalArgumentException.class,
                () -> alert.review(AlertStatus.OPEN));

        assertEquals(AlertStatus.OPEN, alert.getStatus());
        assertNull(alert.getReviewedAt());
    }
}
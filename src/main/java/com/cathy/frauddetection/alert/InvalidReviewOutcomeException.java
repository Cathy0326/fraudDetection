package com.cathy.frauddetection.alert;

/**
 * Thrown when a review request names a status that is not a review outcome.
 * OPEN is a valid AlertStatus but not a valid thing to review an alert into.
 */
public class InvalidReviewOutcomeException extends RuntimeException {

    public InvalidReviewOutcomeException(AlertStatus outcome) {
        super("Not a review outcome: " + outcome);
    }
}
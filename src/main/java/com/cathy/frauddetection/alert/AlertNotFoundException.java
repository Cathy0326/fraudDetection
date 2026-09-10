package com.cathy.frauddetection.alert;

/**
 * Thrown when a review targets an alert id that does not exist.
 * Own type, not NoSuchElementException: the JDK throws that one too, and then
 * "client asked for a missing id" and "our code misused an Optional" look alike.
 */
public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(Long alertId) {
        super("Alert not found: " + alertId);
    }
}
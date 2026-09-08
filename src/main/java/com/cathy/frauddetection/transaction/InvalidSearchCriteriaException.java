package com.cathy.frauddetection.transaction;

/**
 * Thrown when search input cannot be satisfied by any row — a reversed range,
 * or a sort field outside the whitelist.
 *
 * A dedicated type, not IllegalArgumentException: the handler needs to answer
 * "is this the client's fault or ours?", and IllegalArgumentException is also
 * thrown by the JDK and by libraries, so it cannot draw that line.
 *
 * The message is echoed back to the client, so callers must only put values the
 * client already sent into it — never internal state.
 */
public class InvalidSearchCriteriaException extends RuntimeException {

    public InvalidSearchCriteriaException(String message) {
        super(message);
    }
}
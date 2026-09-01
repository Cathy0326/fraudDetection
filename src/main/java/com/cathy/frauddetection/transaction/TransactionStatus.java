package com.cathy.frauddetection.transaction;

/**
 * Pipeline state, not business outcome.
 * Values must stay in sync with ck_transactions_status in V1.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSED,
    FAILED
}
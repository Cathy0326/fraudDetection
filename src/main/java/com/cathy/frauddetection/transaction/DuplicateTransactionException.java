package com.cathy.frauddetection.transaction;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String transactionRef) {
        super("Transaction already exists: " + transactionRef);
    }
}
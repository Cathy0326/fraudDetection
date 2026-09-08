package com.cathy.frauddetection.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Package-private: nothing outside this package can reach the database directly.
interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    // Derived query: Spring Data parses the method name into
    // SELECT EXISTS(... WHERE transaction_ref = ?) at startup.
    boolean existsByTransactionRef(String transactionRef);
}
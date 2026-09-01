package com.cathy.frauddetection.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

// Package-private: nothing outside this package can reach the database directly.
interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Derived query: Spring Data parses the method name into
    // SELECT EXISTS(... WHERE transaction_ref = ?) at startup.
    boolean existsByTransactionRef(String transactionRef);
}
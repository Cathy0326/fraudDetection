package com.cathy.frauddetection.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Package-private: only AlertService should reach the database.
interface AlertRepository extends JpaRepository<Alert, Long> {

    // Sort comes from the caller (created_at DESC + id tie-breaker, see V4 index).
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    // Guard before insert. The unique constraint would also catch a duplicate,
    // but by throwing — and a throwing consumer retries forever (no DLQ).
    boolean existsByTransactionId(Long transactionId);
}
package com.cathy.frauddetection.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, length = 64)
    private String transactionRef;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    // BigDecimal, never Double: NUMERIC(19,4) must not round-trip through binary float.
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "destination_country", nullable = false, length = 2)
    private String destinationCountry;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    // Instant, never LocalDateTime: LocalDateTime silently drops the offset.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // STRING is mandatory. The default is ORDINAL, which would write an integer
    // and be rejected by ck_transactions_status.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    // insertable = false: the database DEFAULT now() owns this value.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // Required by Hibernate for proxy creation. Not for application code.
    protected Transaction() {
    }

    public Transaction(String transactionRef, String accountId, BigDecimal amount,
                       String currency, String destinationCountry, String transactionType,
                       Instant occurredAt, TransactionStatus status) {
        this.transactionRef = transactionRef;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.destinationCountry = destinationCountry;
        this.transactionType = transactionType;
        this.occurredAt = occurredAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
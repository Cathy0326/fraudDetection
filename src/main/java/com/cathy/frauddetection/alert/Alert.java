package com.cathy.frauddetection.alert;

import com.cathy.frauddetection.transaction.Decision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain FK value, not @ManyToOne: no lazy loading with open-in-view disabled.
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    // Snapshots. Column is SMALLINT, so Short (ddl-auto: validate compares JDBC type codes).
    @Column(name = "risk_score", nullable = false, updatable = false)
    private Short riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 20)
    private Decision decision;

    @Column(name = "triggered_rules", nullable = false, updatable = false, length = 255)
    private String triggeredRules;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    // DB DEFAULT now() owns this value.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // Null until reviewed. Only review() writes it, together with status.
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected Alert() {
        // for Hibernate
    }

    public Alert(Long transactionId, int riskScore, Decision decision, String triggeredRules) {
        this.transactionId = Objects.requireNonNull(transactionId);
        this.riskScore = (short) riskScore;
        this.decision = Objects.requireNonNull(decision);
        this.triggeredRules = Objects.requireNonNull(triggeredRules);
        // Not a parameter: an alert is always born OPEN and unreviewed.
        this.status = AlertStatus.OPEN;
    }

    /**
     * Records a review outcome. Writes both fields at once so the
     * ck_alerts_reviewed_at invariant cannot be broken by a caller.
     */
    public void review(AlertStatus outcome) {
        if (outcome == null || outcome == AlertStatus.OPEN) {
            throw new IllegalArgumentException("Not a review outcome: " + outcome);
        }
        this.status = outcome;
        this.reviewedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Short getRiskScore() {
        return riskScore;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getTriggeredRules() {
        return triggeredRules;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
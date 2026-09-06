-- Adds the two columns produced by rule evaluation (Phase 2).
-- Both are nullable on purpose: a PENDING row has not been scored yet,
-- and 0 is a valid score meaning "clean", so a DEFAULT would erase the
-- difference between "scored clean" and "never scored".

ALTER TABLE transactions
    ADD COLUMN risk_score SMALLINT;

ALTER TABLE transactions
    ADD COLUMN decision VARCHAR(20);

-- Range enforced in the database as well as in the evaluator: Phase 3 adds
-- a second evaluator writing to this column, and the invariant must hold
-- regardless of which one wrote the row.
-- The IS NULL branch is required, otherwise this contradicts the nullable
-- column above and every INSERT of a PENDING row would fail.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_risk_score_range
        CHECK (risk_score IS NULL OR risk_score BETWEEN 0 AND 100);

-- Same pattern as ck_transactions_status: VARCHAR + CHECK rather than a
-- native ENUM, so the allowed set can evolve with DROP/ADD CONSTRAINT.
-- Values must stay in sync by hand with the Decision enum in Java.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_decision
        CHECK (decision IS NULL OR decision IN ('APPROVE', 'REVIEW', 'BLOCK'));
-- V1: transactions table.
-- Scope: facts as submitted. Rule-engine output (risk_score, decision) arrives in V2.

CREATE TABLE transactions (
                              id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Business identifier. The unique constraint is the duplicate-delivery guard:
    -- Kafka is at-least-once, so the same event can arrive twice.
                              transaction_ref     VARCHAR(64)     NOT NULL,

                              account_id          VARCHAR(64)     NOT NULL,

    -- NUMERIC, never float: threshold rules compare on exact boundaries.
                              amount              NUMERIC(19, 4)  NOT NULL,

                              currency            VARCHAR(3)      NOT NULL,
                              destination_country VARCHAR(2)      NOT NULL,
                              transaction_type    VARCHAR(20)     NOT NULL,

    -- When the transaction happened (client-supplied). Velocity windows read this one.
                              occurred_at         TIMESTAMPTZ     NOT NULL,

    -- Pipeline state, not business outcome. No DEFAULT: the service must state it.
                              status              VARCHAR(20)     NOT NULL,

    -- When we stored it. DEFAULT so the database clock decides, not app instances.
                              created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

                              CONSTRAINT uq_transactions_ref UNIQUE (transaction_ref),
                              CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
                              CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0)
);
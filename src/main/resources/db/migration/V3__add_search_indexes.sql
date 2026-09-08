-- V3: index for the transaction search endpoint.
--
-- Added now, not in V1: an index is argued by a query, and the query did not
-- exist until this phase.
--
-- Only one index. Every list request runs ORDER BY occurred_at DESC, id DESC
-- unconditionally, so that one is argued. The optional filters are not: an index
-- on decision or status would sit on a three-value column the planner would
-- refuse to use, while still taxing every INSERT on the Kafka path.
--
-- Ascending, even though the sort is descending: a btree scanned backwards
-- yields the exact reverse order. Explicit DESC would only matter for a
-- mixed-direction sort such as occurred_at DESC, id ASC.
--
-- id is in the index so the whole ORDER BY is satisfied by index order and the
-- scan can stop once LIMIT rows are produced.
--
-- transaction_ref already has an index: uq_transactions_ref created one.
CREATE INDEX idx_transactions_occurred_at_id
    ON transactions (occurred_at, id);
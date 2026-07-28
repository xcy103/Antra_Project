-- Idempotency ledger for order-service: one row per handled event.
-- The event id is the PK, so a redelivered PaymentCompleted (Kafka is at-least-once)
-- is recognized and skipped, keeping the PENDING -> PAID transition idempotent.

CREATE TABLE processed_event (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    order_id     BIGINT       NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency ledger for notification-service: one row per handled event.
-- The event id is the PK, so a redelivered event is recognized and skipped.

CREATE TABLE processed_event (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    order_id     BIGINT       NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

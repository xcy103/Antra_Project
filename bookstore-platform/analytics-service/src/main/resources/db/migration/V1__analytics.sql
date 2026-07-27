-- analytics-service schema: idempotency ledger + running aggregates.

CREATE TABLE processed_event (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE metric (
    name         VARCHAR(64)    PRIMARY KEY,
    total_count  BIGINT         NOT NULL DEFAULT 0,
    total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0
);

ALTER TABLE subscriptions
    ADD COLUMN consecutive_failures integer     NOT NULL DEFAULT 0,
    ADD COLUMN circuit_open_until   timestamptz;
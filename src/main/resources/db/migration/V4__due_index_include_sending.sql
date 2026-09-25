DROP INDEX idx_deliveries_due;

CREATE INDEX idx_deliveries_due ON deliveries (next_attempt_at)
    WHERE status IN ('PENDING', 'SENDING', 'FAILED');
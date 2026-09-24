ALTER TABLE events ADD COLUMN idempotency_key varchar(255);
ALTER TABLE events ADD CONSTRAINT uq_events_tenant_idempotency_key UNIQUE (tenant_id, idempotency_key)
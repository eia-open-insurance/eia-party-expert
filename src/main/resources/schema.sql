CREATE SEQUENCE IF NOT EXISTS party_number_seq START 1;

CREATE TABLE IF NOT EXISTS party (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_number VARCHAR(32) NOT NULL,
    party_type VARCHAR(64) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    legal_name VARCHAR(300) NOT NULL,
    legal_name_normalized VARCHAR(300) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sanctions_status VARCHAR(32) NOT NULL,
    confidentiality VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    record_state VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_party_number UNIQUE (tenant_id, party_number)
);

CREATE TABLE IF NOT EXISTS party_identifier (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL,
    identifier_type VARCHAR(32) NOT NULL,
    identifier_value VARCHAR(128) NOT NULL,
    issuing_authority VARCHAR(128),
    effective_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    CONSTRAINT fk_party_identifier_party
        FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT uq_party_identifier
        UNIQUE (tenant_id, party_id, identifier_type, identifier_value)
);

CREATE INDEX IF NOT EXISTS ix_party_identifier_party
    ON party_identifier (tenant_id, party_id);

CREATE TABLE IF NOT EXISTS party_address (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL,
    address_type VARCHAR(32) NOT NULL,
    line1 VARCHAR(128) NOT NULL,
    line2 VARCHAR(128),
    city VARCHAR(64) NOT NULL,
    state_region VARCHAR(64),
    postal_code VARCHAR(16) NOT NULL,
    country CHAR(2) NOT NULL,
    effective_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    CONSTRAINT fk_party_address_party
        FOREIGN KEY (party_id) REFERENCES party (id)
);

CREATE INDEX IF NOT EXISTS ix_party_address_party
    ON party_address (tenant_id, party_id);

-- Transactional outbox (SPEC 7.5.1). The canonical copy lives in
-- eia-platform/libs/src/main/resources/db/outbox_event.sql; keep both in sync.
CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    event_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    partition_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT ck_outbox_event_status
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'DEAD')),
    CONSTRAINT uq_outbox_event_aggregate_version
        UNIQUE (aggregate_type, aggregate_id, aggregate_version, event_type)
);

CREATE INDEX IF NOT EXISTS ix_outbox_event_due
    ON outbox_event (status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS ix_outbox_event_partition
    ON outbox_event (partition_key, event_sequence);

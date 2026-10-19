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

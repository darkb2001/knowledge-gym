CREATE TABLE search_runtime_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('POSTGRES', 'ELASTICSEARCH', 'AUTO')),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID REFERENCES users(id)
);

INSERT INTO search_runtime_settings(id, mode)
VALUES (1, 'POSTGRES');

CREATE TABLE search_runtime_setting_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mode VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by UUID REFERENCES users(id)
);

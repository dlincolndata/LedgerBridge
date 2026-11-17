CREATE TABLE integration_audit_log (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(100) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    target_system VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payload JSONB,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_external_id ON integration_audit_log(external_id);
CREATE INDEX idx_audit_correlation_id ON integration_audit_log(correlation_id);

CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_accounts_normalized_email UNIQUE (normalized_email)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(64),
    replaced_by_session_id UUID,
    reuse_detected_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_sessions (user_id, revoked_at, expires_at);
CREATE INDEX idx_refresh_sessions_family
    ON refresh_sessions (family_id, revoked_at);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_external_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_external_identities_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
);

CREATE INDEX idx_external_identities_user ON external_identities (user_id);

CREATE TABLE security_audit_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    subject_identifier VARCHAR(320),
    session_id UUID,
    correlation_id VARCHAR(128),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    details VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_security_audit_user_time
    ON security_audit_events (user_id, occurred_at);
CREATE INDEX idx_security_audit_type_time
    ON security_audit_events (event_type, occurred_at);

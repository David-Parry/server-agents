-- ============================================================================
-- V1: Core Agent Configuration and Customer Authentication Tables (UUID-based)
-- ============================================================================

-- -----------------------------------------------------------------------------
-- Part 1: LLM Model Table (normalized model references)
-- -----------------------------------------------------------------------------

CREATE TABLE llm_model (
    model VARCHAR(100) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    display_name VARCHAR(200),
    description VARCHAR(500),
    default_tokens_for_new_customers BIGINT DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_llm_model_provider ON llm_model(provider);
CREATE INDEX idx_llm_model_enabled ON llm_model(enabled);

-- -----------------------------------------------------------------------------
-- Part 2: Agent Configuration Tables
-- -----------------------------------------------------------------------------

-- Agent configuration table
CREATE TABLE agent_config (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    agent_type VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system_prompt CLOB NOT NULL,
    model VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    metadata CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_agent_type UNIQUE (agent_type),
    CONSTRAINT fk_agent_config_model FOREIGN KEY (model) REFERENCES llm_model(model)
);

CREATE INDEX idx_agent_config_type ON agent_config(agent_type);
CREATE INDEX idx_agent_config_enabled ON agent_config(enabled);
CREATE INDEX idx_agent_config_model ON agent_config(model);

-- Execution configuration per agent type
CREATE TABLE agent_execution_config (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    agent_config_id UUID NOT NULL,
    max_tokens INT DEFAULT 4096,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    timeout_seconds INT DEFAULT 300,
    retry_attempts INT DEFAULT 2,
    retry_delay_ms INT DEFAULT 1000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_exec_config_agent FOREIGN KEY (agent_config_id) 
        REFERENCES agent_config(id) ON DELETE CASCADE,
    CONSTRAINT uk_exec_config_agent UNIQUE (agent_config_id)
);

CREATE INDEX idx_exec_config_agent ON agent_execution_config(agent_config_id);

-- -----------------------------------------------------------------------------
-- Part 3: Policy Type Table (defines reset periods only - days based)
-- -----------------------------------------------------------------------------

CREATE TABLE policy_type (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    reset_days INT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_policy_type_name UNIQUE (name)
);

CREATE INDEX idx_policy_type_enabled ON policy_type(enabled);

-- -----------------------------------------------------------------------------
-- Part 4: Customer Table (with global notification settings)
-- -----------------------------------------------------------------------------

CREATE TABLE customer (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    customer_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- Global default notification threshold for this customer (used when model-specific is NULL)
    default_min_token_notification_threshold BIGINT DEFAULT 30000,
    -- Webhook URL for low-token notifications (optional)
    notification_webhook_url VARCHAR(500),
    -- Email for low-token notifications (optional)
    notification_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_customer_id UNIQUE (customer_id)
);

CREATE INDEX idx_customer_enabled ON customer(enabled);
CREATE INDEX idx_customer_customer_id ON customer(customer_id);

-- -----------------------------------------------------------------------------
-- Part 5: Customer Model Allowance Table (per-customer, per-model limits)
-- -----------------------------------------------------------------------------

CREATE TABLE customer_model_allowance (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    customer_id UUID NOT NULL,
    model VARCHAR(100) NOT NULL,
    policy_type_id UUID,
    allowed_tokens BIGINT DEFAULT 0,
    tokens_used BIGINT DEFAULT 0,
    tokens_reset_at TIMESTAMP,
    -- Model-specific notification threshold (NULL = use customer's default)
    min_token_notification_threshold BIGINT DEFAULT 30000,
    -- Tracks when the last low-token notification was sent (to avoid duplicate notifications per reset period)
    low_token_notification_sent_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_allowance_customer FOREIGN KEY (customer_id) 
        REFERENCES customer(id) ON DELETE CASCADE,
    CONSTRAINT fk_allowance_model FOREIGN KEY (model) REFERENCES llm_model(model),
    CONSTRAINT fk_allowance_policy_type FOREIGN KEY (policy_type_id) REFERENCES policy_type(id),
    CONSTRAINT uk_customer_model UNIQUE (customer_id, model)
);

CREATE INDEX idx_allowance_customer ON customer_model_allowance(customer_id);
CREATE INDEX idx_allowance_model ON customer_model_allowance(model);
CREATE INDEX idx_allowance_policy_type ON customer_model_allowance(policy_type_id);
CREATE INDEX idx_allowance_enabled ON customer_model_allowance(enabled);

-- -----------------------------------------------------------------------------
-- Part 6: Customer Tokens Table (authentication)
-- -----------------------------------------------------------------------------

CREATE TABLE customer_token (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    customer_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    secret_version VARCHAR(10) NOT NULL DEFAULT 'V1',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    
    CONSTRAINT fk_token_customer FOREIGN KEY (customer_id) 
        REFERENCES customer(id) ON DELETE CASCADE
);

CREATE INDEX idx_token_active ON customer_token(is_active);
CREATE INDEX idx_token_customer ON customer_token(customer_id);
CREATE INDEX idx_token_expires ON customer_token(expires_at);
CREATE INDEX idx_token_secret_version ON customer_token(secret_version);

-- -----------------------------------------------------------------------------
-- Part 7: Security Audit Log Table
-- -----------------------------------------------------------------------------

CREATE TABLE security_audit_log (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    event_category VARCHAR(30) NOT NULL,
    customer_id UUID,
    token_id UUID,
    secret_version VARCHAR(10),
    description VARCHAR(500),
    metadata TEXT,
    actor_type VARCHAR(30) NOT NULL,
    actor_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_audit_event_type CHECK (event_type IN (
        'TOKEN_CREATED', 'TOKEN_REVOKED', 'TOKEN_EXPIRED',
        'TOKEN_VALIDATED', 'TOKEN_VALIDATION_FAILED',
        'SECRET_VERSION_CONFIGURED', 'SECRET_VERSION_DEPRECATED',
        'USAGE_RECORDED', 'USAGE_RESET', 'LIMIT_EXCEEDED',
        'ALLOWANCE_CREATED', 'ALLOWANCE_UPDATED', 'MODEL_ADDED',
        'LOW_TOKEN_WARNING',
        'ADMIN_CUSTOMER_DISABLED', 'ADMIN_CUSTOMER_ENABLED',
        'ADMIN_MODEL_DISABLED', 'ADMIN_MODEL_ENABLED', 'ADMIN_MODEL_UPDATED',
        'ADMIN_POLICY_TYPE_CREATED', 'ADMIN_POLICY_TYPE_UPDATED', 'ADMIN_POLICY_TYPE_DISABLED',
        'ADMIN_ALLOWANCE_BULK_UPDATED', 'ADMIN_USAGE_RESET', 'ADMIN_AUDIT_CLEANUP'
    )),
    CONSTRAINT chk_audit_event_category CHECK (event_category IN (
        'TOKEN', 'SECRET', 'AUTHENTICATION', 'USAGE', 'ALLOWANCE', 'MODEL', 'NOTIFICATION', 'ADMIN'
    )),
    CONSTRAINT chk_audit_actor_type CHECK (actor_type IN (
        'SYSTEM', 'ADMIN', 'CUSTOMER', 'SCHEDULER'
    ))
);

CREATE INDEX idx_audit_event_type ON security_audit_log(event_type);
CREATE INDEX idx_audit_customer_id ON security_audit_log(customer_id);
CREATE INDEX idx_audit_created_at ON security_audit_log(created_at);
CREATE INDEX idx_audit_secret_version ON security_audit_log(secret_version);
CREATE INDEX idx_audit_category_time ON security_audit_log(event_category, created_at);

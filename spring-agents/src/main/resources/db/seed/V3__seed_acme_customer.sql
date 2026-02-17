-- ============================================================================
-- V3: Seed ACME Customers with Auto-Created Model Allowances
-- ============================================================================

-- -----------------------------------------------------------------------------
-- Part 1: ACME Unlimited Customer
-- -----------------------------------------------------------------------------

INSERT INTO customer (id, customer_id, name, enabled, created_at, updated_at)
VALUES (
    '75d75941-1d5b-4db5-a7c5-b8561c5402e0',
    'b0617a36-9a4a-4848-8f8e-8caa478ec8b8',
    'acme',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- ACME Unlimited customer token
INSERT INTO customer_token (id, customer_id, token_hash, secret_version, is_active, expires_at, created_at, revoked_at)
VALUES (
    '36539aa2-223a-4c44-a1b8-d46ae1171e94',
    '75d75941-1d5b-4db5-a7c5-b8561c5402e0',
    'B9C33B44272190BC0333BB61674BEB8DCAFA1AC0FCFE83CAA3BEDD44843CBC1D',
    'V1',
    TRUE,
    NULL,
    '2026-01-09 18:23:35.126543',
    NULL
);

-- ACME Unlimited: Create allowances for ALL enabled models with UNLIMITED policy type
INSERT INTO customer_model_allowance (id, customer_id, model, policy_type_id, allowed_tokens, tokens_used, tokens_reset_at, enabled)
SELECT RANDOM_UUID(), '75d75941-1d5b-4db5-a7c5-b8561c5402e0', m.model, 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', NULL, 0, NULL, TRUE
FROM llm_model m WHERE m.enabled = TRUE;

-- -----------------------------------------------------------------------------
-- Part 2: ACME Monthly Customer
-- -----------------------------------------------------------------------------

INSERT INTO customer (id, customer_id, name, enabled, created_at, updated_at)
VALUES (
    '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d',
    '2094e435-14a0-423d-ba77-69c78026bc4d',
    'acme-monthly',
    TRUE,
    '2026-01-12 11:19:30.977541',
    '2026-01-12 11:19:30.977546'
);

-- ACME Monthly customer token
INSERT INTO customer_token (id, customer_id, token_hash, secret_version, is_active, expires_at, created_at, revoked_at)
VALUES (
    'a2a82959-b755-4e08-94a8-d9f3e4820bee',
    '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d',
    '76750F24AFD85252899E1310D1B6E0C6E2F98C43BC8A6EC84BC370B31BDDDEC7',
    'V1',
    TRUE,
    NULL,
    '2026-01-12 11:19:30.979627',
    NULL
);

-- ACME Monthly: Create allowances for ALL enabled models with MONTHLY policy type
INSERT INTO customer_model_allowance (id, customer_id, model, policy_type_id, allowed_tokens, tokens_used, tokens_reset_at, enabled)
SELECT RANDOM_UUID(), '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', m.model, 'b2c3d4e5-f6a7-8901-bcde-f12345678901', 0, 0, CURRENT_TIMESTAMP, TRUE
FROM llm_model m WHERE m.enabled = TRUE;

-- Update specific model allowances for ACME Monthly
UPDATE customer_model_allowance
SET allowed_tokens = 100000
WHERE customer_id = '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d' AND model = 'claude-sonnet-4-5';

UPDATE customer_model_allowance
SET allowed_tokens = 50000
WHERE customer_id = '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d' AND model = 'claude-opus-4-20250514';

UPDATE customer_model_allowance
SET allowed_tokens = 80000
WHERE customer_id = '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d' AND model = 'gpt-4o';

UPDATE customer_model_allowance
SET allowed_tokens = 80000
WHERE customer_id = '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d' AND model = 'gpt-4-turbo';

-- -----------------------------------------------------------------------------
-- Part 3: Assign Agent Types to ACME Unlimited Customer
-- -----------------------------------------------------------------------------
-- ACME Unlimited gets access to all agent types with default settings

INSERT INTO customer_agent_type (id, customer_id, agent_type, enabled, custom_token_limit, priority, created_at, updated_at)
VALUES
    (RANDOM_UUID(), '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 'ANALYST', TRUE, NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (RANDOM_UUID(), '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 'ENGINEER', TRUE, NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (RANDOM_UUID(), '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 'REVIEWER', TRUE, NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (RANDOM_UUID(), '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 'DIAGNOSTICIAN', TRUE, NULL, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- -----------------------------------------------------------------------------
-- Part 4: Assign Agent Types to ACME Monthly Customer
-- -----------------------------------------------------------------------------
-- ACME Monthly gets access to ANALYST, ENGINEER, and REVIEWER only, with token limits

INSERT INTO customer_agent_type (id, customer_id, agent_type, enabled, custom_token_limit, priority, created_at, updated_at)
VALUES
    (RANDOM_UUID(), '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 'ANALYST', TRUE, 50000, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (RANDOM_UUID(), '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 'ENGINEER', TRUE, 50000, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (RANDOM_UUID(), '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 'REVIEWER', TRUE, 25000, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- -----------------------------------------------------------------------------
-- Part 5: Customer-Specific Execution Configs for ACME Unlimited
-- -----------------------------------------------------------------------------
-- ACME Unlimited gets higher limits and more retries

-- ANALYST config for ACME Unlimited - higher token limit for comprehensive analysis
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 8192, 0.50, 600, 3, 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'ANALYST';

-- ENGINEER config for ACME Unlimited - higher token limit for complex implementations
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 8192, 0.30, 600, 3, 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'ENGINEER';

-- REVIEWER config for ACME Unlimited - lower temperature for consistent reviews
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 6144, 0.20, 450, 3, 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'REVIEWER';

-- DIAGNOSTICIAN config for ACME Unlimited - longer timeout for complex diagnostics
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '75d75941-1d5b-4db5-a7c5-b8561c5402e0', 8192, 0.40, 900, 4, 3000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'DIAGNOSTICIAN';

-- -----------------------------------------------------------------------------
-- Part 6: Customer-Specific Execution Configs for ACME Monthly
-- -----------------------------------------------------------------------------
-- ACME Monthly gets standard limits with shorter timeouts

-- ANALYST config for ACME Monthly - standard limits
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 4096, 0.60, 300, 2, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'ANALYST';

-- ENGINEER config for ACME Monthly - standard limits
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 4096, 0.40, 300, 2, 1000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'ENGINEER';

-- REVIEWER config for ACME Monthly - lower limits
INSERT INTO agent_execution_config (id, agent_config_id, customer_id, max_tokens, temperature, timeout_seconds, retry_attempts, retry_delay_ms, created_at, updated_at)
SELECT RANDOM_UUID(), ac.id, '24b6ca5d-fcbc-4f72-a2ac-b3e4d410ed2d', 2048, 0.30, 180, 1, 500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM agent_config ac WHERE ac.agent_type = 'REVIEWER';

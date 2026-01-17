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

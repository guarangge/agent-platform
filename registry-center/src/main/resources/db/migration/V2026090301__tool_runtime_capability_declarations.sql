-- 冻结 Tool 的运行时安全能力与副作用声明；能力声明本身不代表授权。
ALTER TABLE tools
    ADD COLUMN IF NOT EXISTS runtime_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS side_effect_level VARCHAR(32) NOT NULL DEFAULT 'none';

ALTER TABLE tools
    DROP CONSTRAINT IF EXISTS ck_tools_runtime_capabilities_array,
    ADD CONSTRAINT ck_tools_runtime_capabilities_array
        CHECK (jsonb_typeof(runtime_capabilities) = 'array'),
    DROP CONSTRAINT IF EXISTS ck_tools_side_effect_level,
    ADD CONSTRAINT ck_tools_side_effect_level
        CHECK (side_effect_level IN ('none', 'idempotent', 'compensatable', 'irreversible'));

-- 保留已有 metadata.sideEffectPolicy 的语义，顶层字段成为运行时权威声明。
UPDATE tools
SET side_effect_level = CASE metadata #>> '{sideEffectPolicy,level}'
    WHEN 'idempotent' THEN 'idempotent'
    WHEN 'compensatable' THEN 'compensatable'
    WHEN 'irreversible' THEN 'irreversible'
    ELSE 'none'
END;

-- 平台内置 Tool 按实际可触达边界预置最小能力集合。
UPDATE tools SET runtime_capabilities = '[]'::jsonb
WHERE tool_code IN ('TIME_INFO', 'CALCULATOR') AND is_deleted = FALSE;

UPDATE tools SET runtime_capabilities = '["database.read"]'::jsonb
WHERE tool_code = 'KNOWLEDGE_SEARCH' AND is_deleted = FALSE;

UPDATE tools SET runtime_capabilities = '["network.read"]'::jsonb
WHERE tool_code IN ('HTTP_FETCH', 'WEB_SEARCH') AND is_deleted = FALSE;

UPDATE tools SET runtime_capabilities = '["filesystem.read"]'::jsonb
WHERE tool_code = 'DOCUMENT_PARSE' AND is_deleted = FALSE;

UPDATE tools
SET runtime_capabilities = '["filesystem.read","filesystem.write"]'::jsonb,
    side_effect_level = 'idempotent',
    metadata = jsonb_set(
        metadata,
        '{sideEffectPolicy}',
        '{"level":"idempotent","idempotencyMode":"generated"}'::jsonb,
        TRUE
    )
WHERE tool_code = 'WORKSPACE_FILE' AND is_deleted = FALSE;

UPDATE tools SET runtime_capabilities = '["process.execute","filesystem.read","filesystem.write"]'::jsonb
WHERE tool_code = 'SANDBOX_EXECUTE' AND is_deleted = FALSE;

COMMENT ON COLUMN tools.runtime_capabilities IS
    'Tool 运行时安全能力声明 JSON 数组，仅描述可能触达边界，不代表已授权';
COMMENT ON COLUMN tools.side_effect_level IS
    'Tool 副作用等级：none/idempotent/compensatable/irreversible';

CREATE TABLE tool_capability_packs (
    id BIGSERIAL PRIMARY KEY,
    pack_code VARCHAR(64) NOT NULL,
    pack_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    version VARCHAR(64) NOT NULL DEFAULT '1.0.0',
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    tenant_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(NULLIF(current_setting('app.tenant_id', true), ''), '0')::BIGINT, 1),
    organization_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.organization_id', true), '')::BIGINT, 1),
    workspace_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.workspace_id', true), '')::BIGINT, 1),
    project_id BIGINT NOT NULL DEFAULT COALESCE(NULLIF(current_setting('app.project_id', true), '')::BIGINT, 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_tool_capability_packs_status CHECK (status IN ('draft', 'enabled', 'disabled'))
);

CREATE UNIQUE INDEX uk_tool_capability_packs_scope_code
    ON tool_capability_packs (tenant_id, project_id, pack_code) WHERE is_deleted = FALSE;
CREATE INDEX idx_tool_capability_packs_scope_status
    ON tool_capability_packs (tenant_id, project_id, status) WHERE is_deleted = FALSE;

CREATE TABLE tool_capability_pack_items (
    id BIGSERIAL PRIMARY KEY,
    pack_id BIGINT NOT NULL REFERENCES tool_capability_packs(id),
    tool_id BIGINT NOT NULL REFERENCES tools(id),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_tool_capability_pack_items_pack_tool
    ON tool_capability_pack_items (pack_id, tool_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_tool_capability_pack_items_tool
    ON tool_capability_pack_items (tool_id) WHERE is_deleted = FALSE;

CREATE TABLE agent_tool_capability_pack_rel (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agents(id),
    pack_id BIGINT NOT NULL REFERENCES tool_capability_packs(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_agent_tool_capability_pack_rel
    ON agent_tool_capability_pack_rel (agent_id, pack_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_agent_tool_capability_pack_rel_pack
    ON agent_tool_capability_pack_rel (pack_id) WHERE is_deleted = FALSE;

ALTER TABLE tool_capability_packs ENABLE ROW LEVEL SECURITY;
ALTER TABLE tool_capability_packs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope_policy ON tool_capability_packs
    USING (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0
        OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0))
    WITH CHECK (COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0) = 0
        OR tenant_id = COALESCE(NULLIF(current_setting('app.tenant_id', true), '')::BIGINT, 0));

COMMENT ON TABLE tool_capability_packs IS '可复用 Tool 能力包定义';
COMMENT ON COLUMN tool_capability_packs.id IS '主键ID';
COMMENT ON COLUMN tool_capability_packs.pack_code IS '能力包稳定编码';
COMMENT ON COLUMN tool_capability_packs.pack_name IS '能力包名称';
COMMENT ON COLUMN tool_capability_packs.description IS '能力包说明';
COMMENT ON COLUMN tool_capability_packs.version IS '能力包版本';
COMMENT ON COLUMN tool_capability_packs.status IS '状态：draft/enabled/disabled';
COMMENT ON COLUMN tool_capability_packs.metadata IS '扩展配置与治理策略';
COMMENT ON COLUMN tool_capability_packs.tenant_id IS '租户ID';
COMMENT ON COLUMN tool_capability_packs.organization_id IS '组织ID';
COMMENT ON COLUMN tool_capability_packs.workspace_id IS '工作区ID';
COMMENT ON COLUMN tool_capability_packs.project_id IS '项目ID';
COMMENT ON COLUMN tool_capability_packs.created_at IS '创建时间';
COMMENT ON COLUMN tool_capability_packs.created_by IS '创建人';
COMMENT ON COLUMN tool_capability_packs.updated_at IS '更新时间';
COMMENT ON COLUMN tool_capability_packs.updated_by IS '更新人';
COMMENT ON COLUMN tool_capability_packs.is_deleted IS '逻辑删除标记';
COMMENT ON TABLE tool_capability_pack_items IS 'Tool 能力包成员';
COMMENT ON COLUMN tool_capability_pack_items.pack_id IS '能力包ID';
COMMENT ON COLUMN tool_capability_pack_items.tool_id IS 'Tool ID';
COMMENT ON COLUMN tool_capability_pack_items.required IS '是否为能力包必需 Tool';
COMMENT ON COLUMN tool_capability_pack_items.sort_order IS '展示与注入顺序';
COMMENT ON COLUMN tool_capability_pack_items.metadata IS '成员级覆盖配置';
COMMENT ON TABLE agent_tool_capability_pack_rel IS 'Agent 与 Tool 能力包关联';
COMMENT ON COLUMN agent_tool_capability_pack_rel.agent_id IS 'Agent ID';
COMMENT ON COLUMN agent_tool_capability_pack_rel.pack_id IS '能力包ID';

INSERT INTO tool_capability_packs (pack_code, pack_name, description, version, status, metadata)
VALUES
    ('BASIC_REASONING', '基础推理能力包', '时间查询与安全计算', '1.0.0', 'enabled', '{"managedBy":"platform"}'::jsonb),
    ('RESEARCH_RETRIEVAL', '研究检索能力包', '平台知识库、Web 搜索和受控 HTTP 内容读取', '1.0.0', 'enabled', '{"managedBy":"platform"}'::jsonb),
    ('DOCUMENT_WORKSPACE', '文档工作区能力包', '文档解析与会话隔离文件工作区', '1.0.0', 'enabled', '{"managedBy":"platform"}'::jsonb),
    ('CONTROLLED_EXECUTION', '受控执行能力包', 'Docker Sandbox 强隔离命令执行', '1.0.0', 'enabled', '{"managedBy":"platform","approvalRequired":true}'::jsonb)
ON CONFLICT (tenant_id, project_id, pack_code) WHERE is_deleted = FALSE DO NOTHING;

INSERT INTO tool_capability_pack_items (pack_id, tool_id, required, sort_order, metadata)
SELECT p.id, t.id, TRUE, v.sort_order, '{}'::jsonb
FROM tool_capability_packs p
JOIN (VALUES
    ('BASIC_REASONING', 'TIME_INFO', 10),
    ('BASIC_REASONING', 'CALCULATOR', 20),
    ('RESEARCH_RETRIEVAL', 'KNOWLEDGE_SEARCH', 10),
    ('RESEARCH_RETRIEVAL', 'WEB_SEARCH', 20),
    ('RESEARCH_RETRIEVAL', 'HTTP_FETCH', 30),
    ('DOCUMENT_WORKSPACE', 'DOCUMENT_PARSE', 10),
    ('DOCUMENT_WORKSPACE', 'WORKSPACE_FILE', 20),
    ('CONTROLLED_EXECUTION', 'SANDBOX_EXECUTE', 10)
) AS v(pack_code, tool_code, sort_order) ON v.pack_code = p.pack_code
JOIN tools t ON t.tool_code = v.tool_code AND t.is_deleted = FALSE
WHERE p.is_deleted = FALSE
ON CONFLICT (pack_id, tool_id) WHERE is_deleted = FALSE DO NOTHING;

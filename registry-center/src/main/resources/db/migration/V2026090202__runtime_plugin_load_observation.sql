ALTER TABLE runtime_plugin_installations
    ADD COLUMN observed_plugin_code VARCHAR(64),
    ADD COLUMN observed_version VARCHAR(64),
    ADD COLUMN observed_artifact_sha256 CHAR(64),
    ADD COLUMN loaded_at TIMESTAMP,
    ADD COLUMN last_reported_at TIMESTAMP;

ALTER TABLE runtime_plugin_installations
    ADD CONSTRAINT ck_runtime_plugin_installations_observed_sha256
        CHECK (observed_artifact_sha256 IS NULL OR observed_artifact_sha256 ~ '^[0-9a-f]{64}$');

COMMENT ON COLUMN runtime_plugin_installations.observed_plugin_code IS 'agent-core 实际发现的插件编码';
COMMENT ON COLUMN runtime_plugin_installations.observed_version IS 'agent-core 实际加载的插件版本';
COMMENT ON COLUMN runtime_plugin_installations.observed_artifact_sha256 IS 'agent-core 实际加载 Jar 的 SHA-256';
COMMENT ON COLUMN runtime_plugin_installations.loaded_at IS '最近一次确认加载成功时间';
COMMENT ON COLUMN runtime_plugin_installations.last_reported_at IS 'agent-core 最近回写加载结果时间';

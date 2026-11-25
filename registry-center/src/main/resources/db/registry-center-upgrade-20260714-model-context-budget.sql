ALTER TABLE models ADD COLUMN IF NOT EXISTS context_window INTEGER;
ALTER TABLE models ADD COLUMN IF NOT EXISTS reserved_output_tokens INTEGER;
ALTER TABLE models ADD COLUMN IF NOT EXISTS reserved_system_tokens INTEGER;
ALTER TABLE models ADD COLUMN IF NOT EXISTS compaction_trigger_ratio DOUBLE PRECISION;
ALTER TABLE models ADD COLUMN IF NOT EXISTS tokenizer_family VARCHAR(64);

UPDATE models
SET context_window = COALESCE(context_window, 128000),
    reserved_output_tokens = COALESCE(reserved_output_tokens, GREATEST(COALESCE(max_tokens, 4096), 1024)),
    reserved_system_tokens = COALESCE(reserved_system_tokens, 2048),
    compaction_trigger_ratio = COALESCE(compaction_trigger_ratio, 0.8),
    tokenizer_family = COALESCE(tokenizer_family, 'cl100k_base')
WHERE is_deleted = FALSE;

COMMENT ON COLUMN models.context_window IS '模型总上下文窗口 Token 数';
COMMENT ON COLUMN models.reserved_output_tokens IS '为模型输出预留的 Token 数';
COMMENT ON COLUMN models.reserved_system_tokens IS '为系统提示和固定前缀预留的 Token 数';
COMMENT ON COLUMN models.compaction_trigger_ratio IS '触发上下文压缩的窗口占用比例';
COMMENT ON COLUMN models.tokenizer_family IS '用于 Token 估算的分词器族标识';

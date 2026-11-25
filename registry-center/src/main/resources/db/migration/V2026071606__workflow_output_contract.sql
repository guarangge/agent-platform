UPDATE workflows
SET metadata = jsonb_set(
        COALESCE(metadata, '{}'::jsonb),
        '{routing,outputMode}',
        '"user_reply"'::jsonb,
        TRUE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE workflow_code = 'general_context_reply_workflow_v1'
  AND is_deleted = FALSE;

COMMENT ON COLUMN workflows.metadata IS 'Workflow扩展元数据；routing.outputMode=user_reply 表示执行结果可直接作为用户回复';

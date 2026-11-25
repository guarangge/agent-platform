ALTER TABLE agents ADD COLUMN IF NOT EXISTS planner_prompt_id BIGINT;

INSERT INTO dict_items (
    type_id,
    item_code,
    item_name,
    sort_order,
    status,
    is_default,
    description,
    metadata,
    remark
)
SELECT
    t.id,
    'planner_system',
    '任务规划提示词',
    30,
    'enabled',
    FALSE,
    'Planner 阶段任务拆解 Prompt',
    '{}'::jsonb,
    '升级补充数据'
FROM dict_types t
WHERE t.type_code = 'prompt_type'
  AND NOT EXISTS (
      SELECT 1
      FROM dict_items i
      WHERE i.type_id = t.id
        AND i.item_code = 'planner_system'
        AND i.is_deleted = FALSE
  );

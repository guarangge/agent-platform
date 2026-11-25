DROP INDEX IF EXISTS uk_task_steps_task_step_deleted;

CREATE UNIQUE INDEX uk_task_steps_task_step_active
    ON task_steps (task_id, step_no)
    WHERE is_deleted = FALSE;

COMMENT ON INDEX uk_task_steps_task_step_active IS
    '同一任务步骤序号仅允许一条活动记录，历史软删除记录可保留多个恢复版本';

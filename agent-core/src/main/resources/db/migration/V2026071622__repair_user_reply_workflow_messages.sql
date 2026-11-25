UPDATE messages assistant
SET content = workflow_step.output_snapshot::jsonb #>> '{payload,runtimeVariables,lastResponseText}',
    updated_at = CURRENT_TIMESTAMP
FROM tasks task
JOIN messages user_message
  ON user_message.id = task.message_id
 AND user_message.is_deleted = FALSE
JOIN task_steps workflow_step
  ON workflow_step.task_id = task.id
 AND workflow_step.step_type = 'workflow'
 AND workflow_step.status = 'success'
 AND workflow_step.is_deleted = FALSE
WHERE assistant.conversation_id = task.conversation_id
  AND assistant.message_type = 'assistant'
  AND assistant.sequence_no = user_message.sequence_no + 1
  AND assistant.content LIKE '已执行 Workflow「通用上下文回复流程」。%'
  AND workflow_step.output_snapshot::jsonb #>> '{payload,workflowCode}' = 'general_context_reply_workflow_v1'
  AND COALESCE(workflow_step.output_snapshot::jsonb #>> '{payload,runtimeVariables,lastResponseText}', '') <> ''
  AND assistant.is_deleted = FALSE;

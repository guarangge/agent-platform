ALTER TABLE messages
    ADD CONSTRAINT fk_messages_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversations (id);

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversations (id);

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_message
    FOREIGN KEY (message_id) REFERENCES messages (id);

ALTER TABLE task_steps
    ADD CONSTRAINT fk_task_steps_task
    FOREIGN KEY (task_id) REFERENCES tasks (id);

ALTER TABLE agent_runs
    ADD CONSTRAINT fk_agent_runs_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversations (id);

ALTER TABLE agent_runs
    ADD CONSTRAINT fk_agent_runs_task
    FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL;

ALTER TABLE agent_runs
    ADD CONSTRAINT ck_agent_runs_status
    CHECK (status IN ('queued', 'running', 'completed', 'waiting_approval', 'failed', 'cancelled'));

ALTER TABLE agent_runs
    ADD CONSTRAINT ck_agent_runs_attempts
    CHECK (attempt_count >= 0 AND max_attempts > 0 AND attempt_count <= max_attempts);

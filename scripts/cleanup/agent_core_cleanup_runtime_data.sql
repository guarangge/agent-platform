-- 清空 agent-core 全部运行态数据，只保留表结构

TRUNCATE TABLE
    approvals,
    task_traces,
    task_plan_steps,
    task_plans,
    task_steps,
    task_memory_snapshots,
    tasks,
    messages,
    conversation_memories,
    user_memories,
    conversations
RESTART IDENTITY;

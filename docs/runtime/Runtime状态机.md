# Runtime 状态机

## 1. 结论

Runtime Protocol 统一表达执行事实，但不把 `Run`、`Task`、`Step`、`Approval` 和 `Wait Subscription` 合并成一个状态字段。各对象保留自己的职责，通过稳定 ID 和 Runtime Event 关联。

状态判断必须区分：

1. **不可恢复终态**：执行已完成、失败或取消
2. **本轮流关闭状态**：当前 SSE 应结束，但任务可由审批、事件或人工操作恢复
3. **处理中状态**：Worker 仍持有执行权或等待重试

`waiting_approval / waiting_event / paused` 属于“本轮流关闭、业务可恢复”，不能伪装成 Assistant 聊天回答，也不能等同于永久失败。

## 2. 对象职责

| 对象 | 事实职责 | 主状态字段 |
| --- | --- | --- |
| Conversation | 用户可见会话当前总体状态 | `conversations.status` |
| Run | 队列、Worker 租约、本轮执行和取消 | `agent_runs.status` |
| Task | 一次业务执行目标的总体结果 | `tasks.status` |
| Step | 计划步骤的局部执行结果 | `task_steps.status` |
| Approval | 人工审批决议 | `task_approvals.status` |
| Wait Subscription | timer/event/webhook 是否已触发 | `task_wait_subscriptions.status` |
| Checkpoint | 可恢复执行边界 | `task_execution_checkpoints.checkpoint_status` |

Run 与 Task 不重复表达同一职责：Run 是调度实例，Task 是业务执行实例。一个主 Task 通过 `run_code` 关联一个 Run；Delegation 子 Task 不重复创建主 Run。

## 3. Run 状态机

Run 状态：

| 状态 | 含义 | 当前 SSE | 是否可恢复 |
| --- | --- | --- | --- |
| `queued` | 已入持久化队列，等待 Worker | 保持 | 是 |
| `running` | Worker 已领取并持有租约 | 保持 | 失败前可重试 |
| `completed` | 本轮执行成功完成 | 关闭 | 否 |
| `waiting_approval` | 等待人工审批 | 关闭 | 审批动作恢复 |
| `waiting_event` | 等待 timer/event/webhook | 关闭 | 信号触发恢复 |
| `paused` | 人工暂停 | 关闭 | 人工恢复 |
| `failed` | 执行失败 | 关闭 | 可通过 Recovery 创建续跑 |
| `cancelled` | 已取消 | 关闭 | 否 |

允许的主要流转：

```text
queued -> running
queued -> cancelled
running -> queued                 # Worker 领取失败或可重试退避
running -> completed
running -> waiting_approval
running -> waiting_event
running -> paused
running -> failed
running -> cancelled
waiting_approval -> running/completed/failed/waiting_approval # 审批通过后同步续跑
waiting_event -> queued/running/completed/failed/waiting_event # 外部信号恢复
paused -> queued/running/completed/failed                     # 人工恢复
failed -> queued/running/completed/failed                     # 符合恢复策略时续跑
```

约束：

1. `queued` 取消时直接更新为 `cancelled` 并发布 `run.cancelled`
2. `running` 取消先写 `cancel_requested=true`，Worker 在安全边界确认后进入 `cancelled`
3. `waiting_approval / waiting_event / paused` 已释放当前 Worker，不通过队列取消入口发送无效中断，应走各自控制入口
4. Worker 租约过期且未耗尽尝试次数时回到 `queued`；耗尽尝试次数后进入 `failed`
5. Run 关闭状态和对应 `run.*` 事件必须在同一事务提交
6. 同步恢复可能从挂起状态直接写入本次恢复的最终状态，不强制持久化中间 `running`；异步恢复先回到 `queued`

## 4. Task 与 Conversation 状态机

Task / Conversation 主状态：

```text
pending -> processing
processing -> success
processing -> failed
processing -> waiting_approval
processing -> waiting_event
processing -> paused
waiting_approval -> processing/success/failed/waiting_approval
waiting_event -> processing/success/failed/waiting_event
paused -> processing/success/failed
failed -> processing/success/failed       # 显式 Recovery
```

映射规则：

| Task 状态 | Run 状态 | 用户语义 |
| --- | --- | --- |
| `pending / processing` | `queued / running` | 正在处理 |
| `success` | `completed` | 已完成 |
| `waiting_approval` | `waiting_approval` | 对话内弹出审批 |
| `waiting_event` | `waiting_event` | 等待外部事件 |
| `paused` | `paused` | 已人工暂停 |
| `failed` | `failed` | 执行失败，可评估恢复 |

Conversation 只展示当前会话最近一次主任务状态，不作为恢复事实源。恢复判断必须读取 Task、Step、Approval 和 Checkpoint。

## 5. Step 状态机

Step 状态：`pending / processing / success / failed / waiting_approval / waiting_event / paused`。

```text
pending -> processing
processing -> success
processing -> failed
processing -> waiting_approval
processing -> waiting_event
processing -> paused
waiting_approval -> processing/success/failed
waiting_event -> processing/success/failed
paused -> processing/success/failed
failed -> processing/success/failed       # 显式恢复或新计划版本
```

约束：

1. `step.started` 对应进入 `processing`
2. `step.completed` 只对应 `success`
3. `step.failed` 只对应 `failed`
4. `step.waiting` 通过 Payload `status` 区分三种可恢复挂起状态
5. 每次 Step 尝试只能有一个完成、失败或等待结果；重规划使用新 Plan Version，不覆盖历史计划快照

## 6. Approval 状态机

Approval 状态：`pending / approved / rejected / timed_out / revoked`。

```text
pending -> approved
pending -> rejected
pending -> timed_out
pending -> revoked
```

所有非 `pending` 状态都是 Approval 决议终态，不允许再次审批。

处理规则：

1. 创建审批记录和 `approval.requested` 同事务提交
2. `approved / rejected / timed_out` 发布 `approval.resolved`
3. `revoked` 发布 `approval.revoked`
4. 事件不保存审批意见和恢复元数据；UI 收到事件后按 `approvalId / taskId` 查询业务详情
5. `approved` 恢复原 Run 作用域，并从持久化 Checkpoint 继续
6. `rejected` 按配置终止任务或产生新的不可变 Plan Version
7. 审批控制状态不得写成 Assistant 消息

## 7. Durable Wait 状态机

Wait Subscription 状态：`waiting / signaled / cancelled`。

```text
waiting -> signaled
waiting -> cancelled
```

`wait_type` 为 `timer / event / webhook`。进入等待时 Task、Step、Run 同步使用 `waiting_event` 并释放 Worker；信号到达后 Subscription 原子标记为 `signaled`，Run 重新进入 `queued`，恢复链从 Checkpoint 继续。

当前实现为复用标准 Recovery，会在信号到达后把对应 Task / Step 暂时转换为恢复入口可识别状态；该内部兼容状态不应作为用户可见失败，也不应生成失败聊天回答。

## 8. Checkpoint 状态

Checkpoint 状态：`step_started / step_succeeded / step_failed / waiting_approval / waiting_event / paused`。

Checkpoint 是执行现场快照，不是调度状态机：

1. 创建快照与 `checkpoint.created` 同事务提交
2. 只有实际加载快照后才发布 `checkpoint.restored`
3. 恢复前的副作用检查、可恢复性查询不发布 restored
4. 审批恢复、事件恢复和人工恢复必须记录 `recoveryMode / recoveryDisposition`

## 9. Runtime Event 映射

| 状态变化 | Runtime Event |
| --- | --- |
| Run 入队 / Worker 开始 | `run.queued / run.started` |
| Run 可恢复挂起 | `run.waiting`，Payload `status` 区分原因 |
| Run 完成 / 失败 / 取消 | `run.completed / run.failed / run.cancelled` |
| Step 开始 / 完成 / 失败 / 挂起 | `step.started / step.completed / step.failed / step.waiting` |
| 审批请求 / 决议 / 撤回 | `approval.requested / approval.resolved / approval.revoked` |
| 等待恢复边界 | `checkpoint.created / checkpoint.restored` |

事件消费规则：

1. 状态表是业务查询事实，Runtime Event 是按 Run 有序的运行事实
2. 消费端按 `sequenceNo` 幂等处理，不能按到达时间重排
3. SSE 结束只表示当前 Run 到达关闭状态，不表示可恢复任务永久结束
4. 控制事件只更新 UI 状态或触发详情查询，不追加聊天消息

## 10. 非法流转

以下流转必须拒绝或幂等返回：

1. 已完成或已取消 Run 再次领取、重试或取消
2. 非 `waiting_approval` Task 执行 approve / reject
3. 非 `pending` Approval 再次决议
4. 非 `failed / paused` Task 进入通用恢复
5. 未实际加载 Checkpoint 就发布 `checkpoint.restored`
6. Provider 已产生外部副作用后因结果事件写入失败自动重放 Tool
7. 将 `waiting_approval / waiting_event / paused` 转换为聊天回答并结束业务任务

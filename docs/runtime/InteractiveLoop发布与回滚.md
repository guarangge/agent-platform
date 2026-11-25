# Interactive Loop 发布与回滚

## 1. 结论

Interactive Loop 使用独立 Feature Flag、紧急熔断开关、Project/Agent 白名单和稳定百分比灰度。默认关闭，且 `kill-switch` 优先级最高；熔断后新 Run 立即回退到 `workflow_plan`，已挂起的 Approval、Checkpoint 和 Runtime Event 仍保留，不做数据回滚。

## 2. 配置与优先级

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CORE_INTERACTIVE_LOOP_KILL_SWITCH` | `false` | 紧急熔断；`true` 时忽略其他灰度配置 |
| `CORE_INTERACTIVE_LOOP_ENABLED` | `false` | Interactive Loop 总开关 |
| `CORE_INTERACTIVE_LOOP_PROJECT_IDS` | 空 | Project ID 白名单，英文逗号分隔 |
| `CORE_INTERACTIVE_LOOP_AGENT_IDS` | 空 | Agent ID 白名单，英文逗号分隔 |
| `CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT` | `0` | Agent 白名单为空时生效，范围 `0-100` |

决策优先级：`kill-switch` > 总开关 > Project 白名单 > Agent 白名单 > 稳定百分比。

## 3. 推荐灰度顺序

macOS / Linux，在项目根目录执行：

```bash
# 第一批：只放行验收 Agent
CORE_INTERACTIVE_LOOP_ENABLED=true \
CORE_INTERACTIVE_LOOP_AGENT_IDS=7 \
CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT=0 \
bash scripts/deploy/recreate_local_agent_services.sh

# 第二批：限定 Project 的 10% 稳定灰度
CORE_INTERACTIVE_LOOP_ENABLED=true \
CORE_INTERACTIVE_LOOP_PROJECT_IDS=20 \
CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT=10 \
bash scripts/deploy/recreate_local_agent_services.sh
```

Windows PowerShell，在项目根目录执行：

```powershell
$env:CORE_INTERACTIVE_LOOP_ENABLED = "true"
$env:CORE_INTERACTIVE_LOOP_AGENT_IDS = "7"
$env:CORE_INTERACTIVE_LOOP_ROLLOUT_PERCENT = "0"
```

纯 PowerShell 部署应在容器编排文件中设置同名环境变量后重建 `agent-core`。如使用 Git Bash 或 WSL，则执行上方 macOS / Linux 脚本命令。

## 4. 可观测指标

Actuator 已暴露仅允许 `ROLE_ADMIN` 访问的 `metrics` 端点。Micrometer 指标不包含用户、Agent、Tool 或参数：

| 指标 | Tag / 数值 | 用途 |
| --- | --- | --- |
| `agent.runtime.interactive.loop.route` | `outcome=selected/fallback`、`reason` | 灰度命中和回退原因 |
| `agent.runtime.interactive.loop.stop` | `reason` | 完成、审批、取消、预算或失败分布 |
| `agent.runtime.interactive.loop.iterations` | `count/total/max` | 每次 Loop 迭代数 |
| `agent.runtime.interactive.loop.llm.calls` | `count/total/max` | LLM 调用消耗 |
| `agent.runtime.interactive.loop.tool.calls` | `count/total/max` | Tool 调用消耗 |
| `agent.runtime.interactive.loop.tokens` | `count/total/max` | Token 消耗 |
| `agent.runtime.interactive.loop.cost.amount` | `count/total/max` | 标准计价金额消耗 |

```bash
# macOS / Linux；ADMIN_TOKEN 必须是 Registry 签发的有效 JWT
curl -fsS -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  http://localhost:8081/actuator/metrics/agent.runtime.interactive.loop.stop
```

## 5. 每批验收门禁

每批至少观察 30 个 Interactive Loop Run 或 24 小时，同时满足：

1. 每个 Run 只有一个 `run.completed / run.failed / run.cancelled` 关闭终态，重复率为 0
2. 高风险 Tool 审批绕过数为 0，审批后重复 Tool 执行数为 0
3. 除 `approval_required / cancelled` 外，`failed` 停止率不高于 5%
4. `max_iterations / max_llm_calls / max_tool_calls / max_tokens / max_cost` 合计停止率不高于 15%
5. 审批恢复的 `run.completed` 可从原 `sequenceNo` Replay，成功率为 100%
6. 取消后不再产生新 LLM/Tool 调用，取消 Run 进入 Worker 重试的数量为 0

关闭终态重复检查，在 PostgreSQL `agent_core` 数据库执行：

```sql
SELECT run_code,
       COUNT(*) FILTER (WHERE event_type IN ('run.completed', 'run.failed', 'run.cancelled')) AS terminal_count
FROM agent_run_stream_events
WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY run_code
HAVING COUNT(*) FILTER (WHERE event_type IN ('run.completed', 'run.failed', 'run.cancelled')) > 1;
```

查询 Loop 停止原因：

```sql
SELECT content::jsonb ->> 'stopReason' AS stop_reason, COUNT(*) AS stop_count
FROM agent_run_stream_events
WHERE event_type = 'run.loop.stopped'
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY content::jsonb ->> 'stopReason'
ORDER BY stop_count DESC;
```

## 6. 紧急回滚

出现审批绕过、重复副作用、终态重复，或任一比率超过验收门禁时，立即熔断：

```bash
# macOS / Linux，项目根目录
CORE_INTERACTIVE_LOOP_KILL_SWITCH=true \
bash scripts/deploy/recreate_local_agent_services.sh
```

PowerShell 部署参数：

```powershell
$env:CORE_INTERACTIVE_LOOP_KILL_SWITCH = "true"
```

重启后检查 `run.mode.selected.reason=interactive_loop_kill_switched`。恢复灰度前必须先完成根因分析，再将 `CORE_INTERACTIVE_LOOP_KILL_SWITCH=false`，不得仅扩大预算掩盖失败。

## 7. 回归命令

在项目根目录执行：

```bash
cd agent-core && mvn test
cd ../frontend-web && npm run build
npx playwright test tests/e2e/runtime-event.spec.ts
```

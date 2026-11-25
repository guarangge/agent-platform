# Replay 发布与回滚

## 1. 目标

Replay 执行入口由 `agent-core` 后端 Feature Flag 强制控制。前端页面不是安全边界；即使绕过页面直接调用接口，未命中灰度的请求也必须在读取冻结事件和创建 Replay 实例前失败。

关闭执行入口后，Replay 列表和详情查询继续可用，历史 `runtime_replays` 审计记录不删除、不覆盖。

## 2. 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CORE_REPLAY_ENABLED` | `true` | Replay 执行总开关；关闭后 Prompt 和 Simulation 均禁止新执行 |
| `CORE_REPLAY_PROMPT_ENABLED` | `true` | Prompt Replay 模式开关 |
| `CORE_REPLAY_SIMULATION_ENABLED` | `true` | Simulation Replay 模式开关 |
| `CORE_REPLAY_PROJECT_IDS` | 空 | Project ID 白名单，英文逗号分隔；空表示不限制 Project |
| `CORE_REPLAY_AGENT_IDS` | 空 | Agent ID 白名单，英文逗号分隔；配置后白名单 Agent 直接命中，不再参与百分比分桶 |
| `CORE_REPLAY_ROLLOUT_PERCENT` | `100` | Agent 白名单为空时生效，按 `mode + agentId + projectId` 做 SHA-256 稳定分桶，范围按 `0-100` 收敛 |

`execution_replay` 在代码契约层固定拒绝，不能通过环境变量开放。

决策顺序：总开关 -> 模式开关 -> Project 白名单 -> Agent 白名单 -> 稳定百分比。每次成功创建 Replay 时，决策结果冻结到 `manifest.featureDecision`。

## 3. 灰度发布

macOS / Linux，在 `agent-core` 的部署环境配置并重启服务：

```bash
# 第一批：仅 Project 1 下的 Agent 10、12
export CORE_REPLAY_ENABLED=true
export CORE_REPLAY_PROMPT_ENABLED=true
export CORE_REPLAY_SIMULATION_ENABLED=true
export CORE_REPLAY_PROJECT_IDS=1
export CORE_REPLAY_AGENT_IDS=10,12

# 第二批：取消 Agent 白名单，对 Project 1 做 20% 稳定灰度
export CORE_REPLAY_AGENT_IDS=
export CORE_REPLAY_ROLLOUT_PERCENT=20

# 全量
export CORE_REPLAY_PROJECT_IDS=
export CORE_REPLAY_ROLLOUT_PERCENT=100
```

Windows PowerShell，在 `agent-core` 的部署环境配置并重启服务：

```powershell
$env:CORE_REPLAY_ENABLED = "true"
$env:CORE_REPLAY_PROJECT_IDS = "1"
$env:CORE_REPLAY_AGENT_IDS = "10,12"
$env:CORE_REPLAY_ROLLOUT_PERCENT = "100"
```

配置由 Spring Boot 启动时加载，修改环境变量后必须滚动重启 `agent-core`。

## 4. 回滚

只关闭 Simulation Replay，保留 Prompt Replay：

```bash
CORE_REPLAY_SIMULATION_ENABLED=false
```

完全关闭新增 Replay 执行：

```bash
CORE_REPLAY_ENABLED=false
```

PowerShell：

```powershell
$env:CORE_REPLAY_SIMULATION_ENABLED = "false"
# 或
$env:CORE_REPLAY_ENABLED = "false"
```

回滚后：

1. 后端在 Event、Step Snapshot 和 Replay 实例创建前拒绝新请求
2. Replay 管理列表和详情保持可读，便于排查历史执行
3. 不回滚数据库 migration，不删除 `runtime_replays`
4. 已经进入 `running` 的同步 Replay 不被强制中断；需要先停止入口，再按现有请求超时和实例状态处理
5. 原 Run、Task、Trace、Artifact 始终不因灰度切换而修改

## 5. 每批验证

1. 检查 `manifest.featureDecision.reason / rolloutBucket`
2. 检查 Simulation Replay 的 `providerInvocationCount=0`
3. 检查 `eventDiff` 和 `artifactVerification` 是否完整
4. 检查未命中灰度时没有新增 `runtime_replays`
5. 检查来源 Run、Task、Step、Trace 和 Artifact 的更新时间未变化
6. 检查普通用户无法查询或执行 Replay

自动化命令见 `docs/测试与稳定性回归.md`。

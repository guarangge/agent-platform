# Runtime Protocol 发布与回滚

## 1. 结论

前端通过构建期 Feature Flag 选择统一 Runtime Event 或旧 Run SSE。后端在 M1 兼容期同时保留两个接口，因此回滚前端读取协议不需要回滚数据库或后端事件写入。

当前平台尚未引入独立 `Project` 聚合，本期采用 Agent 白名单和按 Agent 稳定百分比灰度，不虚构 Project 配置。后续增加 Project 维度时应沿用同一解析入口。

## 2. Feature Flag

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_RUNTIME_EVENT_PROTOCOL_MODE` | `auto` | `auto` 优先统一协议并在连接失败时自动回退旧 SSE；`unified` 只使用统一协议；`legacy` 只使用旧 SSE |
| `VITE_RUNTIME_EVENT_PROTOCOL_AGENT_IDS` | 空 | `auto` 模式下的 Agent ID 白名单，英文逗号分隔；配置后非白名单 Agent 使用旧 SSE |
| `VITE_RUNTIME_EVENT_PROTOCOL_ROLLOUT_PERCENT` | `100` | 白名单为空时生效，按 Agent ID 稳定分桶，范围 `0-100` |

优先级：`mode` 强制模式 > Agent 白名单 > 稳定百分比。Vite 环境变量在构建时写入产物，修改后必须重新执行 `npm run build`。

## 3. 推荐发布顺序

macOS / Linux，在 `/path/to/agent/frontend-web` 执行：

```bash
# 1. 指定少量 Agent 灰度；统一协议失败时自动回退旧 SSE
VITE_RUNTIME_EVENT_PROTOCOL_MODE=auto \
VITE_RUNTIME_EVENT_PROTOCOL_AGENT_IDS=1,3 \
npm run build
docker restart agent-frontend-web

# 2. 白名单验证后改为 20% 稳定灰度
VITE_RUNTIME_EVENT_PROTOCOL_MODE=auto \
VITE_RUNTIME_EVENT_PROTOCOL_ROLLOUT_PERCENT=20 \
npm run build
docker restart agent-frontend-web

# 3. 全量启用且继续保留自动回退
VITE_RUNTIME_EVENT_PROTOCOL_MODE=auto \
VITE_RUNTIME_EVENT_PROTOCOL_ROLLOUT_PERCENT=100 \
npm run build
docker restart agent-frontend-web
```

当前本地 Nginx 容器只读挂载 `frontend-web/dist`，重新构建后重启容器即可发布，不需要重建 Nginx 镜像。

Windows PowerShell，在项目的 `frontend-web` 目录执行：

```powershell
$env:VITE_RUNTIME_EVENT_PROTOCOL_MODE = "auto"
$env:VITE_RUNTIME_EVENT_PROTOCOL_AGENT_IDS = "1,3"
npm run build
docker restart agent-frontend-web
```

## 4. 快速回滚

macOS / Linux，在 `/path/to/agent/frontend-web` 执行：

```bash
VITE_RUNTIME_EVENT_PROTOCOL_MODE=legacy npm run build
docker restart agent-frontend-web
curl -fsS http://localhost:5173/ >/dev/null
```

回滚只改变会话页读取路径。后端继续写入统一事件事实源，旧接口继续将标准 `message.reset / message.delta / run.progress` 投影为旧事件，不丢失兼容期数据。

## 5. 发布验证

每批灰度至少检查：

1. 普通流式问答只出现一份增量正文，最终消息与落库内容一致
2. 浏览器断网重连后从最后 `sequenceNo` 继续，不重复追加正文
3. 高风险 Tool 在会话窗口弹出审批，不产生“等待人工审批”的聊天回答
4. 审批通过后弹窗关闭并从 Checkpoint 继续
5. PDF 等产物显示真实文件名和受保护下载链接
6. 失败、取消、等待事件和人工暂停能结束当前 SSE，不无限重连
7. 浏览器 Network 中统一接口失败时，`auto` 模式会请求旧 `/conversations/{conversationId}/runs/{runCode}/stream`

自动化验证命令，在项目根目录执行：

```bash
cd agent-core && mvn test
cd ../frontend-web && npm run build
npx playwright test tests/e2e/runtime-event.spec.ts
```

2026-08-31 本地发布记录：`agent-registry-center`、`agent-core`、`agent-frontend-web` 已完成 Docker 重建或重启；Registry/Core Actuator 返回 `UP`，前端首页可访问，同源 API 代理可到达后端；`agent_core` Flyway 已应用 `2026083101 - runtime event protocol`。

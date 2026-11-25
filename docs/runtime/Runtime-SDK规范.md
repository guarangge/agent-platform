# Runtime SDK 规范

## 1. 定位

Runtime SDK 对外统一 Thread、Run、Runtime Event、Approval、Artifact 和 Replay 语义，隔离业务页面与后端 Controller 路径、响应包和认证细节。

前四个纵向切片已在 `frontend-web/src/runtime/sdk` 建立 TypeScript Client，覆盖 Thread、异步 Run、Runtime Event SSE、Approval、Task Recovery、Artifact 和 Replay。当前保持与现有 `/api/core` 响应字段兼容，不要求后端改接口。

## 2. TypeScript Client 初始化

```ts
import { RuntimeClient } from "@/runtime/sdk";

const client = new RuntimeClient({
  baseUrl: "/api/core",
  accessToken: () => sessionStorage.getItem("access_token"),
  onUnauthorized: () => sessionStorage.removeItem("access_token")
});
```

稳定配置：

1. `baseUrl`：Core API 根路径，SDK 统一去除末尾 `/`
2. `accessToken`：每次请求时动态读取 JWT，不在 Client 中长期复制 Token
3. `requestTimeoutMs`：普通 HTTP 请求超时，默认 60 秒
4. `fetcher`：可替换 Transport，用于测试、Node.js 宿主或自定义网关
5. `onUnauthorized`：HTTP 401 时统一清理宿主认证状态

## 3. 已实现 API

| SDK 方法 | 平台语义 | 当前 Core API |
| --- | --- | --- |
| `createThread(request)` | 创建 Thread | `POST /conversations` |
| `getThread(threadId)` | 查询 Thread | `GET /conversations/{id}` |
| `createRun(threadId, request, options)` | 创建异步 Run | `POST /conversations/{id}/messages/async` |
| `getRun(threadId, runCode)` | 查询 Run | `GET /conversations/{id}/runs/{runCode}` |
| `cancelRun(threadId, runCode)` | 请求取消 Run | `POST /conversations/{id}/runs/{runCode}/cancel` |
| `streamEvents(runCode, callbacks, options)` | 订阅 Runtime Event | `GET /runtime/runs/{runCode}/events` |
| `resumeTask(threadId, taskId)` | 从失败步骤恢复 Task | `POST /conversations/{id}/tasks/{taskId}/resume` |
| `approveTask(threadId, taskId, approvalId, request)` | 审批通过并继续 | `POST /conversations/{id}/tasks/{taskId}/approvals/{approvalId}/approve` |
| `rejectTask(threadId, taskId, approvalId, request)` | 审批拒绝或重新规划 | `POST /conversations/{id}/tasks/{taskId}/approvals/{approvalId}/reject` |
| `revokeApproval(threadId, taskId, approvalId, request)` | 撤回待处理审批 | `POST /conversations/{id}/tasks/{taskId}/approvals/{approvalId}/revoke` |
| `signalWait(eventKey, payload)` | 唤醒 waiting_event Task | `POST /conversations/waits/{eventKey}/signal` |
| `downloadArtifact(threadId, taskId, path)` | 下载受保护 Artifact | `GET /conversations/{id}/tasks/{taskId}/artifacts` |
| `listReplays(query)` | 分页查询 Replay | `GET /runtime/replays` |
| `getReplay(replayCode)` | 查询 Replay 详情 | `GET /runtime/replays/{replayCode}` |
| `createSimulationReplay(runCode, request)` | 发起无副作用 Replay | `POST /runtime/replays/runs/{runCode}/simulation` |

`createRun` 支持显式 `idempotencyKey` 和 `priority`。宿主未传入幂等键时 SDK 自动生成，并使用 `Idempotency-Key` 请求头传递。`runCode` 统一做 URL 编码。

`streamEvents` 支持 `afterSequence`、`AbortSignal`、总等待超时和重连间隔。SDK 同时发送 `afterSequence` 查询参数与 `Last-Event-ID` 请求头，按 `sequenceNo` 去重；连接在 Run 进入终态前关闭时，自动从最后已处理游标续传。

```ts
const result = await client.streamEvents("RUN_1", {
  onDelta: (content) => console.log(content),
  onApprovalRequested: (event) => showApproval(event),
  onArtifactPublished: (event) => showArtifact(event)
}, { afterSequence: 0, signal: abortController.signal });
```

Approval 和失败恢复会沿原 Task Checkpoint 同步执行，SDK 保持现有 10 分钟长请求窗口。`rejectTask` 可通过 `replanAfterReject` 请求拒绝后二次规划；`signalWait` 对 `eventKey` 做 URL 编码，并允许传入业务事件 Payload。

`downloadArtifact` 返回 Blob、服务端文件名、Content-Type 和字节数，由宿主决定下载或预览；SDK 优先解析 `Content-Disposition: filename*=UTF-8''...`，并正确编码中文、空格和括号路径。Replay API 对编码、分页范围和事件序号范围做统一校验，Simulation Replay 保持 10 分钟执行窗口。

## 4. 错误契约

SDK 只向调用方抛出 `RuntimeSdkError`：

| `code` | 含义 |
| --- | --- |
| `INVALID_CONFIG` | Client 初始化参数错误 |
| `INVALID_ARGUMENT` | Thread ID、Run Code 等参数非法 |
| `HTTP_<status>` | HTTP 非 2xx，例如 `HTTP_401` |
| `API_<code>` | HTTP 成功但平台响应 `code != 200` |
| `INVALID_RESPONSE` | 响应不是平台统一响应包 |
| `TIMEOUT` | 请求超时 |
| `NETWORK_ERROR` | Transport 或网络异常 |
| `STREAM_NETWORK_ERROR` | Runtime Event 连接或读取发生网络异常 |
| `STREAM_TIMEOUT` | Runtime Event 在总等待时限内未进入终态或等待态 |
| `STREAM_CLOSED` | 连续空连接结束且没有 Run 终态 |
| `ABORTED` | 宿主通过 `AbortSignal` 主动取消事件消费 |

错误对象保留 `httpStatus` 和 `originalCause`，但不保存 JWT、请求正文或 Tool 参数。

## 5. 前端接入边界

`frontend-web/src/api/conversation.ts` 现保留原函数签名，内部将以下调用委托给共享 `runtimeClient`：

1. `createConversation`
2. `fetchConversationDetail`
3. `enqueueConversationMessage`
4. `fetchAgentRun`
5. `cancelAgentRun`
6. `resumeConversationTask`
7. `approveConversationTask`
8. `rejectConversationTask`
9. `revokeConversationApproval`
10. `downloadTaskArtifact`

`frontend-web/src/api/replay.ts` 也保留原函数和类型名称，内部的 Replay 列表、详情和 Simulation Replay 已全部委托 SDK。

现有 `runtimeEventStream.ts` 也保留原函数签名，但只作为共享 `runtimeClient.streamEvents` 的兼容适配层。因此会话页无需改动组件状态和响应类型，审批弹窗、Artifact 下载、Replay 管理、灰度模式及旧 SSE 回退逻辑保持不变。同步发送仍保留原接口，后续开始 Java SDK。

## 6. 测试门禁

```bash
cd frontend-web
npm run build
npx playwright test tests/e2e/runtime-sdk.spec.ts tests/e2e/runtime-event.spec.ts
```

必须验证：

1. Thread/Run 路径、HTTP Method 和 `runCode` URL 编码正确
2. JWT 每次动态注入，401 触发宿主回调
3. `createRun` 总是携带幂等键
4. HTTP、业务响应、非法响应、超时和网络错误映射稳定
5. 现有 Runtime Event 续传与投影测试不回归
6. SDK 直接消费事件时正确注入 JWT、编码 `runCode`、传递双游标并映射取消和 401 错误
7. Approval、失败恢复和等待唤醒路径、ID 校验及请求体符合 Core 契约
8. Artifact 路径和 UTF-8 文件名正确处理，Replay 分页、详情、发起与范围校验稳定

## 7. Java Client 基座

Java SDK 位于独立 Maven 模块 `agent-runtime-sdk-java`，产物坐标：

```xml
<dependency>
    <groupId>com.zc.agent</groupId>
    <artifactId>agent-runtime-sdk-java</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Java SDK 三个切片已覆盖 `createThread / getThread / createRun / getRun / cancelRun`，以及 Runtime Event、Approval、Recovery、Artifact 和 Replay。SDK 不依赖 Spring Boot，JSON 仅依赖 Jackson，默认传输使用 Java 17 `HttpClient`；业务系统可实现 `RuntimeHttpTransport` 接入企业网关或测试桩，并通过 `openStream` 支持 SSE 与二进制响应。

```java
RuntimeClient client = RuntimeClient.builder("http://127.0.0.1:8081/api/core")
        .accessTokenProvider(tokenService::currentToken)
        .requestTimeout(Duration.ofSeconds(60))
        .onUnauthorized(tokenService::clear)
        .build();

RuntimeThread thread = client.createThread(new CreateRuntimeThreadRequest(7, "Java SDK"));
RuntimeRun run = client.createRun(
        thread.conversationId(),
        new CreateRuntimeRunRequest("生成学习计划"),
        new CreateRuntimeRunOptions(null, 50)
);
```

Java SDK 每次请求动态获取 JWT，未传 `Idempotency-Key` 时自动生成 UUID，路径参数统一编码。异常统一为 `RuntimeSdkException`，错误码与 TypeScript SDK 对齐；Java 线程中断额外使用 `INTERRUPTED`，并恢复线程中断标记。

Runtime Event 使用阻塞式 `streamEvents` 消费，支持初始 `afterSequence`、`Last-Event-ID`、按序号去重、非终态正常断连续传、总超时和线程中断。Listener 可接收原始事件、消息增量、进度、审批、产物和 Run 状态投影。

```java
RuntimeEventStreamResult result = client.streamEvents(run.runCode(), new RuntimeEventListener() {
    @Override
    public void onDelta(String content) {
        System.out.print(content);
    }

    @Override
    public void onApprovalRequested(RuntimeEventEnvelope event) {
        approvalUi.show(event);
    }
});
```

恢复与治理 API 包括：

1. `resumeTask`
2. `approveTask / rejectTask / revokeApproval`
3. `signalWait`
4. `downloadArtifact`
5. `listReplays / getReplay / createSimulationReplay`

Artifact 返回 `byte[]`、UTF-8 文件名、Content-Type 和大小；Replay 与 Approval 长操作使用 10 分钟窗口。所有 ID、分页、事件范围及路径参数在请求前统一校验。

验证命令：

```bash
cd /path/to/agent
mvn -pl agent-runtime-sdk-java test
```

## 8. Java 独立示例与发布验收

`agent-runtime-sdk-java-example` 是独立 Maven 宿主模块，可创建 Thread/Run、阻塞消费 SSE，并在收到 `waiting_approval` 后使用事件中的 `taskId + approvalId` 沿原 Checkpoint 继续。为避免 Token 出现在进程参数中，示例优先从 `AGENT_RUNTIME_TOKEN` 环境变量读取 JWT。

在项目根目录构建并运行：

```bash
mvn -pl agent-runtime-sdk-java-example -am package
java -jar agent-runtime-sdk-java-example/target/agent-runtime-sdk-java-example.jar
```

完整 macOS、Linux、Windows PowerShell 示例见 `agent-runtime-sdk-java-example/README.md`。

阶段发布门禁分两层：

1. `RuntimeSdkExampleContractTest` 使用真实 JDK HTTP Server 冻结 Thread、Run、SSE、审批、双游标续传、JWT、幂等键和优先级契约。
2. `scripts/test/run_platform_e2e.sh` 在隔离 pgvector、双后端和 Mock LLM 环境中运行打包后的示例 Jar，验证真实 Run、Runtime Event、Task 关联和终态。

平台持久化 Runtime Event 保证状态、审批、产物和游标事实；`message.delta` 只代表实时增量，不作为历史连接必定可重组最终正文的契约。宿主需要最终完整消息时，应在 Run 完成后查询 Thread 消息。

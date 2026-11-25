# Java Runtime SDK 示例

该模块是独立、可执行的 Java 17 宿主示例，演示以下完整链路：

1. 创建 Thread
2. 创建异步 Run
3. 通过 SSE 消费 Runtime Event
4. 使用 `afterSequence + Last-Event-ID` 续传
5. 在 `waiting_approval` 时提交审批并继续原 Run

## 构建

在项目根目录执行：

```bash
mvn -pl agent-runtime-sdk-java-example -am package
```

生成的可执行 Jar：

```text
agent-runtime-sdk-java-example/target/agent-runtime-sdk-java-example.jar
```

## 运行

macOS / Linux：

```bash
export AGENT_RUNTIME_TOKEN='<JWT>'
java -jar agent-runtime-sdk-java-example/target/agent-runtime-sdk-java-example.jar \
  --base-url http://127.0.0.1:8081/api/core \
  --agent-id 1 \
  --title 'Java SDK 示例' \
  --message '请介绍当前 Agent Runtime。' \
  --auto-approve true
```

Windows PowerShell：

```powershell
$env:AGENT_RUNTIME_TOKEN = '<JWT>'
java -jar agent-runtime-sdk-java-example/target/agent-runtime-sdk-java-example.jar `
  --base-url http://127.0.0.1:8081/api/core `
  --agent-id 1 `
  --title 'Java SDK 示例' `
  --message '请介绍当前 Agent Runtime。' `
  --auto-approve true
```

默认不会输出 JWT、Tool 参数或事件 Payload。生产接入时建议将 `--auto-approve` 设置为 `false`，由业务审批界面调用 SDK 的审批方法。

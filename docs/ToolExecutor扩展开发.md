# ToolExecutor 扩展开发

## 1. 适用范围

业务专用本地 Tool 不应继续写入 `agent-core` 通用运行模块。确需进程内执行时，应放在独立 Maven 模块或独立 Jar 中，通过 `ToolExecutor` SPI 注册；可远程部署的业务能力优先使用 `external_http` 或 `MCP`。

## 2. 接入步骤

1. 新建独立业务扩展模块，并依赖 `agent-core` 暴露的 Tool SPI
2. 实现 `ToolExecutor`，使用 Spring `@Component` 注册 Bean
3. `supports(toolCode)` 只声明本扩展实际拥有的 Tool code
4. 在 Registry 创建同 code 的 `internal` Tool 定义并绑定到 Agent
5. 将扩展 Jar 加入 `agent-core` 部署 classpath

```java
@Component
public class ExampleToolExecutor implements ToolExecutor {

    @Override
    public boolean supports(String toolCode) {
        return "EXAMPLE_TOOL".equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context,
                                       RegistryAgentTool tool,
                                       ToolInvocationRequest request) {
        // 调用业务服务并返回标准 ToolExecutionResult。
        throw new UnsupportedOperationException("请实现业务执行逻辑");
    }
}
```

## 3. 约束

1. 同一 Tool code 只能有一个 `ToolExecutor`，重复注册会返回 `TOOL_EXECUTOR_CONFLICT`
2. 扩展不得绕过 `ToolRouterService`，否则会丢失 schema 校验、Guardrail、Trace 和 fallback
3. 密钥不得写入扩展代码，统一通过 Registry Tool Provider 实例配置
4. 高考等 Demo Tool 后续按此机制迁出核心；迁移完成前保持现有 code 兼容
5. 新扩展优先实现接收 `ToolInvocationRequest` 的结构化重载；旧的 `String inputText` 方法仅用于兼容存量插件

## 4. Runtime Plugin 迁移状态

M4 前三个切片已冻结 `AgentRuntimePlugin` 和 `META-INF/agent-runtime-plugin.json` 声明格式，并通过 Java `ServiceLoader` 接入插件入口发现，详见 `docs/runtime/RuntimePlugin规范.md`。当前 `ToolExecutor` 仍按 Spring Bean 自动创建和执行，新插件需增加以下两个资源：

1. `META-INF/agent-runtime-plugin.json`：声明插件身份、版本、入口、能力、依赖和 Tool code
2. `META-INF/services/com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin`：填写入口实现类全限定名

`ToolExecutorRegistry` 会记录 Manifest Tool 归属，但 Tool 调用仍经过原 `ToolRouterService`，不会绕过参数校验、Guardrail、Trace、Runtime Event 和副作用治理。

启动时会校验每个 Manifest Tool 恰好匹配一个 `ToolExecutor`。未实现会抛出 `PLUGIN_TOOL_EXECUTOR_MISSING`，多个实现同时声明支持同一 Tool 会抛出 `PLUGIN_TOOL_EXECUTOR_CONFLICT`；插件编码、组件归属或依赖不合法时同样阻断启动，详细错误码见 `docs/runtime/RuntimePlugin规范.md`。

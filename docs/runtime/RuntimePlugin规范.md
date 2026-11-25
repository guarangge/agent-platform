# Runtime Plugin 规范

## 1. 当前阶段

M4 Runtime Plugin 阶段六个纵向切片已完成，已冻结 SPI、Manifest Java 模型和 JSON Schema，接入基于 Java `ServiceLoader` 的插件入口发现、启动校验、加载观测和真实 Docker 发布验证，并在 `registry-center` 增加插件定义、不可变版本、环境安装、Agent 绑定和审计能力。现有 `ToolExecutor` 仍由 Spring Bean 创建并经 `ToolRouterService` 执行，不引入 JVM ClassLoader 热卸载。

## 2. SPI

插件入口实现：

```java
public interface AgentRuntimePlugin {
    String PLATFORM_API_VERSION = "1";
    String MANIFEST_RESOURCE = "META-INF/agent-runtime-plugin.json";

    RuntimePluginManifest manifest();
}
```

约定：

1. 每个插件 Jar 使用唯一的 `pluginCode + version`
2. Manifest 固定放在 `META-INF/agent-runtime-plugin.json`
3. `entrypoint` 填写 `AgentRuntimePlugin` 实现类全限定名
4. 插件只能声明组件和能力，具体执行仍进入平台对应扩展点
5. 当前 `platformApiVersion` 为 `1`

当前重启加载链：

```text
PropertiesLauncher loader.path
  -> Context ClassLoader
     -> ServiceLoader<AgentRuntimePlugin>
        -> RuntimePluginDiscovery
           -> ToolExecutorRegistry 插件 Tool 归属映射
              -> 原 ToolExecutor Bean / ToolRouterService 执行链
```

插件 Jar 还必须提供 Java SPI Provider 文件：

```text
META-INF/services/com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin
```

文件内容为插件入口类全限定名，每行一个 Provider。

## 3. Manifest

完整示例：

```json
{
  "pluginCode": "example-plugin",
  "version": "1.0.0",
  "platformApiVersion": "1",
  "entrypoint": "com.example.ExampleAgentPlugin",
  "capabilities": [
    "filesystem.read"
  ],
  "dependencies": [
    {
      "pluginCode": "base-plugin",
      "versionConstraint": "^1.0.0",
      "optional": false
    }
  ],
  "components": {
    "tools": [
      "EXAMPLE_TOOL"
    ],
    "stepHandlers": [
      "example-step"
    ]
  }
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `pluginCode` | 是 | 全局唯一插件编码，使用小写字母、数字及 `-_.` 分隔 |
| `version` | 是 | 插件 SemVer 版本 |
| `platformApiVersion` | 是 | 目标平台 API 主版本 |
| `entrypoint` | 是 | 插件 SPI 实现类全限定名 |
| `capabilities` | 是 | 插件声明使用的能力编码，只允许九类 Runtime Capability，可为空数组 |
| `dependencies` | 是 | 插件依赖、版本约束和可选标记，可为空数组 |
| `components` | 是 | 按类型声明的稳定组件编码，可为空对象 |

JSON Schema 随 `agent-core` 发布，路径为 `META-INF/agent-runtime-plugin.schema.json`。

Capability 只允许 `filesystem.read / filesystem.write / network.read / network.write / process.execute / database.read / database.write / artifact.publish / external.side_effect`。声明表示插件可能触达的边界，不代表本次运行已授权。

## 4. 首批组件类型

1. `tools`
2. `toolProviders`
3. `stepHandlers`
4. `contextProviders`
5. `policies`
6. `hooks`
7. `skills`

未知组件类型、重复组件编码、重复 Capability 和重复依赖会被基础模型拒绝。跨插件组件冲突、依赖是否已安装及版本范围兼容由启动校验器统一处理。

## 5. 启动校验

`RuntimePluginDiscovery` 完成稳定排序后调用 `RuntimePluginValidator`，校验成功才允许 `ToolExecutorRegistry` 建立组件归属。校验顺序如下：

1. 平台 API 主版本兼容
2. Manifest `entrypoint` 与实际 SPI Provider 类一致
3. `pluginCode` 在当前进程唯一
4. 同一 `componentType + componentCode` 只能归属一个插件
5. 必需依赖必须存在，可选依赖缺失允许启动
6. 已安装依赖必须满足版本约束
7. 依赖图不能存在自环或多插件循环
8. Manifest 声明的每个 Tool 必须恰好匹配一个 Spring `ToolExecutor`

版本约束支持：

1. 精确版本：`1.2.3`
2. Caret：`^1.2.3`
3. Tilde：`~1.2.3`
4. 比较区间：`>=1.0.0 <2.0.0`
5. 通配符：`1.x`、`1.2.x`、`*`
6. 或关系：`1.x || 2.1.x`

稳定错误码：

| 错误码 | 含义 |
| --- | --- |
| `PLUGIN_MANIFEST_MISSING` | SPI Provider 未返回 Manifest |
| `PLUGIN_API_VERSION_INCOMPATIBLE` | 平台 API 主版本不兼容 |
| `PLUGIN_ENTRYPOINT_MISMATCH` | Manifest 入口与 Provider 类不一致 |
| `PLUGIN_CODE_CONFLICT` | 插件编码重复 |
| `PLUGIN_COMPONENT_CONFLICT` | 跨插件组件声明冲突 |
| `PLUGIN_DEPENDENCY_MISSING` | 必需依赖缺失 |
| `PLUGIN_VERSION_CONSTRAINT_INVALID` | 依赖版本约束无法解析 |
| `PLUGIN_DEPENDENCY_VERSION_MISMATCH` | 已安装依赖版本不满足约束 |
| `PLUGIN_DEPENDENCY_CYCLE` | 插件依赖存在循环 |
| `PLUGIN_TOOL_EXECUTOR_MISSING` | Manifest Tool 没有对应 Executor |
| `PLUGIN_TOOL_EXECUTOR_CONFLICT` | Manifest Tool 匹配多个 Executor |

启动失败时先根据错误码移除或替换问题插件 Jar，再重启 `agent-core`。本阶段不尝试跳过失败插件继续运行，避免部分组件注册造成不确定路由。

## 6. Registry 治理

`registry-center` 通过以下资源管理 Runtime Plugin：

1. `runtime_plugins`：项目作用域内唯一的插件定义和启停状态
2. `runtime_plugin_versions`：不可变 Manifest、制品 URI、SHA-256 和发布状态
3. `runtime_plugin_installations`：指定环境当前安装版本、期望状态和实际加载状态
4. `agent_runtime_plugin_rel`：Agent 到具体环境安装的无歧义绑定
5. `runtime_plugin_lifecycle_audits`：定义、版本、安装、启停和 Agent 绑定变更审计

管理接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET / POST` | `/api/registry/runtime-plugins` | 查询或创建插件定义 |
| `GET` | `/api/registry/runtime-plugins/{pluginId}` | 查询定义、版本、安装和审计 |
| `PUT` | `/api/registry/runtime-plugins/{pluginId}/status` | 启用或停用插件定义 |
| `POST` | `/api/registry/runtime-plugins/{pluginId}/versions` | 创建不可变版本 |
| `PUT` | `/api/registry/runtime-plugins/{pluginId}/versions/{versionId}/status` | 发布或废弃版本 |
| `POST` | `/api/registry/runtime-plugins/{pluginId}/installations` | 安装或替换指定环境版本 |
| `PUT` | `/api/registry/runtime-plugins/{pluginId}/installations/{installationId}/status` | 启用、停用或卸载环境安装 |
| `GET / PUT` | `/api/registry/agents/{agentId}/runtime-plugins` | 查询或覆盖更新 Agent 插件绑定 |
| `GET` | `/api/registry/runtime-plugins/runtime/installations?environmentCode={code}` | Core 查询环境期望安装 |
| `PUT` | `/api/registry/runtime-plugins/runtime/installations/{installationId}/load-result` | Core 回写实际加载结果 |

当前阶段安装、替换版本、启停和卸载都会把 `load_status` 设为 `pending_restart`，只表示 Registry 已接受期望状态，不宣称 JVM 已热加载或热卸载。已发布版本的 Manifest 和制品定位不允许修改；同一插件在同一环境只保留一个当前安装。替换、停用或卸载仍被 Agent 绑定的安装会被拒绝；同版本保持 `enabled` 的幂等重装仍允许。

## 7. Core 加载观测与对账

Core 使用 `CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE` 选择 Registry 环境，默认值为 `default`。应用就绪后执行一次对账，也可由管理员手动重试：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/core/runtime/plugins` | 查询总体状态、实际插件、依赖、组件和最近对账结果 |
| `GET` | `/api/core/runtime/plugins/{pluginCode}` | 查询单个实际加载插件 |
| `POST` | `/api/core/runtime/plugins/reconcile` | 重新读取 Registry 并回写加载结果 |

对账规则：

1. 插件编码不存在时回写 `PLUGIN_NOT_LOADED`
2. 实际版本与期望版本不同时回写 `PLUGIN_VERSION_MISMATCH`
3. 本地来源为普通 Jar 且可计算 SHA-256 时必须与 Registry 一致，否则回写 `PLUGIN_ARTIFACT_SHA256_MISMATCH`
4. IDE classes 目录等来源无法计算单一 Jar 摘要时保留 `loaded`，同时写入“摘要不可计算”观测消息
5. 本地已加载但 Registry 未登记、结果回写失败或期望插件不匹配时总体状态为 `DEGRADED`
6. Registry 暂时不可用时对账状态为 `failed`，不抛出启动异常，不改变 Core 存活状态
7. 必需依赖和版本错误已由启动校验阻断；观测接口额外展示允许缺失的可选依赖

Registry 对账接口只接受 `SERVICE_RUNTIME`，Core 观测接口只接受 `ROLE_ADMIN / SCOPE_tool`。错误摘要不包含 Token、插件参数、Prompt 或堆栈。

Docker 发布与回滚操作见 `docs/runtime/RuntimePlugin发布与回滚.md`。发布脚本必须同时挂载 Jar 和设置 `loader.path`，仅在 Registry 登记安装不会让 JVM 自动获取制品。

## 8. 不变量

1. Manifest 模型为不可变对象，集合不向调用方暴露可变引用
2. Manifest JSON 字段和组件类型编码一经发布保持向后兼容
3. 插件不得绕过 `ToolRouterService`、`RuntimeStepExecutor` 等现有治理入口
4. 声明 Capability 不代表自动授权；Capability Policy 在 M4 后续纵向切片统一执行
5. 第一阶段只支持随应用启动加载，配置变化通过重启生效
6. 当前切片不改变已有插件是否加载、Tool 路由、审批、Checkpoint 和 Runtime Event 语义
7. `RuntimePluginDiscovery` 使用线程上下文 ClassLoader，以兼容 Spring Boot `PropertiesLauncher loader.path`
8. `ToolExecutorRegistry` 保留内置和存量 Tool Bean，同时记录 Manifest 声明的 Tool 与插件归属
9. 跨插件组件冲突、依赖错误和声明 Tool 实现异常必须在启动期阻断，不允许延迟到首次调用
10. 校验错误详情只包含插件编码、版本、组件和入口类，不记录密钥、Tool 参数或 Prompt

11. Registry 只保存插件定义和期望安装状态，不直接执行插件代码
12. Agent 只能绑定定义已启用、版本已发布且安装已启用的记录
13. 制品 SHA-256 必填，插件密钥、Tool 参数和 Prompt 正文不进入审计快照
14. Registry 保存期望状态，Core 回写实际状态；两服务不跨库读写
15. Registry 对账失败不得阻断 Core 启动或把 Actuator 存活状态置为 DOWN

## 9. 验证

`RuntimePluginManifestContractTest` 验证：

1. Manifest JSON 可无损读取和写回
2. Capability、Dependency 和 Component 集合不可变
3. 非法基础字段、重复声明和未知组件类型明确失败
4. 发布的 JSON Schema 可解析并覆盖全部七类组件

`RuntimePluginDiscoveryTest / ToolExecutorRegistryTest` 验证：

1. 独立 ClassLoader 中的 Java SPI Provider 可被发现
2. 插件按编码、版本和入口类稳定排序
3. Manifest Tool 归属可查询且不可变
4. 接入插件元数据后仍返回原 `ToolExecutor` 实例

`RuntimePluginVersionMatcherTest / RuntimePluginValidatorTest` 验证：

1. 精确、Caret、Tilde、比较区间、通配符、或关系和预发布版本排序
2. 平台 API 不兼容、入口不一致和重复插件编码明确失败
3. 组件冲突、Tool Executor 缺失或重复在启动期失败
4. 必需依赖缺失、版本不匹配、非法约束和依赖环明确失败
5. 可选依赖缺失不阻断启动

`RuntimePluginServiceTest / RuntimePluginRepositoryTest` 验证：

1. Registry 从请求生成与 Runtime 一致的不可变 Manifest
2. 不支持的依赖版本约束在持久化前失败
3. 已发布版本安装后返回 `pending_restart`
4. Agent 只能绑定已启用的发布版本安装
5. Core 加载结果可回写实际编码、版本、摘要和生命周期审计

`RuntimePluginObservationServiceTest / RuntimePluginRegistryReconcilerTest` 验证：

1. 实际插件、可选依赖缺失和 Tool 实现注册可被稳定观测
2. 匹配、缺失和本地未登记插件生成正确对账计数及回写结果
3. Registry 不可用时生成 `failed` 观测但不向启动链抛出异常

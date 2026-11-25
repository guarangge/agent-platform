# 高考志愿 Tool 插件

该模块承载 `SCORE_ANALYSIS / DOC_SEARCH / GAOKAO_POLICY_SEARCH / GAOKAO_UNIVERSITY_SEARCH / GAOKAO_MAJOR_SEARCH` 的领域实现和知识样例，不属于通用 `agent-core`。

该模块已接入统一 Runtime Plugin SPI：

1. 插件入口：`com.zc.agent.plugin.gaokao.GaokaoAgentRuntimePlugin`
2. Manifest：`META-INF/agent-runtime-plugin.json`
3. Java SPI Provider：`META-INF/services/com.zc.agent.core.modules.runtime.plugin.AgentRuntimePlugin`
4. 插件编码：`gaokao-tool-plugin`

构建命令（项目根目录）：

```bash
mvn -pl agent-tool-plugin-gaokao -am package
```

部署时将 `agent-tool-plugin-gaokao-0.0.1-SNAPSHOT.jar` 放入独立插件目录，并使用 `agent-core-0.0.1-SNAPSHOT-exec.jar` 的 Spring Boot `PropertiesLauncher` 加载：

```bash
java -Dloader.path=./plugins -jar agent-core-0.0.1-SNAPSHOT-exec.jar
```

未安装该插件时，注册中心可保留 Tool 定义，但运行时健康检查会明确返回“未找到本地 Tool 执行器”。普通 `agent-core-0.0.1-SNAPSHOT.jar` 保留为插件编译所需的 API Jar，不用于启动服务。

启动时 `RuntimePluginDiscovery` 会从线程上下文 ClassLoader 发现该入口，`ToolExecutorRegistry` 将 5 个 Tool code 记录为 `gaokao-tool-plugin` 所有；实际调用继续使用现有 Spring `ToolExecutor` Bean。

本地 Docker 发布脚本已自动构建并只读挂载该 Jar：

```bash
cd /path/to/agent
CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE=default bash scripts/deploy/recreate_local_agent_services.sh
```

发布前仍需在 Registry 登记与实际 Jar 一致的版本、SHA-256 和目标环境安装。完整流程见 `docs/runtime/RuntimePlugin发布与回滚.md`。

领域文档目录通过插件配置 `agent.plugin.gaokao.doc-search-root` 或环境变量映射配置，不进入 `agent-core` 的通用 Tool 配置。

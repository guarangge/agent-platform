# Runtime Plugin 发布与回滚

## 1. 发布结论

Runtime Plugin 当前采用“构建 Jar -> Registry 登记不可变版本 -> 挂载插件目录 -> 重启 Core -> 自动对账”的发布方式，不支持 JVM 热加载或热卸载。

本地 Docker 发布脚本 `scripts/deploy/recreate_local_agent_services.sh` 已负责：

1. 构建 `agent-core`、`registry-center` 和 `agent-tool-plugin-gaokao`
2. 将插件 Jar 只读挂载到 `/app/plugins/agent-tool-plugin-gaokao.jar`
3. 设置 `-Dloader.path=/app/plugins`
4. 设置 `CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE`
5. 等待服务健康并检查 Core 启动日志已发现插件

## 2. 发布前登记

在项目根目录计算待发布 Jar 摘要。

macOS：

```bash
cd /path/to/agent
shasum -a 256 agent-tool-plugin-gaokao/target/agent-tool-plugin-gaokao-0.0.1-SNAPSHOT.jar
```

Linux：

```bash
cd /path/to/agent
sha256sum agent-tool-plugin-gaokao/target/agent-tool-plugin-gaokao-0.0.1-SNAPSHOT.jar
```

在 Registry 依次完成：

1. 创建或启用 `gaokao-tool-plugin` 定义
2. 使用实际版本、Manifest、制品 URI 和上述 SHA-256 创建 `published` 版本
3. 为目标 `environmentCode` 安装该版本并保持 `enabled`
4. 确认安装状态进入 `pending_restart`

`environmentCode` 必须与 Core 的 `CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE` 完全一致。

## 3. Docker 发布

macOS / Linux，在项目根目录执行：

```bash
cd /path/to/agent
CORE_RUNTIME_PLUGIN_ENVIRONMENT_CODE=default bash scripts/deploy/recreate_local_agent_services.sh
```

脚本成功后检查：

```bash
docker logs --tail 200 agent-core | grep 'gaokao-tool-plugin:0.0.1-SNAPSHOT'
docker inspect agent-core --format '{{range .Mounts}}{{println .Destination .Source}}{{end}}' | grep '/app/plugins/'
```

管理员可调用以下接口确认或手动重试对账：

```bash
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://localhost:8081/api/core/runtime/plugins

curl -X POST \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{}' \
  http://localhost:8081/api/core/runtime/plugins/reconcile
```

发布成功标准：

1. `plugins[].pluginCode` 包含 `gaokao-tool-plugin`
2. `plugins[].artifactSha256` 与 Registry 版本登记值一致
3. `registrySync.status` 为 `succeeded`
4. Registry 安装的 `load_status` 为 `loaded`
5. 生命周期审计包含 `report_load_result`

## 4. 回滚

回滚不覆盖已发布版本，也不删除审计：

1. 在 Registry 选择上一已发布版本，重新安装到同一环境
2. 确认安装状态变为 `pending_restart`
3. 将对应旧 Jar 挂载到 `/app/plugins` 的稳定文件名
4. 重启 `agent-core`
5. 确认 Core 对账为 `succeeded`，Registry 回写为 `loaded`

若新 Jar 导致 Core 启动校验失败，应先恢复旧 Jar 再重启。不要尝试在运行中的 JVM 内替换 Jar；不要把 `pending_restart` 手工改成 `loaded`。

## 5. 自动验证

平台 E2E 会构建真实插件 Jar，通过 `PropertiesLauncher` 装载，并经管理 API 创建版本和环境安装，最后验证 SHA-256、`loaded` 状态及租户内加载审计：

```bash
cd /path/to/agent
PLATFORM_E2E_POSTGRES_IMAGE=pgvector/pgvector:pg16 \
  bash scripts/test/run_platform_e2e.sh
```

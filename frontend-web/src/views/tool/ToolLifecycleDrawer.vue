<template>
  <el-drawer :model-value="modelValue" title="Tool / MCP 生命周期治理" size="82%" @close="emit('update:modelValue', false)">
    <div class="lifecycle-toolbar">
      <div>
        <strong>{{ toolName || `Tool #${toolId}` }}</strong>
        <span>版本、灰度实例、迁移、下线影响与审计统一管理</span>
      </div>
      <div>
        <el-button @click="openDiscovery">远程发现</el-button>
        <el-button @click="openCatalog">新建 Catalog</el-button>
        <el-button type="primary" @click="openVersion">新建版本</el-button>
        <el-button type="primary" plain :disabled="!lifecycle.versions.length" @click="openDeployment">部署实例</el-button>
        <el-button type="danger" plain @click="retireTool">安全下线</el-button>
      </div>
    </div>

    <el-alert
      :title="impactTitle"
      :type="impactCount > 0 ? 'warning' : 'success'"
      :description="impactDescription"
      show-icon
      :closable="false"
      class="impact-alert"
    />

    <el-tabs v-model="activeTab" v-loading="loading">
      <el-tab-pane label="部署实例" name="deployments">
        <el-table :data="lifecycle.deployments" border stripe>
          <el-table-column prop="deployment_name" label="实例" min-width="150" />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column prop="environment_code" label="环境" width="100" />
          <el-table-column prop="transport_type" label="协议" min-width="130" />
          <el-table-column prop="traffic_weight" label="权重" width="80" />
          <el-table-column prop="rollout_percent" label="灰度" width="90">
            <template #default="{ row }">{{ row.rollout_percent }}%</template>
          </el-table-column>
          <el-table-column prop="failure_threshold" label="熔断阈值" width="100" />
          <el-table-column prop="recovery_seconds" label="恢复等待" width="100">
            <template #default="{ row }">{{ row.recovery_seconds }}s</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'enabled' ? 'success' : row.status === 'draining' ? 'warning' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editRoute(row)">灰度配置</el-button>
              <el-button v-if="row.status === 'enabled'" link type="warning" @click="startDraining(row)">Draining</el-button>
              <el-button v-else link type="success" @click="enableDeployment(row)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
        <h4 v-if="runtimeGovernance.states.length">实例运行状态</h4>
        <el-table v-if="runtimeGovernance.states.length" :data="runtimeGovernance.states" border size="small">
          <el-table-column prop="deployment_code" label="实例" />
          <el-table-column prop="circuit_state" label="Circuit" />
          <el-table-column prop="consecutive_failures" label="连续失败" />
          <el-table-column prop="total_successes" label="成功总数" />
          <el-table-column prop="total_failures" label="失败总数" />
          <el-table-column prop="next_probe_at" label="下次恢复探测" min-width="180" />
          <el-table-column prop="last_error" label="最近错误" min-width="220" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="版本与迁移" name="versions">
        <el-table :data="lifecycle.versions" border>
          <el-table-column prop="version" label="版本" width="130" />
          <el-table-column prop="compatibility" label="兼容性" width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="release_notes" label="发布说明" min-width="240" />
          <el-table-column prop="published_at" label="发布时间" min-width="180" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }"><el-button link type="primary" @click="openMigration(row.id)">迁移</el-button></template>
          </el-table-column>
        </el-table>
        <h4>迁移记录</h4>
        <el-table :data="lifecycle.migrations" border size="small">
          <el-table-column prop="source_version" label="源版本" />
          <el-table-column prop="target_version" label="目标版本" />
          <el-table-column prop="strategy" label="策略" />
          <el-table-column prop="rollout_percent" label="灰度比例" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="created_at" label="创建时间" min-width="180" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="安装与发现" name="catalogs">
        <el-table :data="catalogs" border>
          <el-table-column prop="catalog_name" label="Catalog" min-width="180" />
          <el-table-column prop="catalog_type" label="类型" width="120" />
          <el-table-column prop="transport_type" label="协议" width="140" />
          <el-table-column prop="source_url" label="来源" min-width="260" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="last_discovered_at" label="最近发现" min-width="180" />
          <el-table-column prop="last_error" label="最近错误" min-width="220" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.config?.template" link type="primary" @click="applyCatalogTemplate(row)">使用模板</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="discoveredItems.length" class="discovery-result">
          <h4>最近发现结果</h4>
          <el-table :data="discoveredItems" border size="small">
            <el-table-column prop="remote_name" label="远程名称" />
            <el-table-column prop="display_name" label="展示名称" />
            <el-table-column prop="description" label="描述" min-width="260" />
            <el-table-column prop="remote_version" label="版本" />
            <el-table-column label="操作"><template #default="{ row }"><el-button v-if="row.id" link type="primary" @click="openInstall(row)">安装</el-button></template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="审计记录" name="audits">
        <el-table :data="lifecycle.audits" border size="small">
          <el-table-column prop="action" label="动作" width="150" />
          <el-table-column prop="deployment_id" label="实例 ID" width="100" />
          <el-table-column prop="result" label="结果" width="100" />
          <el-table-column prop="message" label="说明" min-width="260" />
          <el-table-column prop="operator_id" label="操作人" width="100" />
          <el-table-column prop="created_at" label="时间" min-width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="versionVisible" title="创建不可变版本" width="620px" append-to-body>
      <el-form :model="versionForm" label-width="110px">
        <el-form-item label="版本号"><el-input v-model="versionForm.version" placeholder="例如 2.0.0" /></el-form-item>
        <el-form-item label="兼容性"><el-select v-model="versionForm.compatibility"><el-option label="兼容" value="compatible" /><el-option label="破坏性变更" value="breaking" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="versionForm.status"><el-option label="草稿" value="draft" /><el-option label="发布" value="published" /></el-select></el-form-item>
        <el-form-item label="Input Schema"><el-input v-model="versionForm.inputSchema" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="Output Schema"><el-input v-model="versionForm.outputSchema" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="调用配置"><el-input v-model="versionForm.invokeConfig" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="发布说明"><el-input v-model="versionForm.releaseNotes" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="versionVisible = false">取消</el-button><el-button type="primary" @click="submitVersion">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="deploymentVisible" title="部署 Tool 实例" width="680px" append-to-body>
      <el-form :model="deploymentForm" label-width="110px">
        <el-form-item label="版本"><el-select v-model="deploymentForm.versionId"><el-option v-for="item in lifecycle.versions" :key="item.id" :label="item.version" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="实例编码"><el-input v-model="deploymentForm.deploymentCode" /></el-form-item>
        <el-form-item label="实例名称"><el-input v-model="deploymentForm.deploymentName" /></el-form-item>
        <el-form-item label="环境"><el-input v-model="deploymentForm.environmentCode" /></el-form-item>
        <el-form-item label="Provider / 协议"><div class="inline-fields"><el-select v-model="deploymentForm.providerType"><el-option label="MCP" value="mcp" /><el-option label="External HTTP" value="external_http" /><el-option label="Internal" value="internal" /></el-select><el-select v-model="deploymentForm.transportType"><el-option label="Streamable HTTP" value="streamable_http" /><el-option label="stdio" value="stdio" /><el-option label="Legacy SSE" value="legacy_sse" /><el-option label="External HTTP" value="external_http" /></el-select></div></el-form-item>
        <el-form-item label="Endpoint"><el-input v-model="deploymentForm.endpoint" /></el-form-item>
        <el-form-item label="权重 / 灰度"><div class="inline-fields"><el-input-number v-model="deploymentForm.trafficWeight" :min="0" :max="10000" /><el-input-number v-model="deploymentForm.rolloutPercent" :min="0" :max="100" /></div></el-form-item>
        <el-form-item label="熔断 / 恢复"><div class="inline-fields"><el-input-number v-model="deploymentForm.failureThreshold" :min="1" /><el-input-number v-model="deploymentForm.recoverySeconds" :min="1" /></div></el-form-item>
        <el-form-item label="认证配置"><el-input v-model="deploymentForm.authConfig" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="请求头"><el-input v-model="deploymentForm.headers" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="调用配置"><el-input v-model="deploymentForm.invokeConfig" type="textarea" :rows="4" placeholder='stdio 示例：{"command":["npx","-y","server"]}' /></el-form-item>
      </el-form>
      <template #footer><el-button @click="deploymentVisible = false">取消</el-button><el-button type="primary" @click="submitDeployment">部署</el-button></template>
    </el-dialog>

    <el-dialog v-model="routeVisible" title="灰度流量与熔断策略" width="520px" append-to-body>
      <el-form :model="routeForm" label-width="110px">
        <el-form-item label="流量权重"><el-input-number v-model="routeForm.trafficWeight" :min="0" :max="10000" /></el-form-item>
        <el-form-item label="灰度比例"><el-slider v-model="routeForm.rolloutPercent" show-input /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="routeForm.priority" /></el-form-item>
        <el-form-item label="失败阈值"><el-input-number v-model="routeForm.failureThreshold" :min="1" /></el-form-item>
        <el-form-item label="恢复等待"><el-input-number v-model="routeForm.recoverySeconds" :min="1" /> 秒</el-form-item>
        <el-form-item label="状态"><el-select v-model="routeForm.status"><el-option label="启用" value="enabled" /><el-option label="Draining" value="draining" /><el-option label="停用" value="disabled" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="routeVisible = false">取消</el-button><el-button type="primary" @click="submitRoute">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="migrationVisible" title="版本迁移" width="520px" append-to-body>
      <el-form :model="migrationForm" label-width="110px">
        <el-form-item label="源版本"><el-select v-model="migrationForm.sourceVersionId" clearable><el-option v-for="item in lifecycle.versions" :key="item.id" :label="item.version" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="目标版本"><el-select v-model="migrationForm.targetVersionId"><el-option v-for="item in lifecycle.versions" :key="item.id" :label="item.version" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="策略"><el-select v-model="migrationForm.strategy"><el-option label="灰度迁移" value="canary" /><el-option label="立即切换" value="immediate" /><el-option label="强制迁移" value="force" /></el-select></el-form-item>
        <el-form-item label="灰度比例"><el-slider v-model="migrationForm.rolloutPercent" show-input /></el-form-item>
      </el-form>
      <template #footer><el-button @click="migrationVisible = false">取消</el-button><el-button type="primary" @click="submitMigration">执行迁移</el-button></template>
    </el-dialog>

    <el-dialog v-model="discoveryVisible" title="远程 tools/list 发现" width="650px" append-to-body>
      <el-form :model="discoveryForm" label-width="110px">
        <el-form-item label="Catalog"><el-select v-model="discoveryForm.catalogId" clearable><el-option v-for="item in catalogs" :key="String(item.id)" :label="String(item.catalog_name)" :value="Number(item.id)" /></el-select></el-form-item>
        <el-form-item label="Provider"><el-select v-model="discoveryForm.providerType"><el-option label="MCP" value="mcp" /><el-option label="External HTTP" value="external_http" /></el-select></el-form-item>
        <el-form-item label="传输协议"><el-select v-model="discoveryForm.transportType"><el-option label="Streamable HTTP" value="streamable_http" /><el-option label="stdio" value="stdio" /><el-option label="Legacy SSE" value="legacy_sse" /><el-option label="External HTTP" value="external_http" /></el-select></el-form-item>
        <el-form-item label="Endpoint"><el-input v-model="discoveryForm.endpoint" /></el-form-item>
        <el-form-item label="认证配置"><el-input v-model="discoveryForm.authConfig" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="请求头"><el-input v-model="discoveryForm.headers" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="调用配置"><el-input v-model="discoveryForm.invokeConfig" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="discoveryVisible = false">取消</el-button><el-button type="primary" @click="submitDiscovery">开始发现</el-button></template>
    </el-dialog>

    <el-dialog v-model="catalogVisible" title="新建 Tool Catalog" width="520px" append-to-body>
      <el-form :model="catalogForm" label-width="110px">
        <el-form-item label="编码"><el-input v-model="catalogForm.catalogCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="catalogForm.catalogName" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="catalogForm.catalogType"><el-option label="Registry" value="registry" /><el-option label="MCP Server" value="mcp_server" /></el-select></el-form-item>
        <el-form-item label="来源地址"><el-input v-model="catalogForm.sourceUrl" /></el-form-item>
        <el-form-item label="传输协议"><el-input v-model="catalogForm.transportType" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="catalogVisible = false">取消</el-button><el-button type="primary" @click="submitCatalog">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="installVisible" title="安装为平台 Tool" width="560px" append-to-body>
      <el-form :model="installForm" label-width="110px">
        <el-form-item label="Tool 编码"><el-input v-model="installForm.toolCode" /></el-form-item>
        <el-form-item label="Tool 名称"><el-input v-model="installForm.toolName" /></el-form-item>
        <el-form-item label="类型 / 分类"><div class="inline-fields"><el-input v-model="installForm.toolType" /><el-input v-model="installForm.toolCategory" /></div></el-form-item>
        <el-form-item label="版本"><el-input v-model="installForm.version" /></el-form-item>
        <el-form-item label="Endpoint"><el-input v-model="installForm.endpoint" /></el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="installForm.authType">
            <el-option label="无认证" value="none" />
            <el-option label="Bearer Token" value="bearer" />
            <el-option label="API Key" value="api_key" />
            <el-option label="Basic Auth" value="basic" />
            <el-option label="自定义 Header" value="custom_header" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境"><el-input v-model="installForm.environmentCode" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="installVisible = false">取消</el-button><el-button type="primary" @click="submitInstall">安装</el-button></template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { fetchToolRuntimeGovernance } from "@/api/toolDebug";
import {
  createToolCatalog, createToolDeployment, createToolVersion, discoverRemoteTools, fetchToolCatalogs,
  fetchToolLifecycle, installCatalogTool, migrateToolVersion, retireToolLifecycle, updateToolDeploymentRoute,
  type ToolDeploymentPayload, type ToolDeploymentRecord, type ToolLifecycleData
} from "@/api/tool";

const props = defineProps<{ modelValue: boolean; toolId: number | null; toolName?: string }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
const emptyLifecycle = (): ToolLifecycleData => ({ versions: [], deployments: [], migrations: [], audits: [], impact: {} });
const lifecycle = reactive<ToolLifecycleData>(emptyLifecycle());
const catalogs = ref<Array<Record<string, unknown>>>([]);
const discoveredItems = ref<Array<Record<string, unknown>>>([]);
const runtimeGovernance = reactive({ states: [] as Array<Record<string, unknown>>, audits: [] as Array<Record<string, unknown>> });
const loading = ref(false);
const activeTab = ref("deployments");
const versionVisible = ref(false); const deploymentVisible = ref(false); const routeVisible = ref(false);
const migrationVisible = ref(false); const discoveryVisible = ref(false); const catalogVisible = ref(false);
const installVisible = ref(false);
const editingDeploymentId = ref<number | null>(null);
const impactCount = computed(() => Number(lifecycle.impact.agent_count || 0) + Number(lifecycle.impact.workflow_count || 0));
const impactTitle = computed(() => impactCount.value ? `下线影响：存在 ${impactCount.value} 个依赖` : "下线影响：当前无 Agent / Workflow 依赖");
const impactDescription = computed(() => `Agent ${lifecycle.impact.agent_count || 0}，Workflow ${lifecycle.impact.workflow_count || 0}，活跃实例 ${lifecycle.impact.active_deployment_count || 0}`);

const versionForm = reactive({ version: "", compatibility: "compatible", status: "draft", inputSchema: "{}", outputSchema: "{}", invokeConfig: "{}", releaseNotes: "" });
const deploymentForm = reactive({ versionId: 0, deploymentCode: "", deploymentName: "", environmentCode: "prod", providerType: "mcp", transportType: "streamable_http", endpoint: "", authType: "none", authConfig: "{}", headers: "{}", invokeConfig: "{}", trafficWeight: 100, rolloutPercent: 100, priority: 100, timeoutSeconds: 30, retryTimes: 0, failureThreshold: 3, recoverySeconds: 60 });
const routeForm = reactive({ trafficWeight: 100, rolloutPercent: 100, priority: 100, failureThreshold: 3, recoverySeconds: 60, status: "enabled" });
const migrationForm = reactive<{ sourceVersionId: number | null; targetVersionId: number; strategy: string; rolloutPercent: number }>({ sourceVersionId: null, targetVersionId: 0, strategy: "canary", rolloutPercent: 10 });
const discoveryForm = reactive<{ catalogId: number | null; providerType: string; transportType: string; endpoint: string; authConfig: string; headers: string; invokeConfig: string }>({ catalogId: null, providerType: "mcp", transportType: "streamable_http", endpoint: "", authConfig: "{}", headers: "{}", invokeConfig: "{}" });
const catalogForm = reactive({ catalogCode: "", catalogName: "", catalogType: "registry", sourceUrl: "", transportType: "streamable_http" });
const installForm = reactive({ catalogItemId: 0, toolCode: "", toolName: "", toolType: "function", toolCategory: "general", permissionLevel: "default", version: "1.0.0", providerType: "mcp", transportType: "streamable_http", endpoint: "", authType: "none", environmentCode: "prod", authConfig: {}, headers: {}, invokeConfig: {} });

watch(() => [props.modelValue, props.toolId], async ([visible]) => { if (visible) await load(); }, { immediate: true });
async function load() { if (!props.toolId) return; loading.value = true; try { const [data, catalogData, runtimeData] = await Promise.all([fetchToolLifecycle(props.toolId), fetchToolCatalogs(), fetchToolRuntimeGovernance(props.toolId)]); Object.assign(lifecycle, data); catalogs.value = catalogData; Object.assign(runtimeGovernance, runtimeData); } catch { ElMessage.error("生命周期数据加载失败"); } finally { loading.value = false; } }
function parseJson(value: string) { try { return JSON.parse(value || "{}"); } catch { throw new Error("JSON 配置格式不正确"); } }
function openVersion() { versionVisible.value = true; }
function openDeployment() { deploymentForm.versionId = lifecycle.versions[0]?.id || 0; deploymentVisible.value = true; }
function openMigration(targetId: number) { migrationForm.targetVersionId = targetId; migrationVisible.value = true; }
function openDiscovery() { discoveryVisible.value = true; }
function applyCatalogTemplate(row: Record<string, unknown>) {
  const config = (row.config || {}) as Record<string, unknown>;
  const discovery = (config.discovery || {}) as Record<string, unknown>;
  Object.assign(discoveryForm, {
    catalogId: Number(row.id),
    providerType: String(discovery.providerType || "mcp"),
    transportType: String(discovery.transportType || row.transport_type || "streamable_http"),
    endpoint: String(discovery.endpoint || row.source_url || ""),
    authConfig: JSON.stringify(discovery.authConfig || {}, null, 2),
    headers: JSON.stringify(discovery.headers || {}, null, 2),
    invokeConfig: JSON.stringify(discovery.invokeConfig || {}, null, 2)
  });
  discoveryVisible.value = true;
}
function openCatalog() { catalogVisible.value = true; }
function openInstall(row: Record<string, unknown>) { const authConfig = parseJson(discoveryForm.authConfig); Object.assign(installForm, { catalogItemId: Number(row.id), toolCode: String(row.remote_name || "").toUpperCase().replace(/[^A-Z0-9]+/g, "_"), toolName: String(row.display_name || row.remote_name || ""), version: String(row.remote_version || "1.0.0"), providerType: discoveryForm.providerType, transportType: discoveryForm.transportType, endpoint: discoveryForm.endpoint, authType: authConfig.bearerToken || authConfig.token ? "bearer" : "none", authConfig, headers: parseJson(discoveryForm.headers), invokeConfig: parseJson(discoveryForm.invokeConfig) }); installVisible.value = true; }
function editRoute(row: ToolDeploymentRecord) { editingDeploymentId.value = row.id; Object.assign(routeForm, { trafficWeight: row.traffic_weight, rolloutPercent: row.rollout_percent, priority: row.priority, failureThreshold: row.failure_threshold, recoverySeconds: row.recovery_seconds, status: row.status }); routeVisible.value = true; }
async function submitVersion() { if (!props.toolId || !versionForm.version) return ElMessage.warning("请输入版本号"); try { await createToolVersion(props.toolId, { ...versionForm, inputSchema: parseJson(versionForm.inputSchema), outputSchema: parseJson(versionForm.outputSchema), invokeConfig: parseJson(versionForm.invokeConfig) }); versionVisible.value = false; await load(); ElMessage.success("版本已创建"); } catch (error) { ElMessage.error(error instanceof Error ? error.message : "创建失败"); } }
async function submitDeployment() { if (!props.toolId || !deploymentForm.versionId || !deploymentForm.deploymentCode) return ElMessage.warning("请填写版本和实例编码"); try { const payload: ToolDeploymentPayload = { ...deploymentForm, authConfig: parseJson(deploymentForm.authConfig), headers: parseJson(deploymentForm.headers), invokeConfig: parseJson(deploymentForm.invokeConfig), status: "enabled", metadata: {} }; await createToolDeployment(props.toolId, payload); deploymentVisible.value = false; await load(); ElMessage.success("实例已部署"); } catch (error) { ElMessage.error(error instanceof Error ? error.message : "部署失败"); } }
async function submitRoute() { if (!props.toolId || !editingDeploymentId.value) return; await updateToolDeploymentRoute(props.toolId, editingDeploymentId.value, routeForm); routeVisible.value = false; await load(); ElMessage.success("路由策略已更新"); }
async function startDraining(row: ToolDeploymentRecord) { await ElMessageBox.confirm("Draining 后不再接收新流量，确认继续？", "实例下线"); editingDeploymentId.value = row.id; Object.assign(routeForm, { trafficWeight: 0, rolloutPercent: 0, priority: row.priority, failureThreshold: row.failure_threshold, recoverySeconds: row.recovery_seconds, status: "draining" }); await submitRoute(); }
async function enableDeployment(row: ToolDeploymentRecord) { editingDeploymentId.value = row.id; Object.assign(routeForm, { trafficWeight: Math.max(1, row.traffic_weight), rolloutPercent: Math.max(1, row.rollout_percent), priority: row.priority, failureThreshold: row.failure_threshold, recoverySeconds: row.recovery_seconds, status: "enabled" }); await submitRoute(); }
async function submitMigration() { if (!props.toolId || !migrationForm.targetVersionId) return; await migrateToolVersion(props.toolId, migrationForm); migrationVisible.value = false; await load(); ElMessage.success("迁移任务已执行"); }
async function submitDiscovery() { try { const result = await discoverRemoteTools({ ...discoveryForm, authConfig: parseJson(discoveryForm.authConfig), headers: parseJson(discoveryForm.headers), invokeConfig: parseJson(discoveryForm.invokeConfig) }); discoveredItems.value = (result.items as Array<Record<string, unknown>>) || []; discoveryVisible.value = false; activeTab.value = "catalogs"; await load(); ElMessage.success(`发现 ${result.count || 0} 个 Tool`); } catch (error) { ElMessage.error(error instanceof Error ? error.message : "发现失败"); } }
async function submitCatalog() { await createToolCatalog({ ...catalogForm, config: {}, status: "enabled" }); catalogVisible.value = false; await load(); ElMessage.success("Catalog 已创建"); }
async function submitInstall() { await installCatalogTool(installForm); installVisible.value = false; emit("update:modelValue", false); ElMessage.success("Tool 已安装，可在列表中绑定 Agent"); }
async function retireTool() { if (!props.toolId) return; await ElMessageBox.confirm("仅当依赖为零且所有实例已停用时允许下线，确认继续？", "安全下线"); try { await retireToolLifecycle(props.toolId); emit("update:modelValue", false); ElMessage.success("Tool 已安全下线"); } catch { ElMessage.error("下线校验失败，请先处理依赖和实例状态"); } }
</script>

<style scoped>
.lifecycle-toolbar { display: flex; justify-content: space-between; gap: 20px; align-items: center; margin-bottom: 16px; }
.lifecycle-toolbar strong { display: block; color: #17324d; font-size: 18px; }
.lifecycle-toolbar span { color: #6b7f93; font-size: 13px; }
.impact-alert { margin-bottom: 16px; }
.inline-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; width: 100%; }
.discovery-result { margin-top: 20px; }
h4 { color: #29465f; margin: 20px 0 10px; }
@media (max-width: 900px) { .lifecycle-toolbar { align-items: flex-start; flex-direction: column; } .inline-fields { grid-template-columns: 1fr; } }
</style>

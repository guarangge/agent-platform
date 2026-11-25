<template>
  <section class="sandbox-page">
    <header class="page-hero">
      <div>
        <span class="eyebrow">ISOLATED RUNTIME</span>
        <h1>Sandbox 控制台</h1>
        <p>Docker 强隔离执行、资源策略与完整审计。平台不会回退到宿主机执行。</p>
      </div>
      <div class="security-badges">
        <el-tag effect="dark" type="success">只读 RootFS</el-tag>
        <el-tag effect="dark">非 root</el-tag>
        <el-tag effect="dark" type="warning">CapDrop ALL</el-tag>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="content-card">
      <el-tab-pane label="安全配置" name="profiles">
        <div class="toolbar">
          <el-input v-model="profileQuery.keyword" clearable placeholder="配置编码 / 名称" @keyup.enter="loadProfiles" />
          <el-select v-model="profileQuery.status" clearable placeholder="全部状态">
            <el-option label="启用" value="enabled" /><el-option label="停用" value="disabled" />
          </el-select>
          <el-button type="primary" @click="loadProfiles">查询</el-button>
          <el-button class="push-right" type="primary" plain @click="openProfile()">新增配置</el-button>
        </div>
        <el-table :data="profiles" border stripe v-loading="loadingProfiles">
          <el-table-column prop="profileCode" label="配置编码" min-width="160" />
          <el-table-column prop="profileName" label="配置名称" min-width="170" />
          <el-table-column prop="image" label="固定版本镜像" min-width="220" show-overflow-tooltip />
          <el-table-column label="隔离策略" min-width="190">
            <template #default="{ row }"><span class="mono">net={{ row.networkMode }} · {{ formatMemory(row.memoryBytes) }} · {{ formatCpu(row.nanoCpus) }} CPU</span></template>
          </el-table-column>
          <el-table-column label="允许命令" min-width="220"><template #default="{ row }">{{ row.allowedCommands.join(", ") }}</template></el-table-column>
          <el-table-column prop="timeoutSeconds" label="超时" width="90"><template #default="{ row }">{{ row.timeoutSeconds }}s</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="openProfile(row)">编辑</el-button><el-button link type="primary" @click="openExecute(row)">执行测试</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination class="pagination" layout="total, prev, pager, next" :total="profileTotal" :page-size="20" v-model:current-page="profileQuery.pageNum" @current-change="loadProfiles" />
      </el-tab-pane>

      <el-tab-pane label="执行审计" name="audits">
        <div class="toolbar">
          <el-input v-model="auditQuery.executionCode" clearable placeholder="执行编码" @keyup.enter="loadExecutions" />
          <el-select v-model="auditQuery.status" clearable placeholder="全部状态">
            <el-option v-for="status in executionStatuses" :key="status" :label="status" :value="status" />
          </el-select>
          <el-button type="primary" @click="loadExecutions">查询</el-button>
        </div>
        <el-table :data="executions" border stripe v-loading="loadingExecutions">
          <el-table-column prop="executionCode" label="执行编码" min-width="230" />
          <el-table-column prop="profileCode" label="配置" min-width="150" />
          <el-table-column label="命令" min-width="240" show-overflow-tooltip><template #default="{ row }"><span class="mono">{{ row.command.join(" ") }}</span></template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="退出码" width="90"><template #default="{ row }">{{ row.exitCode ?? "-" }}</template></el-table-column>
          <el-table-column label="耗时" width="110"><template #default="{ row }">{{ row.durationMs == null ? "-" : `${row.durationMs} ms` }}</template></el-table-column>
          <el-table-column prop="requestedUsername" label="请求人" min-width="120" />
          <el-table-column prop="createdAt" label="创建时间" min-width="175" :formatter="formatTableDateTime" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }"><el-button link type="primary" @click="showDetail(row.executionId)">详情</el-button><el-button v-if="['pending','running'].includes(row.status)" link type="danger" @click="cancelExecution(row)">取消</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination class="pagination" layout="total, prev, pager, next" :total="executionTotal" :page-size="20" v-model:current-page="auditQuery.pageNum" @current-change="loadExecutions" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="profileVisible" :title="editingProfileId ? '编辑 Sandbox 配置' : '新增 Sandbox 配置'" width="720px">
      <el-form label-width="150px">
        <el-form-item label="配置编码"><el-input v-model="profileForm.profileCode" :disabled="Boolean(editingProfileId)" /></el-form-item>
        <el-form-item label="配置名称"><el-input v-model="profileForm.profileName" /></el-form-item>
        <el-form-item label="固定版本镜像"><el-input v-model="profileForm.image" placeholder="image:1.0.0 或 image@sha256:..." /></el-form-item>
        <el-form-item label="允许命令"><el-select v-model="profileForm.allowedCommands" multiple allow-create filterable style="width:100%" placeholder="仅填写可执行文件名，不含路径" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="网络策略"><el-select v-model="profileForm.networkMode"><el-option label="完全禁网" value="none" /><el-option label="Docker bridge" value="bridge" /></el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="profileForm.status"><el-option label="启用" value="enabled" /><el-option label="停用" value="disabled" /></el-select></el-form-item>
          <el-form-item label="内存 MiB"><el-input-number v-model="memoryMiB" :min="64" :max="4096" /></el-form-item>
          <el-form-item label="CPU 核数"><el-input-number v-model="cpuCores" :min="0.1" :max="4" :step="0.1" /></el-form-item>
          <el-form-item label="PID 上限"><el-input-number v-model="profileForm.pidsLimit" :min="16" :max="1024" /></el-form-item>
          <el-form-item label="超时秒数"><el-input-number v-model="profileForm.timeoutSeconds" :min="1" :max="3600" /></el-form-item>
          <el-form-item label="输出上限 KiB"><el-input-number v-model="outputKiB" :min="1" :max="10240" /></el-form-item>
          <el-form-item label="工作区上限 MiB"><el-input-number v-model="workspaceMiB" :min="1" :max="100" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="profileVisible=false">取消</el-button><el-button type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="executeVisible" title="容器隔离执行测试" width="720px">
      <el-alert type="warning" :closable="false" title="命令按参数数组执行，不经过 Shell；工作区在执行结束后立即销毁。" />
      <el-form label-width="120px" class="dialog-form">
        <el-form-item label="配置">{{ selectedProfile?.profileCode }}</el-form-item>
        <el-form-item label="命令 JSON"><el-input v-model="commandJson" type="textarea" :rows="5" placeholder='["python3", "-c", "print(\"hello\")"]' /></el-form-item>
        <el-form-item label="产物路径"><el-input v-model="artifactPathsText" placeholder="逗号分隔，例如 result.json" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="executeVisible=false">取消</el-button><el-button type="primary" :loading="executing" @click="runSandbox">执行</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="Sandbox 执行审计" size="56%">
      <el-descriptions v-if="selectedExecution" :column="2" border>
        <el-descriptions-item label="执行编码">{{ selectedExecution.executionCode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selectedExecution.status }}</el-descriptions-item>
        <el-descriptions-item label="容器 ID">{{ selectedExecution.containerId || "-" }}</el-descriptions-item>
        <el-descriptions-item label="退出码">{{ selectedExecution.exitCode ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="超时">{{ selectedExecution.timedOut ? "是" : "否" }}</el-descriptions-item>
        <el-descriptions-item label="输出截断">{{ selectedExecution.outputTruncated ? "是" : "否" }}</el-descriptions-item>
        <el-descriptions-item label="错误" :span="2">{{ selectedExecution.errorMessage || "-" }}</el-descriptions-item>
      </el-descriptions>
      <h3>STDOUT</h3><pre class="console">{{ selectedExecution?.stdoutText || "(empty)" }}</pre>
      <h3>STDERR</h3><pre class="console error">{{ selectedExecution?.stderrText || "(empty)" }}</pre>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatTableDateTime } from "@/utils/dateTime";
import { cancelSandboxExecution, createSandboxProfile, executeSandbox, fetchSandboxExecution, fetchSandboxExecutions, fetchSandboxProfiles, updateSandboxProfile } from "@/api/sandbox";
import type { SandboxExecution, SandboxProfile, SandboxProfilePayload } from "@/types/sandbox";

const activeTab = ref("profiles");
const profiles = ref<SandboxProfile[]>([]); const executions = ref<SandboxExecution[]>([]);
const profileTotal = ref(0); const executionTotal = ref(0);
const loadingProfiles = ref(false); const loadingExecutions = ref(false);
const profileVisible = ref(false); const executeVisible = ref(false); const detailVisible = ref(false);
const savingProfile = ref(false); const executing = ref(false); const editingProfileId = ref<number | null>(null);
const selectedProfile = ref<SandboxProfile | null>(null); const selectedExecution = ref<SandboxExecution | null>(null);
const commandJson = ref('["python3", "-c", "print(\"sandbox ready\")"]'); const artifactPathsText = ref("");
const profileQuery = reactive({ pageNum: 1, pageSize: 20, keyword: "", status: "" });
const auditQuery = reactive({ pageNum: 1, pageSize: 20, executionCode: "", status: "" });
const executionStatuses = ["pending", "running", "succeeded", "failed", "timed_out", "cancelled"];
const profileForm = reactive<SandboxProfilePayload>(defaultProfile());
const memoryMiB = computed({ get: () => profileForm.memoryBytes / 1048576, set: (v) => profileForm.memoryBytes = v * 1048576 });
const cpuCores = computed({ get: () => profileForm.nanoCpus / 1e9, set: (v) => profileForm.nanoCpus = v * 1e9 });
const outputKiB = computed({ get: () => profileForm.maxOutputBytes / 1024, set: (v) => profileForm.maxOutputBytes = v * 1024 });
const workspaceMiB = computed({ get: () => profileForm.maxWorkspaceBytes / 1048576, set: (v) => profileForm.maxWorkspaceBytes = v * 1048576 });

function defaultProfile(): SandboxProfilePayload { return { profileCode: "", profileName: "", image: "agent-sandbox-runtime:1.0.0", allowedCommands: ["python3"], networkMode: "none", memoryBytes: 268435456, nanoCpus: 1e9, pidsLimit: 128, timeoutSeconds: 60, maxOutputBytes: 1048576, maxWorkspaceBytes: 10485760, status: "enabled" }; }
async function loadProfiles() { loadingProfiles.value = true; try { const page = await fetchSandboxProfiles(profileQuery); profiles.value = page.records; profileTotal.value = page.total; } catch (e) { ElMessage.error(errorMessage(e)); } finally { loadingProfiles.value = false; } }
async function loadExecutions() { loadingExecutions.value = true; try { const page = await fetchSandboxExecutions(auditQuery); executions.value = page.records; executionTotal.value = page.total; } catch (e) { ElMessage.error(errorMessage(e)); } finally { loadingExecutions.value = false; } }
function openProfile(row?: SandboxProfile) { editingProfileId.value = row?.profileId ?? null; Object.assign(profileForm, row ? { ...row } : defaultProfile()); profileVisible.value = true; }
async function saveProfile() { savingProfile.value = true; try { if (editingProfileId.value) await updateSandboxProfile(editingProfileId.value, profileForm); else await createSandboxProfile(profileForm); ElMessage.success("Sandbox 配置已保存"); profileVisible.value = false; await loadProfiles(); } catch (e) { ElMessage.error(errorMessage(e)); } finally { savingProfile.value = false; } }
function openExecute(row: SandboxProfile) { selectedProfile.value = row; commandJson.value = JSON.stringify([row.allowedCommands[0] ?? "python3", "--version"], null, 2); executeVisible.value = true; }
async function runSandbox() { if (!selectedProfile.value) return; executing.value = true; try { const command = JSON.parse(commandJson.value); if (!Array.isArray(command) || command.some((v) => typeof v !== "string")) throw new Error("命令必须是字符串数组"); const result = await executeSandbox({ profileId: selectedProfile.value.profileId, command, inputFiles: [], artifactPaths: artifactPathsText.value.split(",").map(v => v.trim()).filter(Boolean) }); ElMessage.success(`执行完成：${result.execution.status}`); executeVisible.value = false; activeTab.value = "audits"; await loadExecutions(); await showDetail(result.execution.executionId); } catch (e) { ElMessage.error(errorMessage(e)); await loadExecutions(); } finally { executing.value = false; } }
async function showDetail(id: number) { try { selectedExecution.value = await fetchSandboxExecution(id); detailVisible.value = true; } catch (e) { ElMessage.error(errorMessage(e)); } }
async function cancelExecution(row: SandboxExecution) { await ElMessageBox.confirm("容器会被立即终止并清理，确认取消？", "取消执行", { type: "warning" }); try { await cancelSandboxExecution(row.executionId); ElMessage.success("已取消"); await loadExecutions(); } catch (e) { ElMessage.error(errorMessage(e)); } }
function statusType(status: string) { return status === "succeeded" ? "success" : status === "failed" || status === "timed_out" ? "danger" : status === "running" ? "warning" : "info"; }
function formatMemory(bytes: number) { return `${Math.round(bytes / 1048576)} MiB`; } function formatCpu(nano: number) { return (nano / 1e9).toFixed(1); }
function errorMessage(error: unknown) { return error instanceof Error ? error.message : "操作失败"; }
onMounted(async () => { await Promise.all([loadProfiles(), loadExecutions()]); });
</script>

<style scoped>
.sandbox-page{min-width:0}.page-hero{display:flex;justify-content:space-between;align-items:flex-end;padding:28px 30px;color:#f4f7f2;background:linear-gradient(125deg,#12261f,#1c4738 58%,#b56728);border-radius:18px 18px 0 0}.eyebrow{font-size:11px;letter-spacing:.18em;color:#d4b07a}.page-hero h1{margin:7px 0;font-family:Georgia,serif;font-size:32px}.page-hero p{margin:0;color:#cbd9d3}.security-badges{display:flex;gap:8px}.content-card{padding:18px 20px 24px;background:#fff;border:1px solid #dce5df;border-top:0;border-radius:0 0 18px 18px}.toolbar{display:flex;gap:10px;margin-bottom:16px}.toolbar .el-input{width:240px}.toolbar .el-select{width:160px}.push-right{margin-left:auto}.pagination{justify-content:flex-end;margin-top:18px}.mono,.console{font-family:"SFMono-Regular",Consolas,monospace}.form-grid{display:grid;grid-template-columns:1fr 1fr}.dialog-form{margin-top:18px}.console{min-height:120px;padding:14px;white-space:pre-wrap;word-break:break-all;color:#dceade;background:#122019;border-radius:10px}.console.error{color:#f2b7a8;background:#281713}@media(max-width:900px){.page-hero{align-items:flex-start;flex-direction:column;gap:18px}.toolbar{flex-wrap:wrap}.push-right{margin-left:0}.form-grid{grid-template-columns:1fr}}
</style>

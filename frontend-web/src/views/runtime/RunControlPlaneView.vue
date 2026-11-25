<template><section class="control-page"><header class="hero"><div><span>DISTRIBUTED EXECUTION</span><h1>Agent Run 调度控制面</h1><p>Worker Group 容量、节点心跳、能力标签路由、drain 与队列积压统一治理。</p></div><el-button color="#ef9d4c" :loading="loading" @click="load">刷新拓扑</el-button></header>
<div class="groups"><article v-for="g in dashboard?.groups" :key="g.id"><div><strong>{{g.groupName}}</strong><el-tag :type="g.status==='enabled'?'success':g.status==='draining'?'warning':'info'">{{g.status}}</el-tag></div><p class="code">{{g.groupCode}}</p><div class="numbers"><span><b>{{g.onlineWorkerCount}}</b>节点</span><span><b>{{g.runningCount}}</b>/{{g.maxConcurrency}} 运行</span><span><b>{{g.queuedCount}}</b>/{{g.queueCapacity}} 排队</span></div><p>能力：{{g.requiredCapabilities.join(', ')||'通用'}}</p><el-button link type="primary" @click="openGroup(g)">配置容量</el-button></article><button class="add-card" @click="openGroup()">+ 新增 Worker Group</button></div>
<el-tabs v-model="tab" class="panel"><el-tab-pane label="Worker 节点" name="nodes"><el-table :data="dashboard?.nodes||[]" border stripe><el-table-column prop="workerId" label="Worker ID" min-width="250"/><el-table-column prop="groupCode" label="组" min-width="130"/><el-table-column label="负载" width="130"><template #default="{row}">{{row.currentLoad}} / {{row.maxConcurrency}}</template></el-table-column><el-table-column label="能力" min-width="180"><template #default="{row}">{{row.capabilities.join(', ')||'通用'}}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="nodeType(row.status)">{{row.status}}</el-tag></template></el-table-column><el-table-column prop="lastHeartbeatAt" label="最近心跳" min-width="175" :formatter="formatTableDateTime"/><el-table-column label="操作" width="190"><template #default="{row}"><el-button v-if="row.status==='online'" link type="warning" @click="nodeStatus(row.workerId,'draining')">Drain</el-button><el-button v-if="row.status!=='online'" link type="success" @click="nodeStatus(row.workerId,'online')">恢复</el-button><el-button link type="danger" @click="nodeStatus(row.workerId,'offline')">下线</el-button></template></el-table-column></el-table></el-tab-pane>
<el-tab-pane label="路由规则" name="rules"><div class="toolbar"><el-button type="primary" @click="openRule()">新增路由规则</el-button></div><el-table :data="rules" border><el-table-column prop="priority" label="顺序" width="80"/><el-table-column prop="ruleCode" label="编码" min-width="150"/><el-table-column prop="ruleName" label="名称" min-width="160"/><el-table-column label="Agent" width="110"><template #default="{row}">{{row.agentId||'全部'}}</template></el-table-column><el-table-column label="运行优先级" width="140"><template #default="{row}">{{row.minPriority??0}} - {{row.maxPriority??100}}</template></el-table-column><el-table-column prop="targetGroupCode" label="目标组" min-width="130"/><el-table-column label="能力" min-width="170"><template #default="{row}">{{row.requiredCapabilities.join(', ')||'-'}}</template></el-table-column><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="90"><template #default="{row}"><el-button link @click="openRule(row)">编辑</el-button></template></el-table-column></el-table></el-tab-pane></el-tabs>
<el-dialog v-model="groupVisible" :title="groupForm.id?'编辑 Worker Group':'新增 Worker Group'" width="650"><el-form label-width="140"><el-form-item label="编码"><el-input v-model="groupForm.groupCode" :disabled="!!groupForm.id"/></el-form-item><el-form-item label="名称"><el-input v-model="groupForm.groupName"/></el-form-item><el-form-item label="最大并发"><el-input-number v-model="groupForm.maxConcurrency" :min="1"/></el-form-item><el-form-item label="队列容量"><el-input-number v-model="groupForm.queueCapacity" :min="1"/></el-form-item><el-form-item label="能力标签"><el-select v-model="groupForm.requiredCapabilities" multiple allow-create filterable style="width:100%"/></el-form-item><el-form-item label="状态"><el-select v-model="groupForm.status"><el-option value="enabled"/><el-option value="draining"/><el-option value="disabled"/></el-select></el-form-item></el-form><template #footer><el-button @click="groupVisible=false">取消</el-button><el-button type="primary" @click="saveGroup">保存</el-button></template></el-dialog>
<el-dialog v-model="ruleVisible" :title="ruleForm.id?'编辑路由规则':'新增路由规则'" width="720"><el-form label-width="140"><el-form-item label="编码 / 名称"><el-input v-model="ruleForm.ruleCode" :disabled="!!ruleForm.id" style="width:45%"/><el-input v-model="ruleForm.ruleName" style="width:52%;margin-left:3%"/></el-form-item><el-form-item label="Agent ID"><el-input-number v-model="ruleForm.agentId" :min="1"/><span class="hint">留空匹配全部 Agent</span></el-form-item><el-form-item label="运行优先级"><el-input-number v-model="ruleForm.minPriority" :min="0" :max="100"/><span> 至 </span><el-input-number v-model="ruleForm.maxPriority" :min="0" :max="100"/></el-form-item><el-form-item label="所需能力"><el-select v-model="ruleForm.requiredCapabilities" multiple allow-create filterable style="width:100%"/></el-form-item><el-form-item label="目标组"><el-select v-model="ruleForm.targetGroupCode"><el-option v-for="g in dashboard?.groups" :key="g.groupCode" :label="g.groupName" :value="g.groupCode"/></el-select></el-form-item><el-form-item label="规则顺序 / 状态"><el-input-number v-model="ruleForm.priority" :min="0"/><el-select v-model="ruleForm.status" style="margin-left:15px"><el-option value="enabled"/><el-option value="disabled"/></el-select></el-form-item></el-form><template #footer><el-button @click="ruleVisible=false">取消</el-button><el-button type="primary" @click="saveRule">保存</el-button></template></el-dialog></section></template>
<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatTableDateTime } from "@/utils/dateTime";
import { fetchControlDashboard, fetchRoutingRules, saveRoutingRule, saveWorkerGroup, setWorkerStatus } from "@/api/runControl";
import type { ControlDashboard, RoutingRule, WorkerGroup } from "@/types/runControl";

const loading = ref(false);
const tab = ref("nodes");
const dashboard = ref<ControlDashboard | null>(null);
const rules = ref<RoutingRule[]>([]);
const groupVisible = ref(false);
const ruleVisible = ref(false);
const groupForm = reactive<Record<string, any>>({});
const ruleForm = reactive<Record<string, any>>({});

async function load(): Promise<void> {
  loading.value = true;
  try {
    [dashboard.value, rules.value] = await Promise.all([fetchControlDashboard(), fetchRoutingRules()]);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function openGroup(value?: WorkerGroup): void {
  Object.assign(groupForm, value ? { ...value } : { id: null, groupCode: "", groupName: "", maxConcurrency: 8, queueCapacity: 500, requiredCapabilities: [], status: "enabled" });
  groupVisible.value = true;
}

function openRule(value?: RoutingRule): void {
  Object.assign(ruleForm, value ? { ...value } : { id: null, ruleCode: "", ruleName: "", agentId: null, minPriority: 0, maxPriority: 100, requiredCapabilities: [], targetGroupCode: dashboard.value?.groups[0]?.groupCode || "default", priority: 100, status: "enabled" });
  ruleVisible.value = true;
}

async function saveGroup(): Promise<void> {
  const payload = { ...groupForm };
  const id = payload.id as number | null;
  ["id", "queuedCount", "runningCount", "onlineWorkerCount", "oldestQueuedAt"].forEach((key) => delete payload[key]);
  await saveWorkerGroup(id, payload);
  groupVisible.value = false;
  ElMessage.success("Worker Group 已保存");
  await load();
}

async function saveRule(): Promise<void> {
  const payload = { ...ruleForm };
  const id = payload.id as number | null;
  delete payload.id;
  await saveRoutingRule(id, payload);
  ruleVisible.value = false;
  ElMessage.success("路由规则已保存");
  await load();
}

async function nodeStatus(id: string, status: string): Promise<void> {
  await ElMessageBox.confirm(`确认将节点调整为 ${status}？`, "节点状态");
  await setWorkerStatus(id, status);
  ElMessage.success("节点状态已更新");
  await load();
}

function nodeType(value: string): "success" | "warning" | "danger" | "info" {
  return value === "online" ? "success" : value === "draining" ? "warning" : value === "unhealthy" ? "danger" : "info";
}

function errorMessage(error: unknown): string { return error instanceof Error ? error.message : "操作失败"; }
onMounted(load);
</script>
<style scoped>.control-page{min-width:0}.hero{display:flex;align-items:end;justify-content:space-between;padding:30px;color:#eef8f4;background:linear-gradient(120deg,#122735,#174d57 65%,#735127);border-radius:20px 20px 0 0}.hero span{font:600 11px Georgia;letter-spacing:.2em;color:#ef9d4c}.hero h1{margin:8px 0;font:700 33px Georgia}.hero p{margin:0;color:#bed5d4}.groups{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:18px;background:#e9f0ef}.groups article,.add-card{min-height:155px;padding:18px;text-align:left;background:#fff;border:1px solid #ceddda;border-radius:14px}.groups article>div:first-child{display:flex;justify-content:space-between}.code{color:#71817f}.numbers{display:flex;gap:18px}.numbers b{display:block;font-size:22px;color:#174d57}.add-card{color:#39726e;border-style:dashed;font-size:16px}.panel{padding:18px 20px;background:#fff;border-radius:0 0 20px 20px}.toolbar{margin-bottom:14px}.hint{margin-left:12px;color:#81918f}@media(max-width:1000px){.groups{grid-template-columns:1fr}}</style>

<template>
  <section class="audit-page">
    <el-card shadow="never" class="query-card">
      <template #header>
        <div>
          <div class="card-title">Capability Policy 审计</div>
          <div class="card-subtitle">查询每一次 Tool 调用前的安全决策，并按 Tool Call ID 关联审批与执行结果。</div>
        </div>
      </template>
      <el-form :model="query" inline>
        <el-form-item label="决策">
          <el-select v-model="query.decision" clearable style="width: 140px">
            <el-option label="放行" value="allow" />
            <el-option label="Sandbox" value="sandbox" />
            <el-option label="需审批" value="approval" />
            <el-option label="阻断" value="block" />
          </el-select>
        </el-form-item>
        <el-form-item label="Capability">
          <el-select v-model="query.capability" clearable filterable style="width: 210px">
            <el-option v-for="item in capabilityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="副作用">
          <el-select v-model="query.sideEffectLevel" clearable style="width: 170px">
            <el-option label="无副作用" value="none" />
            <el-option label="幂等" value="idempotent" />
            <el-option label="可补偿" value="compensatable" />
            <el-option label="不可逆" value="irreversible" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent">
          <el-select v-model="query.agentId" clearable filterable style="width: 220px">
            <el-option v-for="item in agents" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Tool">
          <el-input v-model="query.toolCode" clearable placeholder="Tool Code" />
        </el-form-item>
        <el-form-item label="策略">
          <el-input v-model="query.policyCode" clearable placeholder="Policy Code" />
        </el-form-item>
        <el-form-item label="Run / Trace">
          <el-input v-model="query.runCode" clearable placeholder="Run Code" style="width: 190px" />
          <el-input v-model="query.traceId" clearable placeholder="Trace ID" style="width: 190px; margin-left: 8px" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <div class="query-actions">
          <el-button @click="reset">重置</el-button>
          <el-button type="primary" @click="loadData">查询</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="metrics-grid">
      <el-card v-for="item in metricCards" :key="item.label" shadow="never" class="metric-card">
        <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
      </el-card>
    </div>

    <div class="distribution-grid">
      <el-card shadow="never">
        <template #header><span class="card-title">Capability 分布</span></template>
        <el-table :data="metrics.capabilityBuckets" size="small" max-height="260">
          <el-table-column prop="label" label="Capability" min-width="180" />
          <el-table-column prop="count" label="决策数" width="90" />
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header><span class="card-title">Tool 分布</span></template>
        <el-table :data="metrics.toolBuckets" size="small" max-height="260">
          <el-table-column prop="label" label="Tool" min-width="180" />
          <el-table-column prop="count" label="决策数" width="90" />
        </el-table>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div>
          <div class="card-title">决策记录</div>
          <div class="card-subtitle">参数和目标仅展示 SHA-256，不返回原始 Tool 输入。</div>
        </div>
      </template>
      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="occurredAt" label="时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column prop="agentName" label="Agent" min-width="150" />
        <el-table-column prop="toolName" label="Tool" min-width="180">
          <template #default="{ row }"><div>{{ row.toolName || row.toolCode }}</div><small>{{ row.toolCode }}</small></template>
        </el-table-column>
        <el-table-column prop="decision" label="决策" width="110">
          <template #default="{ row }"><el-tag :type="decisionTag(row.decision)">{{ decisionText(row.decision) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sideEffectLevel" label="副作用" width="130" />
        <el-table-column label="Capability" min-width="230">
          <template #default="{ row }">
            <el-space wrap><el-tag v-for="item in row.runtimeCapabilities" :key="item" size="small" effect="plain">{{ item }}</el-tag></el-space>
          </template>
        </el-table-column>
        <el-table-column prop="policyCode" label="策略" min-width="170" />
        <el-table-column prop="runCode" label="Run" min-width="190" />
        <el-table-column prop="toolCallId" label="Tool Call ID" min-width="230" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row.eventId)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          @current-change="changePage"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="Capability 决策审计详情" size="66%" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Tool">{{ detail.decision.toolName }}（{{ detail.decision.toolCode }}）</el-descriptions-item>
          <el-descriptions-item label="决策"><el-tag :type="decisionTag(detail.decision.decision)">{{ decisionText(detail.decision.decision) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="策略">{{ detail.decision.policyCode || "安全基线" }} / v{{ detail.decision.policyVersion ?? "-" }}</el-descriptions-item>
          <el-descriptions-item label="审批续跑">{{ detail.decision.approvalGranted ? "是" : "否" }}</el-descriptions-item>
          <el-descriptions-item label="Run">{{ detail.decision.runCode }}</el-descriptions-item>
          <el-descriptions-item label="Trace">{{ detail.decision.traceId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="Tool Call ID" :span="2">{{ detail.decision.toolCallId }}</el-descriptions-item>
          <el-descriptions-item label="参数哈希" :span="2"><code>{{ detail.decision.parameterHash }}</code></el-descriptions-item>
          <el-descriptions-item label="目标哈希" :span="2"><code>{{ detail.decision.targetHash || "-" }}</code></el-descriptions-item>
        </el-descriptions>
        <h3>关联事件链</h3>
        <el-timeline>
          <el-timeline-item
            v-for="event in detail.correlatedEvents"
            :key="event.eventId"
            :timestamp="formatDateTime(event.occurredAt, '-')"
          >
            <div class="event-title">#{{ event.sequenceNo }} {{ event.eventType }}</div>
            <pre>{{ prettyJson(event.payload) }}</pre>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { fetchAgentPage } from "@/api/agent";
import {
  fetchCapabilityPolicyAuditDetail,
  fetchCapabilityPolicyAuditMetrics,
  fetchCapabilityPolicyAudits,
  type CapabilityPolicyAuditDetail,
  type CapabilityPolicyAuditItem,
  type CapabilityPolicyAuditMetrics,
  type CapabilityPolicyAuditQuery
} from "@/api/capabilityPolicyAudit";
import { formatDateTime, formatTableDateTime, toBackendUtcDateTime } from "@/utils/dateTime";

const capabilityOptions = [
  "filesystem.read", "filesystem.write", "network.read", "network.write", "process.execute",
  "database.read", "database.write", "artifact.publish", "external.side_effect"
];
const emptyMetrics = (): CapabilityPolicyAuditMetrics => ({
  totalCount: 0, allowCount: 0, sandboxCount: 0, approvalCount: 0, blockCount: 0,
  approvedReplayCount: 0, runCount: 0, toolCount: 0, decisionBuckets: [], capabilityBuckets: [], toolBuckets: []
});

const loading = ref(false);
const records = ref<CapabilityPolicyAuditItem[]>([]);
const metrics = ref<CapabilityPolicyAuditMetrics>(emptyMetrics());
const total = ref(0);
const timeRange = ref<[string, string] | []>([]);
const agents = ref<Array<{ label: string; value: number }>>([]);
const detailVisible = ref(false);
const detail = ref<CapabilityPolicyAuditDetail | null>(null);
const query = reactive({
  pageNum: 1, pageSize: 20, decision: "", toolCode: "", capability: "", sideEffectLevel: "",
  policyCode: "", agentId: null as number | null, runCode: "", traceId: ""
});

const metricCards = computed(() => [
  { label: "决策总数", value: metrics.value.totalCount },
  { label: "放行", value: metrics.value.allowCount },
  { label: "Sandbox", value: metrics.value.sandboxCount },
  { label: "需审批", value: metrics.value.approvalCount },
  { label: "阻断", value: metrics.value.blockCount },
  { label: "审批续跑", value: metrics.value.approvedReplayCount },
  { label: "Run 数", value: metrics.value.runCount },
  { label: "Tool 数", value: metrics.value.toolCount }
]);

function params(): CapabilityPolicyAuditQuery {
  const [occurredFrom, occurredTo] = Array.isArray(timeRange.value) ? timeRange.value : [];
  return {
    pageNum: query.pageNum, pageSize: query.pageSize, decision: query.decision || undefined,
    toolCode: query.toolCode || undefined, capability: query.capability || undefined,
    sideEffectLevel: query.sideEffectLevel || undefined, policyCode: query.policyCode || undefined,
    agentId: query.agentId || undefined, runCode: query.runCode || undefined, traceId: query.traceId || undefined,
    occurredFrom: toBackendUtcDateTime(occurredFrom), occurredTo: toBackendUtcDateTime(occurredTo)
  };
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const [page, summary] = await Promise.all([
      fetchCapabilityPolicyAudits(params()), fetchCapabilityPolicyAuditMetrics(params())
    ]);
    records.value = page.records;
    total.value = page.total;
    metrics.value = summary;
  } catch {
    ElMessage.error("Capability Policy 审计加载失败");
  } finally {
    loading.value = false;
  }
}

async function openDetail(eventId: number): Promise<void> {
  try {
    detail.value = await fetchCapabilityPolicyAuditDetail(eventId);
    detailVisible.value = true;
  } catch {
    ElMessage.error("Capability Policy 审计详情加载失败");
  }
}

function reset(): void {
  Object.assign(query, { pageNum: 1, pageSize: 20, decision: "", toolCode: "", capability: "",
    sideEffectLevel: "", policyCode: "", agentId: null, runCode: "", traceId: "" });
  timeRange.value = [];
  void loadData();
}

function changePage(pageNum: number): void { query.pageNum = pageNum; void loadData(); }
function decisionText(value: string): string { return ({ allow: "放行", sandbox: "Sandbox", approval: "需审批", block: "阻断" } as Record<string, string>)[value] || value; }
function decisionTag(value: string): "success" | "warning" | "danger" | "info" { return ({ allow: "success", sandbox: "info", approval: "warning", block: "danger" } as Record<string, "success" | "warning" | "danger" | "info">)[value] || "info"; }
function prettyJson(value: Record<string, unknown>): string { return JSON.stringify(value, null, 2); }

onMounted(async () => {
  try {
    const page = await fetchAgentPage({ pageNum: 1, pageSize: 100, status: "enabled" });
    agents.value = page.records.map(item => ({ label: `${item.agentName}（${item.agentCode}）`, value: item.agentId }));
  } catch {
    agents.value = [];
  }
  await loadData();
});
</script>

<style scoped>
.audit-page { display: flex; flex-direction: column; gap: 16px; padding: 20px; min-width: 0; }
.card-title { font-size: 17px; font-weight: 700; color: #1f2937; }
.card-subtitle { margin-top: 5px; color: #6b7280; font-size: 13px; }
.query-actions { display: inline-flex; gap: 8px; vertical-align: top; }
.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; }
.metric-card :deep(.el-card__body) { display: flex; flex-direction: column; gap: 8px; }
.metric-card span { color: #6b7280; font-size: 13px; }
.metric-card strong { font-size: 25px; color: #111827; }
.distribution-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.event-title { margin-bottom: 8px; font-weight: 700; }
pre { max-height: 260px; overflow: auto; margin: 0; padding: 12px; border-radius: 6px; background: #f6f8fa; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
code { word-break: break-all; }
small { display: block; color: #9ca3af; }
@media (max-width: 900px) { .distribution-grid { grid-template-columns: 1fr; } }
</style>

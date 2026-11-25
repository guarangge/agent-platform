<template>
  <section class="trace-page">
    <el-card shadow="never" class="query-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Context Compaction 运营中心</div>
            <div class="card-subtitle">独立查看上下文压缩质量、节省 Token、阶段分布，并导出压缩问题排查数据。</div>
          </div>
        </div>
      </template>
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Trace ID">
          <el-input v-model="queryForm.traceId" clearable placeholder="支持模糊检索" />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-input v-model="queryForm.eventType" clearable placeholder="例如 planning / reply" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.traceStatus" clearable style="width: 180px;">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="待审批" value="waiting_approval" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent">
          <el-select v-model="queryForm.agentId" clearable filterable style="width: 220px;">
            <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务 / 会话">
          <el-input v-model="queryForm.keyword" clearable placeholder="TaskCode / ConversationCode / Span / 错误" style="width: 280px;" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button type="primary" plain @click="handleExport">导出 CSV</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="metrics-grid">
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">压缩事件数</div>
        <div class="metric-value">{{ metrics.totalCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">Critical 数</div>
        <div class="metric-value">{{ metrics.criticalCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">Warning 数</div>
        <div class="metric-value">{{ metrics.warningCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均质量分</div>
        <div class="metric-value">{{ metrics.avgQualityScore }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均节省字符</div>
        <div class="metric-value">{{ metrics.avgSavedChars }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均节省 Token</div>
        <div class="metric-value">{{ metrics.avgSavedTokens }}</div>
      </el-card>
    </div>

    <div class="analysis-grid">
      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-title">阶段 / 质量分布</div>
        </template>
        <div class="bucket-section">
          <div class="bucket-title">按阶段聚合</div>
          <el-table :data="metrics.stageBuckets" size="small" border>
            <el-table-column prop="bucketLabel" label="阶段" min-width="160" />
            <el-table-column prop="countValue" label="记录数" min-width="100" />
          </el-table>
        </div>
        <div class="bucket-section">
          <div class="bucket-title">按质量状态聚合</div>
          <el-table :data="metrics.qualityBuckets" size="small" border>
            <el-table-column prop="bucketLabel" label="质量状态" min-width="160" />
            <el-table-column prop="countValue" label="记录数" min-width="100" />
          </el-table>
        </div>
      </el-card>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-title">节省 Token 排行</div>
        </template>
        <el-table :data="metrics.topSavedTokenRecords" size="small" border>
          <el-table-column prop="taskCode" label="任务" min-width="160" />
          <el-table-column prop="stage" label="阶段" min-width="120" />
          <el-table-column prop="qualityStatus" label="质量" min-width="100">
            <template #default="{ row }">
              <el-tag :type="qualityTagType(row.qualityStatus)">{{ row.qualityStatus || "-" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="savedTokens" label="节省 Token" min-width="110" />
          <el-table-column prop="savedChars" label="节省字符" min-width="110" />
          <el-table-column prop="startedAt" label="开始时间" min-width="180" :formatter="formatTableDateTime" />
        </el-table>
      </el-card>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">压缩记录</div>
            <div class="card-subtitle">点击“排查”可查看压缩策略、质量分和完整 metadata。</div>
          </div>
        </div>
      </template>

        <el-table :data="records" border stripe v-loading="loading">
          <el-table-column prop="conversationCode" label="会话" min-width="150" />
          <el-table-column prop="taskCode" label="任务" min-width="150" />
          <el-table-column prop="stage" label="阶段" min-width="110" />
          <el-table-column prop="eventType" label="事件类型" min-width="120" />
          <el-table-column prop="tokenizerFamily" label="Tokenizer" min-width="140" />
          <el-table-column prop="qualityStatus" label="质量状态" min-width="110">
            <template #default="{ row }">
              <el-tag :type="qualityTagType(row.qualityStatus)">{{ qualityStatusText(row.qualityStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="qualityScore" label="质量分" min-width="90" />
          <el-table-column prop="savedTokens" label="节省 Token" min-width="110" />
          <el-table-column prop="savedChars" label="节省字符" min-width="110" />
          <el-table-column prop="startedAt" label="开始时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTroubleshoot(row)">排查</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :current-page="queryForm.pageNum"
          :page-size="queryForm.pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="压缩问题排查" size="50%">
      <template v-if="selectedRecord">
        <div class="drawer-section">
          <div class="drawer-title">基础信息</div>
          <div class="info-grid">
            <div>会话：{{ selectedRecord.conversationCode }}</div>
            <div>任务：{{ selectedRecord.taskCode }}</div>
            <div>Trace：{{ selectedRecord.traceId }}</div>
            <div>阶段：{{ selectedRecord.stage || "-" }}</div>
            <div>质量：{{ qualityStatusText(selectedRecord.qualityStatus) }}</div>
            <div>质量分：{{ selectedRecord.qualityScore ?? "-" }}</div>
            <div>节省 Token：{{ selectedRecord.savedTokens ?? 0 }}</div>
            <div>节省字符：{{ selectedRecord.savedChars ?? 0 }}</div>
          </div>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">策略快照</div>
          <pre class="json-block">{{ formatJson(selectedRecord.policyJson) }}</pre>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">压缩说明</div>
          <pre class="json-block">{{ formatCompactionExplain(selectedRecord.metadata) }}</pre>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">Prompt 组装块</div>
          <template v-if="parsePromptAssembly(selectedRecord.metadata)">
            <div class="prompt-preview-grid">
              <el-card shadow="never" class="prompt-preview-card">
                <div class="bucket-title">System Prompt 预览</div>
                <pre class="json-block">{{ parsePromptAssembly(selectedRecord.metadata)?.systemPromptPreview || "-" }}</pre>
              </el-card>
              <el-card shadow="never" class="prompt-preview-card">
                <div class="bucket-title">最终 User Prompt 预览</div>
                <pre class="json-block">{{ parsePromptAssembly(selectedRecord.metadata)?.finalUserPromptPreview || "-" }}</pre>
              </el-card>
            </div>
            <pre class="json-block">{{ formatPromptAssemblyPreview(selectedRecord.metadata) }}</pre>
            <el-table :data="parsePromptAssembly(selectedRecord.metadata)?.blocks || []" size="small" border>
              <el-table-column prop="label" label="块" min-width="140" />
              <el-table-column prop="source" label="来源" min-width="90" />
              <el-table-column prop="layerKey" label="层Key" min-width="160" />
              <el-table-column prop="compactedChars" label="字符数" min-width="100" />
              <el-table-column prop="compactedTokens" label="Token" min-width="100" />
              <el-table-column label="存在" min-width="80">
                <template #default="{ row }">
                  {{ row.present ? "是" : "否" }}
                </template>
              </el-table-column>
              <el-table-column label="截断" min-width="80">
                <template #default="{ row }">
                  {{ row.truncated ? "是" : "否" }}
                </template>
              </el-table-column>
              <el-table-column label="摘要" min-width="80">
                <template #default="{ row }">
                  {{ row.summarized ? "是" : "否" }}
                </template>
              </el-table-column>
              <el-table-column prop="sample" label="样本" min-width="260" show-overflow-tooltip />
            </el-table>
          </template>
          <el-empty v-else description="当前记录未生成 Prompt 组装块" :image-size="64" />
        </div>
        <div class="drawer-section">
          <div class="drawer-title">完整 Metadata</div>
          <pre class="json-block">{{ formatJson(selectedRecord.metadata) }}</pre>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatTableDateTime, toBackendUtcDateTime } from "@/utils/dateTime";
import { fetchAgentPage } from "@/api/agent";
import {
  exportCompactionCsv,
  fetchCompactionMetrics,
  fetchCompactionPage,
  type CompactionMetrics,
  type CompactionTraceRecord
} from "@/api/compaction";

interface SelectOption {
  label: string;
  value: number;
}

interface QueryForm {
  pageNum: number;
  pageSize: number;
  traceId: string;
  eventType: string;
  traceStatus: string;
  agentId: number | null;
  keyword: string;
}

const loading = ref(false);
const records = ref<CompactionTraceRecord[]>([]);
const total = ref(0);
const agentOptions = ref<SelectOption[]>([]);
const drawerVisible = ref(false);
const selectedRecord = ref<CompactionTraceRecord | null>(null);
const queryTimeRange = ref<[string, string] | []>([]);

const metrics = ref<CompactionMetrics>({
  totalCount: 0,
  criticalCount: 0,
  warningCount: 0,
  avgQualityScore: 0,
  avgSavedChars: 0,
  avgSavedTokens: 0,
  stageBuckets: [],
  qualityBuckets: [],
  topSavedTokenRecords: []
});

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 20,
  traceId: "",
  eventType: "",
  traceStatus: "",
  agentId: null,
  keyword: ""
});

async function loadAgents(): Promise<void> {
  const page = await fetchAgentPage({
    pageNum: 1,
    pageSize: 100,
    status: "enabled"
  });
  agentOptions.value = page.records.map((item) => ({
    label: `${item.agentName}（${item.agentCode}）`,
    value: item.agentId
  }));
}

function buildQueryParams() {
  const [startedFrom, startedTo] = Array.isArray(queryTimeRange.value) ? queryTimeRange.value : [];
  return {
    pageNum: queryForm.pageNum,
    pageSize: queryForm.pageSize,
    traceId: queryForm.traceId || undefined,
    eventType: queryForm.eventType || undefined,
    traceStatus: queryForm.traceStatus || undefined,
    agentId: queryForm.agentId || undefined,
    keyword: queryForm.keyword || undefined,
    startedFrom: toBackendUtcDateTime(startedFrom),
    startedTo: toBackendUtcDateTime(startedTo)
  };
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const params = buildQueryParams();
    const [page, metricData] = await Promise.all([
      fetchCompactionPage(params),
      fetchCompactionMetrics(params)
    ]);
    records.value = page.records;
    total.value = page.total;
    metrics.value = metricData;
  } catch {
    ElMessage.error("压缩运营数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function handleExport(): Promise<void> {
  try {
    await exportCompactionCsv(buildQueryParams());
    ElMessage.success("导出成功");
  } catch {
    ElMessage.error("导出失败");
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 20;
  queryForm.traceId = "";
  queryForm.eventType = "";
  queryForm.traceStatus = "";
  queryForm.agentId = null;
  queryForm.keyword = "";
  queryTimeRange.value = [];
  void loadData();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadData();
}

function openTroubleshoot(record: CompactionTraceRecord): void {
  selectedRecord.value = record;
  drawerVisible.value = true;
}

function qualityTagType(status: string | null | undefined): "success" | "warning" | "danger" | "info" {
  if (status === "good") {
    return "success";
  }
  if (status === "warning") {
    return "warning";
  }
  if (status === "critical") {
    return "danger";
  }
  return "info";
}

function qualityStatusText(status: string | null | undefined): string {
  if (status === "good") {
    return "良好";
  }
  if (status === "warning") {
    return "预警";
  }
  if (status === "critical") {
    return "严重";
  }
  return "-";
}

function formatJson(value: string): string {
  if (!value) {
    return "{}";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatCompactionExplain(metadataText: string): string {
  if (!metadataText) {
    return "-";
  }
  try {
    const metadata = JSON.parse(metadataText) as Record<string, any>;
    const compaction = metadata.contextCompaction as Record<string, any> | undefined;
    if (!compaction) {
      return "-";
    }
    return JSON.stringify({
      explain: compaction.explain,
      tokenEstimateMode: compaction.tokenEstimateMode,
      validation: compaction.validation,
      layers: Array.isArray(compaction.layers)
        ? compaction.layers.map((item) => ({
            key: item.key,
            strategy: item.strategy,
            sampleBefore: item.sampleBefore,
            sampleAfter: item.sampleAfter
          }))
        : []
    }, null, 2);
  } catch {
    return metadataText;
  }
}

function parsePromptAssembly(metadataText: string): Record<string, any> | null {
  if (!metadataText) {
    return null;
  }
  try {
    const metadata = JSON.parse(metadataText) as Record<string, any>;
    const compaction = metadata.contextCompaction as Record<string, any> | undefined;
    const promptAssembly = compaction?.promptAssembly;
    return promptAssembly && typeof promptAssembly === "object" ? promptAssembly as Record<string, any> : null;
  } catch {
    return null;
  }
}

function formatPromptAssemblyPreview(metadataText: string): string {
  const promptAssembly = parsePromptAssembly(metadataText);
  if (!promptAssembly) {
    return "-";
  }
  return JSON.stringify({
    stage: promptAssembly.stage,
    blockOrder: promptAssembly.blockOrder,
    preview: promptAssembly.preview,
    diff: promptAssembly.diff
  }, null, 2);
}

onMounted(() => {
  void Promise.all([loadAgents(), loadData()]);
});
</script>

<style scoped>
.trace-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.query-card,
.table-card,
.metric-card {
  border-radius: 20px;
  border: 1px solid #dbe5ef;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: #17324d;
}

.card-subtitle {
  margin-top: 6px;
  color: #64768a;
  font-size: 13px;
}

.query-form {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.query-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.metric-card {
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.metric-label {
  font-size: 13px;
  color: #6b7c8f;
}

.metric-value {
  margin-top: 12px;
  font-size: 28px;
  font-weight: 700;
  color: #17324d;
}

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.bucket-section + .bucket-section {
  margin-top: 18px;
}

.bucket-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: #29445f;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.drawer-section + .drawer-section {
  margin-top: 20px;
}

.drawer-title {
  margin-bottom: 10px;
  font-size: 16px;
  font-weight: 700;
  color: #17324d;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
  color: #44596e;
}

.json-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #29445f;
}

.prompt-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.prompt-preview-card {
  border: 1px solid #dbe5ef;
}

@media (max-width: 1200px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}
</style>

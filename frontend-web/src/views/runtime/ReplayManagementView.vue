<template>
  <section class="replay-page">
    <header class="replay-hero">
      <div>
        <span>DETERMINISTIC RUNTIME AUDIT</span>
        <h1>Replay 管理</h1>
        <p>查看不可变 Manifest、输入快照、Event Diff 与 Artifact 校验；Replay 结果不回写来源执行。</p>
      </div>
      <div class="hero-actions">
        <el-button plain :loading="loading" @click="loadPage">刷新</el-button>
        <el-button color="#d97745" @click="openSimulationDialog">发起 Simulation Replay</el-button>
      </div>
    </header>

    <el-card shadow="never" class="query-card">
      <el-form inline>
        <el-form-item label="模式">
          <el-select v-model="query.replayMode" clearable placeholder="全部模式" style="width: 190px">
            <el-option label="Prompt Replay" value="prompt_replay" />
            <el-option label="Simulation Replay" value="simulation_replay" />
            <el-option label="Execution Replay（未开放）" value="execution_replay" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option label="运行中" value="running" />
            <el-option label="成功" value="succeeded" />
            <el-option label="失败" value="failed" />
            <el-option label="等待" value="pending" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 Run">
          <el-input
            v-model="query.sourceRunCode"
            clearable
            placeholder="完整 Run Code"
            style="width: 250px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form>
    </el-card>

    <div class="metrics-strip">
      <article><span>匹配 Replay</span><strong>{{ total }}</strong></article>
      <article><span>当前页成功</span><strong>{{ pageMetrics.succeeded }}</strong></article>
      <article><span>当前页失败</span><strong>{{ pageMetrics.failed }}</strong></article>
      <article><span>当前页运行中</span><strong>{{ pageMetrics.running }}</strong></article>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="records" border stripe v-loading="loading" @row-dblclick="openDetail">
        <el-table-column label="Replay" min-width="235">
          <template #default="{ row }">
            <div class="primary-cell"><strong>{{ row.replayCode }}</strong><span>{{ row.replayTraceId }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="175">
          <template #default="{ row }"><el-tag effect="plain">{{ modeText(row.replayMode) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="来源" min-width="190">
          <template #default="{ row }">
            <div class="primary-cell"><strong>{{ row.sourceRunCode || "Prompt Trace" }}</strong><span>Task {{ row.sourceTaskId }}</span></div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="耗时" width="110"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="175" :formatter="formatTableDateTime" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="changePage"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="Replay 审计详情" size="76%" destroy-on-close>
      <div v-if="selectedDetail" class="detail-shell" v-loading="detailLoading">
        <header class="detail-header">
          <div><span>{{ selectedDetail.replayCode }}</span><h2>{{ modeText(selectedDetail.replayMode) }}</h2><p>{{ selectedDetail.replayTraceId }}</p></div>
          <el-tag size="large" :type="statusType(selectedDetail.status)">{{ statusText(selectedDetail.status) }}</el-tag>
        </header>
        <el-alert
          v-if="selectedDetail.errorMessage"
          type="error"
          :title="selectedDetail.errorMessage"
          :closable="false"
          show-icon
        />
        <div class="detail-summary">
          <article><span>来源 Run</span><strong>{{ selectedDetail.sourceRunCode || "-" }}</strong></article>
          <article><span>来源 Task</span><strong>{{ selectedDetail.sourceTaskId }}</strong></article>
          <article><span>发起用户</span><strong>{{ selectedDetail.userId }}</strong></article>
          <article><span>创建时间</span><strong>{{ formatDateTime(selectedDetail.createdAt) }}</strong></article>
        </div>
        <el-tabs v-model="detailTab">
          <el-tab-pane label="结果摘要" name="result">
            <div class="result-grid">
              <article><h3>Event Diff</h3><pre>{{ formatJson(selectedDetail.resultSnapshot.eventDiff) }}</pre></article>
              <article><h3>Artifact 校验</h3><pre>{{ formatJson(selectedDetail.resultSnapshot.artifactVerification) }}</pre></article>
            </div>
          </el-tab-pane>
          <el-tab-pane label="Manifest" name="manifest"><pre class="json-block">{{ formatJson(selectedDetail.manifest) }}</pre></el-tab-pane>
          <el-tab-pane label="Input Snapshot" name="input"><pre class="json-block">{{ formatJson(selectedDetail.inputSnapshot) }}</pre></el-tab-pane>
          <el-tab-pane label="Result Snapshot" name="snapshot"><pre class="json-block">{{ formatJson(selectedDetail.resultSnapshot) }}</pre></el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="simulationVisible" title="发起 Simulation Replay" width="560px">
      <el-alert
        title="Simulation Replay 只注入已录制事实，不调用 LLM、Tool Provider、Workflow 或 Sandbox。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="simulation-form">
        <el-form-item label="来源 Run Code" required>
          <el-input v-model="simulationForm.runCode" placeholder="例如 RUN_..." />
        </el-form-item>
        <div class="sequence-grid">
          <el-form-item label="起始事件序号">
            <el-input-number v-model="simulationForm.fromSequence" :min="1" :controls="false" placeholder="默认 1" />
          </el-form-item>
          <el-form-item label="结束事件序号">
            <el-input-number v-model="simulationForm.toSequence" :min="1" :controls="false" placeholder="默认最新" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="simulationVisible = false">取消</el-button>
        <el-button type="primary" :loading="simulationLoading" @click="submitSimulation">确认执行</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import {
  createSimulationReplay,
  fetchReplayDetail,
  fetchReplayPage,
  type ReplayDetail,
  type ReplayMode,
  type ReplayPageItem,
  type ReplayStatus
} from "@/api/replay";

const loading = ref(false);
const detailLoading = ref(false);
const simulationLoading = ref(false);
const detailVisible = ref(false);
const simulationVisible = ref(false);
const detailTab = ref("result");
const records = ref<ReplayPageItem[]>([]);
const selectedDetail = ref<ReplayDetail | null>(null);
const total = ref(0);
const query = reactive<{
  pageNum: number;
  pageSize: number;
  replayMode?: ReplayMode;
  status?: ReplayStatus;
  sourceRunCode?: string;
}>({ pageNum: 1, pageSize: 20 });
const simulationForm = reactive<{
  runCode: string;
  fromSequence?: number;
  toSequence?: number;
}>({ runCode: "" });

const pageMetrics = computed(() => ({
  succeeded: records.value.filter((item) => item.status === "succeeded").length,
  failed: records.value.filter((item) => item.status === "failed").length,
  running: records.value.filter((item) => item.status === "running").length
}));

async function loadPage(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchReplayPage({
      ...query,
      sourceRunCode: query.sourceRunCode?.trim() || undefined
    });
    records.value = result.records;
    total.value = result.total;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function openDetail(row: ReplayPageItem): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  detailTab.value = "result";
  try {
    selectedDetail.value = await fetchReplayDetail(row.replayCode);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    detailLoading.value = false;
  }
}

function openSimulationDialog(): void {
  Object.assign(simulationForm, { runCode: "", fromSequence: undefined, toSequence: undefined });
  simulationVisible.value = true;
}

async function submitSimulation(): Promise<void> {
  const runCode = simulationForm.runCode.trim();
  if (!runCode) {
    ElMessage.warning("请输入来源 Run Code");
    return;
  }
  if (simulationForm.fromSequence && simulationForm.toSequence
      && simulationForm.fromSequence > simulationForm.toSequence) {
    ElMessage.warning("起始事件序号不能大于结束事件序号");
    return;
  }
  simulationLoading.value = true;
  try {
    const result = await createSimulationReplay(runCode, {
      fromSequence: simulationForm.fromSequence,
      toSequence: simulationForm.toSequence
    });
    simulationVisible.value = false;
    ElMessage.success(`Simulation Replay 已完成：${result.replayCode}`);
    query.pageNum = 1;
    await loadPage();
    const row = records.value.find((item) => item.replayCode === result.replayCode);
    if (row) await openDetail(row);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    simulationLoading.value = false;
  }
}

function search(): void { query.pageNum = 1; void loadPage(); }
function resetQuery(): void { Object.assign(query, { pageNum: 1, pageSize: 20, replayMode: undefined, status: undefined, sourceRunCode: undefined }); void loadPage(); }
function changePage(page: number): void { query.pageNum = page; void loadPage(); }
function modeText(value: ReplayMode): string { return value === "prompt_replay" ? "Prompt Replay" : value === "simulation_replay" ? "Simulation Replay" : "Execution Replay"; }
function statusText(value: ReplayStatus): string { return value === "succeeded" ? "成功" : value === "failed" ? "失败" : value === "running" ? "运行中" : "等待"; }
function statusType(value: ReplayStatus): "success" | "danger" | "warning" | "info" { return value === "succeeded" ? "success" : value === "failed" ? "danger" : value === "running" ? "warning" : "info"; }
function formatDuration(value: number | null): string { if (value == null) return "-"; if (value < 1000) return `${value} ms`; return `${(value / 1000).toFixed(2)} s`; }
function formatJson(value: unknown): string { return value == null ? "-" : JSON.stringify(value, null, 2); }
function errorMessage(error: unknown): string { return error instanceof Error ? error.message : "操作失败"; }

onMounted(loadPage);
</script>

<style scoped>
.replay-page{min-width:0}.replay-hero{display:flex;align-items:end;justify-content:space-between;gap:24px;padding:30px;color:#eef8f4;background:linear-gradient(120deg,#122735,#174d57 65%,#735127);border-radius:20px 20px 0 0}.replay-hero span{font:600 11px Georgia;letter-spacing:.2em;color:#ef9d4c}.replay-hero h1{margin:8px 0;font:700 33px Georgia}.replay-hero p{margin:0;color:#bed5d4}.hero-actions{display:flex;gap:10px}.query-card{border-radius:0;border-top:0}.query-card :deep(.el-form){align-items:center}.metrics-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:16px 0}.metrics-strip article{padding:16px 18px;border:1px solid #d8e3e0;border-radius:14px;background:#fff}.metrics-strip span{display:block;color:#71817f;font-size:13px}.metrics-strip strong{display:block;margin-top:5px;color:#174d57;font-size:25px}.table-card{border-radius:16px}.primary-cell{display:flex;flex-direction:column;gap:4px}.primary-cell span{color:#7a8885;font-size:12px}.pagination-row{display:flex;justify-content:flex-end;padding-top:18px}.detail-shell{min-height:320px}.detail-header{display:flex;align-items:center;justify-content:space-between;padding:20px;border-radius:14px;background:#edf4f2}.detail-header span,.detail-header p{color:#6e807b}.detail-header h2{margin:6px 0;color:#173f43}.detail-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}.detail-summary article{padding:14px;border:1px solid #dbe5e2;border-radius:12px}.detail-summary span{display:block;color:#71817f;font-size:12px}.detail-summary strong{display:block;margin-top:6px;overflow-wrap:anywhere}.result-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.result-grid article{min-width:0;padding:16px;border:1px solid #dbe5e2;border-radius:12px}.result-grid h3{margin-top:0}.result-grid pre,.json-block{max-height:560px;margin:0;overflow:auto;padding:16px;border-radius:10px;background:#102a2f;color:#d9f1e8;font:12px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;white-space:pre-wrap;overflow-wrap:anywhere}.simulation-form{margin-top:18px}.sequence-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.sequence-grid :deep(.el-input-number){width:100%}@media(max-width:1000px){.replay-hero{align-items:flex-start;flex-direction:column}.metrics-strip,.detail-summary,.result-grid{grid-template-columns:1fr}.sequence-grid{grid-template-columns:1fr}}
</style>

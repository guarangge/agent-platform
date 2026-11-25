<template>
  <section class="eval-page">
    <div class="page-header">
      <div>
        <h2>Eval Harness</h2>
        <p>定义评测集、隔离执行并查看批次结果。</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">新建 Suite</el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="suiteQuery">
        <el-form-item label="Suite 编码">
          <el-input v-model="suiteQuery.suiteCode" clearable />
        </el-form-item>
        <el-form-item label="Suite 名称">
          <el-input v-model="suiteQuery.suiteName" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="suiteQuery.status" clearable style="width: 160px;">
            <el-option label="enabled" value="enabled" />
            <el-option label="disabled" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSuitePage">查询</el-button>
          <el-button @click="resetSuiteQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Suite 列表</span>
        </div>
      </template>
      <el-table :data="suiteRecords" border>
        <el-table-column prop="suiteCode" label="Suite 编码" min-width="160" />
        <el-table-column prop="suiteName" label="Suite 名称" min-width="180" />
        <el-table-column prop="currentVersion" label="当前版本" width="100" />
        <el-table-column prop="agentName" label="Agent" min-width="160" />
        <el-table-column prop="caseCount" label="用例数" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="openEditDialog(row.suiteId)">编辑</el-button>
              <el-button link @click="openVersionDrawer(row.suiteId)">版本</el-button>
              <el-button link type="success" @click="handleExecute(row.suiteId)">执行</el-button>
              <el-button link @click="openRunFilter(row.suiteId)">查看批次</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="suiteQuery.pageNum"
          :page-size="suiteQuery.pageSize"
          :total="suiteTotal"
          @current-change="handleSuitePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="executeDialogVisible" title="执行 Eval Suite" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="触发方式">
          <el-select v-model="executeForm.triggerType" style="width: 100%;">
            <el-option label="manual" value="manual" />
            <el-option label="ci" value="ci" />
          </el-select>
        </el-form-item>
        <el-form-item label="Baseline Run">
          <el-select v-model="executeForm.baselineRunId" clearable filterable style="width: 100%;">
            <el-option v-for="item in executeBaselineOptions" :key="item.runId" :label="`${item.runCode} / v${item.suiteVersion}`" :value="item.runId" />
          </el-select>
        </el-form-item>
        <el-form-item label="Scenario">
          <el-input v-model="executeForm.scenario" placeholder="留空表示执行全部场景" />
        </el-form-item>
        <el-form-item label="Case Tags">
          <el-select v-model="executeForm.caseTags" multiple filterable allow-create default-first-option style="width: 100%;" />
        </el-form-item>
        <el-form-item label="CI Gate">
          <el-switch v-model="executeForm.enforceCiGate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-space>
          <el-button @click="executeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitExecute">开始执行</el-button>
        </el-space>
      </template>
    </el-dialog>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Run 列表</span>
          <el-space>
            <el-select v-model="runCompareForm.fromRunId" placeholder="基准 Run" clearable style="width: 180px;">
              <el-option v-for="item in runRecords" :key="`from-run-${item.runId}`" :label="item.runCode" :value="item.runId" />
            </el-select>
            <el-select v-model="runCompareForm.toRunId" placeholder="目标 Run" clearable style="width: 180px;">
              <el-option v-for="item in runRecords" :key="`to-run-${item.runId}`" :label="item.runCode" :value="item.runId" />
            </el-select>
            <el-button @click="loadRunCompare">对比 Run</el-button>
            <el-button plain :disabled="runCompareForm.fromRunId == null || runCompareForm.toRunId == null" @click="handleExportRunCompare">导出对比</el-button>
          </el-space>
        </div>
      </template>
      <el-form :inline="true" :model="runQuery" class="run-form">
        <el-form-item label="Suite ID">
          <el-input-number v-model="runQuery.suiteId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="runQuery.runStatus" clearable style="width: 160px;">
            <el-option label="success" value="success" />
            <el-option label="failed" value="failed" />
            <el-option label="running" value="running" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadRunPage">查询</el-button>
          <el-button @click="resetRunQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="runRecords" border>
        <el-table-column prop="runCode" label="Run 编码" min-width="180" />
        <el-table-column prop="suiteName" label="Suite" min-width="180" />
        <el-table-column prop="suiteVersion" label="Suite版本" width="100" />
        <el-table-column prop="agentName" label="Agent" min-width="140" />
        <el-table-column prop="runStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="runStatusTagType(row.runStatus)">{{ row.runStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCases" label="总数" width="80" />
        <el-table-column prop="passedCases" label="通过" width="80" />
        <el-table-column prop="failedCases" label="失败" width="80" />
        <el-table-column prop="averageScore" label="均分" width="100" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-space wrap>
              <el-button link type="primary" @click="openRunDetail(row.runId)">详情</el-button>
              <el-button link @click="handleMarkBaseline(row)">设为基线</el-button>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="runQuery.pageNum"
          :page-size="runQuery.pageSize"
          :total="runTotal"
          @current-change="handleRunPageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="suiteDialogVisible" :title="suiteDialogTitle" width="1100px" destroy-on-close>
      <el-form label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Suite 编码">
              <el-input v-model="suiteForm.suiteCode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Suite 名称">
              <el-input v-model="suiteForm.suiteName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Agent">
              <el-select v-model="suiteForm.agentId" filterable style="width: 100%;" @change="handleAgentChange">
                <el-option v-for="agent in agentOptions" :key="agent.agentId" :label="agent.agentName" :value="agent.agentId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="suiteForm.status" style="width: 100%;">
                <el-option label="enabled" value="enabled" />
                <el-option label="disabled" value="disabled" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="描述">
              <el-input v-model="suiteForm.description" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Judge 模式">
              <el-select v-model="suiteJudgeMode" style="width: 100%;">
                <el-option label="rule_only" value="rule_only" />
                <el-option label="rule_and_llm" value="rule_and_llm" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="CI Gate">
              <el-switch v-model="suiteGateConfig.enabled" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最低均分">
              <el-input-number v-model="suiteGateConfig.minimumAverageScore" :min="0" :max="100" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最低通过率">
              <el-input-number v-model="suiteGateConfig.minimumPassRate" :min="0" :max="100" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大失败数">
              <el-input-number v-model="suiteGateConfig.maximumFailedCases" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="case-toolbar">
        <span>用例列表</span>
        <el-button size="small" @click="addCase">新增用例</el-button>
      </div>
      <el-table :data="suiteForm.evalCases" border>
        <el-table-column label="#" width="60">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="编码" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.caseCode" />
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.caseName" />
          </template>
        </el-table-column>
        <el-table-column label="输入" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.userInput" type="textarea" :rows="3" />
          </template>
        </el-table-column>
        <el-table-column label="场景" min-width="140">
          <template #default="{ row }">
            <el-input :model-value="readCaseScenario(row)" @update:model-value="writeCaseScenario(row, $event)" />
          </template>
        </el-table-column>
        <el-table-column label="Tags" min-width="180">
          <template #default="{ row }">
            <el-select
              :model-value="readCaseTags(row)"
              multiple
              filterable
              allow-create
              default-first-option
              style="width: 100%;"
              @update:model-value="writeCaseTags(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="期望 Tool" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.expectedToolCodes" multiple filterable allow-create default-first-option style="width: 100%;">
              <el-option v-for="tool in toolOptions" :key="tool" :label="tool" :value="tool" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="期望步骤类型" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.expectedStepTypes" multiple allow-create default-first-option style="width: 100%;">
              <el-option v-for="stepType in stepTypeOptions" :key="stepType" :label="stepType" :value="stepType" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="回复关键词" min-width="200">
          <template #default="{ row }">
            <el-select v-model="row.expectedReplyKeywords" multiple allow-create default-first-option style="width: 100%;" />
          </template>
        </el-table-column>
        <el-table-column label="禁止词" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.forbiddenReplyKeywords" multiple allow-create default-first-option style="width: 100%;" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-select v-model="row.status">
              <el-option label="enabled" value="enabled" />
              <el-option label="disabled" value="disabled" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="阈值" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.passThreshold" :min="0" :max="100" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeCase($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-space>
          <el-button @click="suiteDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitSuite">保存</el-button>
        </el-space>
      </template>
    </el-dialog>

    <el-drawer v-model="runDrawerVisible" title="Run 详情" size="960px">
      <div v-if="selectedRun" class="run-detail">
        <div class="run-summary">
          <el-tag :type="runStatusTagType(selectedRun.runStatus)">{{ selectedRun.runStatus }}</el-tag>
          <span>{{ selectedRun.runCode }}</span>
          <span>均分 {{ selectedRun.averageScore }}</span>
          <span>通过 {{ selectedRun.passedCases }} / {{ selectedRun.totalCases }}</span>
          <el-button plain size="small" @click="handleExportRunDetail">导出 Run</el-button>
          <el-button plain size="small" @click="handleExportRunJson">导出 JSON</el-button>
          <el-button plain size="small" @click="handleExportRunMarkdown">导出 Markdown</el-button>
        </div>
        <el-alert
          v-if="selectedRun.baselineComparison"
          type="info"
          :closable="false"
          show-icon
          :title="`对比基线 ${selectedRun.baselineComparison.baselineRunCode}`"
          :description="`均分变化 ${formatDelta(selectedRun.baselineComparison.scoreDelta)}，通过数变化 ${formatIntDelta(selectedRun.baselineComparison.passDelta)}，改善 ${selectedRun.baselineComparison.improvedCaseCount}，回退 ${selectedRun.baselineComparison.regressedCaseCount}`"
        />
        <el-table :data="selectedRun.runCases" border>
          <el-table-column prop="caseCode" label="用例编码" min-width="140" />
          <el-table-column prop="caseName" label="用例名称" min-width="180" />
          <el-table-column prop="runStatus" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="runStatusTagType(row.runStatus)">{{ row.runStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="90" />
          <el-table-column prop="ruleScore" label="规则分" width="90" />
          <el-table-column prop="judgeScore" label="Judge分" width="90" />
          <el-table-column label="基线差值" width="100">
            <template #default="{ row }">{{ formatNullableDelta(row.scoreDelta) }}</template>
          </el-table-column>
          <el-table-column label="Tool" min-width="160">
            <template #default="{ row }">{{ row.actualToolCodes.join(", ") || "-" }}</template>
          </el-table-column>
          <el-table-column label="步骤类型" min-width="160">
            <template #default="{ row }">{{ row.actualStepTypes.join(", ") || "-" }}</template>
          </el-table-column>
          <el-table-column label="Judge 结论" min-width="220">
            <template #default="{ row }">{{ row.judgeRationale || "-" }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="240">
            <template #default="{ row }">{{ row.failureReasons.join("；") || "-" }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="versionDrawerVisible" title="Suite 版本" size="980px">
      <div class="version-toolbar">
        <el-space>
          <el-select v-model="diffForm.fromVersion" placeholder="基准版本" style="width: 160px;">
            <el-option v-for="item in suiteVersions" :key="`from-${item.versionId}`" :label="`v${item.versionNo}`" :value="item.versionNo" />
          </el-select>
          <el-select v-model="diffForm.toVersion" placeholder="目标版本" style="width: 160px;">
            <el-option v-for="item in suiteVersions" :key="`to-${item.versionId}`" :label="`v${item.versionNo}`" :value="item.versionNo" />
          </el-select>
          <el-button type="primary" @click="loadSuiteDiff">对比</el-button>
          <el-button plain :disabled="diffForm.fromVersion == null || diffForm.toVersion == null" @click="handleExportSuiteDiff">导出 Diff</el-button>
        </el-space>
      </div>
      <el-table :data="suiteVersions" border>
        <el-table-column prop="versionNo" label="版本" width="90">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="suiteName" label="Suite 名称" min-width="180" />
        <el-table-column prop="agentName" label="Agent" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="caseCount" label="用例数" width="100" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
      </el-table>
      <div v-if="suiteDiff" class="version-diff">
        <div class="version-diff__summary">
          <span>新增 {{ suiteDiff.summary.addedCount }}</span>
          <span>删除 {{ suiteDiff.summary.removedCount }}</span>
          <span>变更 {{ suiteDiff.summary.changedCount }}</span>
        </div>
        <el-table :data="suiteDiff.changedItems" border>
          <el-table-column prop="changeType" label="类型" width="100" />
          <el-table-column prop="itemKey" label="对象" min-width="180" />
          <el-table-column prop="beforeText" label="变更前" min-width="260" show-overflow-tooltip />
          <el-table-column prop="afterText" label="变更后" min-width="260" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="runCompareDrawerVisible" title="Run 对比" size="1080px">
      <div v-if="runCompareResult" class="run-compare">
        <div class="version-diff__summary">
          <span>{{ runCompareResult.fromRunCode }} (v{{ runCompareResult.fromSuiteVersion }})</span>
          <span>{{ runCompareResult.toRunCode }} (v{{ runCompareResult.toSuiteVersion }})</span>
          <span>均分变化 {{ formatDelta(runCompareResult.summary.averageScoreDelta) }}</span>
          <span>通过数变化 {{ formatIntDelta(runCompareResult.summary.passDelta) }}</span>
        </div>
        <div class="version-diff__summary">
          <span>改善 {{ runCompareResult.summary.improvedCount }}</span>
          <span>回退 {{ runCompareResult.summary.regressedCount }}</span>
          <span>不变 {{ runCompareResult.summary.unchangedCount }}</span>
          <span>新增 {{ runCompareResult.summary.addedCount }}</span>
          <span>删除 {{ runCompareResult.summary.removedCount }}</span>
        </div>
        <el-table :data="runCompareResult.comparedCases" border>
          <el-table-column prop="caseCode" label="用例编码" min-width="140" />
          <el-table-column prop="caseName" label="用例名称" min-width="180" />
          <el-table-column prop="changeType" label="变化" width="100" />
          <el-table-column prop="fromScore" label="基准分" width="90" />
          <el-table-column prop="toScore" label="目标分" width="90" />
          <el-table-column label="差值" width="90">
            <template #default="{ row }">{{ formatNullableDelta(row.scoreDelta) }}</template>
          </el-table-column>
          <el-table-column prop="fromStatus" label="基准状态" width="100" />
          <el-table-column prop="toStatus" label="目标状态" width="100" />
          <el-table-column prop="fromReply" label="基准回复" min-width="260" show-overflow-tooltip />
          <el-table-column prop="toReply" label="目标回复" min-width="260" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatTableDateTime } from "@/utils/dateTime";
import { fetchAgentPage, fetchAgentToolScope, type AgentRecord, type ToolOption } from "@/api/agent";
import {
  createEvalSuite,
  executeEvalSuite,
  exportEvalRunCompare,
  exportEvalRunDetail,
  exportEvalRunJsonReport,
  exportEvalRunMarkdownReport,
  exportEvalSuiteDiff,
  fetchEvalRunCompare,
  fetchEvalRunDetail,
  fetchEvalRunPage,
  fetchEvalSuiteDiff,
  fetchEvalSuiteDetail,
  fetchEvalSuitePage,
  fetchEvalSuiteVersions,
  markEvalBaselineRun,
  updateEvalSuite,
  type ExecuteEvalSuitePayload,
  type EvalSuiteDiffResult,
  type EvalSuiteVersionRecord,
  type EvalCasePayload,
  type EvalRunCompareResult,
  type EvalRunDetail,
  type EvalRunRecord,
  type EvalSuitePayload,
  type EvalSuiteRecord
} from "@/api/eval";

interface SuiteFormState extends EvalSuitePayload {}

const stepTypeOptions = ["tool", "workflow", "reply", "condition", "approval", "handoff"];
const suiteRecords = ref<EvalSuiteRecord[]>([]);
const runRecords = ref<EvalRunRecord[]>([]);
const suiteTotal = ref(0);
const runTotal = ref(0);
const suiteDialogVisible = ref(false);
const editingSuiteId = ref<number | null>(null);
const executeDialogVisible = ref(false);
const executingSuiteId = ref<number | null>(null);
const runDrawerVisible = ref(false);
const versionDrawerVisible = ref(false);
const selectedRun = ref<EvalRunDetail | null>(null);
const runCompareDrawerVisible = ref(false);
const runCompareResult = ref<EvalRunCompareResult | null>(null);
const suiteVersions = ref<EvalSuiteVersionRecord[]>([]);
const suiteDiff = ref<EvalSuiteDiffResult | null>(null);
const currentVersionSuiteId = ref<number | null>(null);
const agentOptions = ref<AgentRecord[]>([]);
const toolOptions = ref<string[]>([]);

const suiteQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  suiteCode: "",
  suiteName: "",
  status: ""
});

const runQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  suiteId: undefined as number | undefined,
  runStatus: ""
});

const diffForm = reactive({
  fromVersion: undefined as number | undefined,
  toVersion: undefined as number | undefined
});

const runCompareForm = reactive({
  fromRunId: undefined as number | undefined,
  toRunId: undefined as number | undefined
});

const suiteForm = reactive<SuiteFormState>(createEmptySuiteForm());
const suiteJudgeMode = ref("rule_only");
const suiteGateConfig = reactive({
  enabled: false,
  minimumAverageScore: 80,
  minimumPassRate: 80,
  maximumFailedCases: 0
});
const executeForm = reactive<ExecuteEvalSuitePayload>({
  triggerType: "manual",
  baselineRunId: undefined,
  scenario: "",
  caseTags: [],
  enforceCiGate: false
});

const suiteDialogTitle = computed(() => (editingSuiteId.value == null ? "新建 Eval Suite" : "编辑 Eval Suite"));
const executeBaselineOptions = computed(() =>
  runRecords.value.filter((item) => item.suiteId === executingSuiteId.value)
);

function createEmptyCase(): EvalCasePayload {
  return {
    caseCode: "",
    caseName: "",
    userInput: "",
    expectedReplyKeywords: [],
    forbiddenReplyKeywords: [],
    expectedToolCodes: [],
    expectedStepTypes: [],
    expectedFinalStatus: "success",
    passThreshold: 80,
    orderNo: 1,
    status: "enabled",
    metadata: {}
  };
}

function createEmptySuiteForm(): SuiteFormState {
  return {
    suiteCode: "",
    suiteName: "",
    agentId: 0,
    agentName: "",
    description: "",
    status: "enabled",
    metadata: {},
    evalCases: [createEmptyCase()]
  };
}

function resetSuiteForm(): void {
  Object.assign(suiteForm, createEmptySuiteForm());
  suiteJudgeMode.value = "rule_only";
  suiteGateConfig.enabled = false;
  suiteGateConfig.minimumAverageScore = 80;
  suiteGateConfig.minimumPassRate = 80;
  suiteGateConfig.maximumFailedCases = 0;
}

function resetExecuteForm(): void {
  executeForm.triggerType = "manual";
  executeForm.baselineRunId = undefined;
  executeForm.scenario = "";
  executeForm.caseTags = [];
  executeForm.enforceCiGate = false;
}

async function loadAgentOptions(): Promise<void> {
  const page = await fetchAgentPage({ pageNum: 1, pageSize: 200 });
  agentOptions.value = page.records;
}

async function loadSuitePage(): Promise<void> {
  const page = await fetchEvalSuitePage({
    pageNum: suiteQuery.pageNum,
    pageSize: suiteQuery.pageSize,
    suiteCode: suiteQuery.suiteCode || undefined,
    suiteName: suiteQuery.suiteName || undefined,
    status: suiteQuery.status || undefined
  });
  suiteRecords.value = page.records;
  suiteTotal.value = page.total;
}

async function loadRunPage(): Promise<void> {
  const page = await fetchEvalRunPage({
    pageNum: runQuery.pageNum,
    pageSize: runQuery.pageSize,
    suiteId: runQuery.suiteId,
    runStatus: runQuery.runStatus || undefined
  });
  runRecords.value = page.records;
  runTotal.value = page.total;
}

function resetSuiteQuery(): void {
  suiteQuery.pageNum = 1;
  suiteQuery.suiteCode = "";
  suiteQuery.suiteName = "";
  suiteQuery.status = "";
  void loadSuitePage();
}

function resetRunQuery(): void {
  runQuery.pageNum = 1;
  runQuery.suiteId = undefined;
  runQuery.runStatus = "";
  void loadRunPage();
}

function handleSuitePageChange(pageNum: number): void {
  suiteQuery.pageNum = pageNum;
  void loadSuitePage();
}

function handleRunPageChange(pageNum: number): void {
  runQuery.pageNum = pageNum;
  void loadRunPage();
}

function openCreateDialog(): void {
  editingSuiteId.value = null;
  resetSuiteForm();
  suiteDialogVisible.value = true;
}

async function openEditDialog(suiteId: number): Promise<void> {
  editingSuiteId.value = suiteId;
  const detail = await fetchEvalSuiteDetail(suiteId);
  Object.assign(suiteForm, {
    suiteCode: detail.suiteCode,
    suiteName: detail.suiteName,
    agentId: detail.agentId,
    agentName: detail.agentName,
    description: detail.description ?? "",
    status: detail.status,
    metadata: detail.metadata ?? {},
    evalCases: detail.evalCases.map((item) => ({
      caseCode: item.caseCode,
      caseName: item.caseName,
      userInput: item.userInput,
      expectedReplyKeywords: item.expectedReplyKeywords ?? [],
      forbiddenReplyKeywords: item.forbiddenReplyKeywords ?? [],
      expectedToolCodes: item.expectedToolCodes ?? [],
      expectedStepTypes: item.expectedStepTypes ?? [],
      expectedFinalStatus: item.expectedFinalStatus ?? "success",
      passThreshold: item.passThreshold,
      orderNo: item.orderNo,
      status: item.status,
      metadata: item.metadata ?? {}
    }))
  });
  suiteJudgeMode.value = String((detail.metadata ?? {}).judgeMode ?? "rule_only");
  const ciGate = (detail.metadata?.ciGate ?? {}) as Record<string, unknown>;
  suiteGateConfig.enabled = ciGate.enabled === true;
  suiteGateConfig.minimumAverageScore = Number(ciGate.minimumAverageScore ?? 80);
  suiteGateConfig.minimumPassRate = Number(ciGate.minimumPassRate ?? 80);
  suiteGateConfig.maximumFailedCases = Number(ciGate.maximumFailedCases ?? 0);
  await loadToolOptions(detail.agentId);
  suiteDialogVisible.value = true;
}

async function openVersionDrawer(suiteId: number): Promise<void> {
  currentVersionSuiteId.value = suiteId;
  suiteVersions.value = await fetchEvalSuiteVersions(suiteId);
  suiteDiff.value = null;
  diffForm.fromVersion = suiteVersions.value.length > 1 ? suiteVersions.value[1].versionNo : suiteVersions.value[0]?.versionNo;
  diffForm.toVersion = suiteVersions.value[0]?.versionNo;
  versionDrawerVisible.value = true;
}

async function loadToolOptions(agentId: number): Promise<void> {
  if (!agentId) {
    toolOptions.value = [];
    return;
  }
  const scope = await fetchAgentToolScope(agentId);
  toolOptions.value = scope.availableTools.map((item: ToolOption) => item.toolCode);
}

function handleAgentChange(agentId: number): void {
  const agent = agentOptions.value.find((item) => item.agentId === agentId);
  suiteForm.agentName = agent?.agentName ?? "";
  void loadToolOptions(agentId);
}

function addCase(): void {
  suiteForm.evalCases.push({
    ...createEmptyCase(),
    orderNo: suiteForm.evalCases.length + 1
  });
}

function readCaseScenario(casePayload: EvalCasePayload): string {
  return String(casePayload.metadata?.scenario ?? "");
}

function writeCaseScenario(casePayload: EvalCasePayload, value: string): void {
  casePayload.metadata = {
    ...(casePayload.metadata ?? {}),
    scenario: value
  };
}

function readCaseTags(casePayload: EvalCasePayload): string[] {
  const raw = casePayload.metadata?.tags;
  return Array.isArray(raw) ? raw.map((item) => String(item)) : [];
}

function writeCaseTags(casePayload: EvalCasePayload, value: string[]): void {
  casePayload.metadata = {
    ...(casePayload.metadata ?? {}),
    tags: value
  };
}

function removeCase(index: number): void {
  suiteForm.evalCases.splice(index, 1);
  suiteForm.evalCases.forEach((item, currentIndex) => {
    item.orderNo = currentIndex + 1;
  });
}

async function submitSuite(): Promise<void> {
  if (!suiteForm.suiteCode.trim() || !suiteForm.suiteName.trim() || !suiteForm.agentId || !suiteForm.agentName.trim()) {
    ElMessage.warning("请先完整填写 Suite 基础信息");
    return;
  }
  if (!suiteForm.evalCases.length) {
    ElMessage.warning("至少保留一个用例");
    return;
  }
  const payload: EvalSuitePayload = {
    ...suiteForm,
    suiteCode: suiteForm.suiteCode.trim(),
    suiteName: suiteForm.suiteName.trim(),
    agentName: suiteForm.agentName.trim(),
    description: suiteForm.description.trim(),
    metadata: {
      ...(suiteForm.metadata ?? {}),
      judgeMode: suiteJudgeMode.value,
      ciGate: {
        enabled: suiteGateConfig.enabled,
        minimumAverageScore: suiteGateConfig.minimumAverageScore,
        minimumPassRate: suiteGateConfig.minimumPassRate,
        maximumFailedCases: suiteGateConfig.maximumFailedCases
      }
    }
  };
  if (editingSuiteId.value == null) {
    await createEvalSuite(payload);
  } else {
    await updateEvalSuite(editingSuiteId.value, payload);
  }
  ElMessage.success("保存成功");
  suiteDialogVisible.value = false;
  await loadSuitePage();
}

async function handleExecute(suiteId: number): Promise<void> {
  executingSuiteId.value = suiteId;
  resetExecuteForm();
  runQuery.suiteId = suiteId;
  runQuery.pageNum = 1;
  await loadRunPage();
  executeDialogVisible.value = true;
}

async function submitExecute(): Promise<void> {
  if (executingSuiteId.value == null) {
    return;
  }
  await executeEvalSuite(executingSuiteId.value, {
    triggerType: executeForm.triggerType,
    baselineRunId: executeForm.baselineRunId,
    scenario: executeForm.scenario || undefined,
    caseTags: executeForm.caseTags,
    enforceCiGate: executeForm.enforceCiGate
  });
  ElMessage.success("执行完成");
  executeDialogVisible.value = false;
  await loadRunPage();
}

async function loadSuiteDiff(): Promise<void> {
  if (currentVersionSuiteId.value == null || diffForm.fromVersion == null || diffForm.toVersion == null) {
    ElMessage.warning("请先选择两个版本");
    return;
  }
  suiteDiff.value = await fetchEvalSuiteDiff(currentVersionSuiteId.value, diffForm.fromVersion, diffForm.toVersion);
}

async function handleExportSuiteDiff(): Promise<void> {
  if (currentVersionSuiteId.value == null || diffForm.fromVersion == null || diffForm.toVersion == null) {
    ElMessage.warning("请先选择两个版本");
    return;
  }
  await exportEvalSuiteDiff(currentVersionSuiteId.value, diffForm.fromVersion, diffForm.toVersion);
  ElMessage.success("导出成功");
}

function openRunFilter(suiteId: number): void {
  runQuery.suiteId = suiteId;
  runQuery.pageNum = 1;
  void loadRunPage();
}

async function openRunDetail(runId: number): Promise<void> {
  selectedRun.value = await fetchEvalRunDetail(runId);
  runDrawerVisible.value = true;
}

async function handleMarkBaseline(run: EvalRunRecord): Promise<void> {
  await markEvalBaselineRun(run.suiteId, run.runId);
  ElMessage.success("已设为 Suite 基线");
  await loadSuitePage();
}

async function loadRunCompare(): Promise<void> {
  if (runCompareForm.fromRunId == null || runCompareForm.toRunId == null) {
    ElMessage.warning("请先选择两个 Run");
    return;
  }
  if (runCompareForm.fromRunId === runCompareForm.toRunId) {
    ElMessage.warning("请选择两个不同的 Run");
    return;
  }
  runCompareResult.value = await fetchEvalRunCompare(runCompareForm.fromRunId, runCompareForm.toRunId);
  runCompareDrawerVisible.value = true;
}

async function handleExportRunCompare(): Promise<void> {
  if (runCompareForm.fromRunId == null || runCompareForm.toRunId == null) {
    ElMessage.warning("请先选择两个 Run");
    return;
  }
  if (runCompareForm.fromRunId === runCompareForm.toRunId) {
    ElMessage.warning("请选择两个不同的 Run");
    return;
  }
  await exportEvalRunCompare(runCompareForm.fromRunId, runCompareForm.toRunId);
  ElMessage.success("导出成功");
}

async function handleExportRunDetail(): Promise<void> {
  if (selectedRun.value == null) {
    return;
  }
  await exportEvalRunDetail(selectedRun.value.runId);
  ElMessage.success("导出成功");
}

async function handleExportRunJson(): Promise<void> {
  if (selectedRun.value == null) {
    return;
  }
  await exportEvalRunJsonReport(selectedRun.value.runId);
  ElMessage.success("JSON 报告已导出");
}

async function handleExportRunMarkdown(): Promise<void> {
  if (selectedRun.value == null) {
    return;
  }
  await exportEvalRunMarkdownReport(selectedRun.value.runId);
  ElMessage.success("Markdown 报告已导出");
}

function runStatusTagType(status: string): "success" | "danger" | "warning" | "info" {
  if (status === "success") {
    return "success";
  }
  if (status === "failed") {
    return "danger";
  }
  if (status === "running") {
    return "warning";
  }
  return "info";
}

function formatNullableDelta(value: number | null): string {
  if (value == null) {
    return "-";
  }
  return formatDelta(value);
}

function formatDelta(value: number): string {
  return value > 0 ? `+${value}` : `${value}`;
}

function formatIntDelta(value: number): string {
  return value > 0 ? `+${value}` : `${value}`;
}

onMounted(async () => {
  await Promise.all([loadAgentOptions(), loadSuitePage(), loadRunPage()]);
});
</script>

<style scoped>
.eval-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-header h2 {
  margin: 0;
  font-size: 28px;
  color: #17324d;
}

.page-header p {
  margin: 8px 0 0;
  color: #60758b;
}

.filter-card,
.run-form {
  margin-bottom: 12px;
}

.card-header,
.case-toolbar,
.run-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.run-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.version-toolbar,
.version-diff,
.run-compare {
  margin-top: 16px;
}

.version-diff__summary {
  margin-bottom: 12px;
  display: flex;
  gap: 16px;
  color: #44596e;
  font-weight: 600;
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

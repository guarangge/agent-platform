<template>
  <section class="task-ops-page">
    <header class="task-ops-hero">
      <div>
        <span class="eyebrow">AGENT EXECUTION OPERATIONS</span>
        <h1>任务运行中心</h1>
        <p>统一查看 Task、Step、Plan、Approval、Recovery 与 Delegation；会话中心只负责交互。</p>
      </div>
      <div class="hero-actions">
        <el-button plain @click="openTracing()">进入 Tracing</el-button>
        <el-button color="#d97745" :loading="loading" @click="loadTasks">刷新运行</el-button>
      </div>
    </header>

    <el-tabs v-model="activeWorkbench" class="workbench-tabs">
      <el-tab-pane label="任务运行" name="tasks">
        <el-card shadow="never" class="query-card">
          <el-form inline>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 170px;">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Agent">
              <el-select v-model="query.agentId" clearable filterable placeholder="全部 Agent" style="width: 220px;">
                <el-option v-for="agent in agentOptions" :key="agent.agentId" :label="agent.agentName" :value="agent.agentId" />
              </el-select>
            </el-form-item>
            <el-form-item label="会话 ID">
              <el-input-number v-model="query.conversationId" :min="1" :controls="false" style="width: 150px;" />
            </el-form-item>
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" clearable placeholder="Task / 会话编码 / 标题" style="width: 260px;" @keyup.enter="searchTasks" />
            </el-form-item>
            <div class="query-actions">
              <el-button @click="resetTaskQuery">重置</el-button>
              <el-button type="primary" @click="searchTasks">查询</el-button>
            </div>
          </el-form>
        </el-card>

        <div class="metrics-strip">
          <article><span>匹配任务</span><strong>{{ total }}</strong></article>
          <article><span>当前页运行中</span><strong>{{ pageMetrics.running }}</strong></article>
          <article><span>当前页待审批</span><strong>{{ pageMetrics.pendingApprovals }}</strong></article>
          <article><span>当前页失败</span><strong>{{ pageMetrics.failed }}</strong></article>
          <article><span>当前页委派</span><strong>{{ pageMetrics.delegations }}</strong></article>
        </div>

        <el-card shadow="never" class="table-card">
          <el-table :data="records" border stripe v-loading="loading" @row-dblclick="openTaskDetail">
            <el-table-column prop="taskCode" label="任务" min-width="190">
              <template #default="{ row }">
                <div class="primary-cell"><strong>{{ row.taskCode }}</strong><span>{{ row.taskKind || "primary" }} · {{ row.executionType || "-" }}</span></div>
              </template>
            </el-table-column>
            <el-table-column label="会话 / Agent" min-width="220">
              <template #default="{ row }">
                <div class="primary-cell"><strong>{{ row.conversationTitle || row.conversationCode }}</strong><span>{{ row.agentName }} · {{ row.conversationCode }}</span></div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="执行进度" min-width="170">
              <template #default="{ row }">
                <div class="progress-cell"><span>{{ row.successStepCount }}/{{ row.stepCount }} 步骤</span><el-progress :percentage="stepPercentage(row)" :show-text="false" :stroke-width="6" /></div>
              </template>
            </el-table-column>
            <el-table-column label="治理信号" min-width="180">
              <template #default="{ row }">
                <el-space wrap>
                  <el-tag v-if="row.pendingApprovalCount" type="warning" effect="plain">待审批 {{ row.pendingApprovalCount }}</el-tag>
                  <el-tag v-if="row.delegationCount" type="info" effect="plain">委派 {{ row.delegationCount }}</el-tag>
                  <span v-if="!row.pendingApprovalCount && !row.delegationCount">-</span>
                </el-space>
              </template>
            </el-table-column>
            <el-table-column label="耗时" width="120"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="175" :formatter="formatTableDateTime" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTaskDetail(row)">运行详情</el-button>
                <el-button link @click="openConversation(row.conversationId)">会话</el-button>
                <el-button link :disabled="!row.traceId" @click="openTracing(row)">Trace</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="query.pageSize" :current-page="query.pageNum" @current-change="changePage" />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="审批工作台" name="approvals">
        <el-card shadow="never" class="query-card">
          <el-form inline>
            <el-form-item label="状态">
              <el-select v-model="approvalQuery.status" clearable style="width: 170px;">
                <el-option label="待审批" value="pending" /><el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" /><el-option label="已超时" value="timed_out" /><el-option label="已撤回" value="revoked" />
              </el-select>
            </el-form-item>
            <el-form-item label="会话"><el-input v-model="approvalQuery.conversationCode" clearable style="width: 210px;" /></el-form-item>
            <el-form-item label="任务"><el-input v-model="approvalQuery.taskCode" clearable style="width: 210px;" /></el-form-item>
            <el-button @click="resetApprovalQuery">重置</el-button><el-button type="primary" @click="loadApprovals">查询</el-button>
          </el-form>
        </el-card>
        <el-card shadow="never" class="table-card">
          <el-table :data="approvalRecords" border stripe v-loading="approvalLoading">
            <el-table-column prop="taskCode" label="任务" min-width="170" />
            <el-table-column prop="conversationCode" label="会话" min-width="160" />
            <el-table-column prop="agentName" label="Agent" min-width="150" />
            <el-table-column prop="nodeName" label="审批节点" min-width="180" />
            <el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="等待 / SLA" min-width="150"><template #default="{ row }">{{ row.waitingMinutes ?? 0 }} 分钟<span v-if="row.deadlineAt"><br />至 {{ formatDateTime(row.deadlineAt) }}</span></template></el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="175" :formatter="formatTableDateTime" />
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <el-button link @click="openApprovalTask(row)">任务详情</el-button>
                <el-button link type="success" :disabled="row.status !== 'pending'" @click="handleApproval('approve', row)">通过</el-button>
                <el-button link type="danger" :disabled="row.status !== 'pending'" @click="handleApproval('reject', row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row"><el-pagination background layout="total, prev, pager, next" :total="approvalTotal" :page-size="approvalQuery.pageSize" :current-page="approvalQuery.pageNum" @current-change="changeApprovalPage" /></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="任务运行详情" size="78%" destroy-on-close>
      <div v-if="selectedSummary" class="detail-shell" v-loading="detailLoading">
        <header class="detail-header">
          <div><span>{{ selectedSummary.taskCode }}</span><h2>{{ selectedSummary.conversationTitle || selectedSummary.conversationCode }}</h2><p>{{ selectedSummary.agentName }} · {{ selectedSummary.runCode || "同步运行" }}</p></div>
          <div class="detail-actions">
            <el-button @click="openConversation(selectedSummary.conversationId)">查看会话</el-button>
            <el-button :disabled="!selectedSummary.traceId" @click="openTracing(selectedSummary)">排查 Trace</el-button>
            <el-button v-if="canResume(selectedTask?.status)" type="warning" :loading="actionLoading" @click="resumeSelectedTask">恢复执行</el-button>
          </div>
        </header>
        <div class="detail-summary-grid">
          <article><span>状态</span><strong>{{ statusText(selectedTask?.status || selectedSummary.status) }}</strong></article>
          <article><span>步骤</span><strong>{{ selectedTask?.steps.length || selectedSummary.stepCount }}</strong></article>
          <article><span>计划版本</span><strong>v{{ selectedTask?.currentPlanVersion || selectedSummary.currentPlanVersion || 1 }}</strong></article>
          <article><span>审批</span><strong>{{ selectedTask?.approvals.length || 0 }}</strong></article>
          <article><span>Trace</span><strong>{{ selectedTask?.traces.length || 0 }}</strong></article>
          <article><span>耗时</span><strong>{{ formatDuration(selectedSummary.durationMs) }}</strong></article>
        </div>

        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane label="运行概览" name="overview">
            <div class="overview-grid">
              <article><h3>结果摘要</h3><p>{{ selectedTask?.finalResponseSummary || selectedTask?.keyFindings || "暂无结果摘要" }}</p></article>
              <article><h3>错误与恢复</h3><p :class="{ danger: selectedTask?.errorMessage }">{{ selectedTask?.errorMessage || "当前任务未记录错误" }}</p></article>
              <article><h3>执行关系</h3><p>父任务：{{ selectedTask?.parentTaskId || "-" }} / 根任务：{{ selectedTask?.rootTaskId || selectedTask?.taskId || "-" }}</p></article>
              <article><h3>审批 SLA</h3><p>待审批 {{ approvalMetrics?.pendingCount || 0 }}，超 SLA {{ approvalMetrics?.overduePendingCount || 0 }}，平均等待 {{ formatDuration(approvalMetrics?.averageWaitMs) }}</p></article>
            </div>
          </el-tab-pane>

          <el-tab-pane label="步骤" name="steps">
            <el-table :data="selectedTask?.steps || []" border stripe>
              <el-table-column prop="stepNo" label="#" width="65" /><el-table-column prop="stepType" label="类型" width="120" />
              <el-table-column prop="stepName" label="步骤" min-width="220" /><el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
              <el-table-column prop="retryCount" label="重试" width="80" /><el-table-column prop="errorMessage" label="错误" min-width="220" />
              <el-table-column label="快照" width="100"><template #default="{ row }"><el-button link @click="openSnapshot(row)">查看</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="计划 / Replan" name="plans">
            <div class="plan-toolbar">
              <el-button :disabled="(selectedTask?.plans.length || 0) < 2" @click="showLatestPlanDiff">对比最近版本</el-button>
              <el-button type="primary" plain :disabled="!selectedTask?.plans.length" @click="openManualPlan">人工改计划并继续</el-button>
              <el-dropdown @command="handlePlanControl"><el-button>执行控制</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="pause">暂停计划</el-dropdown-item><el-dropdown-item command="resume">继续计划</el-dropdown-item><el-dropdown-item command="single_step">单步执行</el-dropdown-item><el-dropdown-item command="skip_step">跳过步骤</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
            </div>
            <el-collapse accordion>
              <el-collapse-item v-for="plan in selectedTask?.plans || []" :key="plan.taskPlanId" :name="plan.taskPlanId">
                <template #title><strong>v{{ plan.planVersion }} · {{ plan.goal }}</strong><el-tag class="plan-status" size="small" :type="statusTagType(plan.status)">{{ statusText(plan.status) }}</el-tag></template>
                <el-table :data="plan.steps" size="small" border><el-table-column prop="stepNo" label="#" width="60" /><el-table-column prop="stepType" label="类型" width="110" /><el-table-column prop="stepName" label="步骤" min-width="190" /><el-table-column prop="objective" label="目标" min-width="220" /><el-table-column prop="failurePolicy" label="失败策略" width="120" /><el-table-column prop="replannable" label="可重规划" width="100"><template #default="{ row }">{{ row.replannable ? "是" : "否" }}</template></el-table-column><el-table-column prop="status" label="状态" width="100" /></el-table>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>

          <el-tab-pane label="审批 / Recovery" name="approvals">
            <div class="approval-metrics" v-if="approvalMetrics">
              <span>总数 <strong>{{ approvalMetrics.totalCount }}</strong></span><span>待审批 <strong>{{ approvalMetrics.pendingCount }}</strong></span><span>超时 <strong>{{ approvalMetrics.timedOutCount }}</strong></span><span>已撤回 <strong>{{ approvalMetrics.revokedCount }}</strong></span><span>平均等待 <strong>{{ formatDuration(approvalMetrics.averageWaitMs) }}</strong></span>
            </div>
            <el-table :data="selectedTask?.approvals || []" border>
              <el-table-column prop="nodeName" label="节点" min-width="180" /><el-table-column prop="status" label="状态" width="110"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column><el-table-column prop="approvalComment" label="意见" min-width="220" /><el-table-column prop="createdAt" label="创建时间" min-width="175" :formatter="formatTableDateTime" />
              <el-table-column label="操作" width="230"><template #default="{ row }"><el-button link type="success" :disabled="row.status !== 'pending'" @click="handleTaskApproval('approve', row.approvalId)">通过</el-button><el-button link type="danger" :disabled="row.status !== 'pending'" @click="handleTaskApproval('reject', row.approvalId)">驳回</el-button><el-button link :disabled="row.status !== 'approved'" @click="revokeSelectedApproval(row.approvalId)">撤回</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="Delegation" name="delegation">
            <div v-if="delegationTree?.roots.length" class="delegation-layout">
              <el-tree :data="delegationNodes" node-key="taskId" default-expand-all class="delegation-tree"><template #default="{ data }"><div class="tree-node"><strong>{{ data.label }}</strong><span>{{ statusText(data.status) }} · 子任务 {{ data.descendantCount }} · Trace {{ data.traceCount }}</span></div></template></el-tree>
              <el-table :data="delegationTree.audits" border size="small"><el-table-column prop="auditChain" label="任务链" min-width="240" /><el-table-column prop="delegateAgentName" label="委派 Agent" min-width="150" /><el-table-column prop="status" label="状态" width="100" /><el-table-column prop="traceId" label="Trace" min-width="180" /><el-table-column prop="errorMessage" label="错误" min-width="200" /></el-table>
            </div>
            <el-empty v-else description="当前会话没有 Delegation 任务" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="snapshotVisible" title="步骤输入 / 输出快照" width="72%"><div class="snapshot-grid"><div><h4>输入</h4><pre>{{ formatJson(selectedStep?.inputSnapshot) }}</pre></div><div><h4>输出</h4><pre>{{ formatJson(selectedStep?.outputSnapshot) }}</pre></div></div></el-dialog>
    <el-dialog v-model="planDiffVisible" title="最近计划版本差异" width="70%"><template v-if="planDiff"><div class="diff-summary">新增 {{ planDiff.summary.addedCount }} / 删除 {{ planDiff.summary.removedCount }} / 修改 {{ planDiff.summary.changedCount }}</div><el-table :data="planDiff.changedSteps" border><el-table-column prop="changeType" label="变化" width="100" /><el-table-column prop="stepKey" label="步骤" width="150" /><el-table-column prop="beforeText" label="修改前" min-width="240" /><el-table-column prop="afterText" label="修改后" min-width="240" /></el-table></template></el-dialog>
    <el-drawer v-model="manualPlanVisible" title="人工改计划并继续" size="55%"><p class="drawer-tip">编辑 JSON 后将生成新计划版本，并从未完成位置继续。</p><el-input v-model="manualPlanJson" type="textarea" :rows="28" /><div class="drawer-actions"><el-button @click="manualPlanVisible=false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="submitManualPlan">提交并继续</el-button></div></el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import { fetchAgentPage, type AgentRecord } from "@/api/agent";
import { fetchApprovalWorkbenchPage, type ApprovalWorkbenchRecord } from "@/api/guardrail";
import {
  approveConversationTask,
  continueConversationTaskWithManualPlan,
  controlConversationTaskPlan,
  fetchConversationApprovalMetrics,
  fetchConversationExecutions,
  fetchDelegationTree,
  fetchTaskPlanDiff,
  rejectConversationTask,
  resumeConversationTask,
  revokeConversationApproval,
  type ApprovalMetrics,
  type DelegationTreeNode,
  type DelegationTreeResult,
  type ManualTaskPlanContinuePayload,
  type TaskPlanDiffResult,
  type TaskRecord,
  type TaskStepRecord
} from "@/api/conversation";
import { fetchTaskOperationsPage, type TaskOperationsRecord } from "@/api/taskOperations";

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detailLoading = ref(false);
const approvalLoading = ref(false);
const actionLoading = ref(false);
const detailVisible = ref(false);
const snapshotVisible = ref(false);
const planDiffVisible = ref(false);
const manualPlanVisible = ref(false);
const activeWorkbench = ref("tasks");
const detailTab = ref("overview");
const records = ref<TaskOperationsRecord[]>([]);
const approvalRecords = ref<ApprovalWorkbenchRecord[]>([]);
const agentOptions = ref<AgentRecord[]>([]);
const total = ref(0);
const approvalTotal = ref(0);
const selectedSummary = ref<TaskOperationsRecord | null>(null);
const selectedTask = ref<TaskRecord | null>(null);
const selectedStep = ref<TaskStepRecord | null>(null);
const delegationTree = ref<DelegationTreeResult | null>(null);
const approvalMetrics = ref<ApprovalMetrics | null>(null);
const planDiff = ref<TaskPlanDiffResult | null>(null);
const manualPlanJson = ref("");

const query = reactive({ pageNum: 1, pageSize: 20, status: "", agentId: undefined as number | undefined, conversationId: undefined as number | undefined, taskId: undefined as number | undefined, keyword: "" });
const approvalQuery = reactive({ pageNum: 1, pageSize: 20, status: "pending", conversationCode: "", taskCode: "" });
const statusOptions = [
  { label: "排队中", value: "queued" }, { label: "执行中", value: "processing" }, { label: "成功", value: "success" },
  { label: "失败", value: "failed" }, { label: "待审批", value: "waiting_approval" }, { label: "已暂停", value: "paused" },
  { label: "等待事件", value: "waiting_event" }, { label: "已取消", value: "cancelled" }
];

const pageMetrics = computed(() => ({
  running: records.value.filter((item) => ["processing", "running", "queued"].includes(item.status)).length,
  pendingApprovals: records.value.reduce((sum, item) => sum + item.pendingApprovalCount, 0),
  failed: records.value.filter((item) => item.status === "failed").length,
  delegations: records.value.reduce((sum, item) => sum + item.delegationCount, 0)
}));

const delegationNodes = computed(() => (delegationTree.value?.roots || []).map(toTreeNode));

async function loadTasks(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchTaskOperationsPage({
      pageNum: query.pageNum, pageSize: query.pageSize, status: query.status || undefined,
      agentId: query.agentId, conversationId: query.conversationId, taskId: query.taskId, keyword: query.keyword || undefined
    });
    records.value = result.records;
    total.value = result.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, "任务运行记录加载失败"));
  } finally {
    loading.value = false;
  }
}

async function loadApprovals(): Promise<void> {
  approvalLoading.value = true;
  try {
    const result = await fetchApprovalWorkbenchPage({ ...approvalQuery, status: approvalQuery.status || undefined, conversationCode: approvalQuery.conversationCode || undefined, taskCode: approvalQuery.taskCode || undefined });
    approvalRecords.value = result.records;
    approvalTotal.value = result.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, "审批记录加载失败"));
  } finally {
    approvalLoading.value = false;
  }
}

async function openTaskDetail(row: TaskOperationsRecord): Promise<void> {
  selectedSummary.value = row;
  selectedTask.value = null;
  detailVisible.value = true;
  detailLoading.value = true;
  detailTab.value = "overview";
  try {
    const [execution, tree, metrics] = await Promise.all([
      fetchConversationExecutions(row.conversationId), fetchDelegationTree(row.conversationId), fetchConversationApprovalMetrics(row.conversationId)
    ]);
    selectedTask.value = execution.tasks.find((task) => task.taskId === row.taskId) || null;
    delegationTree.value = tree;
    approvalMetrics.value = metrics;
    if (!selectedTask.value) ElMessage.warning("任务详情不在当前会话执行记录中");
  } catch (error) {
    ElMessage.error(errorMessage(error, "任务详情加载失败"));
  } finally {
    detailLoading.value = false;
  }
}

async function reloadSelectedTask(): Promise<void> {
  if (!selectedSummary.value) return;
  await loadTasks();
  const refreshed = records.value.find((item) => item.taskId === selectedSummary.value?.taskId) || selectedSummary.value;
  await openTaskDetail(refreshed);
}

async function resumeSelectedTask(): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value) return;
  actionLoading.value = true;
  try {
    await resumeConversationTask(selectedSummary.value.conversationId, selectedTask.value.taskId);
    ElMessage.success("任务已恢复执行");
    await reloadSelectedTask();
  } catch (error) {
    ElMessage.error(errorMessage(error, "任务恢复失败"));
  } finally { actionLoading.value = false; }
}

async function handleTaskApproval(mode: "approve" | "reject", approvalId: number): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value) return;
  const { value } = await ElMessageBox.prompt("可填写本次审批意见", mode === "approve" ? "通过审批" : "驳回审批", { inputType: "textarea", confirmButtonText: "确认", cancelButtonText: "取消" });
  actionLoading.value = true;
  try {
    if (mode === "approve") await approveConversationTask(selectedSummary.value.conversationId, selectedTask.value.taskId, approvalId, { comment: value || undefined });
    else await rejectConversationTask(selectedSummary.value.conversationId, selectedTask.value.taskId, approvalId, { comment: value || undefined, replanAfterReject: true });
    ElMessage.success(mode === "approve" ? "审批已通过" : "审批已驳回并触发恢复策略");
    await reloadSelectedTask();
    await loadApprovals();
  } finally { actionLoading.value = false; }
}

async function handleApproval(mode: "approve" | "reject", approval: ApprovalWorkbenchRecord): Promise<void> {
  const { value } = await ElMessageBox.prompt("可填写本次审批意见", mode === "approve" ? "通过审批" : "驳回审批", { inputType: "textarea" });
  if (mode === "approve") await approveConversationTask(approval.conversationId, approval.taskId, approval.approvalId, { comment: value || undefined });
  else await rejectConversationTask(approval.conversationId, approval.taskId, approval.approvalId, { comment: value || undefined, replanAfterReject: true });
  ElMessage.success("审批状态已更新");
  await Promise.all([loadApprovals(), loadTasks()]);
}

async function revokeSelectedApproval(approvalId: number): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value) return;
  await ElMessageBox.confirm("撤回后任务将回到待处理状态，确认继续？", "撤回审批");
  await revokeConversationApproval(selectedSummary.value.conversationId, selectedTask.value.taskId, approvalId, { comment: "由任务运行中心撤回" });
  ElMessage.success("审批已撤回");
  await reloadSelectedTask();
}

async function handlePlanControl(command: "pause" | "resume" | "single_step" | "skip_step"): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value) return;
  let targetStepNo: number | undefined;
  if (["single_step", "skip_step"].includes(command)) {
    const result = await ElMessageBox.prompt("请输入目标步骤号", "计划执行控制", { inputPattern: /^\d+$/, inputErrorMessage: "请输入正整数" });
    targetStepNo = Number(result.value);
  }
  await controlConversationTaskPlan(selectedSummary.value.conversationId, selectedTask.value.taskId, { action: command, targetStepNo, comment: "由任务运行中心操作" });
  ElMessage.success("计划控制指令已提交");
  await reloadSelectedTask();
}

async function showLatestPlanDiff(): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value || selectedTask.value.plans.length < 2) return;
  const versions = [...selectedTask.value.plans].sort((a, b) => b.planVersion - a.planVersion);
  planDiff.value = await fetchTaskPlanDiff(selectedSummary.value.conversationId, selectedTask.value.taskId, versions[1].planVersion, versions[0].planVersion);
  planDiffVisible.value = true;
}

function openManualPlan(): void {
  const plan = selectedTask.value?.plans.slice().sort((a, b) => b.planVersion - a.planVersion)[0];
  if (!plan) return;
  manualPlanJson.value = JSON.stringify({
    goal: plan.goal, planType: plan.planType, assumptions: parseStringArray(plan.assumptionsJson), constraints: parseStringArray(plan.constraintsJson), risks: parseStringArray(plan.risksJson), comment: "人工调整计划",
    steps: plan.steps.filter((step) => !["success", "skipped"].includes(step.status)).map((step) => ({
      stepNo: step.stepNo, stepId: step.stepId, stepType: step.stepType, stepName: step.stepName, objective: step.objective,
      dependsOnStepIds: parseStringArray(step.dependsOnJson), targetToolCode: step.targetToolCode, targetWorkflowId: step.targetWorkflowId,
      inputs: parseObject(step.inputsJson), expectedOutput: step.expectedOutput, failurePolicy: step.failurePolicy,
      replannable: step.replannable, status: step.status, description: step.description
    }))
  }, null, 2);
  manualPlanVisible.value = true;
}

async function submitManualPlan(): Promise<void> {
  if (!selectedSummary.value || !selectedTask.value) return;
  actionLoading.value = true;
  try {
    const payload = JSON.parse(manualPlanJson.value) as ManualTaskPlanContinuePayload;
    await continueConversationTaskWithManualPlan(selectedSummary.value.conversationId, selectedTask.value.taskId, payload);
    manualPlanVisible.value = false;
    ElMessage.success("新计划版本已创建并继续执行");
    await reloadSelectedTask();
  } catch (error) {
    ElMessage.error(errorMessage(error, "人工计划提交失败，请检查 JSON"));
  } finally { actionLoading.value = false; }
}

function searchTasks(): void { query.pageNum = 1; void loadTasks(); }
function resetTaskQuery(): void { Object.assign(query, { pageNum: 1, status: "", agentId: undefined, conversationId: undefined, taskId: undefined, keyword: "" }); void loadTasks(); }
function changePage(page: number): void { query.pageNum = page; void loadTasks(); }
function resetApprovalQuery(): void { Object.assign(approvalQuery, { pageNum: 1, status: "pending", conversationCode: "", taskCode: "" }); void loadApprovals(); }
function changeApprovalPage(page: number): void { approvalQuery.pageNum = page; void loadApprovals(); }
function openApprovalTask(row: ApprovalWorkbenchRecord): void { void router.push({ path: "/task-operations", query: { taskId: String(row.taskId), conversationId: String(row.conversationId) } }); void openTaskFromId(row.taskId, row.conversationId); }
function openConversation(conversationId: number): void { void router.push({ path: "/chat", query: { conversationId: String(conversationId) } }); }
function openTracing(row?: Pick<TaskOperationsRecord, "taskId" | "traceId">): void { void router.push({ path: "/traces", query: row ? { taskId: String(row.taskId), traceId: row.traceId || undefined } : {} }); }
function openSnapshot(step: TaskStepRecord): void { selectedStep.value = step; snapshotVisible.value = true; }
function canResume(status?: string): boolean { return !!status && ["failed", "paused", "waiting_approval", "waiting_event"].includes(status); }
function stepPercentage(row: TaskOperationsRecord): number { return row.stepCount ? Math.round((row.successStepCount / row.stepCount) * 100) : 0; }
function formatDuration(value?: number | null): string { if (value == null) return "-"; if (value < 1000) return `${value} ms`; if (value < 60000) return `${(value / 1000).toFixed(1)} s`; return `${(value / 60000).toFixed(1)} min`; }
function statusText(value?: string): string { return ({ queued: "排队中", running: "执行中", processing: "执行中", success: "成功", completed: "已完成", failed: "失败", waiting_approval: "待审批", waiting_event: "等待事件", paused: "已暂停", cancelled: "已取消", pending: "待审批", approved: "已通过", rejected: "已拒绝", timed_out: "已超时", revoked: "已撤回", skipped: "已跳过" } as Record<string, string>)[value || ""] || value || "-"; }
function statusTagType(value?: string): "success" | "warning" | "danger" | "info" | "primary" { if (["success", "completed", "approved"].includes(value || "")) return "success"; if (["failed", "rejected", "timed_out"].includes(value || "")) return "danger"; if (["pending", "queued", "waiting_approval", "waiting_event", "paused"].includes(value || "")) return "warning"; return "info"; }
function formatJson(value?: string | null): string { try { return JSON.stringify(JSON.parse(value || "{}"), null, 2); } catch { return value || "-"; } }
function parseStringArray(value?: string | null): string[] { try { const result = JSON.parse(value || "[]"); return Array.isArray(result) ? result.map(String) : []; } catch { return []; } }
function parseObject(value?: string | null): Record<string, unknown> { try { const result = JSON.parse(value || "{}"); return result && typeof result === "object" && !Array.isArray(result) ? result : {}; } catch { return {}; } }
function errorMessage(error: unknown, fallback: string): string { return error instanceof Error && error.message ? error.message : fallback; }
function toTreeNode(node: DelegationTreeNode): Record<string, unknown> { return { ...node, label: `${node.taskCode} · ${node.agentName || node.agentId}`, children: node.children.map(toTreeNode) }; }

async function openTaskFromId(taskId: number, conversationId?: number): Promise<void> {
  query.conversationId = conversationId;
  query.taskId = taskId;
  query.keyword = "";
  query.pageNum = 1;
  await loadTasks();
  const row = records.value.find((item) => item.taskId === taskId);
  if (row) await openTaskDetail(row);
}

onMounted(async () => {
  const taskId = Number(route.query.taskId || 0);
  const conversationId = Number(route.query.conversationId || 0);
  if (conversationId > 0) query.conversationId = conversationId;
  if (taskId > 0) query.taskId = taskId;
  const [agents] = await Promise.all([fetchAgentPage({ pageNum: 1, pageSize: 100 }), loadTasks(), loadApprovals()]);
  agentOptions.value = agents.records;
  if (taskId > 0) {
    const row = records.value.find((item) => item.taskId === taskId);
    if (row) await openTaskDetail(row);
  }
});
</script>

<style scoped>
.task-ops-page{min-width:0;color:#24343b}.task-ops-hero{display:flex;justify-content:space-between;align-items:flex-end;padding:30px 34px;border-radius:22px 22px 0 0;color:#f6f1e8;background:radial-gradient(circle at 78% 10%,rgba(217,119,69,.3),transparent 32%),linear-gradient(120deg,#172a31,#244b50 62%,#69503b)}.eyebrow{font:700 11px Georgia,serif;letter-spacing:.2em;color:#efb27f}.task-ops-hero h1{margin:8px 0 6px;font:700 34px Georgia,serif}.task-ops-hero p{margin:0;color:#c8d7d5}.hero-actions{display:flex;gap:10px}.workbench-tabs{padding:14px 20px 24px;background:#edf2ef;border-radius:0 0 22px 22px}.query-card,.table-card{border-color:#d4dfda}.query-actions{display:inline-flex;gap:8px}.metrics-strip{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin:14px 0}.metrics-strip article{padding:16px 18px;border:1px solid #d5dfda;border-radius:14px;background:#fff}.metrics-strip span,.detail-summary-grid span{display:block;color:#718079;font-size:12px}.metrics-strip strong{display:block;margin-top:7px;color:#1d514e;font:700 26px Georgia,serif}.primary-cell{display:flex;flex-direction:column;gap:4px}.primary-cell span{color:#75837e;font-size:12px}.progress-cell{display:grid;gap:7px}.pagination-row{display:flex;justify-content:flex-end;padding-top:16px}.detail-shell{min-height:520px}.detail-header{display:flex;justify-content:space-between;gap:20px;padding:22px 25px;color:#f8f3ea;background:linear-gradient(120deg,#18363b,#35605d);border-radius:16px}.detail-header span{color:#efb27f;font:700 12px Georgia,serif;letter-spacing:.08em}.detail-header h2{margin:6px 0;font:700 25px Georgia,serif}.detail-header p{margin:0;color:#c8d7d5}.detail-actions{display:flex;align-items:center;gap:8px}.detail-summary-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin:14px 0}.detail-summary-grid article{padding:14px;border:1px solid #d9e2de;border-radius:12px;background:#f8faf9}.detail-summary-grid strong{display:block;margin-top:5px;color:#234c4a;font-size:18px}.detail-tabs{margin-top:14px}.overview-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.overview-grid article{padding:18px;border:1px solid #dce4e1;border-radius:14px;background:#f8faf9}.overview-grid h3{margin:0 0 9px;font:700 17px Georgia,serif;color:#294e4b}.overview-grid p{margin:0;line-height:1.7;color:#53635e}.danger{color:#b64343!important}.plan-toolbar{display:flex;gap:10px;margin-bottom:12px}.plan-status{margin-left:10px}.approval-metrics{display:flex;gap:20px;padding:14px;margin-bottom:12px;border-radius:12px;background:#edf4f1}.approval-metrics span{color:#5c6e67}.delegation-layout{display:grid;gap:14px}.delegation-tree{padding:14px;border:1px solid #dce5e1;border-radius:12px}.tree-node{display:flex;flex-direction:column;gap:3px}.tree-node span{color:#74827d;font-size:12px}.snapshot-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.snapshot-grid pre{max-height:60vh;overflow:auto;padding:14px;border-radius:12px;background:#14272d;color:#dce9e5;white-space:pre-wrap}.diff-summary{margin-bottom:12px;color:#50635c}.drawer-tip{color:#687973}.drawer-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:14px}@media(max-width:1100px){.metrics-strip,.detail-summary-grid{grid-template-columns:repeat(2,1fr)}.task-ops-hero,.detail-header{align-items:flex-start;flex-direction:column}.overview-grid,.snapshot-grid{grid-template-columns:1fr}}
</style>

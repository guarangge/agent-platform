<template>
  <section class="workflow-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Workflow 编码">
          <el-input v-model="queryForm.workflowCode" placeholder="请输入 Workflow 编码" clearable />
        </el-form-item>
        <el-form-item label="Workflow 名称">
          <el-input v-model="queryForm.workflowName" placeholder="请输入 Workflow 名称" clearable />
        </el-form-item>
        <el-form-item label="Workflow 类型">
          <el-select v-model="queryForm.workflowType" placeholder="请选择 Workflow 类型" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('workflow_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Workflow 分类">
          <el-select v-model="queryForm.workflowCategory" placeholder="请选择 Workflow 分类" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('workflow_category')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="queryForm.version" placeholder="请输入版本" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="草稿" value="draft" />
            <el-option label="已发布" value="published" />
            <el-option label="已停用" value="disabled" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadWorkflows">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建 Workflow</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Workflow 管理</div>
            <div class="card-subtitle">区分平台默认 Workflow 和行业 Workflow，保留定义 JSON、发布和停用能力。</div>
          </div>
        </div>
      </template>

      <el-table :data="workflowRecords" border stripe v-loading="loading">
        <el-table-column prop="workflowName" label="Workflow 名称" min-width="180" />
        <el-table-column prop="workflowCode" label="Workflow 编码" min-width="180" />
        <el-table-column prop="workflowType" label="Workflow 类型" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.workflowType" effect="plain" type="primary" round>
              {{ dictLabel("workflow_type", row.workflowType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="workflowCategory" label="Workflow 分类" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.workflowCategory" effect="light" type="success" round>
              {{ dictLabel("workflow_category", row.workflowCategory) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" min-width="120" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220">
          <template #default="{ row }">
            {{ row.description || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.workflowId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.workflowId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'published' ? 'warning' : 'success'"
              @click="toggleWorkflowStatus(row)"
            >
              {{ row.status === "published" ? "停用" : "发布" }}
            </el-button>
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

    <el-dialog v-model="editDialogVisible" :title="editingWorkflowId ? '编辑 Workflow' : '新建 Workflow'" width="760px">
      <el-form :model="editForm" label-width="118px">
        <el-form-item :label="editingWorkflowId ? 'JSON 回填' : 'JSON 导入'">
          <div class="json-import-panel">
            <el-input
              v-model="editForm.importJsonText"
              type="textarea"
              :rows="8"
              placeholder="请输入完整 Workflow JSON，点击“自动填充”后回填到下方表单"
            />
            <div class="json-import-actions">
              <el-button type="primary" plain @click="fillFormByImportJson">自动填充</el-button>
              <el-button @click="editForm.importJsonText = ''">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Workflow 编码">
          <el-input v-model="editForm.workflowCode" placeholder="请输入 Workflow 编码" />
        </el-form-item>
        <el-form-item label="Workflow 名称">
          <el-input v-model="editForm.workflowName" placeholder="请输入 Workflow 名称" />
        </el-form-item>
        <el-form-item label="Workflow 类型">
          <el-select v-model="editForm.workflowType" placeholder="请选择 Workflow 类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('workflow_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Workflow 分类">
          <el-select v-model="editForm.workflowCategory" placeholder="请选择 Workflow 分类" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('workflow_category')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="editForm.version" placeholder="请输入版本" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio value="draft">草稿</el-radio>
            <el-radio value="published">已发布</el-radio>
            <el-radio value="disabled">已停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="执行 Prompt">
          <el-select
            v-model="editForm.executionPromptId"
            placeholder="请选择执行 Prompt"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in promptOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用场景">
          <el-input
            v-model="editForm.applicableScenariosText"
            type="textarea"
            :rows="3"
            placeholder="一行一个适用场景"
          />
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input
            v-model="editForm.triggerConditionsText"
            type="textarea"
            :rows="3"
            placeholder="一行一个触发条件"
          />
        </el-form-item>
        <el-form-item label="不适用场景">
          <el-input
            v-model="editForm.nonApplicableScenariosText"
            type="textarea"
            :rows="3"
            placeholder="一行一个不适用场景"
          />
        </el-form-item>
        <el-form-item label="输入前提">
          <el-input
            v-model="editForm.inputPrerequisitesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个输入前提"
          />
        </el-form-item>
        <el-form-item label="预期结果">
          <el-input
            v-model="editForm.expectedOutcome"
            type="textarea"
            :rows="2"
            placeholder="请输入执行后的预期结果"
          />
        </el-form-item>
        <el-form-item label="风险说明">
          <el-input
            v-model="editForm.riskNotesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个风险说明"
          />
        </el-form-item>
        <el-form-item label="Definition JSON">
          <div class="definition-editor">
            <div class="definition-toolbar">
              <div class="definition-toolbar__title">节点模板助手</div>
              <div class="definition-toolbar__actions">
                <el-button plain size="small" @click="fillWorkflowSkeleton">生成最小骨架</el-button>
                <el-button plain size="small" @click="formatDefinitionJson">格式化 JSON</el-button>
                <el-button type="success" plain size="small" @click="validateCurrentDefinition">发布预检</el-button>
              </div>
            </div>
            <div class="definition-template-list">
              <el-tag
                v-for="template in workflowNodeTemplates"
                :key="template.type"
                class="definition-template-tag"
                effect="plain"
                round
                @click="appendWorkflowNodeTemplate(template.type)"
              >
                {{ template.label }}
              </el-tag>
            </div>
            <div class="definition-hints">
              <div
                v-for="template in workflowNodeTemplates"
                :key="`${template.type}-hint`"
                class="definition-hint-item"
              >
                <span class="definition-hint-name">{{ template.type }}</span>
                <span class="definition-hint-desc">{{ template.hint }}</span>
              </div>
            </div>
            <el-input
              v-model="editForm.definitionJsonText"
              type="textarea"
              :rows="12"
              placeholder="请输入合法 JSON，例如 {}"
            />
          </div>
        </el-form-item>
        <el-form-item label="高级 Metadata">
          <el-input
            v-model="editForm.metadataText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEditForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="Workflow 详情" size="620px">
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="Workflow 编码">{{ detailRecord.workflowCode }}</el-descriptions-item>
        <el-descriptions-item label="Workflow 名称">{{ detailRecord.workflowName }}</el-descriptions-item>
        <el-descriptions-item label="Workflow 类型">{{ dictLabel("workflow_type", detailRecord.workflowType) }}</el-descriptions-item>
        <el-descriptions-item label="Workflow 分类">{{ dictLabel("workflow_category", detailRecord.workflowCategory) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detailRecord.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detailRecord.status) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailRecord.description || "-" }}</el-descriptions-item>
        <el-descriptions-item label="执行 Prompt">
          {{ promptLabel(extractExecutionPromptId(asObject(detailRecord.metadata))) }}
        </el-descriptions-item>
        <el-descriptions-item label="适用场景">
          <pre class="json-block">{{ formatLineList(extractWorkflowRouting(asObject(detailRecord.metadata)).applicableScenarios) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="触发条件">
          <pre class="json-block">{{ formatLineList(extractWorkflowRouting(asObject(detailRecord.metadata)).triggerConditions) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="不适用场景">
          <pre class="json-block">{{ formatLineList(extractWorkflowRouting(asObject(detailRecord.metadata)).nonApplicableScenarios) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输入前提">
          <pre class="json-block">{{ formatLineList(extractWorkflowRouting(asObject(detailRecord.metadata)).inputPrerequisites) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="预期结果">
          {{ extractWorkflowRouting(asObject(detailRecord.metadata)).expectedOutcome || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="风险说明">
          <pre class="json-block">{{ formatLineList(extractWorkflowRouting(asObject(detailRecord.metadata)).riskNotes) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="Definition JSON">
          <pre class="json-block">{{ formatJson(detailRecord.definitionJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="高级 Metadata">
          <pre class="json-block">{{ formatJson(detailRecord.metadata) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailRecord.remark || "-" }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无详情数据" />
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import { fetchDictOptions, type DictOptionItem } from "@/api/dict";
import { fetchPromptDetail, fetchPromptPage } from "@/api/prompt";
import {
  createWorkflow,
  disableWorkflow,
  fetchWorkflowDetail,
  fetchWorkflowPage,
  publishWorkflow,
  validateWorkflowDefinition,
  updateWorkflow,
  type WorkflowDetail,
  type WorkflowRecord
} from "@/api/workflow";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  workflowCode: string;
  workflowName: string;
  workflowType: string;
  workflowCategory: string;
  version: string;
  status: string;
}

interface EditForm {
  importJsonText: string;
  workflowCode: string;
  workflowName: string;
  workflowType: string;
  workflowCategory: string;
  description: string;
  version: string;
  executionPromptId: number | null;
  applicableScenariosText: string;
  triggerConditionsText: string;
  nonApplicableScenariosText: string;
  inputPrerequisitesText: string;
  expectedOutcome: string;
  riskNotesText: string;
  definitionJsonText: string;
  status: string;
  metadataText: string;
  remark: string;
}

interface ResourceOption {
  value: number;
  label: string;
}

type JsonObject = Record<string, unknown>;
type WorkflowNodeTemplateType =
  | "tool"
  | "condition"
  | "approval"
  | "clarify"
  | "judge"
  | "wait"
  | "memory_write"
  | "parallel"
  | "join"
  | "reflection"
  | "handoff";

interface WorkflowNodeTemplate {
  type: WorkflowNodeTemplateType;
  label: string;
  hint: string;
}

const loading = ref(false);
const total = ref(0);
const workflowRecords = ref<WorkflowRecord[]>([]);
const detailRecord = ref<WorkflowDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingWorkflowId = ref<number | null>(null);
const saving = ref(false);
const dictOptionMap = ref<Record<string, DictOptionItem[]>>({});
const promptOptions = ref<ResourceOption[]>([]);
const workflowNodeTemplates: WorkflowNodeTemplate[] = [
  { type: "tool", label: "Tool", hint: "单步调用工具，填写 toolCode / inputTemplate。" },
  { type: "condition", label: "Condition", hint: "条件分支节点，填写 expression 和 true/false nextNodeId。" },
  { type: "approval", label: "Approval", hint: "人工审批节点，填写 approvalCode / timeoutSeconds。" },
  { type: "clarify", label: "Clarify", hint: "澄清问题节点，填写 question / requiredFields。" },
  { type: "judge", label: "Judge", hint: "LLM 判断节点，填写 instruction / outputKey。" },
  { type: "wait", label: "Wait", hint: "等待节点，填写 waitSeconds 或 waitMillis。" },
  { type: "memory_write", label: "Memory Write", hint: "写入记忆节点，填写 scope / contentTemplate。" },
  { type: "parallel", label: "Parallel", hint: "并行调度节点，填写 branches 或 parallelToolCodes。" },
  { type: "join", label: "Join", hint: "并行汇聚节点，填写 joinMode / joinInstruction。" },
  { type: "reflection", label: "Reflection", hint: "反思节点，填写 instruction / replannable。" },
  { type: "handoff", label: "Handoff", hint: "委派节点，填写 targetAgentCode / handoffInstruction。" }
];

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  workflowCode: "",
  workflowName: "",
  workflowType: "",
  workflowCategory: "",
  version: "",
  status: ""
});

const editForm = reactive<EditForm>({
  importJsonText: "",
  workflowCode: "",
  workflowName: "",
  workflowType: "",
  workflowCategory: "",
  description: "",
  version: "v1.0",
  executionPromptId: null,
  applicableScenariosText: "",
  triggerConditionsText: "",
  nonApplicableScenariosText: "",
  inputPrerequisitesText: "",
  expectedOutcome: "",
  riskNotesText: "",
  definitionJsonText: "{}",
  status: "draft",
  metadataText: "{}",
  remark: ""
});

async function loadWorkflows(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchWorkflowPage(queryForm);
    workflowRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    workflowRecords.value = [];
    total.value = 0;
    ElMessage.error("Workflow 列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadDictOptionMap(): Promise<void> {
  try {
    dictOptionMap.value = await fetchDictOptions([
      "workflow_type",
      "workflow_category",
      "prompt_type",
      "prompt_category"
    ]) || {};
  } catch (error) {
    dictOptionMap.value = {};
    ElMessage.error("字典选项加载失败");
  }
}

async function loadPromptOptions(): Promise<void> {
  try {
    let page = await fetchPromptPage({
      pageNum: 1,
      pageSize: 100,
      promptType: "workflow_node",
      status: "enabled"
    });
    if (!page.records.length) {
      page = await fetchPromptPage({
        pageNum: 1,
        pageSize: 100,
        status: "enabled"
      });
    }
    promptOptions.value = page.records.map((item) => ({
      value: item.promptId,
      label: formatPromptOptionLabel(item.promptName, item.promptCode, item.promptType, item.promptCategory)
    }));
  } catch (error) {
    promptOptions.value = [];
    ElMessage.error("Prompt 下拉选项加载失败");
  }
}

async function ensurePromptOption(promptId: number | null): Promise<void> {
  if (!promptId || promptOptions.value.some((item) => item.value === promptId)) {
    return;
  }
  const detail = await fetchPromptDetail(promptId);
  promptOptions.value = [
    ...promptOptions.value,
    {
      value: detail.promptId,
      label: formatPromptOptionLabel(detail.promptName, detail.promptCode, detail.promptType, detail.promptCategory)
    }
  ];
}

async function openDetailDrawer(workflowId: number): Promise<void> {
  detailDrawerVisible.value = true;
  try {
    detailRecord.value = await fetchWorkflowDetail(workflowId);
    await Promise.all([
      loadPromptOptions(),
      ensurePromptOption(extractExecutionPromptId(asObject(detailRecord.value?.metadata)))
    ]);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("Workflow 详情加载失败");
  }
}

async function openEditDialog(workflowId: number): Promise<void> {
  editingWorkflowId.value = workflowId;
  editDialogVisible.value = true;
  try {
    const [detail] = await Promise.all([
      fetchWorkflowDetail(workflowId),
      loadPromptOptions()
    ]);
    const metadata = asObject(detail.metadata);
    const routing = extractWorkflowRouting(metadata);
    const executionPromptId = extractExecutionPromptId(metadata);
    await ensurePromptOption(executionPromptId);
    editForm.importJsonText = "";
    editForm.workflowCode = detail.workflowCode;
    editForm.workflowName = detail.workflowName;
    editForm.workflowType = detail.workflowType || "";
    editForm.workflowCategory = detail.workflowCategory || "";
    editForm.description = detail.description || "";
    editForm.version = detail.version;
    editForm.executionPromptId = executionPromptId;
    editForm.applicableScenariosText = joinLines(routing.applicableScenarios);
    editForm.triggerConditionsText = joinLines(routing.triggerConditions);
    editForm.nonApplicableScenariosText = joinLines(routing.nonApplicableScenarios);
    editForm.inputPrerequisitesText = joinLines(routing.inputPrerequisites);
    editForm.expectedOutcome = routing.expectedOutcome;
    editForm.riskNotesText = joinLines(routing.riskNotes);
    editForm.definitionJsonText = stringifyJson(detail.definitionJson);
    editForm.status = detail.status;
    editForm.metadataText = stringifyJson(detail.metadata);
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("Workflow 详情加载失败");
  }
}

function openCreateDialog(): void {
  editingWorkflowId.value = null;
  editDialogVisible.value = true;
  void loadPromptOptions();
  editForm.importJsonText = "";
  editForm.workflowCode = "";
  editForm.workflowName = "";
  editForm.workflowType = "";
  editForm.workflowCategory = "";
  editForm.description = "";
  editForm.version = "v1.0";
  editForm.executionPromptId = null;
  editForm.applicableScenariosText = "";
  editForm.triggerConditionsText = "";
  editForm.nonApplicableScenariosText = "";
  editForm.inputPrerequisitesText = "";
  editForm.expectedOutcome = "";
  editForm.riskNotesText = "";
  editForm.definitionJsonText = "{}";
  editForm.status = "draft";
  editForm.metadataText = "{}";
  editForm.remark = "";
}

async function fillFormByImportJson(): Promise<void> {
  const importData = parseWorkflowImportJson(editForm.importJsonText);
  if (!importData) {
    return;
  }

  const metadata = asObject(importData.metadata);
  const routing = extractWorkflowRouting(metadata);
  const executionPromptId = extractExecutionPromptId(metadata);
  await ensurePromptOption(executionPromptId);

  editForm.workflowCode = asString(importData.workflowCode);
  editForm.workflowName = asString(importData.workflowName);
  editForm.workflowType = asString(importData.workflowType);
  editForm.workflowCategory = asString(importData.workflowCategory);
  editForm.description = asString(importData.description);
  editForm.version = asString(importData.version) || "v1.0";
  editForm.executionPromptId = executionPromptId;
  editForm.applicableScenariosText = joinLines(routing.applicableScenarios);
  editForm.triggerConditionsText = joinLines(routing.triggerConditions);
  editForm.nonApplicableScenariosText = joinLines(routing.nonApplicableScenarios);
  editForm.inputPrerequisitesText = joinLines(routing.inputPrerequisites);
  editForm.expectedOutcome = routing.expectedOutcome;
  editForm.riskNotesText = joinLines(routing.riskNotes);
  editForm.definitionJsonText = stringifyJson(asObject(importData.definitionJson));
  editForm.status = asString(importData.status) || "draft";
  editForm.metadataText = stringifyJson(metadata);
  editForm.remark = asString(importData.remark);

  ElMessage.success("JSON 已自动填充到表单");
}

async function submitEditForm(): Promise<void> {
  if (!editForm.workflowCode.trim()) {
    ElMessage.warning("请输入 Workflow 编码");
    return;
  }
  if (!editForm.workflowName.trim()) {
    ElMessage.warning("请输入 Workflow 名称");
    return;
  }
  if (!editForm.workflowType.trim()) {
    ElMessage.warning("请输入 Workflow 类型");
    return;
  }
  if (!editForm.workflowCategory.trim()) {
    ElMessage.warning("请输入 Workflow 分类");
    return;
  }
  if (!editForm.version.trim()) {
    ElMessage.warning("请输入版本");
    return;
  }

  const definitionJson = parseJsonText(editForm.definitionJsonText, "Definition JSON");
  if (!definitionJson) {
    return;
  }
  const metadata = parseJsonText(editForm.metadataText, "Metadata");
  if (!metadata) {
    return;
  }
  const mergedMetadata = buildWorkflowMetadata(metadata);

  saving.value = true;
  try {
    const payload = {
      workflowCode: editForm.workflowCode,
      workflowName: editForm.workflowName,
      workflowType: editForm.workflowType,
      workflowCategory: editForm.workflowCategory,
      description: editForm.description,
      version: editForm.version,
      definitionJson,
      status: editForm.status,
      metadata: mergedMetadata,
      remark: editForm.remark
    };
    if (editingWorkflowId.value) {
      await updateWorkflow(editingWorkflowId.value, payload);
      ElMessage.success("Workflow 更新成功");
    } else {
      await createWorkflow(payload);
      ElMessage.success("Workflow 创建成功");
    }
    editDialogVisible.value = false;
    await loadWorkflows();
  } catch (error) {
    ElMessage.error("Workflow 保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleWorkflowStatus(row: WorkflowRecord): Promise<void> {
  try {
    if (row.status === "published") {
      await disableWorkflow(row.workflowId);
      ElMessage.success("Workflow 已停用");
    } else {
      await publishWorkflow(row.workflowId);
      ElMessage.success("Workflow 已发布");
    }
    await loadWorkflows();
  } catch (error) {
    ElMessage.error("Workflow 状态更新失败");
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.workflowCode = "";
  queryForm.workflowName = "";
  queryForm.workflowType = "";
  queryForm.workflowCategory = "";
  queryForm.version = "";
  queryForm.status = "";
  void loadWorkflows();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadWorkflows();
}

function statusText(status: string): string {
  if (status === "published") {
    return "已发布";
  }
  if (status === "disabled") {
    return "已停用";
  }
  return "草稿";
}

function statusTagType(status: string): "success" | "info" | "warning" {
  if (status === "published") {
    return "success";
  }
  if (status === "disabled") {
    return "info";
  }
  return "warning";
}

function getDictOptions(typeCode: string): DictOptionItem[] {
  const optionMap = dictOptionMap.value || {};
  return optionMap[typeCode] || [];
}

function dictLabel(typeCode: string, itemCode: string | null): string {
  if (!itemCode) {
    return "-";
  }
  const matched = getDictOptions(typeCode).find((item) => item.itemCode === itemCode);
  return matched?.itemName || itemCode;
}

function stringifyJson(value: Record<string, unknown> | null): string {
  return JSON.stringify(value ?? {}, null, 2);
}

function formatJson(value: Record<string, unknown> | null): string {
  return JSON.stringify(value ?? {}, null, 2);
}

function parseJsonText(text: string, fieldName: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(text.trim() || "{}") as unknown;
    if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
      ElMessage.warning(`${fieldName} 必须是 JSON object`);
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch (error) {
    ElMessage.warning(`${fieldName} 不是合法 JSON`);
    return null;
  }
}

function parseWorkflowImportJson(text: string): Record<string, unknown> | null {
  if (!text.trim()) {
    ElMessage.warning("请输入用于导入的 Workflow JSON");
    return null;
  }
  return parseJsonText(text, "导入 JSON");
}

function formatDefinitionJson(): void {
  const definitionJson = parseJsonText(editForm.definitionJsonText, "Definition JSON");
  if (!definitionJson) {
    return;
  }
  editForm.definitionJsonText = stringifyJson(definitionJson);
  ElMessage.success("Definition JSON 已格式化");
}

async function validateCurrentDefinition(): Promise<void> {
  const definitionJson = parseJsonText(editForm.definitionJsonText, "Definition JSON");
  if (!definitionJson) return;
  const report = await validateWorkflowDefinition(definitionJson);
  if (report.valid) {
    ElMessage.success("Workflow 发布预检通过");
    return;
  }
  ElMessage.error(report.issues.slice(0, 3)
    .map((item) => `${item.nodeId ? `[${item.nodeId}] ` : ""}${item.message}`)
    .join("；"));
}

function fillWorkflowSkeleton(): void {
  const skeleton = {
    version: "v1",
    nodes: [
      {
        id: "start_1",
        type: "start",
        name: "开始"
      },
      {
        id: "step_1",
        type: "tool",
        name: "执行工具",
        toolCode: "",
        inputTemplate: "{{ userInput }}"
      },
      {
        id: "end_1",
        type: "end",
        name: "结束"
      }
    ],
    edges: [
      { source: "start_1", target: "step_1" },
      { source: "step_1", target: "end_1" }
    ]
  };
  editForm.definitionJsonText = stringifyJson(skeleton);
  ElMessage.success("已生成最小 Workflow 骨架");
}

function appendWorkflowNodeTemplate(type: WorkflowNodeTemplateType): void {
  const definitionJson = parseJsonText(editForm.definitionJsonText, "Definition JSON");
  if (!definitionJson) {
    return;
  }
  const nodes = Array.isArray(definitionJson.nodes) ? [...definitionJson.nodes] : [];
  nodes.push(buildWorkflowNodeTemplate(type, nodes.length + 1));
  definitionJson.nodes = nodes;
  if (typeof definitionJson.version !== "string") {
    definitionJson.version = "v1";
  }
  if (!Array.isArray(definitionJson.edges)) definitionJson.edges = [];
  editForm.definitionJsonText = stringifyJson(definitionJson);
  ElMessage.success(`已追加 ${type} 节点模板`);
}

function buildWorkflowNodeTemplate(type: WorkflowNodeTemplateType, index: number): JsonObject {
  const nodeId = `${type}_${index}`;
  switch (type) {
    case "tool":
      return {
        id: nodeId,
        type: "tool",
        name: `工具节点${index}`,
        toolCode: "",
        inputTemplate: "{{ userInput }}"
      };
    case "condition":
      return {
        id: nodeId,
        type: "condition",
        name: `条件判断${index}`,
        expression: ""
      };
    case "approval":
      return {
        id: nodeId,
        type: "approval",
        name: `审批节点${index}`,
        approvalCode: "",
        timeoutSeconds: 600
      };
    case "clarify":
      return {
        id: nodeId,
        type: "clarify",
        name: `澄清节点${index}`,
        question: "",
        requiredFields: []
      };
    case "judge":
      return {
        id: nodeId,
        type: "judge",
        name: `判断节点${index}`,
        instruction: "",
        outputKey: "judgeResult"
      };
    case "wait":
      return {
        id: nodeId,
        type: "wait",
        name: `等待节点${index}`,
        waitSeconds: 30
      };
    case "memory_write":
      return {
        id: nodeId,
        type: "memory_write",
        name: `记忆写入${index}`,
        scope: "session",
        contentTemplate: ""
      };
    case "parallel":
      return {
        id: nodeId,
        type: "parallel",
        name: `并行节点${index}`,
        parallelToolCodes: [],
        branches: []
      };
    case "join":
      return {
        id: nodeId,
        type: "join",
        name: `汇聚节点${index}`,
        joinMode: "all",
        joinInstruction: ""
      };
    case "reflection":
      return {
        id: nodeId,
        type: "reflection",
        name: `反思节点${index}`,
        instruction: "",
        replannable: true
      };
    case "handoff":
      return {
        id: nodeId,
        type: "handoff",
        name: `委派节点${index}`,
        delegateAgentId: null,
        handoffInstruction: ""
      };
    default:
      return {
        id: nodeId,
        type,
        name: `${type}节点${index}`
      };
  }
}

function asObject(value: unknown): JsonObject {
  if (!value || Array.isArray(value) || typeof value !== "object") {
    return {};
  }
  return value as JsonObject;
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter(Boolean);
}

function extractExecutionPromptId(metadata: JsonObject): number | null {
  const promptRefs = asObject(metadata.promptRefs);
  const promptId = promptRefs.executionPromptId;
  return typeof promptId === "number" && promptId > 0 ? promptId : null;
}

function extractWorkflowRouting(metadata: JsonObject): {
  applicableScenarios: string[];
  triggerConditions: string[];
  nonApplicableScenarios: string[];
  inputPrerequisites: string[];
  expectedOutcome: string;
  riskNotes: string[];
} {
  const routing = asObject(metadata.routing);
  return {
    applicableScenarios: asStringArray(routing.applicableScenarios),
    triggerConditions: asStringArray(routing.triggerConditions),
    nonApplicableScenarios: asStringArray(routing.nonApplicableScenarios),
    inputPrerequisites: asStringArray(routing.inputPrerequisites),
    expectedOutcome: asString(routing.expectedOutcome),
    riskNotes: asStringArray(routing.riskNotes)
  };
}

function joinLines(values: string[]): string {
  return values.join("\n");
}

function parseLines(text: string): string[] {
  return text
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildWorkflowMetadata(baseMetadata: JsonObject): JsonObject {
  return {
    ...baseMetadata,
    promptRefs: {
      ...asObject(baseMetadata.promptRefs),
      executionPromptId: editForm.executionPromptId
    },
    routing: {
      ...asObject(baseMetadata.routing),
      applicableScenarios: parseLines(editForm.applicableScenariosText),
      triggerConditions: parseLines(editForm.triggerConditionsText),
      nonApplicableScenarios: parseLines(editForm.nonApplicableScenariosText),
      inputPrerequisites: parseLines(editForm.inputPrerequisitesText),
      expectedOutcome: editForm.expectedOutcome.trim(),
      riskNotes: parseLines(editForm.riskNotesText)
    }
  };
}

function promptLabel(promptId: number | null): string {
  if (!promptId) {
    return "-";
  }
  return promptOptions.value.find((item) => item.value === promptId)?.label || String(promptId);
}

function formatLineList(values: string[]): string {
  return values.length ? values.join("\n") : "-";
}

function formatPromptOptionLabel(
  promptName: string,
  promptCode: string,
  promptType: string | null,
  promptCategory: string | null
): string {
  const typeLabel = dictLabel("prompt_type", promptType);
  const categoryLabel = dictLabel("prompt_category", promptCategory);
  return `${promptName}（${promptCode}） - ${typeLabel} - ${categoryLabel}`;
}

onMounted(() => {
  void Promise.all([loadDictOptionMap(), loadWorkflows()]);
});
</script>

<style scoped>
.workflow-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.query-card,
.table-card {
  border-radius: 20px;
  border: 1px solid #dbe5ef;
}

.query-form {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.query-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
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

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
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

.json-import-panel {
  width: 100%;
}

.json-import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.definition-editor {
  width: 100%;
}

.definition-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.definition-toolbar__title {
  font-size: 13px;
  font-weight: 700;
  color: #17324d;
}

.definition-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.definition-template-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.definition-template-tag {
  cursor: pointer;
}

.definition-hints {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.definition-hint-item {
  border: 1px solid #dbe5ef;
  border-radius: 12px;
  padding: 8px 10px;
  background: #f7fafc;
}

.definition-hint-name {
  display: inline-block;
  margin-right: 8px;
  font-weight: 700;
  color: #17324d;
}

.definition-hint-desc {
  font-size: 12px;
  color: #64768a;
}
</style>

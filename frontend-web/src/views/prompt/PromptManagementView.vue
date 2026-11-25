<template>
  <section class="prompt-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Prompt 编码">
          <el-input v-model="queryForm.promptCode" placeholder="请输入 Prompt 编码" clearable />
        </el-form-item>
        <el-form-item label="Prompt 名称">
          <el-input v-model="queryForm.promptName" placeholder="请输入 Prompt 名称" clearable />
        </el-form-item>
        <el-form-item label="Prompt 类型">
          <el-select v-model="queryForm.promptType" placeholder="请选择 Prompt 类型" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('prompt_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Prompt 分类">
          <el-select v-model="queryForm.promptCategory" placeholder="请选择 Prompt 分类" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('prompt_category')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="草稿" value="draft" />
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadPrompts">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建 Prompt</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Prompt 管理</div>
            <div class="card-subtitle">统一维护 Agent、决策路由、Workflow、Tool 和 Memory 执行过程中使用的提示词模板。</div>
          </div>
        </div>
      </template>

      <el-table :data="promptRecords" border stripe v-loading="loading">
        <el-table-column prop="promptName" label="Prompt 名称" min-width="180" />
        <el-table-column prop="promptCode" label="Prompt 编码" min-width="180" />
        <el-table-column prop="promptType" label="Prompt 类型" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.promptType" effect="plain" type="primary" round>
              {{ dictLabel("prompt_type", row.promptType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="promptCategory" label="Prompt 分类" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.promptCategory" effect="light" type="success" round>
              {{ dictLabel("prompt_category", row.promptCategory) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="用途说明" min-width="220">
          <template #default="{ row }">
            {{ promptUsageText(row.promptType) }}
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
            <el-button link type="primary" @click="openDetailDrawer(row.promptId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.promptId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="togglePromptStatus(row)"
            >
              {{ row.status === "enabled" ? "停用" : "启用" }}
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

    <el-dialog v-model="editDialogVisible" :title="editingPromptId ? '编辑 Prompt' : '新建 Prompt'" width="760px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="用途说明">
          <div class="prompt-scope-tip">
            推荐按类型区分：`agent_system` 用于 Agent 主提示词，`decision_router` 用于路由决策，
            `workflow_node` 用于流程节点，`tool_execution` 用于 Tool 执行，`memory_summary` 用于记忆整理。
          </div>
        </el-form-item>
        <el-form-item :label="editingPromptId ? 'JSON 回填' : 'JSON 导入'">
          <div class="json-import-panel">
            <el-input
              v-model="editForm.importJsonText"
              type="textarea"
              :rows="8"
              placeholder="请输入完整 Prompt JSON，点击“自动填充”后回填到下方表单"
            />
            <div class="json-import-actions">
              <el-button type="primary" plain @click="fillFormByImportJson">自动填充</el-button>
              <el-button @click="editForm.importJsonText = ''">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Prompt 编码">
          <el-input v-model="editForm.promptCode" placeholder="请输入 Prompt 编码" />
        </el-form-item>
        <el-form-item label="Prompt 名称">
          <el-input v-model="editForm.promptName" placeholder="请输入 Prompt 名称" />
        </el-form-item>
        <el-form-item label="Prompt 类型">
          <el-select v-model="editForm.promptType" placeholder="请选择 Prompt 类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('prompt_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Prompt 分类">
          <el-select v-model="editForm.promptCategory" placeholder="请选择 Prompt 分类" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('prompt_category')"
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
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="Prompt 正文">
          <el-input v-model="editForm.content" type="textarea" :rows="8" placeholder="请输入 Prompt 正文" />
        </el-form-item>
        <el-form-item label="Metadata">
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

    <el-drawer v-model="detailDrawerVisible" title="Prompt 详情" size="620px">
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="Prompt 编码">{{ detailRecord.promptCode }}</el-descriptions-item>
        <el-descriptions-item label="Prompt 名称">{{ detailRecord.promptName }}</el-descriptions-item>
        <el-descriptions-item label="Prompt 类型">{{ dictLabel("prompt_type", detailRecord.promptType) }}</el-descriptions-item>
        <el-descriptions-item label="Prompt 分类">{{ dictLabel("prompt_category", detailRecord.promptCategory) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detailRecord.version }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detailRecord.status) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailRecord.description || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Prompt 正文">
          <pre class="content-block">{{ detailRecord.content }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="Metadata">
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
import {
  createPrompt,
  disablePrompt,
  enablePrompt,
  fetchPromptDetail,
  fetchPromptPage,
  updatePrompt,
  type PromptDetail,
  type PromptRecord
} from "@/api/prompt";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  status: string;
}

interface EditForm {
  importJsonText: string;
  promptCode: string;
  promptName: string;
  promptType: string;
  promptCategory: string;
  description: string;
  content: string;
  version: string;
  status: string;
  metadataText: string;
  remark: string;
}

const loading = ref(false);
const total = ref(0);
const promptRecords = ref<PromptRecord[]>([]);
const detailRecord = ref<PromptDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingPromptId = ref<number | null>(null);
const saving = ref(false);
const dictOptionMap = ref<Record<string, DictOptionItem[]>>({});

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  promptCode: "",
  promptName: "",
  promptType: "",
  promptCategory: "",
  status: ""
});

const editForm = reactive<EditForm>({
  importJsonText: "",
  promptCode: "",
  promptName: "",
  promptType: "",
  promptCategory: "",
  description: "",
  content: "",
  version: "v1.0",
  status: "draft",
  metadataText: "{}",
  remark: ""
});

async function loadPrompts(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchPromptPage(queryForm);
    promptRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    promptRecords.value = [];
    total.value = 0;
    ElMessage.error("Prompt 列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadDictOptionMap(): Promise<void> {
  try {
    dictOptionMap.value = await fetchDictOptions(["prompt_type", "prompt_category"]) || {};
  } catch (error) {
    dictOptionMap.value = {};
    ElMessage.error("字典选项加载失败");
  }
}

async function openDetailDrawer(promptId: number): Promise<void> {
  detailDrawerVisible.value = true;
  try {
    detailRecord.value = await fetchPromptDetail(promptId);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("Prompt 详情加载失败");
  }
}

async function openEditDialog(promptId: number): Promise<void> {
  editingPromptId.value = promptId;
  editDialogVisible.value = true;
  try {
    const detail = await fetchPromptDetail(promptId);
    editForm.importJsonText = "";
    editForm.promptCode = detail.promptCode;
    editForm.promptName = detail.promptName;
    editForm.promptType = detail.promptType;
    editForm.promptCategory = detail.promptCategory;
    editForm.description = detail.description || "";
    editForm.content = detail.content;
    editForm.version = detail.version;
    editForm.status = detail.status;
    editForm.metadataText = stringifyJson(detail.metadata);
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("Prompt 详情加载失败");
  }
}

function openCreateDialog(): void {
  editingPromptId.value = null;
  editDialogVisible.value = true;
  editForm.importJsonText = "";
  editForm.promptCode = "";
  editForm.promptName = "";
  editForm.promptType = "";
  editForm.promptCategory = "";
  editForm.description = "";
  editForm.content = "";
  editForm.version = "v1.0";
  editForm.status = "draft";
  editForm.metadataText = "{}";
  editForm.remark = "";
}

function fillFormByImportJson(): void {
  const importData = parsePromptImportJson(editForm.importJsonText);
  if (!importData) {
    return;
  }

  editForm.promptCode = asString(importData.promptCode);
  editForm.promptName = asString(importData.promptName);
  editForm.promptType = asString(importData.promptType);
  editForm.promptCategory = asString(importData.promptCategory);
  editForm.description = asString(importData.description);
  editForm.content = asString(importData.content);
  editForm.version = asString(importData.version) || "v1.0";
  editForm.status = asString(importData.status) || "draft";
  editForm.metadataText = stringifyJson(asObjectRecord(importData.metadata));
  editForm.remark = asString(importData.remark);

  ElMessage.success("JSON 已自动填充到表单");
}

async function submitEditForm(): Promise<void> {
  if (!editForm.promptCode.trim()) {
    ElMessage.warning("请输入 Prompt 编码");
    return;
  }
  if (!editForm.promptName.trim()) {
    ElMessage.warning("请输入 Prompt 名称");
    return;
  }
  if (!editForm.promptType.trim()) {
    ElMessage.warning("请输入 Prompt 类型");
    return;
  }
  if (!editForm.promptCategory.trim()) {
    ElMessage.warning("请输入 Prompt 分类");
    return;
  }
  if (!editForm.content.trim()) {
    ElMessage.warning("请输入 Prompt 正文");
    return;
  }

  const metadata = parseJsonText(editForm.metadataText, "Metadata");
  if (!metadata) {
    return;
  }

  saving.value = true;
  try {
    const payload = {
      promptCode: editForm.promptCode,
      promptName: editForm.promptName,
      promptType: editForm.promptType,
      promptCategory: editForm.promptCategory,
      description: editForm.description,
      content: editForm.content,
      version: editForm.version,
      status: editForm.status,
      metadata,
      remark: editForm.remark
    };
    if (editingPromptId.value) {
      await updatePrompt(editingPromptId.value, payload);
      ElMessage.success("Prompt 更新成功");
    } else {
      await createPrompt(payload);
      ElMessage.success("Prompt 创建成功");
    }
    editDialogVisible.value = false;
    await loadPrompts();
  } catch (error) {
    ElMessage.error("Prompt 保存失败");
  } finally {
    saving.value = false;
  }
}

async function togglePromptStatus(row: PromptRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disablePrompt(row.promptId);
      ElMessage.success("Prompt 已停用");
    } else {
      await enablePrompt(row.promptId);
      ElMessage.success("Prompt 已启用");
    }
    await loadPrompts();
  } catch (error) {
    ElMessage.error("Prompt 状态更新失败");
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.promptCode = "";
  queryForm.promptName = "";
  queryForm.promptType = "";
  queryForm.promptCategory = "";
  queryForm.status = "";
  void loadPrompts();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadPrompts();
}

function statusText(status: string): string {
  if (status === "enabled") {
    return "启用";
  }
  if (status === "disabled") {
    return "停用";
  }
  return "草稿";
}

function statusTagType(status: string): "success" | "info" | "warning" {
  if (status === "enabled") {
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

function promptUsageText(promptType: string | null): string {
  if (promptType === "agent_system") {
    return "用于 Agent 主执行提示";
  }
  if (promptType === "decision_router") {
    return "用于执行前路由决策";
  }
  if (promptType === "workflow_node") {
    return "用于 Workflow 节点执行";
  }
  if (promptType === "tool_execution") {
    return "用于 Tool 执行包装";
  }
  if (promptType === "memory_summary") {
    return "用于 Memory 提炼与归纳";
  }
  if (promptType === "assistant") {
    return "用于通用助手回复";
  }
  if (promptType === "task") {
    return "用于任务执行阶段";
  }
  if (promptType === "workflow") {
    return "兼容旧流程提示词";
  }
  return "-";
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

function parsePromptImportJson(text: string): Record<string, unknown> | null {
  if (!text.trim()) {
    ElMessage.warning("请输入用于导入的 Prompt JSON");
    return null;
  }
  return parseJsonText(text, "导入 JSON");
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asObjectRecord(value: unknown): Record<string, unknown> {
  if (!value || Array.isArray(value) || typeof value !== "object") {
    return {};
  }
  return value as Record<string, unknown>;
}

onMounted(() => {
  void Promise.all([loadDictOptionMap(), loadPrompts()]);
});
</script>

<style scoped>
.prompt-page {
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

.json-block,
.content-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #29445f;
}

.prompt-scope-tip {
  line-height: 1.7;
  color: #526277;
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
</style>

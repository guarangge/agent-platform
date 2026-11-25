<template>
  <section class="model-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="模型编码">
          <el-input v-model="queryForm.modelCode" placeholder="请输入模型编码" clearable />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="queryForm.modelName" placeholder="请输入模型名称" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="queryForm.provider" placeholder="请选择供应商" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('model_provider')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="queryForm.modelType" placeholder="请选择模型类型" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('model_type')"
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
          <el-button type="primary" @click="loadModels">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建模型</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">模型管理</div>
            <div class="card-subtitle">统一维护 Agent 和后续执行链路所需的模型连接配置、供应商类型和推理参数。</div>
          </div>
        </div>
      </template>

      <el-table :data="modelRecords" border stripe v-loading="loading">
        <el-table-column prop="modelName" label="模型名称" min-width="180" />
        <el-table-column prop="modelCode" label="模型编码" min-width="180" />
        <el-table-column label="供应商" min-width="160">
          <template #default="{ row }">
            {{ dictLabel('model_provider', row.provider) }}
          </template>
        </el-table-column>
        <el-table-column label="模型类型" min-width="140">
          <template #default="{ row }">
            {{ dictLabel('model_type', row.modelType) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.modelId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.modelId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="toggleModelStatus(row)"
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

    <el-dialog v-model="editDialogVisible" :title="editingModelId ? '编辑模型' : '新建模型'" width="760px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item label="模型编码">
          <el-input v-model="editForm.modelCode" placeholder="请输入模型编码" />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input v-model="editForm.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="editForm.provider" placeholder="请选择供应商" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('model_provider')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="editForm.modelType" placeholder="请选择模型类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('model_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="输入模态">
          <el-checkbox-group v-model="editForm.inputModalities" class="model-modalities">
            <el-checkbox value="text" disabled>文本输入</el-checkbox>
            <el-checkbox value="image">图片输入</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="editForm.baseUrl" placeholder="例如 https://api.openai.com" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="editForm.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingModelId ? '留空表示不修改现有 API Key' : '请输入 API Key'"
          />
        </el-form-item>
        <el-form-item label="API Path">
          <el-input v-model="editForm.apiPath" placeholder="例如 /v1/chat/completions" />
        </el-form-item>
        <el-form-item label="模型标识">
          <el-input v-model="editForm.modelIdentifier" placeholder="例如 gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input-number v-model="editForm.temperature" :min="0" :max="2" :step="0.1" style="width: 180px;" />
        </el-form-item>
        <el-form-item label="Max Tokens">
          <el-input-number v-model="editForm.maxTokens" :min="1" :step="256" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="Context Window">
          <el-input-number v-model="editForm.contextWindow" :min="1" :step="1024" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="Reserve Output">
          <el-input-number v-model="editForm.reservedOutputTokens" :min="1" :step="256" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="Reserve System">
          <el-input-number v-model="editForm.reservedSystemTokens" :min="1" :step="256" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="Compact Ratio">
          <el-input-number v-model="editForm.compactionTriggerRatio" :min="0.1" :max="1" :step="0.05" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="Tokenizer">
          <el-input v-model="editForm.tokenizerFamily" placeholder="例如 cl100k_base" />
        </el-form-item>
        <el-form-item label="Timeout(s)">
          <el-input-number v-model="editForm.timeoutSeconds" :min="1" :step="5" style="width: 220px;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio value="draft">草稿</el-radio>
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Metadata">
          <el-input v-model="editForm.metadataText" type="textarea" :rows="4" placeholder="请输入合法 JSON，例如 {}" />
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

    <el-drawer v-model="detailDrawerVisible" title="模型详情" size="620px">
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="模型编码">{{ detailRecord.modelCode }}</el-descriptions-item>
        <el-descriptions-item label="模型名称">{{ detailRecord.modelName }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ dictLabel("model_provider", detailRecord.provider) }}</el-descriptions-item>
        <el-descriptions-item label="模型类型">{{ dictLabel("model_type", detailRecord.modelType) }}</el-descriptions-item>
        <el-descriptions-item label="Base URL">{{ detailRecord.baseUrl || "-" }}</el-descriptions-item>
        <el-descriptions-item label="API Key">
          {{ detailRecord.apiKeyConfigured ? detailRecord.apiKeyMasked || "已配置" : "未配置" }}
        </el-descriptions-item>
        <el-descriptions-item label="API Path">{{ detailRecord.apiPath || "-" }}</el-descriptions-item>
        <el-descriptions-item label="模型标识">{{ detailRecord.modelIdentifier || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Temperature">{{ detailRecord.temperature ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Max Tokens">{{ detailRecord.maxTokens ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Context Window">{{ detailRecord.contextWindow ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Reserve Output">{{ detailRecord.reservedOutputTokens ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Reserve System">{{ detailRecord.reservedSystemTokens ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Compact Ratio">{{ detailRecord.compactionTriggerRatio ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="Tokenizer">{{ detailRecord.tokenizerFamily || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Timeout(s)">{{ detailRecord.timeoutSeconds ?? "-" }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detailRecord.status) }}</el-descriptions-item>
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
  createModel,
  disableModel,
  enableModel,
  fetchModelDetail,
  fetchModelPage,
  updateModel,
  type ModelDetail,
  type ModelRecord
} from "@/api/model";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  modelCode: string;
  modelName: string;
  provider: string;
  modelType: string;
  status: string;
}

interface EditForm {
  modelCode: string;
  modelName: string;
  provider: string;
  modelType: string;
  inputModalities: string[];
  baseUrl: string;
  apiKey: string;
  apiPath: string;
  modelIdentifier: string;
  temperature: number | null;
  maxTokens: number | null;
  contextWindow: number | null;
  reservedOutputTokens: number | null;
  reservedSystemTokens: number | null;
  compactionTriggerRatio: number | null;
  tokenizerFamily: string;
  timeoutSeconds: number | null;
  status: string;
  metadataText: string;
  remark: string;
}

const loading = ref(false);
const total = ref(0);
const modelRecords = ref<ModelRecord[]>([]);
const detailRecord = ref<ModelDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingModelId = ref<number | null>(null);
const saving = ref(false);
const dictOptionMap = ref<Record<string, DictOptionItem[]>>({});

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  modelCode: "",
  modelName: "",
  provider: "",
  modelType: "",
  status: ""
});

const editForm = reactive<EditForm>({
  modelCode: "",
  modelName: "",
  provider: "openai",
  modelType: "chat",
  inputModalities: ["text"],
  baseUrl: "",
  apiKey: "",
  apiPath: "",
  modelIdentifier: "",
  temperature: 0.2,
  maxTokens: 4096,
  contextWindow: 128000,
  reservedOutputTokens: 4096,
  reservedSystemTokens: 2048,
  compactionTriggerRatio: 0.8,
  tokenizerFamily: "cl100k_base",
  timeoutSeconds: 60,
  status: "draft",
  metadataText: "{}",
  remark: ""
});

async function loadModels(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchModelPage(queryForm);
    modelRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    modelRecords.value = [];
    total.value = 0;
    ElMessage.error("模型列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadDictOptionMap(): Promise<void> {
  try {
    dictOptionMap.value = await fetchDictOptions(["model_provider", "model_type"]) || {};
  } catch (error) {
    dictOptionMap.value = {};
    ElMessage.error("字典选项加载失败");
  }
}

async function openDetailDrawer(modelId: number): Promise<void> {
  detailDrawerVisible.value = true;
  try {
    detailRecord.value = await fetchModelDetail(modelId);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("模型详情加载失败");
  }
}

async function openEditDialog(modelId: number): Promise<void> {
  editingModelId.value = modelId;
  editDialogVisible.value = true;
  try {
    const detail = await fetchModelDetail(modelId);
    editForm.modelCode = detail.modelCode;
    editForm.modelName = detail.modelName;
    editForm.provider = detail.provider;
    editForm.modelType = detail.modelType;
    editForm.inputModalities = Array.isArray(detail.metadata?.inputModalities)
      ? detail.metadata.inputModalities.filter((item): item is string => typeof item === "string")
      : detail.modelType === "vision" ? ["text", "image"] : ["text"];
    editForm.baseUrl = detail.baseUrl || "";
    editForm.apiKey = "";
    editForm.apiPath = detail.apiPath || "";
    editForm.modelIdentifier = detail.modelIdentifier || "";
    editForm.temperature = detail.temperature ?? 0.2;
    editForm.maxTokens = detail.maxTokens ?? 4096;
    editForm.contextWindow = detail.contextWindow ?? 128000;
    editForm.reservedOutputTokens = detail.reservedOutputTokens ?? Math.max(detail.maxTokens ?? 4096, 1024);
    editForm.reservedSystemTokens = detail.reservedSystemTokens ?? 2048;
    editForm.compactionTriggerRatio = detail.compactionTriggerRatio ?? 0.8;
    editForm.tokenizerFamily = detail.tokenizerFamily || "cl100k_base";
    editForm.timeoutSeconds = detail.timeoutSeconds ?? 60;
    editForm.status = detail.status;
    editForm.metadataText = formatJson(detail.metadata);
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("模型详情加载失败");
  }
}

function openCreateDialog(): void {
  editingModelId.value = null;
  editDialogVisible.value = true;
  editForm.modelCode = "";
  editForm.modelName = "";
  editForm.provider = "openai";
  editForm.modelType = "chat";
  editForm.inputModalities = ["text"];
  editForm.baseUrl = "";
  editForm.apiKey = "";
  editForm.apiPath = "";
  editForm.modelIdentifier = "";
  editForm.temperature = 0.2;
  editForm.maxTokens = 4096;
  editForm.contextWindow = 128000;
  editForm.reservedOutputTokens = 4096;
  editForm.reservedSystemTokens = 2048;
  editForm.compactionTriggerRatio = 0.8;
  editForm.tokenizerFamily = "cl100k_base";
  editForm.timeoutSeconds = 60;
  editForm.status = "draft";
  editForm.metadataText = "{}";
  editForm.remark = "";
}

async function submitEditForm(): Promise<void> {
  if (!editForm.modelCode.trim()) {
    ElMessage.warning("请输入模型编码");
    return;
  }
  if (!editForm.modelName.trim()) {
    ElMessage.warning("请输入模型名称");
    return;
  }
  let metadata: Record<string, unknown>;
  try {
    metadata = JSON.parse(editForm.metadataText || "{}");
    metadata.inputModalities = Array.from(new Set(["text", ...editForm.inputModalities]));
  } catch (error) {
    ElMessage.warning("Metadata 必须为合法 JSON");
    return;
  }
  saving.value = true;
  try {
    const payload = {
      modelCode: editForm.modelCode,
      modelName: editForm.modelName,
      provider: editForm.provider,
      modelType: editForm.modelType,
      baseUrl: editForm.baseUrl,
      apiKey: editForm.apiKey,
      apiPath: editForm.apiPath,
      modelIdentifier: editForm.modelIdentifier,
      temperature: editForm.temperature,
      maxTokens: editForm.maxTokens,
      contextWindow: editForm.contextWindow,
      reservedOutputTokens: editForm.reservedOutputTokens,
      reservedSystemTokens: editForm.reservedSystemTokens,
      compactionTriggerRatio: editForm.compactionTriggerRatio,
      tokenizerFamily: editForm.tokenizerFamily,
      timeoutSeconds: editForm.timeoutSeconds,
      status: editForm.status,
      metadata,
      remark: editForm.remark
    };
    if (editingModelId.value) {
      await updateModel(editingModelId.value, payload);
      ElMessage.success("模型更新成功");
    } else {
      await createModel(payload);
      ElMessage.success("模型创建成功");
    }
    editDialogVisible.value = false;
    await loadModels();
  } catch (error) {
    ElMessage.error("模型保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleModelStatus(record: ModelRecord): Promise<void> {
  try {
    if (record.status === "enabled") {
      await disableModel(record.modelId);
      ElMessage.success("模型已停用");
    } else {
      await enableModel(record.modelId);
      ElMessage.success("模型已启用");
    }
    await loadModels();
  } catch (error) {
    ElMessage.error("模型状态更新失败");
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.modelCode = "";
  queryForm.modelName = "";
  queryForm.provider = "";
  queryForm.modelType = "";
  queryForm.status = "";
  void loadModels();
}

function handlePageChange(page: number): void {
  queryForm.pageNum = page;
  void loadModels();
}

function getDictOptions(typeCode: string): DictOptionItem[] {
  return dictOptionMap.value[typeCode] || [];
}

function dictLabel(typeCode: string, itemCode: string | null | undefined): string {
  if (!itemCode) {
    return "-";
  }
  const option = getDictOptions(typeCode).find((item) => item.itemCode === itemCode);
  return option?.itemName || itemCode;
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

function statusTagType(status: string): "success" | "warning" | "info" {
  if (status === "enabled") {
    return "success";
  }
  if (status === "disabled") {
    return "warning";
  }
  return "info";
}

function formatJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

onMounted(async () => {
  await loadDictOptionMap();
  await loadModels();
});
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.query-card,
.table-card {
  border-radius: 20px;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.query-actions {
  display: inline-flex;
  gap: 12px;
  align-items: center;
  margin-left: auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 20px;
  font-weight: 700;
  color: #17324d;
}

.card-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7c8f;
}

.pagination-row {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.json-block {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f6f8fb;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

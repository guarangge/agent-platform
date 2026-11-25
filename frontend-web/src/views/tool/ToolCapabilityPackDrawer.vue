<template>
  <el-drawer :model-value="modelValue" title="Tool Capability Pack" size="760px" @close="emit('update:modelValue', false)">
    <div class="pack-toolbar">
      <div>
        <strong>可复用能力包</strong>
        <p>将一组 Tool 作为稳定能力单元绑定到多个 Agent。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建能力包</el-button>
    </div>
    <el-table :data="packs" border stripe v-loading="loading">
      <el-table-column prop="pack_name" label="名称" min-width="160" />
      <el-table-column prop="pack_code" label="编码" min-width="160" />
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="tool_count" label="Tool" width="75" />
      <el-table-column prop="agent_count" label="Agent" width="75" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><el-tag :type="row.status === 'enabled' ? 'success' : 'info'">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row.id)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑能力包' : '新建能力包'" width="680px" append-to-body>
      <el-form :model="form" label-width="95px" v-loading="editorLoading">
        <el-form-item label="编码"><el-input v-model="form.packCode" :disabled="Boolean(editingId)" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.packName" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="form.version" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status"><el-radio value="draft">草稿</el-radio><el-radio value="enabled">启用</el-radio><el-radio value="disabled">停用</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="Tool 成员">
          <el-select v-model="form.toolIds" multiple filterable collapse-tags collapse-tags-tooltip style="width: 100%;">
            <el-option v-for="tool in tools" :key="tool.toolId" :label="`${tool.toolName} (${tool.toolCode})`" :value="tool.toolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="Metadata"><el-input v-model="form.metadataText" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createToolCapabilityPack, deleteToolCapabilityPack, fetchToolCapabilityPack, fetchToolCapabilityPacks,
  fetchToolPage, updateToolCapabilityPack, type ToolCapabilityPack, type ToolRecord
} from "@/api/tool";

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();
const packs = ref<ToolCapabilityPack[]>([]);
const tools = ref<ToolRecord[]>([]);
const loading = ref(false);
const editorLoading = ref(false);
const saving = ref(false);
const editorVisible = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({ packCode: "", packName: "", description: "", version: "1.0.0", status: "enabled", toolIds: [] as number[], metadataText: "{}" });

watch(() => props.modelValue, (visible) => { if (visible) void load(); }, { immediate: true });

async function load() {
  loading.value = true;
  try {
    const [packData, toolData] = await Promise.all([fetchToolCapabilityPacks(), loadAllTools()]);
    packs.value = packData;
    tools.value = toolData;
  } finally { loading.value = false; }
}

async function loadAllTools() {
  const result: ToolRecord[] = [];
  let pageNum = 1;
  while (true) {
    const page = await fetchToolPage({ pageNum, pageSize: 100 });
    result.push(...page.records);
    if (result.length >= page.total || page.records.length === 0) return result;
    pageNum += 1;
  }
}

function resetForm() {
  Object.assign(form, { packCode: "", packName: "", description: "", version: "1.0.0", status: "enabled", toolIds: [], metadataText: "{}" });
}

function openCreate() { editingId.value = null; resetForm(); editorVisible.value = true; }

async function openEdit(packId: number) {
  editingId.value = packId; editorVisible.value = true; editorLoading.value = true;
  try {
    const detail = await fetchToolCapabilityPack(packId);
    Object.assign(form, { packCode: detail.pack_code, packName: detail.pack_name, description: detail.description || "", version: detail.version, status: detail.status, toolIds: (detail.tools || []).map(item => Number(item.tool_id)), metadataText: JSON.stringify(detail.metadata || {}, null, 2) });
  } finally { editorLoading.value = false; }
}

async function save() {
  if (!form.packCode.trim() || !form.packName.trim() || !form.toolIds.length) return ElMessage.warning("请填写编码、名称并至少选择一个 Tool");
  let metadata: Record<string, unknown>;
  try { metadata = JSON.parse(form.metadataText || "{}"); } catch { return ElMessage.error("Metadata 不是合法 JSON"); }
  saving.value = true;
  try {
    const payload = { packCode: form.packCode, packName: form.packName, description: form.description, version: form.version, status: form.status, toolIds: form.toolIds, metadata };
    if (editingId.value) await updateToolCapabilityPack(editingId.value, payload); else await createToolCapabilityPack(payload);
    editorVisible.value = false; await load(); ElMessage.success("能力包已保存");
  } finally { saving.value = false; }
}

async function remove(pack: ToolCapabilityPack) {
  await ElMessageBox.confirm(`确认删除能力包 ${pack.pack_name}？已关联 Agent 时会被阻止。`, "删除能力包");
  await deleteToolCapabilityPack(pack.id); await load(); ElMessage.success("能力包已删除");
}
</script>

<style scoped>
.pack-toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.pack-toolbar strong { color: #19364f; font-size: 18px; }
.pack-toolbar p { margin: 5px 0 0; color: #708399; font-size: 13px; }
</style>

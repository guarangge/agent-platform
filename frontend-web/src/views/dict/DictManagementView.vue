<template>
  <section class="dict-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="typeQueryForm" inline class="query-form">
        <el-form-item label="字典编码">
          <el-input v-model="typeQueryForm.typeCode" placeholder="请输入字典编码" clearable />
        </el-form-item>
        <el-form-item label="字典名称">
          <el-input v-model="typeQueryForm.typeName" placeholder="请输入字典名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="typeQueryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetTypeQuery">重置</el-button>
          <el-button type="primary" @click="loadDictTypes(true)">查询</el-button>
          <el-button type="primary" plain @click="openCreateTypeDialog">新建字典类型</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="dict-grid">
      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-header">
            <div>
              <div class="card-title">字典类型</div>
              <div class="card-subtitle">统一管理平台资源分类、权限等级和范围模式。</div>
            </div>
          </div>
        </template>

        <el-table
          :data="dictTypeRecords"
          border
          stripe
          highlight-current-row
          v-loading="typeLoading"
          :row-class-name="typeRowClassName"
          @row-click="handleTypeRowClick"
        >
          <el-table-column prop="typeName" label="字典名称" min-width="160" />
          <el-table-column prop="typeCode" label="字典编码" min-width="180" />
          <el-table-column label="内置" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.builtIn ? 'success' : 'info'" effect="plain" round>
                {{ row.builtIn ? "内置" : "自定义" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="90">
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
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEditTypeDialog(row.dictTypeId)">编辑</el-button>
              <el-button
                link
                :type="row.status === 'enabled' ? 'warning' : 'success'"
                @click.stop="toggleDictTypeStatus(row)"
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
            :total="typeTotal"
            :current-page="typeQueryForm.pageNum"
            :page-size="typeQueryForm.pageSize"
            @current-change="handleTypePageChange"
          />
        </div>
      </el-card>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-header">
            <div>
              <div class="card-title">字典项</div>
              <div class="card-subtitle">
                <template v-if="selectedDictType">
                  当前字典：{{ selectedDictType.typeName }}（{{ selectedDictType.typeCode }}）
                </template>
                <template v-else>
                  请先选择左侧字典类型
                </template>
              </div>
            </div>
            <div class="card-actions">
              <el-button :disabled="!selectedDictType" @click="resetItemQuery">重置</el-button>
              <el-button type="primary" :disabled="!selectedDictType" @click="loadDictItems">查询</el-button>
              <el-button type="primary" plain :disabled="!selectedDictType" @click="openCreateItemDialog">新建字典项</el-button>
            </div>
          </div>
          <el-form :model="itemQueryForm" inline class="inline-filter" v-if="selectedDictType">
            <el-form-item label="字典项编码">
              <el-input v-model="itemQueryForm.itemCode" placeholder="请输入字典项编码" clearable />
            </el-form-item>
            <el-form-item label="字典项名称">
              <el-input v-model="itemQueryForm.itemName" placeholder="请输入字典项名称" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="itemQueryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
                <el-option label="启用" value="enabled" />
                <el-option label="停用" value="disabled" />
              </el-select>
            </el-form-item>
          </el-form>
        </template>

        <el-table :data="dictItemRecords" border stripe v-loading="itemLoading">
          <el-table-column prop="itemName" label="字典项名称" min-width="160" />
          <el-table-column prop="itemCode" label="字典项编码" min-width="180" />
          <el-table-column prop="sortOrder" label="排序" min-width="80" />
          <el-table-column label="默认" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.isDefault ? 'success' : 'info'" effect="plain" round>
                {{ row.isDefault ? "默认" : "否" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="90">
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
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditItemDialog(row.dictItemId)">编辑</el-button>
              <el-button
                link
                :type="row.status === 'enabled' ? 'warning' : 'success'"
                @click="toggleDictItemStatus(row)"
              >
                {{ row.status === "enabled" ? "停用" : "启用" }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!selectedDictType" description="请选择左侧字典类型后查看字典项" :image-size="96" />

        <div class="pagination-row">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="itemTotal"
            :current-page="itemQueryForm.pageNum"
            :page-size="itemQueryForm.pageSize"
            @current-change="handleItemPageChange"
          />
        </div>
      </el-card>
    </div>

    <el-dialog v-model="typeDialogVisible" :title="editingDictTypeId ? '编辑字典类型' : '新建字典类型'" width="680px">
      <el-form :model="typeForm" label-width="110px">
        <el-form-item label="字典编码">
          <el-input v-model="typeForm.typeCode" placeholder="请输入字典编码" />
        </el-form-item>
        <el-form-item label="字典名称">
          <el-input v-model="typeForm.typeName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否内置">
          <el-switch v-model="typeForm.builtIn" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="typeForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="submitTypeForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="editingDictItemId ? '编辑字典项' : '新建字典项'" width="680px">
      <el-form :model="itemForm" label-width="110px">
        <el-form-item label="字典项编码">
          <el-input v-model="itemForm.itemCode" placeholder="请输入字典项编码" />
        </el-form-item>
        <el-form-item label="字典项名称">
          <el-input v-model="itemForm.itemName" placeholder="请输入字典项名称" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认推荐">
          <el-switch v-model="itemForm.isDefault" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="itemForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSaving" @click="submitItemForm">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createDictItem,
  createDictType,
  disableDictItem,
  disableDictType,
  enableDictItem,
  enableDictType,
  fetchDictItemDetail,
  fetchDictItemPage,
  fetchDictTypeDetail,
  fetchDictTypePage,
  type DictItemRecord,
  type DictTypeRecord,
  updateDictItem,
  updateDictType
} from "@/api/dict";

interface TypeQueryForm {
  pageNum: number;
  pageSize: number;
  typeCode: string;
  typeName: string;
  status: string;
}

interface ItemQueryForm {
  pageNum: number;
  pageSize: number;
  itemCode: string;
  itemName: string;
  status: string;
}

interface TypeForm {
  typeCode: string;
  typeName: string;
  status: string;
  builtIn: boolean;
  description: string;
  remark: string;
}

interface ItemForm {
  itemCode: string;
  itemName: string;
  sortOrder: number;
  status: string;
  isDefault: boolean;
  description: string;
  remark: string;
}

const typeLoading = ref(false);
const typeTotal = ref(0);
const dictTypeRecords = ref<DictTypeRecord[]>([]);
const selectedDictType = ref<DictTypeRecord | null>(null);
const typeDialogVisible = ref(false);
const typeSaving = ref(false);
const editingDictTypeId = ref<number | null>(null);

const itemLoading = ref(false);
const itemTotal = ref(0);
const dictItemRecords = ref<DictItemRecord[]>([]);
const itemDialogVisible = ref(false);
const itemSaving = ref(false);
const editingDictItemId = ref<number | null>(null);

const typeQueryForm = reactive<TypeQueryForm>({
  pageNum: 1,
  pageSize: 10,
  typeCode: "",
  typeName: "",
  status: ""
});

const itemQueryForm = reactive<ItemQueryForm>({
  pageNum: 1,
  pageSize: 10,
  itemCode: "",
  itemName: "",
  status: ""
});

const typeForm = reactive<TypeForm>({
  typeCode: "",
  typeName: "",
  status: "enabled",
  builtIn: false,
  description: "",
  remark: ""
});

const itemForm = reactive<ItemForm>({
  itemCode: "",
  itemName: "",
  sortOrder: 0,
  status: "enabled",
  isDefault: false,
  description: "",
  remark: ""
});

async function loadDictTypes(resetSelection = false): Promise<void> {
  typeLoading.value = true;
  try {
    const page = await fetchDictTypePage(typeQueryForm);
    dictTypeRecords.value = page.records;
    typeTotal.value = page.total;
    if (page.records.length === 0) {
      selectedDictType.value = null;
      dictItemRecords.value = [];
      itemTotal.value = 0;
      return;
    }
    if (resetSelection || !selectedDictType.value) {
      selectedDictType.value = page.records[0];
      await loadDictItems();
      return;
    }
    const matched = page.records.find((item) => item.dictTypeId === selectedDictType.value?.dictTypeId);
    if (matched) {
      selectedDictType.value = matched;
      await loadDictItems();
    } else {
      selectedDictType.value = page.records[0];
      await loadDictItems();
    }
  } catch (error) {
    dictTypeRecords.value = [];
    typeTotal.value = 0;
    ElMessage.error("字典类型列表加载失败");
  } finally {
    typeLoading.value = false;
  }
}

async function loadDictItems(): Promise<void> {
  if (!selectedDictType.value) {
    dictItemRecords.value = [];
    itemTotal.value = 0;
    return;
  }
  itemLoading.value = true;
  try {
    const page = await fetchDictItemPage(selectedDictType.value.dictTypeId, itemQueryForm);
    dictItemRecords.value = page.records;
    itemTotal.value = page.total;
  } catch (error) {
    dictItemRecords.value = [];
    itemTotal.value = 0;
    ElMessage.error("字典项列表加载失败");
  } finally {
    itemLoading.value = false;
  }
}

function handleTypeRowClick(row: DictTypeRecord): void {
  selectedDictType.value = row;
  itemQueryForm.pageNum = 1;
  void loadDictItems();
}

function typeRowClassName({ row }: { row: DictTypeRecord }): string {
  return row.dictTypeId === selectedDictType.value?.dictTypeId ? "is-current-type-row" : "";
}

function resetTypeQuery(): void {
  typeQueryForm.pageNum = 1;
  typeQueryForm.pageSize = 10;
  typeQueryForm.typeCode = "";
  typeQueryForm.typeName = "";
  typeQueryForm.status = "";
  void loadDictTypes(true);
}

function resetItemQuery(): void {
  itemQueryForm.pageNum = 1;
  itemQueryForm.pageSize = 10;
  itemQueryForm.itemCode = "";
  itemQueryForm.itemName = "";
  itemQueryForm.status = "";
  void loadDictItems();
}

function handleTypePageChange(pageNum: number): void {
  typeQueryForm.pageNum = pageNum;
  void loadDictTypes();
}

function handleItemPageChange(pageNum: number): void {
  itemQueryForm.pageNum = pageNum;
  void loadDictItems();
}

function openCreateTypeDialog(): void {
  editingDictTypeId.value = null;
  typeDialogVisible.value = true;
  typeForm.typeCode = "";
  typeForm.typeName = "";
  typeForm.status = "enabled";
  typeForm.builtIn = false;
  typeForm.description = "";
  typeForm.remark = "";
}

async function openEditTypeDialog(dictTypeId: number): Promise<void> {
  editingDictTypeId.value = dictTypeId;
  typeDialogVisible.value = true;
  try {
    const detail = await fetchDictTypeDetail(dictTypeId);
    typeForm.typeCode = detail.typeCode;
    typeForm.typeName = detail.typeName;
    typeForm.status = detail.status;
    typeForm.builtIn = detail.builtIn;
    typeForm.description = detail.description || "";
    typeForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("字典类型详情加载失败");
  }
}

async function submitTypeForm(): Promise<void> {
  if (!typeForm.typeCode.trim()) {
    ElMessage.warning("请输入字典编码");
    return;
  }
  if (!typeForm.typeName.trim()) {
    ElMessage.warning("请输入字典名称");
    return;
  }
  typeSaving.value = true;
  try {
    if (editingDictTypeId.value) {
      await updateDictType(editingDictTypeId.value, { ...typeForm });
      ElMessage.success("字典类型更新成功");
    } else {
      await createDictType({ ...typeForm });
      ElMessage.success("字典类型创建成功");
    }
    typeDialogVisible.value = false;
    await loadDictTypes();
  } catch (error) {
    ElMessage.error("字典类型保存失败");
  } finally {
    typeSaving.value = false;
  }
}

async function toggleDictTypeStatus(row: DictTypeRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableDictType(row.dictTypeId);
      ElMessage.success("字典类型已停用");
    } else {
      await enableDictType(row.dictTypeId);
      ElMessage.success("字典类型已启用");
    }
    await loadDictTypes();
  } catch (error) {
    ElMessage.error("字典类型状态更新失败");
  }
}

function openCreateItemDialog(): void {
  if (!selectedDictType.value) {
    return;
  }
  editingDictItemId.value = null;
  itemDialogVisible.value = true;
  itemForm.itemCode = "";
  itemForm.itemName = "";
  itemForm.sortOrder = 0;
  itemForm.status = "enabled";
  itemForm.isDefault = false;
  itemForm.description = "";
  itemForm.remark = "";
}

async function openEditItemDialog(dictItemId: number): Promise<void> {
  editingDictItemId.value = dictItemId;
  itemDialogVisible.value = true;
  try {
    const detail = await fetchDictItemDetail(dictItemId);
    itemForm.itemCode = detail.itemCode;
    itemForm.itemName = detail.itemName;
    itemForm.sortOrder = detail.sortOrder;
    itemForm.status = detail.status;
    itemForm.isDefault = detail.isDefault;
    itemForm.description = detail.description || "";
    itemForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("字典项详情加载失败");
  }
}

async function submitItemForm(): Promise<void> {
  if (!selectedDictType.value) {
    ElMessage.warning("请先选择字典类型");
    return;
  }
  if (!itemForm.itemCode.trim()) {
    ElMessage.warning("请输入字典项编码");
    return;
  }
  if (!itemForm.itemName.trim()) {
    ElMessage.warning("请输入字典项名称");
    return;
  }
  itemSaving.value = true;
  try {
    if (editingDictItemId.value) {
      await updateDictItem(editingDictItemId.value, { ...itemForm });
      ElMessage.success("字典项更新成功");
    } else {
      await createDictItem(selectedDictType.value.dictTypeId, { ...itemForm });
      ElMessage.success("字典项创建成功");
    }
    itemDialogVisible.value = false;
    await loadDictItems();
  } catch (error) {
    ElMessage.error("字典项保存失败");
  } finally {
    itemSaving.value = false;
  }
}

async function toggleDictItemStatus(row: DictItemRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableDictItem(row.dictItemId);
      ElMessage.success("字典项已停用");
    } else {
      await enableDictItem(row.dictItemId);
      ElMessage.success("字典项已启用");
    }
    await loadDictItems();
  } catch (error) {
    ElMessage.error("字典项状态更新失败");
  }
}

function statusText(status: string): string {
  return status === "enabled" ? "启用" : "停用";
}

function statusTagType(status: string): "success" | "info" {
  return status === "enabled" ? "success" : "info";
}

onMounted(() => {
  void loadDictTypes(true);
});
</script>

<style scoped>
.dict-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dict-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 1fr);
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

.query-actions,
.card-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
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

.inline-filter {
  margin-top: 14px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

:deep(.is-current-type-row td) {
  background: #f1f7ff !important;
}
</style>

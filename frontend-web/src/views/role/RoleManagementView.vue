<template>
  <section class="role-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="角色编码">
          <el-input v-model="queryForm.roleCode" placeholder="请输入角色编码" clearable />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="queryForm.roleName" placeholder="请输入角色名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadRoles">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建角色</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">角色管理</div>
            <div class="card-subtitle">当前阶段先完成角色列表、详情、新增编辑弹层和绑定 Agent 入口。</div>
          </div>
        </div>
      </template>

      <el-table :data="roleRecords" border stripe v-loading="loading">
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220">
          <template #default="{ row }">
            {{ row.description || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
              {{ row.status === "enabled" ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.roleId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.roleId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="toggleRoleStatus(row)"
            >
              {{ row.status === "enabled" ? "停用" : "启用" }}
            </el-button>
            <el-button link type="primary" @click="openBindAgentDialog(row.roleId)">绑定 Agent</el-button>
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

    <el-dialog v-model="editDialogVisible" :title="editingRoleId ? '编辑角色' : '新建角色'" width="680px">
      <el-form :model="editForm" label-width="96px">
        <el-form-item label="角色编码">
          <el-input v-model="editForm.roleCode" placeholder="请输入角色编码" :disabled="Boolean(editingRoleId)" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="editForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单范围">
          <el-select v-model="editForm.menuScope" multiple clearable placeholder="请选择菜单范围" style="width: 100%;">
            <el-option v-for="item in menuScopeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入描述" />
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

    <el-drawer v-model="detailDrawerVisible" title="角色详情" size="560px">
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="角色编码">{{ detailRecord.roleCode }}</el-descriptions-item>
        <el-descriptions-item label="角色名称">{{ detailRecord.roleName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detailRecord.status === "enabled" ? "启用" : "停用" }}
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailRecord.description || "-" }}</el-descriptions-item>
        <el-descriptions-item label="菜单范围">
          <div class="scope-tags">
            <el-tag v-for="item in detailRecord.menuScope" :key="item" round>
              {{ item }}
            </el-tag>
            <span v-if="detailRecord.menuScope.length === 0">-</span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailRecord.remark || "-" }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无详情数据" />
      <div class="drawer-footer">
        <el-button type="primary" plain @click="openBindAgentDialog(detailRecord?.roleId ?? 0)">
          绑定 Agent
        </el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="agentDialogVisible" title="绑定 Agent" width="700px">
      <div class="agent-dialog-body">
        <el-alert
          title="当前采用覆盖式更新，保存后会按本次勾选结果重置该角色的 Agent 绑定关系。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-checkbox-group v-model="selectedAgentIds" class="agent-checkbox-group">
          <el-checkbox v-for="agent in agentOptions" :key="agent.agentId" :value="agent.agentId">
            {{ agent.agentName }}（{{ agent.agentCode }} / {{ agent.version }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="agentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="agentSaving" @click="submitRoleAgents">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import { fetchAgentPage, type AgentRecord } from "@/api/agent";
import {
  createRole,
  disableRole,
  enableRole,
  fetchRoleAgents,
  fetchRoleDetail,
  fetchRolePage,
  updateRoleAgents,
  updateRole,
  type RoleAgentItem,
  type RoleDetail,
  type RoleRecord
} from "@/api/role";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  roleCode: string;
  roleName: string;
  status: string;
}

interface EditForm {
  roleCode: string;
  roleName: string;
  status: string;
  description: string;
  menuScope: string[];
  remark: string;
}

const menuScopeOptions = [
  "dashboard",
  "agent",
  "tool",
  "workflow",
  "prompt",
  "user",
  "role",
  "chat"
];

const loading = ref(false);
const total = ref(0);
const roleRecords = ref<RoleRecord[]>([]);
const detailRecord = ref<RoleDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingRoleId = ref<number | null>(null);
const saving = ref(false);
const agentDialogVisible = ref(false);
const agentSaving = ref(false);
const bindingRoleId = ref<number | null>(null);
const agentOptions = ref<AgentRecord[]>([]);
const selectedAgentIds = ref<number[]>([]);

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  roleCode: "",
  roleName: "",
  status: ""
});

const editForm = reactive<EditForm>({
  roleCode: "",
  roleName: "",
  status: "enabled",
  description: "",
  menuScope: [],
  remark: ""
});

async function loadRoles(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchRolePage(queryForm);
    roleRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    ElMessage.error("角色列表加载失败");
    roleRecords.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function openDetailDrawer(roleId: number): Promise<void> {
  detailDrawerVisible.value = true;
  try {
    detailRecord.value = await fetchRoleDetail(roleId);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("角色详情加载失败");
  }
}

async function openEditDialog(roleId: number): Promise<void> {
  editingRoleId.value = roleId;
  editDialogVisible.value = true;
  try {
    const detail = await fetchRoleDetail(roleId);
    editForm.roleCode = detail.roleCode;
    editForm.roleName = detail.roleName;
    editForm.status = detail.status;
    editForm.description = detail.description || "";
    editForm.menuScope = [...detail.menuScope];
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("角色详情加载失败");
  }
}

function openCreateDialog(): void {
  editingRoleId.value = null;
  editDialogVisible.value = true;
  editForm.roleCode = "";
  editForm.roleName = "";
  editForm.status = "enabled";
  editForm.description = "";
  editForm.menuScope = [];
  editForm.remark = "";
}

function openBindAgentDialog(roleId: number): void {
  if (!roleId) {
    return;
  }
  bindingRoleId.value = roleId;
  agentDialogVisible.value = true;
  void loadRoleAgentBindingData(roleId);
}

async function submitEditForm(): Promise<void> {
  if (!editForm.roleCode.trim() && !editingRoleId.value) {
    ElMessage.warning("请输入角色编码");
    return;
  }
  if (!editForm.roleName.trim()) {
    ElMessage.warning("请输入角色名称");
    return;
  }
  if (editForm.menuScope.length === 0) {
    ElMessage.warning("请选择菜单范围");
    return;
  }

  saving.value = true;
  try {
    if (editingRoleId.value) {
      await updateRole(editingRoleId.value, {
        roleCode: editForm.roleCode,
        roleName: editForm.roleName,
        status: editForm.status,
        description: editForm.description,
        menuScope: editForm.menuScope,
        remark: editForm.remark
      });
      ElMessage.success("角色更新成功");
    } else {
      await createRole({
        roleCode: editForm.roleCode,
        roleName: editForm.roleName,
        status: editForm.status,
        description: editForm.description,
        menuScope: editForm.menuScope,
        remark: editForm.remark
      });
      ElMessage.success("角色创建成功");
    }
    editDialogVisible.value = false;
    await loadRoles();
  } catch (error) {
    ElMessage.error("角色保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleRoleStatus(row: RoleRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableRole(row.roleId);
      ElMessage.success("角色已停用");
    } else {
      await enableRole(row.roleId);
      ElMessage.success("角色已启用");
    }
    await loadRoles();
  } catch (error) {
    ElMessage.error("角色状态更新失败");
  }
}

async function loadRoleAgentBindingData(roleId: number): Promise<void> {
  try {
    const [agentPage, roleAgents] = await Promise.all([
      fetchAgentPage({
        pageNum: 1,
        pageSize: 100,
        status: "enabled"
      }),
      fetchRoleAgents(roleId)
    ]);
    agentOptions.value = agentPage.records;
    selectedAgentIds.value = roleAgents.map((item: RoleAgentItem) => item.agentId);
  } catch (error) {
    agentOptions.value = [];
    selectedAgentIds.value = [];
    agentDialogVisible.value = false;
    ElMessage.error("角色 Agent 绑定数据加载失败");
  }
}

async function submitRoleAgents(): Promise<void> {
  if (!bindingRoleId.value) {
    return;
  }
  agentSaving.value = true;
  try {
    await updateRoleAgents(bindingRoleId.value, selectedAgentIds.value);
    ElMessage.success("角色 Agent 更新成功");
    agentDialogVisible.value = false;
  } catch (error) {
    ElMessage.error("角色 Agent 更新失败");
  } finally {
    agentSaving.value = false;
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.roleCode = "";
  queryForm.roleName = "";
  queryForm.status = "";
  void loadRoles();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadRoles();
}

onMounted(() => {
  void loadRoles();
});
</script>

<style scoped>
.role-page {
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

.drawer-footer {
  margin-top: 20px;
}

.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agent-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>

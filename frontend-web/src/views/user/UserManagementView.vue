<template>
  <section class="user-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="用户名">
          <el-input v-model="queryForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="queryForm.displayName" placeholder="请输入显示名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadUsers">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建用户</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">用户管理</div>
            <div class="card-subtitle">当前阶段先完成用户列表、详情、新增编辑弹层和绑定角色入口。</div>
          </div>
        </div>
      </template>

      <el-table :data="userRecords" border stripe v-loading="loading">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column prop="status" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
              {{ row.status === "enabled" ? "启用" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.lastLoginAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.userId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.userId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="toggleUserStatus(row)"
            >
              {{ row.status === "enabled" ? "停用" : "启用" }}
            </el-button>
            <el-button link type="primary" @click="openResetPasswordDialog(row.userId)">重置密码</el-button>
            <el-button link type="primary" @click="openBindRoleDialog(row.userId)">绑定角色</el-button>
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

    <el-dialog v-model="editDialogVisible" :title="editingUserId ? '编辑用户' : '新建用户'" width="640px">
      <el-form :model="editForm" label-width="92px">
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" placeholder="请输入用户名" :disabled="Boolean(editingUserId)" />
        </el-form-item>
        <el-form-item v-if="!editingUserId" label="登录密码">
          <el-input v-model="editForm.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="editForm.displayName" placeholder="请输入显示名" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
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

    <el-drawer v-model="detailDrawerVisible" title="用户详情" size="520px">
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="用户名">{{ detailRecord.username }}</el-descriptions-item>
        <el-descriptions-item label="显示名">{{ detailRecord.displayName }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailRecord.email || "-" }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailRecord.mobile || "-" }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detailRecord.status === "enabled" ? "启用" : "停用" }}
        </el-descriptions-item>
        <el-descriptions-item label="最近登录">{{ formatDateTime(detailRecord.lastLoginAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailRecord.remark || "-" }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无详情数据" />
      <div class="drawer-footer">
        <el-button type="primary" plain @click="openBindRoleDialog(detailRecord?.userId ?? 0)">
          绑定角色
        </el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="roleDialogVisible" title="绑定角色" width="640px">
      <div class="role-dialog-body">
        <el-alert
          title="当前采用覆盖式更新，保存后会按本次勾选结果重置该用户的角色关系。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-checkbox-group v-model="selectedRoleIds" class="role-checkbox-group">
          <el-checkbox v-for="role in roleOptions" :key="role.roleId" :value="role.roleId">
            {{ role.roleName }}（{{ role.roleCode }}）
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="submitUserRoles">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import { fetchRolePage, type RoleRecord } from "@/api/role";
import {
  createUser,
  disableUser,
  enableUser,
  fetchUserDetail,
  fetchUserPage,
  fetchUserRoles,
  resetUserPassword,
  updateUserRoles,
  updateUser,
  type UserDetail,
  type UserRoleItem,
  type UserRecord
} from "@/api/user";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  username: string;
  displayName: string;
  status: string;
}

interface EditForm {
  username: string;
  password: string;
  displayName: string;
  email: string;
  mobile: string;
  status: string;
  remark: string;
}

const loading = ref(false);
const total = ref(0);
const userRecords = ref<UserRecord[]>([]);
const detailRecord = ref<UserDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingUserId = ref<number | null>(null);
const saving = ref(false);
const roleDialogVisible = ref(false);
const roleSaving = ref(false);
const bindingUserId = ref<number | null>(null);
const roleOptions = ref<RoleRecord[]>([]);
const selectedRoleIds = ref<number[]>([]);

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  username: "",
  displayName: "",
  status: ""
});

const editForm = reactive<EditForm>({
  username: "",
  password: "",
  displayName: "",
  email: "",
  mobile: "",
  status: "enabled",
  remark: ""
});

async function loadUsers(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchUserPage(queryForm);
    userRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    ElMessage.error("用户列表加载失败");
    userRecords.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function openDetailDrawer(userId: number): Promise<void> {
  detailDrawerVisible.value = true;
  try {
    detailRecord.value = await fetchUserDetail(userId);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("用户详情加载失败");
  }
}

async function openEditDialog(userId: number): Promise<void> {
  editingUserId.value = userId;
  editDialogVisible.value = true;
  try {
    const detail = await fetchUserDetail(userId);
    editForm.username = detail.username;
    editForm.password = "";
    editForm.displayName = detail.displayName;
    editForm.email = detail.email;
    editForm.mobile = detail.mobile;
    editForm.status = detail.status;
    editForm.remark = detail.remark;
  } catch (error) {
    ElMessage.error("用户详情加载失败");
  }
}

function openCreateDialog(): void {
  editingUserId.value = null;
  editDialogVisible.value = true;
  editForm.username = "";
  editForm.password = "";
  editForm.displayName = "";
  editForm.email = "";
  editForm.mobile = "";
  editForm.status = "enabled";
  editForm.remark = "";
}

function openBindRoleDialog(userId: number): void {
  if (!userId) {
    return;
  }
  bindingUserId.value = userId;
  roleDialogVisible.value = true;
  void loadRoleBindingData(userId);
}

async function submitEditForm(): Promise<void> {
  if (!editForm.username.trim() && !editingUserId.value) {
    ElMessage.warning("请输入用户名");
    return;
  }
  if (!editingUserId.value && editForm.password.trim().length < 6) {
    ElMessage.warning("初始密码长度不能少于6位");
    return;
  }
  if (!editForm.displayName.trim()) {
    ElMessage.warning("请输入显示名");
    return;
  }

  saving.value = true;
  try {
    if (editingUserId.value) {
      await updateUser(editingUserId.value, {
        displayName: editForm.displayName,
        email: editForm.email,
        mobile: editForm.mobile,
        status: editForm.status,
        remark: editForm.remark
      });
      ElMessage.success("用户更新成功");
    } else {
      await createUser({
        username: editForm.username,
        password: editForm.password,
        displayName: editForm.displayName,
        email: editForm.email,
        mobile: editForm.mobile,
        status: editForm.status,
        remark: editForm.remark
      });
      ElMessage.success("用户创建成功");
    }
    editDialogVisible.value = false;
    await loadUsers();
  } catch (error) {
    ElMessage.error("用户保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleUserStatus(row: UserRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableUser(row.userId);
      ElMessage.success("用户已停用");
    } else {
      await enableUser(row.userId);
      ElMessage.success("用户已启用");
    }
    await loadUsers();
  } catch (error) {
    ElMessage.error("用户状态更新失败");
  }
}

async function openResetPasswordDialog(userId: number): Promise<void> {
  try {
    const result = await ElMessageBox.prompt("请输入新密码，长度至少 6 位。", "重置密码", {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      inputType: "password",
      inputValidator: (value: string) => {
        if (!value || value.trim().length < 6) {
          return "新密码长度不能少于6位";
        }
        return true;
      }
    });
    await resetUserPassword(userId, result.value.trim());
    ElMessage.success("密码重置成功");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("密码重置失败");
    }
  }
}

async function loadRoleBindingData(userId: number): Promise<void> {
  try {
    const [rolePage, userRoles] = await Promise.all([
      fetchRolePage({
        pageNum: 1,
        pageSize: 100,
        status: "enabled"
      }),
      fetchUserRoles(userId)
    ]);
    roleOptions.value = rolePage.records;
    selectedRoleIds.value = userRoles.map((item: UserRoleItem) => item.roleId);
  } catch (error) {
    roleOptions.value = [];
    selectedRoleIds.value = [];
    roleDialogVisible.value = false;
    ElMessage.error("角色绑定数据加载失败");
  }
}

async function submitUserRoles(): Promise<void> {
  if (!bindingUserId.value) {
    return;
  }
  roleSaving.value = true;
  try {
    await updateUserRoles(bindingUserId.value, selectedRoleIds.value);
    ElMessage.success("用户角色更新成功");
    roleDialogVisible.value = false;
  } catch (error) {
    ElMessage.error("用户角色更新失败");
  } finally {
    roleSaving.value = false;
  }
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.username = "";
  queryForm.displayName = "";
  queryForm.status = "";
  void loadUsers();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadUsers();
}

onMounted(() => {
  void loadUsers();
});
</script>

<style scoped>
.user-page {
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

.role-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>

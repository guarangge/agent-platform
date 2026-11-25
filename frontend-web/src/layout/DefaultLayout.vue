<template>
  <div class="layout-shell" :class="{ 'layout-shell--conversation': isConversationPage }">
    <header class="layout-header">
      <div class="brand-block">
        <div class="layout-title">{{ isConversationPage ? "Agent" : "管理后台" }}</div>
        <div v-if="!isConversationPage" class="layout-subtitle">通用 Agent 平台配置与治理中心</div>
      </div>
      <div class="header-actions">
        <el-dropdown trigger="click" placement="bottom-end" @command="handleAccountCommand">
          <button class="account-trigger" type="button" aria-label="打开账户菜单">
            <span class="account-avatar">{{ userInitial }}</span>
            <span class="account-identity">
              <strong>{{ authStore.currentUser?.displayName ?? "未登录" }}</strong>
              <small>{{ userRoleLabel }}</small>
            </span>
            <el-icon class="account-arrow"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="isConversationPage && canAccessSettings" command="settings" :icon="Setting">
                管理设置
              </el-dropdown-item>
              <el-dropdown-item v-else-if="!isConversationPage" command="chat" :icon="ChatDotRound">
                返回会话
              </el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="layout-main">
      <aside v-if="!isConversationPage" class="layout-side">
        <RouterLink
          v-for="item in visibleNavigationItems"
          :key="item.path"
          class="nav-link"
          :to="item.path"
        >
          {{ item.label }}
        </RouterLink>
      </aside>
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowDown, ChatDotRound, Setting, SwitchButton } from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const isConversationPage = computed(() => route.name === "chat");

interface NavigationItem {
  path: string;
  label: string;
  scope?: string;
  adminOnly?: boolean;
}

const navigationItems: NavigationItem[] = [
  { path: "/settings", label: "管理概览" },
  { path: "/models", label: "模型管理", scope: "model" },
  { path: "/agents", label: "Agent 管理", scope: "agent" },
  { path: "/task-operations", label: "任务运行中心", scope: "chat" },
  { path: "/traces", label: "Tracing中心", scope: "tracing" },
  { path: "/trace-compaction", label: "压缩治理", scope: "tracing" },
  { path: "/capability-policy-audits", label: "能力策略审计", scope: "tracing" },
  { path: "/evals", label: "Eval Harness", scope: "eval" },
  { path: "/tools", label: "Tool 管理", scope: "tool" },
  { path: "/knowledge", label: "Knowledge 管理", scope: "knowledge" },
  { path: "/sandboxes", label: "Sandbox 控制台", scope: "sandbox" },
  { path: "/cost-governance", label: "成本与配额", scope: "tracing" },
  { path: "/replays", label: "Replay 管理", adminOnly: true },
  { path: "/run-control", label: "运行调度控制面", adminOnly: true },
  { path: "/experiment-governance", label: "Prompt / Tool 实验", adminOnly: true },
  { path: "/workflows", label: "Workflow 管理", scope: "workflow" },
  { path: "/prompts", label: "Prompt 管理", scope: "prompt" },
  { path: "/dicts", label: "字典管理", scope: "dict" },
  { path: "/guardrails", label: "策略中心", scope: "guardrail" },
  { path: "/users", label: "用户管理", adminOnly: true },
  { path: "/roles", label: "角色管理", adminOnly: true }
];

const visibleNavigationItems = computed(() => {
  const currentUser = authStore.currentUser;
  const isAdmin = currentUser?.roles.some((role) =>
    ["ADMIN", "SUPER_ADMIN"].includes(role.roleCode.toUpperCase())) ?? false;
  return navigationItems.filter((item) => {
    if (isAdmin) {
      return true;
    }
    if (item.adminOnly) {
      return false;
    }
    return !item.scope || currentUser?.menuScopes.includes(item.scope);
  });
});

const canAccessSettings = computed(() => {
  const currentUser = authStore.currentUser;
  const isAdmin = currentUser?.roles.some((role) =>
    ["ADMIN", "SUPER_ADMIN"].includes(role.roleCode.toUpperCase())) ?? false;
  return isAdmin || (currentUser?.menuScopes.some((scope) => scope !== "chat") ?? false);
});

const userInitial = computed(() => (authStore.currentUser?.displayName?.trim().slice(0, 1) || "U").toUpperCase());
const userRoleLabel = computed(() => authStore.currentUser?.roles?.map((item) => item.roleName).join(" / ") || "普通用户");

async function openSettings(): Promise<void> {
  await router.push("/settings");
}

async function openConversations(): Promise<void> {
  await router.push("/chat");
}

async function handleAccountCommand(command: string): Promise<void> {
  if (command === "settings") {
    await openSettings();
    return;
  }
  if (command === "chat") {
    await openConversations();
    return;
  }
  if (command === "logout") {
    await handleLogout();
  }
}

async function handleLogout(): Promise<void> {
  authStore.logout();
  ElMessage.success("已退出登录");
  await router.push("/login");
}
</script>

<style scoped>
.layout-shell {
  min-height: 100vh;
  background: linear-gradient(180deg, #f6f8fb 0%, #eef3f8 100%);
}

.layout-header {
  padding: 24px 32px;
  border-bottom: 1px solid #dbe3ec;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.layout-title {
  font-size: 28px;
  font-weight: 700;
  color: #17324d;
}

.layout-subtitle {
  margin-top: 6px;
  font-size: 14px;
  color: #5f7286;
}

.layout-main {
  padding: 32px;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 24px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 5px 8px 5px 6px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: #273a43;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.account-trigger:hover,
.account-trigger:focus-visible {
  border-color: #d3ded9;
  background: #f4f7f5;
  box-shadow: 0 6px 18px rgba(35, 58, 55, 0.08);
  outline: none;
}

.account-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 50%;
  background: #234b46;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.16);
}

.account-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
  line-height: 1.2;
}

.account-identity strong,
.account-identity small {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-identity strong {
  font-size: 13px;
  font-weight: 650;
}

.account-identity small {
  color: #76847f;
  font-size: 11px;
}

.account-arrow {
  color: #8a9792;
  font-size: 12px;
}

.layout-side {
  padding: 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid #dbe3ec;
  height: fit-content;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nav-link {
  display: block;
  padding: 12px 14px;
  border-radius: 14px;
  text-decoration: none;
  color: #29445f;
  font-weight: 600;
}

.nav-link.router-link-active {
  background: #17324d;
  color: #ffffff;
}

.layout-shell--conversation {
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
}

.layout-shell--conversation .layout-header {
  height: 76px;
  box-sizing: border-box;
  padding: 12px 24px;
}

.layout-shell--conversation .layout-title {
  font-size: 23px;
}

.layout-shell--conversation .layout-subtitle {
  margin-top: 2px;
  font-size: 12px;
}

.layout-shell--conversation .layout-main {
  height: calc(100dvh - 76px);
  min-height: 0;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  padding: 14px 18px;
  overflow: hidden;
}

@media (max-width: 1000px) {
  .layout-shell--conversation {
    height: auto;
    min-height: 100dvh;
    overflow: visible;
  }

  .layout-shell--conversation .layout-main {
    height: auto;
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .layout-header {
    padding: 14px 18px;
  }

  .account-identity {
    display: none;
  }

  .account-trigger {
    padding-right: 6px;
  }
}
</style>

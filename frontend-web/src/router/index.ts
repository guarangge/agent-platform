import { createRouter, createWebHistory } from "vue-router";
import { ElMessage } from "element-plus";
import DefaultLayout from "@/layout/DefaultLayout.vue";
import AgentManagementView from "@/views/agent/AgentManagementView.vue";
import ConversationView from "@/views/chat/ConversationView.vue";
import CompactionOperationsView from "@/views/conversation/CompactionOperationsView.vue";
import TraceOperationsView from "@/views/conversation/TraceOperationsView.vue";
import LoginView from "@/views/auth/LoginView.vue";
import DashboardView from "@/views/dashboard/DashboardView.vue";
import DictManagementView from "@/views/dict/DictManagementView.vue";
import EvalHarnessView from "@/views/eval/EvalHarnessView.vue";
import GuardrailPolicyView from "@/views/guardrail/GuardrailPolicyView.vue";
import ModelManagementView from "@/views/model/ModelManagementView.vue";
import RoleManagementView from "@/views/role/RoleManagementView.vue";
import PromptManagementView from "@/views/prompt/PromptManagementView.vue";
import ToolManagementView from "@/views/tool/ToolManagementView.vue";
import UserManagementView from "@/views/user/UserManagementView.vue";
import WorkflowManagementView from "@/views/workflow/WorkflowManagementView.vue";
import KnowledgeManagementView from "@/views/knowledge/KnowledgeManagementView.vue";
import SandboxManagementView from "@/views/sandbox/SandboxManagementView.vue";
import CostGovernanceView from "@/views/cost/CostGovernanceView.vue";
import RunControlPlaneView from "@/views/runtime/RunControlPlaneView.vue";
import TaskOperationsView from "@/views/runtime/TaskOperationsView.vue";
import ReplayManagementView from "@/views/runtime/ReplayManagementView.vue";
import CapabilityPolicyAuditView from "@/views/runtime/CapabilityPolicyAuditView.vue";
import ExperimentGovernanceView from "@/views/governance/ExperimentGovernanceView.vue";
import pinia from "@/stores";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: LoginView
    },
    {
      path: "/",
      component: DefaultLayout,
      meta: {
        requiresAuth: true
      },
      children: [
        {
          path: "",
          redirect: "/chat"
        },
        {
          path: "settings",
          name: "settings",
          component: DashboardView,
          meta: { settingsOnly: true }
        },
        {
          path: "users",
          name: "users",
          component: UserManagementView,
          meta: { adminOnly: true }
        },
        {
          path: "roles",
          name: "roles",
          component: RoleManagementView,
          meta: { adminOnly: true }
        },
        {
          path: "models",
          name: "models",
          component: ModelManagementView,
          meta: { scope: "model" }
        },
        {
          path: "agents",
          name: "agents",
          component: AgentManagementView,
          meta: { scope: "agent" }
        },
        {
          path: "tools",
          name: "tools",
          component: ToolManagementView,
          meta: { scope: "tool" }
        },
        {
          path: "knowledge",
          name: "knowledge",
          component: KnowledgeManagementView,
          meta: { scope: "knowledge" }
        },
        {
          path: "workflows",
          name: "workflows",
          component: WorkflowManagementView,
          meta: { scope: "workflow" }
        },
        {
          path: "prompts",
          name: "prompts",
          component: PromptManagementView,
          meta: { scope: "prompt" }
        },
        {
          path: "dicts",
          name: "dicts",
          component: DictManagementView,
          meta: { scope: "dict" }
        },
        {
          path: "guardrails",
          name: "guardrails",
          component: GuardrailPolicyView,
          meta: { scope: "guardrail" }
        },
        {
          path: "chat",
          name: "chat",
          component: ConversationView,
          meta: { scope: "chat" }
        },
        {
          path: "task-operations",
          name: "task-operations",
          component: TaskOperationsView,
          meta: { scope: "chat" }
        },
        {
          path: "traces",
          name: "traces",
          component: TraceOperationsView,
          meta: { scope: "tracing" }
        },
        {
          path: "trace-compaction",
          name: "trace-compaction",
          component: CompactionOperationsView,
          meta: { scope: "tracing" }
        },
        {
          path: "capability-policy-audits",
          name: "capability-policy-audits",
          component: CapabilityPolicyAuditView,
          meta: { scope: "tracing" }
        },
        {
          path: "evals",
          name: "evals",
          component: EvalHarnessView,
          meta: { scope: "eval" }
        },
        {
          path: "sandboxes",
          name: "sandboxes",
          component: SandboxManagementView,
          meta: { scope: "sandbox" }
        },
        {
          path: "cost-governance",
          name: "cost-governance",
          component: CostGovernanceView,
          meta: { scope: "tracing" }
        },
        {
          path: "run-control",
          name: "run-control",
          component: RunControlPlaneView,
          meta: { adminOnly: true }
        },
        {
          path: "replays",
          name: "replays",
          component: ReplayManagementView,
          meta: { adminOnly: true }
        },
        {
          path: "experiment-governance",
          name: "experiment-governance",
          component: ExperimentGovernanceView,
          meta: { adminOnly: true }
        }
      ]
    }
  ]
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia);

  if (to.path === "/login") {
    if (authStore.isLoggedIn) {
      return "/";
    }
    return true;
  }

  if (!to.meta.requiresAuth) {
    return true;
  }

  if (!authStore.isLoggedIn) {
    return "/login";
  }

  if (!authStore.currentUser) {
    try {
      await authStore.loadCurrentUser();
    } catch (error) {
      authStore.logout();
      ElMessage.error("登录状态已失效，请重新登录");
      return "/login";
    }
  }

  const currentUser = authStore.currentUser;
  const isAdmin = currentUser?.roles.some((role) =>
    ["ADMIN", "SUPER_ADMIN"].includes(role.roleCode.toUpperCase())) ?? false;
  const canAccessSettings = isAdmin || (currentUser?.menuScopes.some((scope) => scope !== "chat") ?? false);
  if (to.meta.settingsOnly && !canAccessSettings) {
    ElMessage.error("当前账号无权访问管理后台");
    return "/chat";
  }
  if (to.meta.adminOnly && !isAdmin) {
    ElMessage.error("当前账号无权访问该页面");
    return "/";
  }
  const requiredScope = typeof to.meta.scope === "string" ? to.meta.scope : null;
  if (requiredScope && !isAdmin && !currentUser?.menuScopes.includes(requiredScope)) {
    ElMessage.error("当前账号无权访问该页面");
    return "/";
  }

  return true;
});

export default router;

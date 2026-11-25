<template>
  <section class="dashboard-page">
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>当前用户</template>
          <div class="dashboard-text">用户名：{{ authStore.currentUser?.username ?? "-" }}</div>
          <div class="dashboard-text">显示名：{{ authStore.currentUser?.displayName ?? "-" }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>菜单范围</template>
          <div class="dashboard-tags">
            <el-tag v-for="scope in authStore.currentUser?.menuScopes ?? []" :key="scope" round>
              {{ scope }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="dashboard-card">
          <template #header>平台状态</template>
          <div class="dashboard-text">当前已切换为平台化基线，不再内置演示 Agent / Prompt / Tool 快捷入口。</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-row">
      <el-col :span="12">
        <el-card shadow="never" class="dashboard-card dashboard-card--guide">
          <template #header>建议先配置</template>
          <ol class="dashboard-list">
            <li>模型：至少启用 1 个可运行模型。</li>
            <li>Prompt：配置 `agent_system`、`decision_router`、`planner_system`。</li>
            <li>Tool：先注册基础通用 Tool，再为 Agent 绑定可用范围。</li>
            <li>Workflow：按真实业务流程逐步补充，不再依赖演示流。</li>
          </ol>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="dashboard-card dashboard-card--guide">
          <template #header>执行链路</template>
          <div class="dashboard-text">
            当前主干为：
            <code>ExecutionContext -> ExecutionDecision -> ExecutionPlan -> Workflow / Tool / Reply -> TaskStep</code>
          </div>
          <div class="dashboard-text">
            已支持 Planner、条件步骤、审批挂起、计划快照和恢复执行，后续建议优先补基础 Tool 与业务 Workflow。
          </div>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dashboard-row {
  margin-top: 0;
}

.dashboard-card {
  border-radius: 18px;
}

.dashboard-card--guide {
  min-height: 220px;
}

.dashboard-text {
  color: var(--el-text-color-regular);
  line-height: 1.75;
}

.dashboard-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dashboard-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  line-height: 1.9;
}

code {
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
}
</style>

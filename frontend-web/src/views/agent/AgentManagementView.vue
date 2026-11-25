<template>
  <section class="agent-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Agent 编码">
          <el-input v-model="queryForm.agentCode" placeholder="请输入 Agent 编码" clearable />
        </el-form-item>
        <el-form-item label="Agent 名称">
          <el-input v-model="queryForm.agentName" placeholder="请输入 Agent 名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable style="width: 180px;">
            <el-option label="草稿" value="draft" />
            <el-option label="启用" value="enabled" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="queryForm.version" placeholder="请输入版本" clearable />
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadAgents">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建 Agent</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Agent 管理</div>
            <div class="card-subtitle">支持默认 Tool 集继承、自定义 Tool 集和继承后追加行业 Tool 的三态模型。</div>
          </div>
        </div>
      </template>

      <el-table :data="agentRecords" border stripe v-loading="loading">
        <el-table-column prop="agentName" label="Agent 名称" min-width="180" />
        <el-table-column prop="agentCode" label="Agent 编码" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220">
          <template #default="{ row }">
            {{ row.description || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" min-width="110" />
        <el-table-column prop="defaultModel" label="默认模型" min-width="160">
          <template #default="{ row }">
            {{ formatModelCode(row.defaultModel) }}
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
        <el-table-column label="操作" min-width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.agentId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.agentId)">编辑</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="toggleAgentStatus(row)"
            >
              {{ row.status === "enabled" ? "停用" : "启用" }}
            </el-button>
            <el-button link type="primary" @click="goConversation(row.agentId)">进入会话</el-button>
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

    <el-dialog v-model="editDialogVisible" :title="editingAgentId ? '编辑 Agent' : '新建 Agent'" width="720px">
      <el-form :model="editForm" label-width="104px">
        <el-form-item :label="editingAgentId ? 'JSON 回填' : 'JSON 导入'">
          <div class="json-import-panel">
            <el-input
              v-model="editForm.importJsonText"
              type="textarea"
              :rows="8"
              placeholder="请输入完整 Agent JSON，点击“自动填充”后回填到下方表单"
            />
            <div class="json-import-actions">
              <el-button type="primary" plain @click="fillFormByImportJson">自动填充</el-button>
              <el-button @click="editForm.importJsonText = ''">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Agent 编码">
          <el-input v-model="editForm.agentCode" placeholder="请输入 Agent 编码" />
        </el-form-item>
        <el-form-item label="Agent 名称">
          <el-input v-model="editForm.agentName" placeholder="请输入 Agent 名称" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="editForm.version" placeholder="请输入版本" />
        </el-form-item>
        <el-form-item label="默认模型">
          <el-select
            v-model="editForm.defaultModel"
            placeholder="请选择默认模型"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in modelOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
              :disabled="option.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="默认 Prompt">
          <el-select
            v-model="editForm.defaultPromptId"
            placeholder="请选择默认 Prompt"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in defaultPromptOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="决策 Prompt">
          <el-select
            v-model="editForm.decisionPromptId"
            placeholder="请选择决策 Prompt"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in decisionPromptOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="form-tip">
            用于执行前路由决策，决定 direct reply、Tool 和 Workflow 的选择。
          </div>
        </el-form-item>
        <el-form-item label="规划 Prompt">
          <el-select
            v-model="editForm.plannerPromptId"
            placeholder="请选择规划 Prompt"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in plannerPromptOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="form-tip">
            用于 Planner 阶段拆解任务并生成结构化执行步骤。
          </div>
        </el-form-item>
        <el-form-item label="Tool 范围">
          <el-select
            v-model="editForm.toolScopeMode"
            placeholder="请选择 Tool 范围模式"
            style="width: 100%;"
            :disabled="Boolean(editingAgentId)"
          >
            <el-option
              v-for="option in getDictOptions('agent_tool_scope_mode')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
          <div v-if="editingAgentId" class="form-tip">
            已有 Agent 的 Tool 范围请在详情抽屉中单独维护。
          </div>
        </el-form-item>
        <el-divider content-position="left">运行时策略</el-divider>
        <el-form-item label="默认执行模式">
          <el-select v-model="editForm.defaultExecutionMode" style="width: 100%;">
            <el-option label="确定性 Workflow Plan" value="workflow_plan" />
            <el-option label="动态 Interactive Loop（暂未开放）" value="interactive_loop" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许执行模式">
          <el-checkbox-group v-model="editForm.allowedExecutionModes">
            <el-checkbox value="workflow_plan" disabled>workflow_plan</el-checkbox>
            <el-checkbox value="interactive_loop">interactive_loop</el-checkbox>
          </el-checkbox-group>
          <div class="form-tip">
            workflow_plan 是强制安全回退路径；interactive_loop 当前只冻结配置与路由契约，运行时仍会回退到 workflow_plan。
          </div>
        </el-form-item>
        <el-form-item label="Decision 模式">
          <el-select v-model="editForm.decisionMode" style="width: 100%;">
            <el-option label="LLM 优先（兼容）" value="llm_first" />
            <el-option label="规则优先（推荐）" value="rule_first" />
            <el-option label="仅规则" value="rules_only" />
          </el-select>
          <div class="form-tip">规则优先仅在明确命中资源时跳过 Decision LLM，未命中仍由 LLM 判断。</div>
        </el-form-item>
        <el-form-item label="Planner 模式">
          <el-select v-model="editForm.planningMode" style="width: 100%;">
            <el-option label="自适应规划（推荐）" value="adaptive" />
            <el-option label="始终规划" value="always_plan" />
          </el-select>
        </el-form-item>
        <el-form-item label="直接回复快速路径">
          <el-switch v-model="editForm.skipPlannerForDirectReply" />
        </el-form-item>
        <el-form-item label="单 Workflow 快速路径">
          <el-switch v-model="editForm.skipPlannerForSingleWorkflow" />
          <div class="form-tip">仅对声明 outputMode=user_reply 的单 Workflow 生效。</div>
        </el-form-item>
        <el-form-item label="单次运行 LLM 上限">
          <el-input-number v-model="editForm.maxLlmCallsPerRun" :min="1" :max="50" style="width: 100%;" />
        </el-form-item>
        <el-divider content-position="left">Interactive Loop 硬预算</el-divider>
        <el-alert
          title="当前仅配置并校验安全预算，真实 Interactive Loop 将在下一切片开放。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-form-item label="最大迭代数">
          <el-input-number v-model="editForm.interactiveMaxIterations" :min="1" :max="50" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Loop LLM 上限">
          <el-input-number v-model="editForm.interactiveMaxLlmCalls" :min="1" :max="editForm.maxLlmCallsPerRun" style="width: 100%;" />
          <div class="form-tip">不得超过上方单次运行 LLM 上限。</div>
        </el-form-item>
        <el-form-item label="最大 Tool 调用数">
          <el-input-number v-model="editForm.interactiveMaxToolCalls" :min="1" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="editForm.interactiveMaxTokens" :min="1" :max="2000000" :step="1000" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="最大成本">
          <el-input-number v-model="editForm.interactiveMaxCostAmount" :min="0" :max="100000" :precision="2" :step="0.5" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="重复调用上限">
          <el-input-number v-model="editForm.duplicateToolCallLimit" :min="1" :max="10" style="width: 100%;" />
          <div class="form-tip">同一 Tool 与参数哈希达到上限后停止，避免循环调用。</div>
        </el-form-item>
        <el-form-item label="Decision 模型">
          <el-select v-model="editForm.decisionModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="Planner 模型">
          <el-select v-model="editForm.planningModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="Reply 模型">
          <el-select v-model="editForm.replyModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="Reflection 模型">
          <el-select v-model="editForm.reflectionModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="Memory 模型">
          <el-select v-model="editForm.memoryModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="Delegation 模型">
          <el-select v-model="editForm.delegationModel" clearable filterable style="width: 100%;" placeholder="默认模型">
            <el-option v-for="option in modelOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
          <div class="form-tip">留空时使用 Agent 默认模型；所有阶段共享单次运行 LLM 调用预算。</div>
        </el-form-item>
        <el-form-item label="自动 Replan">
          <el-switch v-model="editForm.autoReplanEnabled" />
        </el-form-item>
        <el-form-item label="最大 Replan 次数">
          <el-input-number v-model="editForm.maxReplanTimes" :min="0" :max="20" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Replan 熔断阈值">
          <el-input-number v-model="editForm.replanCircuitBreakerThreshold" :min="0" :max="20" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Replan 最低质量">
          <el-input-number v-model="editForm.minimumReplanQualityScore" :min="0" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Decision 失败策略">
          <el-select v-model="editForm.decisionLlmFailureAction" style="width: 100%;">
            <el-option label="回退规则决策" value="rule_fallback" />
            <el-option label="降级直接回复" value="direct_reply" />
            <el-option label="直接失败" value="fail_fast" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用并行执行">
          <el-switch v-model="editForm.enableParallel" />
        </el-form-item>
        <el-form-item label="启用反思">
          <el-switch v-model="editForm.enableReflection" />
        </el-form-item>
        <el-form-item label="启用委派">
          <el-switch v-model="editForm.enableDelegation" />
        </el-form-item>
        <template v-if="editForm.enableDelegation">
          <el-form-item label="委派最大深度"><el-input-number v-model="editForm.delegationMaxDepth" :min="1" :max="10" style="width: 100%;" /></el-form-item>
          <el-form-item label="子任务数上限"><el-input-number v-model="editForm.delegationMaxChildren" :min="1" :max="50" style="width: 100%;" /></el-form-item>
          <el-form-item label="委派超时秒数"><el-input-number v-model="editForm.delegationTimeoutSeconds" :min="5" :max="3600" style="width: 100%;" /></el-form-item>
          <el-form-item label="允许 Agent 回环"><el-switch v-model="editForm.allowAgentReentry" /></el-form-item>
        </template>
        <el-form-item label="缺信息先澄清">
          <el-switch v-model="editForm.clarifyOnMissing" />
        </el-form-item>
        <el-form-item label="记忆策略">
          <el-select v-model="editForm.memoryStrategy" style="width: 100%;" placeholder="请选择记忆策略">
            <el-option label="自动选择" value="auto" />
            <el-option label="仅会话记忆" value="session_only" />
            <el-option label="优先用户记忆" value="user_first" />
            <el-option label="禁用记忆写入" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户历史记忆">
          <el-switch v-model="editForm.userMemoryEnabled" />
        </el-form-item>
        <el-form-item label="记忆注入模式">
          <el-select v-model="editForm.userMemoryMode" style="width: 100%;" placeholder="请选择记忆注入模式">
            <el-option label="关闭（off）" value="off" />
            <el-option label="仅摘要（summary_only）" value="summary_only" />
            <el-option label="摘要 + 长期事实（summary_and_facts）" value="summary_and_facts" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许记忆写回">
          <el-switch v-model="editForm.userMemoryWritebackEnabled" />
        </el-form-item>
        <el-form-item label="偏好 TTL（天）"><el-input-number v-model="editForm.preferenceTtlDays" :min="0" :max="3650" style="width: 100%;" /></el-form-item>
        <el-form-item label="关注 TTL（天）"><el-input-number v-model="editForm.attentionTtlDays" :min="0" :max="3650" style="width: 100%;" /></el-form-item>
        <el-form-item label="事实 TTL（天）"><el-input-number v-model="editForm.factTtlDays" :min="0" :max="3650" style="width: 100%;" /></el-form-item>
        <el-form-item label="目标 TTL（天）">
          <el-input-number v-model="editForm.goalTtlDays" :min="0" :max="3650" style="width: 100%;" />
          <div class="form-tip">0 表示不过期。</div>
        </el-form-item>
        <el-divider content-position="left">上下文压缩策略</el-divider>
        <el-form-item label="优先摘要历史">
          <el-switch v-model="editForm.preferSummaryOverRawHistory" />
        </el-form-item>
        <el-form-item label="最近消息窗口">
          <el-input-number v-model="editForm.maxRecentMessages" :min="1" :max="50" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="最近用户窗口">
          <el-input-number v-model="editForm.maxRecentUserMessages" :min="1" :max="20" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="状态优先注入">
          <el-switch v-model="editForm.preferStateFirst" />
        </el-form-item>
        <el-form-item label="摘要触发阈值">
          <el-input-number v-model="editForm.historySummaryThresholdMessages" :min="1" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="最低保留比例">
          <el-input-number v-model="editForm.minRetentionRatio" :min="0.1" :max="0.9" :step="0.05" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="质量告警阈值">
          <el-input-number v-model="editForm.qualityWarningThreshold" :min="1" :max="100" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="Tool 失败策略">
          <el-select v-model="editForm.toolFallbackAction" style="width: 100%;" placeholder="请选择 Tool 失败策略">
            <el-option label="切换候选 Tool" value="switch_candidate" />
            <el-option label="重试当前 Tool" value="retry_same_tool" />
            <el-option label="触发 Replan" value="replan" />
            <el-option label="直接失败" value="fail_fast" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复风格">
          <el-select v-model="editForm.replyStyle" style="width: 100%;" placeholder="请选择回复风格">
            <el-option label="默认" value="default" />
            <el-option label="结构化" value="structured" />
            <el-option label="简洁" value="concise" />
            <el-option label="教学型" value="coaching" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">结果可信度策略</el-divider>
        <el-form-item label="启用可信度治理"><el-switch v-model="editForm.trustPolicyEnabled" /></el-form-item>
        <template v-if="editForm.trustPolicyEnabled">
          <el-form-item label="最低置信度"><el-input-number v-model="editForm.minimumTrustConfidence" :min="0" :max="1" :step="0.05" style="width: 100%;" /></el-form-item>
          <el-form-item label="最少证据来源"><el-input-number v-model="editForm.minimumEvidenceSources" :min="0" :max="20" style="width: 100%;" /></el-form-item>
          <el-form-item label="必须包含引用"><el-switch v-model="editForm.requireCitation" /></el-form-item>
          <el-form-item label="Claim 必须有证据"><el-switch v-model="editForm.requireClaimEvidence" /></el-form-item>
          <el-form-item label="检测冲突证据"><el-switch v-model="editForm.detectContradictions" /></el-form-item>
          <el-form-item label="最低来源权威度"><el-input-number v-model="editForm.minimumAuthorityScore" :min="0" :max="1" :step="0.05" style="width: 100%;" /></el-form-item>
          <el-form-item label="启用独立核验"><el-switch v-model="editForm.trustVerifierEnabled" /></el-form-item>
          <template v-if="editForm.trustVerifierEnabled">
            <el-form-item label="核验方式"><el-select v-model="editForm.trustVerifierMode" style="width: 100%;"><el-option label="独立 LLM 调用" value="llm" /><el-option label="Verifier Tool" value="tool" /></el-select></el-form-item>
            <el-form-item v-if="editForm.trustVerifierMode === 'tool'" label="Verifier Tool Code"><el-input v-model="editForm.trustVerifierToolCode" placeholder="例如 FACT_VERIFY" /></el-form-item>
            <el-form-item label="核验异常策略"><el-select v-model="editForm.trustVerifierErrorAction" style="width: 100%;"><el-option label="Fail Closed" value="fail_closed" /><el-option label="记录告警后继续" value="warn" /></el-select></el-form-item>
          </template>
          <el-form-item label="校验失败动作"><el-select v-model="editForm.trustFailureAction" style="width: 100%;"><el-option label="记录告警" value="warn" /><el-option label="触发 Replan" value="replan" /><el-option label="人工复核" value="approval" /><el-option label="阻断执行" value="block" /></el-select></el-form-item>
        </template>
        <el-divider content-position="left">Eval 发布门禁</el-divider>
        <el-form-item label="启用发布门禁">
          <el-switch v-model="editForm.releaseGateEnabled" />
        </el-form-item>
        <template v-if="editForm.releaseGateEnabled">
          <el-form-item label="最低平均分">
            <el-input-number v-model="editForm.minimumAverageScore" :min="0" :max="100" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="最低通过率">
            <el-input-number v-model="editForm.minimumPassRate" :min="0" :max="100" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="最少用例数">
            <el-input-number v-model="editForm.minimumEvalCases" :min="1" :max="100000" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="报告有效期">
            <el-input-number v-model="editForm.maximumEvalAgeHours" :min="1" :max="8760" style="width: 100%;" />
            <div class="form-tip">单位：小时。Core 不可达、无报告或报告过期时禁止启用 Agent。</div>
          </el-form-item>
        </template>
        <el-form-item label="高级 Metadata">
          <el-input
            v-model="editForm.metadataText"
            type="textarea"
            :rows="6"
            placeholder="请输入合法 JSON，例如 {}"
          />
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
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEditForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="Agent 详情" size="560px">
      <div v-loading="detailToolsLoading">
        <el-descriptions :column="1" border v-if="detailRecord">
          <el-descriptions-item label="Agent 编码">{{ detailRecord.agentCode }}</el-descriptions-item>
          <el-descriptions-item label="Agent 名称">{{ detailRecord.agentName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detailRecord.status) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detailRecord.version }}</el-descriptions-item>
          <el-descriptions-item label="默认模型">{{ formatModelCode(detailRecord.defaultModel) }}</el-descriptions-item>
          <el-descriptions-item label="默认 Prompt">
            {{ detailRecord.defaultPromptName || detailRecord.defaultPromptId || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="决策 Prompt">
            {{ detailRecord.decisionPromptName || detailRecord.decisionPromptId || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="规划 Prompt">
            {{ detailRecord.plannerPromptName || detailRecord.plannerPromptId || "-" }}
          </el-descriptions-item>
          <el-descriptions-item label="Tool 范围">
            {{ dictLabel("agent_tool_scope_mode", detailToolScope?.toolScopeMode || detailRecord.toolScopeMode || "") }}
          </el-descriptions-item>
          <el-descriptions-item label="运行时策略摘要">
            <pre class="json-block">{{ buildAgentExecutionSummary(detailRecord, detailToolScope, detailWorkflowScope) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="运行时配置">
            <pre class="json-block">{{ formatJson(detailRecord.metadata) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item label="已配置 Workflow">
            <div class="scope-tags">
              <template v-if="detailWorkflowScope?.workflows?.length">
                <el-tag v-for="workflow in detailWorkflowScope.workflows" :key="workflow.workflowId" round type="warning">
                  {{ workflow.workflowName }}（{{ workflow.workflowCode }}）
                </el-tag>
              </template>
              <span v-else>当前未配置 Workflow</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="已配置 Tool">
            <div class="scope-tags">
              <template v-if="detailToolScope?.tools?.length">
                <el-tag v-for="tool in detailToolScope.tools" :key="tool.toolId" round>
                  {{ tool.toolName }}（{{ tool.toolCode }}）
                </el-tag>
              </template>
              <span v-else>{{ emptyToolScopeText(detailToolScope?.toolScopeMode || detailRecord.toolScopeMode || "") }}</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="描述">{{ detailRecord.description || "-" }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailRecord.remark || "-" }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无详情数据" />
      </div>
      <div class="drawer-footer">
        <el-button type="warning" plain :disabled="!detailRecord" @click="openWorkflowScopeDialog(detailRecord?.agentId ?? 0)">
          维护 Workflow 范围
        </el-button>
        <el-button type="primary" plain :disabled="!detailRecord" @click="openToolScopeDialog(detailRecord?.agentId ?? 0)">
          维护 Tool 范围
        </el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="toolScopeDialogVisible" title="维护 Tool 范围" width="800px">
      <div class="tool-scope-dialog-body" v-loading="toolScopeLoading">
        <el-alert
          title="保存时会按当前模式重建该 Agent 的 Tool 范围。继承默认不保留自定义绑定，继承后追加和完全自定义会写入自定义 Tool 关系。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-form :model="toolScopeForm" label-width="104px">
          <el-form-item label="Tool 范围模式">
            <el-radio-group v-model="toolScopeForm.toolScopeMode" class="tool-scope-mode-group">
              <el-radio
                v-for="option in getDictOptions('agent_tool_scope_mode')"
                :key="option.itemCode"
                :value="option.itemCode"
              >
                {{ option.itemName }}
              </el-radio>
            </el-radio-group>
            <div class="form-tip tool-scope-mode-tip">
              {{ toolScopeModeHint(toolScopeForm.toolScopeMode) }}
            </div>
          </el-form-item>
          <el-form-item label="能力包">
            <el-select v-model="selectedCapabilityPackIds" multiple filterable collapse-tags collapse-tags-tooltip style="width: 100%;" placeholder="选择可复用 Tool Capability Pack">
              <el-option
                v-for="pack in capabilityPackOptions"
                :key="pack.id"
                :label="`${pack.pack_name} (${pack.tool_count || 0} Tools)`"
                :value="pack.id"
              />
            </el-select>
            <div class="form-tip">能力包中的 Tool 会与当前范围模式计算出的 Tool 合并并自动去重。</div>
          </el-form-item>
        </el-form>
        <div v-if="toolScopeForm.toolScopeMode === 'inherit_default'" class="tool-scope-placeholder">
          <el-empty description="当前将完全继承默认 Tool 范围，无需单独勾选 Tool" :image-size="96" />
        </div>
        <template v-else>
          <div class="tool-scope-panel">
            <div class="tool-scope-toolbar">
              <div>
                <div class="tool-scope-title">可选 Tool 列表</div>
                <div class="tool-scope-count">已选 {{ selectedToolIds.length }} 个 Tool</div>
              </div>
              <el-button link type="primary" @click="selectedToolIds = []">清空</el-button>
            </div>
            <div class="tool-scope-list">
              <div class="tool-option-list">
                <div
                  v-for="tool in toolOptions"
                  :key="tool.toolId"
                  class="tool-option-row"
                  :class="{ 'tool-option-row--active': selectedToolIds.includes(tool.toolId) }"
                >
                  <div class="tool-option-check">
                    <el-checkbox
                      :model-value="selectedToolIds.includes(tool.toolId)"
                      @change="toggleToolSelection(tool.toolId, $event)"
                    />
                  </div>
                  <div class="tool-option-card" @click="toggleToolSelection(tool.toolId, !selectedToolIds.includes(tool.toolId))">
                    <div class="tool-option-header">
                      <div class="tool-option-title">
                        <span class="tool-option-name">{{ tool.toolName }}</span>
                        <span class="tool-option-code">{{ tool.toolCode }}</span>
                        <el-tag v-if="tool.toolType" size="small" effect="plain" type="primary" round>
                          {{ dictLabel("tool_type", tool.toolType) }}
                        </el-tag>
                        <el-tag v-if="tool.toolCategory" size="small" effect="light" type="success" round>
                          {{ dictLabel("tool_category", tool.toolCategory) }}
                        </el-tag>
                      </div>
                      <el-tag size="small" :type="statusTagType(tool.status)">
                        {{ statusText(tool.status) }}
                      </el-tag>
                    </div>
                    <div class="tool-option-meta">
                      <span>{{ dictLabel("permission_level", tool.permissionLevel) }}</span>
                      <span>{{ tool.timeoutSeconds ? `${tool.timeoutSeconds}s` : "-" }}</span>
                    </div>
                    <div class="tool-option-desc">{{ tool.description || "暂无描述" }}</div>
                  </div>
                </div>
              </div>
              <el-empty v-if="toolOptions.length === 0" description="暂无可选 Tool" :image-size="96" />
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="toolScopeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="toolScopeSaving" @click="submitToolScope">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="workflowScopeDialogVisible" title="维护 Workflow 范围" width="720px">
      <div class="tool-scope-dialog-body" v-loading="workflowScopeLoading">
        <el-alert
          title="当前 Agent 不再设置默认 Workflow，只维护可用 Workflow 集合；实际使用哪个 Workflow 由任务执行时动态决策。"
          type="warning"
          show-icon
          :closable="false"
        />
        <div class="tool-scope-panel">
          <div class="tool-scope-toolbar">
            <div>
              <div class="tool-scope-title">可选 Workflow 列表</div>
              <div class="tool-scope-count">已选 {{ selectedWorkflowIds.length }} 个 Workflow</div>
            </div>
            <el-button link type="primary" @click="selectedWorkflowIds = []">清空</el-button>
          </div>
          <div class="tool-scope-list">
            <div class="tool-option-list">
              <div
                v-for="workflow in workflowOptions"
                :key="workflow.workflowId"
                class="tool-option-row"
                :class="{ 'tool-option-row--active': selectedWorkflowIds.includes(workflow.workflowId) }"
              >
                <div class="tool-option-check">
                  <el-checkbox
                    :model-value="selectedWorkflowIds.includes(workflow.workflowId)"
                    @change="toggleWorkflowSelection(workflow.workflowId, $event)"
                  />
                </div>
                <div
                  class="tool-option-card"
                  @click="toggleWorkflowSelection(workflow.workflowId, !selectedWorkflowIds.includes(workflow.workflowId))"
                >
                  <div class="tool-option-header">
                    <div class="tool-option-title">
                      <span class="tool-option-name">{{ workflow.workflowName }}</span>
                      <span class="tool-option-code">{{ workflow.workflowCode }}</span>
                      <el-tag v-if="workflow.workflowType" size="small" effect="plain" type="warning" round>
                        {{ workflow.workflowType }}
                      </el-tag>
                      <el-tag v-if="workflow.workflowCategory" size="small" effect="light" type="success" round>
                        {{ workflow.workflowCategory }}
                      </el-tag>
                    </div>
                    <el-tag size="small" :type="statusTagType(workflow.status)">
                      {{ statusText(workflow.status) }}
                    </el-tag>
                  </div>
                  <div class="tool-option-desc">{{ workflow.description || "暂无描述" }}</div>
                </div>
              </div>
            </div>
            <el-empty v-if="workflowOptions.length === 0" description="暂无可选 Workflow" :image-size="96" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="workflowScopeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="workflowScopeSaving" @click="submitWorkflowScope">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import { fetchDictOptions, type DictOptionItem } from "@/api/dict";
import { fetchModelPage } from "@/api/model";
import { fetchPromptDetail, fetchPromptPage } from "@/api/prompt";
import {
  createAgent,
  disableAgent,
  enableAgent,
  fetchAgentDetail,
  fetchAgentToolScope,
  fetchAgentToolCapabilityPacks,
  fetchAgentWorkflowScope,
  fetchAgentPage,
  type AgentToolScope,
  type AgentToolCapabilityPackScope,
  type AgentWorkflowScope,
  updateAgent,
  updateAgentToolScope,
  updateAgentToolCapabilityPacks,
  updateAgentWorkflowScope,
  type AgentDetail,
  type AgentRecord,
  type ToolOption,
  type WorkflowOption
} from "@/api/agent";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  agentCode: string;
  agentName: string;
  status: string;
  version: string;
}

interface EditForm {
  importJsonText: string;
  agentCode: string;
  agentName: string;
  description: string;
  version: string;
  defaultModel: string;
  defaultPromptId: number | null;
  decisionPromptId: number | null;
  plannerPromptId: number | null;
  toolScopeMode: string;
  defaultExecutionMode: string;
  allowedExecutionModes: string[];
  decisionMode: string;
  planningMode: string;
  skipPlannerForDirectReply: boolean;
  skipPlannerForSingleWorkflow: boolean;
  maxLlmCallsPerRun: number;
  interactiveMaxIterations: number;
  interactiveMaxLlmCalls: number;
  interactiveMaxToolCalls: number;
  interactiveMaxTokens: number;
  interactiveMaxCostAmount: number;
  duplicateToolCallLimit: number;
  decisionModel: string;
  planningModel: string;
  replyModel: string;
  reflectionModel: string;
  memoryModel: string;
  delegationModel: string;
  autoReplanEnabled: boolean;
  maxReplanTimes: number;
  replanCircuitBreakerThreshold: number;
  minimumReplanQualityScore: number;
  decisionLlmFailureAction: string;
  enableParallel: boolean;
  enableReflection: boolean;
  enableDelegation: boolean;
  delegationMaxDepth: number;
  delegationMaxChildren: number;
  delegationTimeoutSeconds: number;
  allowAgentReentry: boolean;
  clarifyOnMissing: boolean;
  memoryStrategy: string;
  userMemoryEnabled: boolean;
  userMemoryMode: string;
  userMemoryWritebackEnabled: boolean;
  preferenceTtlDays: number;
  attentionTtlDays: number;
  factTtlDays: number;
  goalTtlDays: number;
  preferSummaryOverRawHistory: boolean;
  maxRecentMessages: number;
  maxRecentUserMessages: number;
  preferStateFirst: boolean;
  historySummaryThresholdMessages: number;
  minRetentionRatio: number;
  qualityWarningThreshold: number;
  toolFallbackAction: string;
  replyStyle: string;
  trustPolicyEnabled: boolean;
  minimumTrustConfidence: number;
  minimumEvidenceSources: number;
  requireCitation: boolean;
  requireClaimEvidence: boolean;
  detectContradictions: boolean;
  minimumAuthorityScore: number;
  trustVerifierEnabled: boolean;
  trustVerifierMode: string;
  trustVerifierToolCode: string;
  trustVerifierErrorAction: string;
  trustFailureAction: string;
  releaseGateEnabled: boolean;
  minimumAverageScore: number;
  minimumPassRate: number;
  minimumEvalCases: number;
  maximumEvalAgeHours: number;
  metadataText: string;
  status: string;
  remark: string;
}

interface ToolScopeForm {
  toolScopeMode: string;
}

interface ResourceOption {
  value: number;
  label: string;
}

interface ModelOption {
  value: string;
  label: string;
  disabled: boolean;
}

type JsonObject = Record<string, unknown>;

const loading = ref(false);
const total = ref(0);
const agentRecords = ref<AgentRecord[]>([]);
const detailRecord = ref<AgentDetail | null>(null);
const detailToolScope = ref<AgentToolScope | null>(null);
const detailWorkflowScope = ref<AgentWorkflowScope | null>(null);
const detailToolsLoading = ref(false);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingAgentId = ref<number | null>(null);
const saving = ref(false);
const toolScopeDialogVisible = ref(false);
const toolScopeLoading = ref(false);
const toolScopeSaving = ref(false);
const toolScopeAgentId = ref<number | null>(null);
const workflowScopeDialogVisible = ref(false);
const workflowScopeLoading = ref(false);
const workflowScopeSaving = ref(false);
const workflowScopeAgentId = ref<number | null>(null);
const toolOptions = ref<ToolOption[]>([]);
const workflowOptions = ref<WorkflowOption[]>([]);
const selectedToolIds = ref<number[]>([]);
const selectedCapabilityPackIds = ref<number[]>([]);
const capabilityPackOptions = ref<AgentToolCapabilityPackScope["availablePacks"]>([]);
const selectedWorkflowIds = ref<number[]>([]);
const dictOptionMap = ref<Record<string, DictOptionItem[]>>({});
const defaultPromptOptions = ref<ResourceOption[]>([]);
const decisionPromptOptions = ref<ResourceOption[]>([]);
const plannerPromptOptions = ref<ResourceOption[]>([]);
const modelOptions = ref<ModelOption[]>([]);
const router = useRouter();

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  agentCode: "",
  agentName: "",
  status: "",
  version: ""
});

const editForm = reactive<EditForm>({
  importJsonText: "",
  agentCode: "",
  agentName: "",
  description: "",
  version: "v1.0",
  defaultModel: "",
  defaultPromptId: null,
  decisionPromptId: null,
  plannerPromptId: null,
  toolScopeMode: "inherit_default",
  defaultExecutionMode: "workflow_plan",
  allowedExecutionModes: ["workflow_plan"],
  decisionMode: "llm_first",
  planningMode: "adaptive",
  skipPlannerForDirectReply: true,
  skipPlannerForSingleWorkflow: true,
  maxLlmCallsPerRun: 6,
  interactiveMaxIterations: 12,
  interactiveMaxLlmCalls: 6,
  interactiveMaxToolCalls: 16,
  interactiveMaxTokens: 50000,
  interactiveMaxCostAmount: 5,
  duplicateToolCallLimit: 2,
  decisionModel: "",
  planningModel: "",
  replyModel: "",
  reflectionModel: "",
  memoryModel: "",
  delegationModel: "",
  autoReplanEnabled: true,
  maxReplanTimes: 2,
  replanCircuitBreakerThreshold: 3,
  minimumReplanQualityScore: 60,
  decisionLlmFailureAction: "rule_fallback",
  enableParallel: true,
  enableReflection: true,
  enableDelegation: false,
  delegationMaxDepth: 3,
  delegationMaxChildren: 5,
  delegationTimeoutSeconds: 120,
  allowAgentReentry: false,
  clarifyOnMissing: true,
  memoryStrategy: "auto",
  userMemoryEnabled: true,
  userMemoryMode: "summary_only",
  userMemoryWritebackEnabled: true,
  preferenceTtlDays: 180,
  attentionTtlDays: 30,
  factTtlDays: 180,
  goalTtlDays: 180,
  preferSummaryOverRawHistory: true,
  maxRecentMessages: 6,
  maxRecentUserMessages: 4,
  preferStateFirst: true,
  historySummaryThresholdMessages: 6,
  minRetentionRatio: 0.18,
  qualityWarningThreshold: 75,
  toolFallbackAction: "switch_candidate",
  replyStyle: "default",
  trustPolicyEnabled: false,
  minimumTrustConfidence: 0.65,
  minimumEvidenceSources: 1,
  requireCitation: false,
  requireClaimEvidence: false,
  detectContradictions: false,
  minimumAuthorityScore: 0,
  trustVerifierEnabled: false,
  trustVerifierMode: "llm",
  trustVerifierToolCode: "",
  trustVerifierErrorAction: "fail_closed",
  trustFailureAction: "warn",
  releaseGateEnabled: false,
  minimumAverageScore: 80,
  minimumPassRate: 90,
  minimumEvalCases: 1,
  maximumEvalAgeHours: 168,
  metadataText: "{}",
  status: "draft",
  remark: ""
});

const toolScopeForm = reactive<ToolScopeForm>({
  toolScopeMode: "inherit_default"
});

async function loadAgents(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchAgentPage(queryForm);
    agentRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    ElMessage.error("Agent 列表加载失败");
    agentRecords.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadDictOptionMap(): Promise<void> {
  try {
    dictOptionMap.value = await fetchDictOptions([
      "tool_type",
      "tool_category",
      "permission_level",
      "agent_tool_scope_mode",
      "model_provider",
      "model_type",
      "prompt_type",
      "prompt_category"
    ]) || {};
  } catch (error) {
    dictOptionMap.value = {};
    ElMessage.error("字典选项加载失败");
  }
}

async function loadPromptOptionsByType(promptType: string): Promise<ResourceOption[]> {
  try {
    const page = await fetchPromptPage({
      pageNum: 1,
      pageSize: 100,
      promptType,
      status: "enabled"
    });
    return page.records.map((item) => ({
      value: item.promptId,
      label: formatPromptOptionLabel(item.promptName, item.promptCode, item.promptType, item.promptCategory)
    }));
  } catch (error) {
    ElMessage.error("Prompt 下拉选项加载失败");
    return [];
  }
}

async function loadAgentPromptOptions(): Promise<void> {
  const [defaultOptions, decisionOptions, plannerOptions] = await Promise.all([
    loadPromptOptionsByType("agent_system"),
    loadPromptOptionsByType("decision_router"),
    loadPromptOptionsByType("planner_system")
  ]);
  defaultPromptOptions.value = defaultOptions;
  decisionPromptOptions.value = decisionOptions;
  plannerPromptOptions.value = plannerOptions;
}

async function loadModelOptions(): Promise<void> {
  try {
    const page = await fetchModelPage({
      pageNum: 1,
      pageSize: 100
    });
    modelOptions.value = page.records.map((item) => ({
      value: item.modelCode,
      label: `${item.modelName}（${item.modelCode}） - ${dictLabel("model_provider", item.provider)} - ${statusText(item.status)}`,
      disabled: item.status !== "enabled"
    }));
  } catch (error) {
    modelOptions.value = [];
    ElMessage.error("模型下拉选项加载失败");
  }
}

async function ensureModelOption(modelCode: string | null): Promise<void> {
  if (!modelCode || modelOptions.value.some((item) => item.value === modelCode)) {
    return;
  }
  modelOptions.value = [
    ...modelOptions.value,
    {
      value: modelCode,
      label: `${modelCode}（已失效或未加载）`,
      disabled: true
    }
  ];
}

async function ensurePromptOption(promptId: number | null, optionsRef: typeof defaultPromptOptions): Promise<void> {
  if (!promptId || optionsRef.value.some((item) => item.value === promptId)) {
    return;
  }
  const detail = await fetchPromptDetail(promptId);
  optionsRef.value = [
    ...optionsRef.value,
    {
      value: detail.promptId,
      label: formatPromptOptionLabel(detail.promptName, detail.promptCode, detail.promptType, detail.promptCategory)
    }
  ];
}

async function openDetailDrawer(agentId: number): Promise<void> {
  detailDrawerVisible.value = true;
  detailToolScope.value = null;
  detailWorkflowScope.value = null;
  try {
    detailRecord.value = await fetchAgentDetail(agentId);
    await Promise.all([loadDetailToolScope(agentId), loadDetailWorkflowScope(agentId)]);
  } catch (error) {
    detailRecord.value = null;
    detailToolScope.value = null;
    detailWorkflowScope.value = null;
    ElMessage.error("Agent 详情加载失败");
  }
}

async function openEditDialog(agentId: number): Promise<void> {
  editingAgentId.value = agentId;
  editDialogVisible.value = true;
  try {
    const [detail] = await Promise.all([
      fetchAgentDetail(agentId),
      loadAgentPromptOptions(),
      loadModelOptions()
    ]);
    await Promise.all([
      ensureModelOption(detail.defaultModel),
      ensurePromptOption(detail.defaultPromptId, defaultPromptOptions),
      ensurePromptOption(detail.decisionPromptId, decisionPromptOptions),
      ensurePromptOption(detail.plannerPromptId, plannerPromptOptions)
    ]);
    const metadata = asObject(detail.metadata);
    const runtimeStrategy = extractAgentRuntimeStrategy(metadata);
    await Promise.all([
      ensureModelOption(runtimeStrategy.decisionModel || null),
      ensureModelOption(runtimeStrategy.planningModel || null),
      ensureModelOption(runtimeStrategy.replyModel || null),
      ensureModelOption(runtimeStrategy.reflectionModel || null),
      ensureModelOption(runtimeStrategy.memoryModel || null),
      ensureModelOption(runtimeStrategy.delegationModel || null)
    ]);
    const releaseGate = extractReleaseGate(metadata);
    const trustPolicy = extractTrustPolicy(metadata);
    editForm.importJsonText = "";
    editForm.agentCode = detail.agentCode;
    editForm.agentName = detail.agentName;
    editForm.description = detail.description || "";
    editForm.version = detail.version;
    editForm.defaultModel = detail.defaultModel || "";
    editForm.defaultPromptId = detail.defaultPromptId;
    editForm.decisionPromptId = detail.decisionPromptId;
    editForm.plannerPromptId = detail.plannerPromptId;
    editForm.toolScopeMode = detail.toolScopeMode || "inherit_default";
    editForm.defaultExecutionMode = runtimeStrategy.defaultExecutionMode;
    editForm.allowedExecutionModes = runtimeStrategy.allowedExecutionModes;
    editForm.decisionMode = runtimeStrategy.decisionMode;
    editForm.planningMode = runtimeStrategy.planningMode;
    editForm.skipPlannerForDirectReply = runtimeStrategy.skipPlannerForDirectReply;
    editForm.skipPlannerForSingleWorkflow = runtimeStrategy.skipPlannerForSingleWorkflow;
    editForm.maxLlmCallsPerRun = runtimeStrategy.maxLlmCallsPerRun;
    editForm.interactiveMaxIterations = runtimeStrategy.interactiveMaxIterations;
    editForm.interactiveMaxLlmCalls = runtimeStrategy.interactiveMaxLlmCalls;
    editForm.interactiveMaxToolCalls = runtimeStrategy.interactiveMaxToolCalls;
    editForm.interactiveMaxTokens = runtimeStrategy.interactiveMaxTokens;
    editForm.interactiveMaxCostAmount = runtimeStrategy.interactiveMaxCostAmount;
    editForm.duplicateToolCallLimit = runtimeStrategy.duplicateToolCallLimit;
    editForm.decisionModel = runtimeStrategy.decisionModel;
    editForm.planningModel = runtimeStrategy.planningModel;
    editForm.replyModel = runtimeStrategy.replyModel;
    editForm.reflectionModel = runtimeStrategy.reflectionModel;
    editForm.memoryModel = runtimeStrategy.memoryModel;
    editForm.delegationModel = runtimeStrategy.delegationModel;
    editForm.autoReplanEnabled = runtimeStrategy.autoReplanEnabled;
    editForm.maxReplanTimes = runtimeStrategy.maxReplanTimes;
    editForm.replanCircuitBreakerThreshold = runtimeStrategy.replanCircuitBreakerThreshold;
    editForm.minimumReplanQualityScore = runtimeStrategy.minimumReplanQualityScore;
    editForm.decisionLlmFailureAction = runtimeStrategy.decisionLlmFailureAction;
    editForm.enableParallel = runtimeStrategy.enableParallel;
    editForm.enableReflection = runtimeStrategy.enableReflection;
    editForm.enableDelegation = runtimeStrategy.enableDelegation;
    editForm.delegationMaxDepth = runtimeStrategy.delegationMaxDepth;
    editForm.delegationMaxChildren = runtimeStrategy.delegationMaxChildren;
    editForm.delegationTimeoutSeconds = runtimeStrategy.delegationTimeoutSeconds;
    editForm.allowAgentReentry = runtimeStrategy.allowAgentReentry;
    editForm.clarifyOnMissing = runtimeStrategy.clarifyOnMissing;
    editForm.memoryStrategy = runtimeStrategy.memoryStrategy;
    editForm.userMemoryEnabled = runtimeStrategy.userMemoryEnabled;
    editForm.userMemoryMode = runtimeStrategy.userMemoryMode;
    editForm.userMemoryWritebackEnabled = runtimeStrategy.userMemoryWritebackEnabled;
    editForm.preferenceTtlDays = runtimeStrategy.preferenceTtlDays;
    editForm.attentionTtlDays = runtimeStrategy.attentionTtlDays;
    editForm.factTtlDays = runtimeStrategy.factTtlDays;
    editForm.goalTtlDays = runtimeStrategy.goalTtlDays;
    editForm.preferSummaryOverRawHistory = runtimeStrategy.preferSummaryOverRawHistory;
    editForm.maxRecentMessages = runtimeStrategy.maxRecentMessages;
    editForm.maxRecentUserMessages = runtimeStrategy.maxRecentUserMessages;
    editForm.preferStateFirst = runtimeStrategy.preferStateFirst;
    editForm.historySummaryThresholdMessages = runtimeStrategy.historySummaryThresholdMessages;
    editForm.minRetentionRatio = runtimeStrategy.minRetentionRatio;
    editForm.qualityWarningThreshold = runtimeStrategy.qualityWarningThreshold;
    editForm.toolFallbackAction = runtimeStrategy.toolFallbackAction;
    editForm.replyStyle = runtimeStrategy.replyStyle;
    editForm.releaseGateEnabled = releaseGate.enabled;
    editForm.minimumAverageScore = releaseGate.minimumAverageScore;
    editForm.minimumPassRate = releaseGate.minimumPassRate;
    editForm.minimumEvalCases = releaseGate.minimumCases;
    editForm.maximumEvalAgeHours = releaseGate.maximumAgeHours;
    applyTrustPolicy(trustPolicy);
    editForm.metadataText = stringifyJson(metadata);
    editForm.status = detail.status;
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("Agent 详情加载失败");
  }
}

function openCreateDialog(): void {
  editingAgentId.value = null;
  editDialogVisible.value = true;
  void loadAgentPromptOptions();
  void loadModelOptions();
  editForm.importJsonText = "";
  editForm.agentCode = "";
  editForm.agentName = "";
  editForm.description = "";
  editForm.version = "v1.0";
  editForm.defaultModel = "";
  editForm.defaultPromptId = null;
  editForm.decisionPromptId = null;
  editForm.plannerPromptId = null;
  editForm.toolScopeMode = "inherit_default";
  editForm.defaultExecutionMode = "workflow_plan";
  editForm.allowedExecutionModes = ["workflow_plan"];
  editForm.decisionMode = "llm_first";
  editForm.planningMode = "adaptive";
  editForm.skipPlannerForDirectReply = true;
  editForm.skipPlannerForSingleWorkflow = true;
  editForm.maxLlmCallsPerRun = 6;
  editForm.interactiveMaxIterations = 12;
  editForm.interactiveMaxLlmCalls = 6;
  editForm.interactiveMaxToolCalls = 16;
  editForm.interactiveMaxTokens = 50000;
  editForm.interactiveMaxCostAmount = 5;
  editForm.duplicateToolCallLimit = 2;
  editForm.decisionModel = "";
  editForm.planningModel = "";
  editForm.replyModel = "";
  editForm.reflectionModel = "";
  editForm.memoryModel = "";
  editForm.delegationModel = "";
  editForm.autoReplanEnabled = true;
  editForm.maxReplanTimes = 2;
  editForm.replanCircuitBreakerThreshold = 3;
  editForm.minimumReplanQualityScore = 60;
  editForm.decisionLlmFailureAction = "rule_fallback";
  editForm.enableParallel = true;
  editForm.enableReflection = true;
  editForm.enableDelegation = false;
  editForm.delegationMaxDepth = 3;
  editForm.delegationMaxChildren = 5;
  editForm.delegationTimeoutSeconds = 120;
  editForm.allowAgentReentry = false;
  editForm.clarifyOnMissing = true;
  editForm.memoryStrategy = "auto";
  editForm.userMemoryEnabled = true;
  editForm.userMemoryMode = "summary_only";
  editForm.userMemoryWritebackEnabled = true;
  editForm.preferenceTtlDays = 180;
  editForm.attentionTtlDays = 30;
  editForm.factTtlDays = 180;
  editForm.goalTtlDays = 180;
  editForm.preferSummaryOverRawHistory = true;
  editForm.maxRecentMessages = 6;
  editForm.maxRecentUserMessages = 4;
  editForm.preferStateFirst = true;
  editForm.historySummaryThresholdMessages = 6;
  editForm.minRetentionRatio = 0.18;
  editForm.qualityWarningThreshold = 75;
  editForm.toolFallbackAction = "switch_candidate";
  editForm.replyStyle = "default";
  editForm.trustPolicyEnabled = false;
  editForm.minimumTrustConfidence = 0.65;
  editForm.minimumEvidenceSources = 1;
  editForm.requireCitation = false;
  editForm.requireClaimEvidence = false;
  editForm.detectContradictions = false;
  editForm.minimumAuthorityScore = 0;
  editForm.trustVerifierEnabled = false;
  editForm.trustVerifierMode = "llm";
  editForm.trustVerifierToolCode = "";
  editForm.trustVerifierErrorAction = "fail_closed";
  editForm.trustFailureAction = "warn";
  editForm.releaseGateEnabled = false;
  editForm.minimumAverageScore = 80;
  editForm.minimumPassRate = 90;
  editForm.minimumEvalCases = 1;
  editForm.maximumEvalAgeHours = 168;
  editForm.metadataText = "{}";
  editForm.status = "draft";
  editForm.remark = "";
}

async function fillFormByImportJson(): Promise<void> {
  const importData = parseAgentImportJson(editForm.importJsonText);
  if (!importData) {
    return;
  }

  const defaultModel = asString(importData.defaultModel);
  const defaultPromptId = asPromptId(importData.defaultPromptId);
  const decisionPromptId = asPromptId(importData.decisionPromptId);
  const plannerPromptId = asPromptId(importData.plannerPromptId);
  const metadata = asObject(importData.metadata);
  const runtimeStrategy = extractAgentRuntimeStrategy(metadata);
  const releaseGate = extractReleaseGate(metadata);
  const trustPolicy = extractTrustPolicy(metadata);

  await Promise.all([
    ensureModelOption(defaultModel || null),
    ensureModelOption(runtimeStrategy.decisionModel || null),
    ensureModelOption(runtimeStrategy.planningModel || null),
    ensureModelOption(runtimeStrategy.replyModel || null),
    ensureModelOption(runtimeStrategy.reflectionModel || null),
    ensureModelOption(runtimeStrategy.memoryModel || null),
    ensureModelOption(runtimeStrategy.delegationModel || null),
    ensurePromptOption(defaultPromptId, defaultPromptOptions),
    ensurePromptOption(decisionPromptId, decisionPromptOptions),
    ensurePromptOption(plannerPromptId, plannerPromptOptions)
  ]);

  editForm.agentCode = asString(importData.agentCode);
  editForm.agentName = asString(importData.agentName);
  editForm.description = asString(importData.description);
  editForm.version = asString(importData.version) || "v1.0";
  editForm.defaultModel = defaultModel;
  editForm.defaultPromptId = defaultPromptId;
  editForm.decisionPromptId = decisionPromptId;
  editForm.plannerPromptId = plannerPromptId;
  editForm.toolScopeMode = asString(importData.toolScopeMode) || "inherit_default";
  editForm.defaultExecutionMode = runtimeStrategy.defaultExecutionMode;
  editForm.allowedExecutionModes = runtimeStrategy.allowedExecutionModes;
  editForm.decisionMode = runtimeStrategy.decisionMode;
  editForm.planningMode = runtimeStrategy.planningMode;
  editForm.skipPlannerForDirectReply = runtimeStrategy.skipPlannerForDirectReply;
  editForm.skipPlannerForSingleWorkflow = runtimeStrategy.skipPlannerForSingleWorkflow;
  editForm.maxLlmCallsPerRun = runtimeStrategy.maxLlmCallsPerRun;
  editForm.interactiveMaxIterations = runtimeStrategy.interactiveMaxIterations;
  editForm.interactiveMaxLlmCalls = runtimeStrategy.interactiveMaxLlmCalls;
  editForm.interactiveMaxToolCalls = runtimeStrategy.interactiveMaxToolCalls;
  editForm.interactiveMaxTokens = runtimeStrategy.interactiveMaxTokens;
  editForm.interactiveMaxCostAmount = runtimeStrategy.interactiveMaxCostAmount;
  editForm.duplicateToolCallLimit = runtimeStrategy.duplicateToolCallLimit;
  editForm.decisionModel = runtimeStrategy.decisionModel;
  editForm.planningModel = runtimeStrategy.planningModel;
  editForm.replyModel = runtimeStrategy.replyModel;
  editForm.reflectionModel = runtimeStrategy.reflectionModel;
  editForm.memoryModel = runtimeStrategy.memoryModel;
  editForm.delegationModel = runtimeStrategy.delegationModel;
  editForm.autoReplanEnabled = runtimeStrategy.autoReplanEnabled;
  editForm.maxReplanTimes = runtimeStrategy.maxReplanTimes;
  editForm.replanCircuitBreakerThreshold = runtimeStrategy.replanCircuitBreakerThreshold;
  editForm.minimumReplanQualityScore = runtimeStrategy.minimumReplanQualityScore;
  editForm.decisionLlmFailureAction = runtimeStrategy.decisionLlmFailureAction;
  editForm.enableParallel = runtimeStrategy.enableParallel;
  editForm.enableReflection = runtimeStrategy.enableReflection;
  editForm.enableDelegation = runtimeStrategy.enableDelegation;
  editForm.delegationMaxDepth = runtimeStrategy.delegationMaxDepth;
  editForm.delegationMaxChildren = runtimeStrategy.delegationMaxChildren;
  editForm.delegationTimeoutSeconds = runtimeStrategy.delegationTimeoutSeconds;
  editForm.allowAgentReentry = runtimeStrategy.allowAgentReentry;
  editForm.clarifyOnMissing = runtimeStrategy.clarifyOnMissing;
  editForm.memoryStrategy = runtimeStrategy.memoryStrategy;
  editForm.userMemoryEnabled = runtimeStrategy.userMemoryEnabled;
  editForm.userMemoryMode = runtimeStrategy.userMemoryMode;
  editForm.userMemoryWritebackEnabled = runtimeStrategy.userMemoryWritebackEnabled;
  editForm.preferenceTtlDays = runtimeStrategy.preferenceTtlDays;
  editForm.attentionTtlDays = runtimeStrategy.attentionTtlDays;
  editForm.factTtlDays = runtimeStrategy.factTtlDays;
  editForm.goalTtlDays = runtimeStrategy.goalTtlDays;
  editForm.preferSummaryOverRawHistory = runtimeStrategy.preferSummaryOverRawHistory;
  editForm.maxRecentMessages = runtimeStrategy.maxRecentMessages;
  editForm.maxRecentUserMessages = runtimeStrategy.maxRecentUserMessages;
  editForm.preferStateFirst = runtimeStrategy.preferStateFirst;
  editForm.historySummaryThresholdMessages = runtimeStrategy.historySummaryThresholdMessages;
  editForm.minRetentionRatio = runtimeStrategy.minRetentionRatio;
  editForm.qualityWarningThreshold = runtimeStrategy.qualityWarningThreshold;
  editForm.toolFallbackAction = runtimeStrategy.toolFallbackAction;
  editForm.replyStyle = runtimeStrategy.replyStyle;
  editForm.releaseGateEnabled = releaseGate.enabled;
  editForm.minimumAverageScore = releaseGate.minimumAverageScore;
  editForm.minimumPassRate = releaseGate.minimumPassRate;
  editForm.minimumEvalCases = releaseGate.minimumCases;
  editForm.maximumEvalAgeHours = releaseGate.maximumAgeHours;
  applyTrustPolicy(trustPolicy);
  editForm.metadataText = stringifyJson(metadata);
  editForm.status = asString(importData.status) || "draft";
  editForm.remark = asString(importData.remark);

  ElMessage.success("JSON 已自动填充到表单");
}

async function loadDetailToolScope(agentId: number): Promise<void> {
  detailToolsLoading.value = true;
  try {
    detailToolScope.value = await fetchAgentToolScope(agentId);
  } catch (error) {
    detailToolScope.value = null;
    ElMessage.warning("Agent Tool 范围加载失败");
  } finally {
    detailToolsLoading.value = false;
  }
}

async function loadDetailWorkflowScope(agentId: number): Promise<void> {
  detailToolsLoading.value = true;
  try {
    detailWorkflowScope.value = await fetchAgentWorkflowScope(agentId);
  } catch (error) {
    detailWorkflowScope.value = null;
    ElMessage.warning("Agent Workflow 范围加载失败");
  } finally {
    detailToolsLoading.value = false;
  }
}

async function openToolScopeDialog(agentId: number): Promise<void> {
  if (!agentId) {
    return;
  }
  toolScopeAgentId.value = agentId;
  toolScopeDialogVisible.value = true;
  toolScopeLoading.value = true;
  try {
    const [scope, packScope] = await Promise.all([
      fetchAgentToolScope(agentId),
      fetchAgentToolCapabilityPacks(agentId)
    ]);
    toolScopeForm.toolScopeMode = scope.toolScopeMode || "inherit_default";
    selectedToolIds.value = [...scope.selectedToolIds];
    toolOptions.value = scope.availableTools;
    selectedCapabilityPackIds.value = [...packScope.selectedPackIds];
    capabilityPackOptions.value = packScope.availablePacks.filter(pack => pack.status === "enabled");
  } catch (error) {
    toolScopeDialogVisible.value = false;
    ElMessage.error("Tool 范围数据加载失败");
  } finally {
    toolScopeLoading.value = false;
  }
}

async function openWorkflowScopeDialog(agentId: number): Promise<void> {
  if (!agentId) {
    return;
  }
  workflowScopeAgentId.value = agentId;
  workflowScopeDialogVisible.value = true;
  workflowScopeLoading.value = true;
  try {
    const scope = await fetchAgentWorkflowScope(agentId);
    selectedWorkflowIds.value = [...scope.selectedWorkflowIds];
    workflowOptions.value = scope.availableWorkflows;
  } catch (error) {
    workflowScopeDialogVisible.value = false;
    ElMessage.error("Workflow 范围数据加载失败");
  } finally {
    workflowScopeLoading.value = false;
  }
}

async function submitToolScope(): Promise<void> {
  if (!toolScopeAgentId.value) {
    return;
  }

  toolScopeSaving.value = true;
  try {
    const [scope] = await Promise.all([
      updateAgentToolScope(toolScopeAgentId.value, {
        toolScopeMode: toolScopeForm.toolScopeMode,
        toolIds: toolScopeForm.toolScopeMode === "inherit_default" ? [] : selectedToolIds.value
      }),
      updateAgentToolCapabilityPacks(toolScopeAgentId.value, selectedCapabilityPackIds.value)
    ]);
    detailToolScope.value = scope;
    if (detailRecord.value && detailRecord.value.agentId === toolScopeAgentId.value) {
      detailRecord.value = {
        ...detailRecord.value,
        toolScopeMode: scope.toolScopeMode
      };
    }
    toolScopeDialogVisible.value = false;
    ElMessage.success("Tool 范围保存成功");
    await loadAgents();
  } catch (error) {
    ElMessage.error("Tool 范围保存失败");
  } finally {
    toolScopeSaving.value = false;
  }
}

async function submitWorkflowScope(): Promise<void> {
  if (!workflowScopeAgentId.value) {
    return;
  }
  workflowScopeSaving.value = true;
  try {
    const scope = await updateAgentWorkflowScope(workflowScopeAgentId.value, {
      workflowIds: selectedWorkflowIds.value
    });
    detailWorkflowScope.value = scope;
    workflowScopeDialogVisible.value = false;
    ElMessage.success("Workflow 范围更新成功");
  } catch (error) {
    ElMessage.error("Workflow 范围更新失败");
  } finally {
    workflowScopeSaving.value = false;
  }
}

async function submitEditForm(): Promise<void> {
  if (!editForm.agentCode.trim()) {
    ElMessage.warning("请输入 Agent 编码");
    return;
  }
  if (!editForm.agentName.trim()) {
    ElMessage.warning("请输入 Agent 名称");
    return;
  }
  if (!editForm.version.trim()) {
    ElMessage.warning("请输入版本");
    return;
  }
  if (!editForm.toolScopeMode.trim()) {
    ElMessage.warning("请选择 Tool 范围模式");
    return;
  }
  const metadata = parseJsonText(editForm.metadataText, "Metadata");
  if (!metadata) {
    return;
  }
  const mergedMetadata = buildAgentMetadata(metadata);

  saving.value = true;
  try {
    if (editingAgentId.value) {
      await updateAgent(editingAgentId.value, buildAgentPayload(mergedMetadata));
      ElMessage.success("Agent 更新成功");
    } else {
      await createAgent(buildAgentPayload(mergedMetadata));
      ElMessage.success("Agent 创建成功");
    }
    editDialogVisible.value = false;
    await loadAgents();
  } catch (error) {
    ElMessage.error("Agent 保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleAgentStatus(row: AgentRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableAgent(row.agentId);
      ElMessage.success("Agent 已停用");
    } else {
      await enableAgent(row.agentId);
      ElMessage.success("Agent 已启用");
    }
    await loadAgents();
  } catch (error) {
    ElMessage.error("Agent 状态更新失败");
  }
}

function goConversation(agentId: number): void {
  void router.push({
    path: "/chat",
    query: {
      agentId: String(agentId)
    }
  });
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.agentCode = "";
  queryForm.agentName = "";
  queryForm.status = "";
  queryForm.version = "";
  void loadAgents();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadAgents();
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

function emptyToolScopeText(toolScopeMode: string): string {
  if (toolScopeMode === "inherit_default") {
    return "继承默认范围";
  }
  if (toolScopeMode === "inherit_and_append") {
    return "继承默认范围，当前未追加行业 Tool";
  }
  return "-";
}

function toolScopeModeHint(toolScopeMode: string): string {
  if (toolScopeMode === "inherit_default") {
    return "使用平台默认 Tool 集，不单独维护当前 Agent 的 Tool 绑定。";
  }
  if (toolScopeMode === "inherit_and_append") {
    return "先继承平台默认 Tool 集，再为当前 Agent 追加行业或场景 Tool。";
  }
  if (toolScopeMode === "custom") {
    return "完全使用当前 Agent 自己维护的 Tool 集，不继承平台默认 Tool。";
  }
  return "";
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

function buildAgentExecutionSummary(
  detail: AgentDetail | null,
  toolScope: AgentToolScope | null,
  workflowScope: AgentWorkflowScope | null
): string {
  if (!detail) {
    return "-";
  }
  const runtimeStrategy = extractAgentRuntimeStrategy(asObject(detail.metadata));
  const lines = [
    `默认模型：${formatModelCode(detail.defaultModel)}`,
    `系统 Prompt：${detail.defaultPromptName || detail.defaultPromptId || "-"}`,
    `决策 Prompt：${detail.decisionPromptName || detail.decisionPromptId || "-"}`,
    `规划 Prompt：${detail.plannerPromptName || detail.plannerPromptId || "-"}`,
    `Tool 范围模式：${dictLabel("agent_tool_scope_mode", toolScope?.toolScopeMode || detail.toolScopeMode || "")}`,
    `执行模式：默认=${runtimeStrategy.defaultExecutionMode} / 允许=${runtimeStrategy.allowedExecutionModes.join(", ")}`,
    `Decision / Planner：${runtimeStrategy.decisionMode} / ${runtimeStrategy.planningMode}`,
    `快速路径：直接回复=${runtimeStrategy.skipPlannerForDirectReply ? "开" : "关"} / 单 Workflow=${runtimeStrategy.skipPlannerForSingleWorkflow ? "开" : "关"} / LLM 上限=${runtimeStrategy.maxLlmCallsPerRun}`,
    `Loop 硬预算：迭代=${runtimeStrategy.interactiveMaxIterations} / LLM=${runtimeStrategy.interactiveMaxLlmCalls} / Tool=${runtimeStrategy.interactiveMaxToolCalls} / Token=${runtimeStrategy.interactiveMaxTokens} / 成本=${runtimeStrategy.interactiveMaxCostAmount} / 重复=${runtimeStrategy.duplicateToolCallLimit}`,
    `阶段模型：Decision=${runtimeStrategy.decisionModel || "默认"} / Planner=${runtimeStrategy.planningModel || "默认"} / Reply=${runtimeStrategy.replyModel || "默认"} / Reflection=${runtimeStrategy.reflectionModel || "默认"}`,
    `自动 Replan：${runtimeStrategy.autoReplanEnabled ? "开启" : "关闭"} / 最大 ${runtimeStrategy.maxReplanTimes} 次`,
    `并行 / 反思 / 委派：${runtimeStrategy.enableParallel ? "开" : "关"} / ${runtimeStrategy.enableReflection ? "开" : "关"} / ${runtimeStrategy.enableDelegation ? "开" : "关"}`,
    `记忆策略：${runtimeStrategy.memoryStrategy} / 用户记忆：${runtimeStrategy.userMemoryEnabled ? "开" : "关"} / 模式：${runtimeStrategy.userMemoryMode} / 写回：${runtimeStrategy.userMemoryWritebackEnabled ? "开" : "关"}`,
    `压缩策略：摘要优先=${runtimeStrategy.preferSummaryOverRawHistory ? "开" : "关"} / 最近消息=${runtimeStrategy.maxRecentMessages} / 最近用户=${runtimeStrategy.maxRecentUserMessages} / 状态优先=${runtimeStrategy.preferStateFirst ? "开" : "关"}`,
    `压缩阈值：摘要触发>${runtimeStrategy.historySummaryThresholdMessages} 条 / 最低保留=${runtimeStrategy.minRetentionRatio} / 质量告警<${runtimeStrategy.qualityWarningThreshold}`,
    `Tool 失败策略：${runtimeStrategy.toolFallbackAction} / 回复风格：${runtimeStrategy.replyStyle}`,
    `可用 Tool 数：${toolScope?.tools?.length || 0}`,
    `可用 Workflow 数：${workflowScope?.workflows?.length || 0}`,
    "执行链路：用户输入 -> 自适应决策 -> 按复杂度规划 -> Workflow/Tool 执行 -> 反思/重规划 -> 最终回复"
  ];
  return lines.join("\n");
}

function parseAgentImportJson(text: string): Record<string, unknown> | null {
  if (!text.trim()) {
    ElMessage.warning("请输入用于导入的 Agent JSON");
    return null;
  }
  try {
    const parsed = JSON.parse(text.trim()) as unknown;
    if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
      ElMessage.warning("导入 JSON 必须是 JSON object");
      return null;
    }
    return parsed as Record<string, unknown>;
  } catch (error) {
    ElMessage.warning("导入 JSON 不是合法 JSON");
    return null;
  }
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function asObject(value: unknown): JsonObject {
  if (!value || Array.isArray(value) || typeof value !== "object") {
    return {};
  }
  return value as JsonObject;
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

function asPromptId(value: unknown): number | null {
  return typeof value === "number" && value > 0 ? value : null;
}

function asBoolean(value: unknown, defaultValue: boolean): boolean {
  return typeof value === "boolean" ? value : defaultValue;
}

function asNonNegativeNumber(value: unknown, defaultValue: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric >= 0 ? numeric : defaultValue;
}

function normalizeUserMemoryMode(value: unknown): string {
  const mode = typeof value === "string" ? value.trim() : "";
  if (mode === "off" || mode === "summary_and_facts") {
    return mode;
  }
  return "summary_only";
}

function extractAgentRuntimeStrategy(metadata: JsonObject): {
  defaultExecutionMode: string;
  allowedExecutionModes: string[];
  decisionMode: string;
  planningMode: string;
  skipPlannerForDirectReply: boolean;
  skipPlannerForSingleWorkflow: boolean;
  maxLlmCallsPerRun: number;
  interactiveMaxIterations: number;
  interactiveMaxLlmCalls: number;
  interactiveMaxToolCalls: number;
  interactiveMaxTokens: number;
  interactiveMaxCostAmount: number;
  duplicateToolCallLimit: number;
  decisionModel: string;
  planningModel: string;
  replyModel: string;
  reflectionModel: string;
  memoryModel: string;
  delegationModel: string;
  autoReplanEnabled: boolean;
  maxReplanTimes: number;
  replanCircuitBreakerThreshold: number;
  minimumReplanQualityScore: number;
  decisionLlmFailureAction: string;
  enableParallel: boolean;
  enableReflection: boolean;
  enableDelegation: boolean;
  delegationMaxDepth: number;
  delegationMaxChildren: number;
  delegationTimeoutSeconds: number;
  allowAgentReentry: boolean;
  clarifyOnMissing: boolean;
  memoryStrategy: string;
  userMemoryEnabled: boolean;
  userMemoryMode: string;
  userMemoryWritebackEnabled: boolean;
  preferenceTtlDays: number;
  attentionTtlDays: number;
  factTtlDays: number;
  goalTtlDays: number;
  preferSummaryOverRawHistory: boolean;
  maxRecentMessages: number;
  maxRecentUserMessages: number;
  preferStateFirst: boolean;
  historySummaryThresholdMessages: number;
  minRetentionRatio: number;
  qualityWarningThreshold: number;
  toolFallbackAction: string;
  replyStyle: string;
} {
  const planner = asObject(metadata.plannerPolicy);
  const decision = asObject(metadata.decisionPolicy);
  const execution = asObject(metadata.executionPolicy);
  const interactiveLoop = asObject(execution.interactiveLoop);
  const stageModels = asObject(execution.stageModels);
  const delegation = asObject(metadata.delegationPolicy);
  const memory = asObject(metadata.memoryPolicy);
  const tool = asObject(metadata.toolPolicy);
  const reply = asObject(metadata.replyPolicy);
  const compaction = asObject(metadata.compactionPolicy);
  const configuredDefaultMode = asString(execution.defaultMode);
  const defaultExecutionMode = configuredDefaultMode === "interactive_loop" ? "interactive_loop" : "workflow_plan";
  const configuredAllowedModes = Array.isArray(execution.allowedModes)
    ? execution.allowedModes.filter((item): item is string => item === "workflow_plan" || item === "interactive_loop")
    : [];
  const allowedExecutionModes = Array.from(new Set(["workflow_plan", ...configuredAllowedModes]));
  if (!allowedExecutionModes.includes(defaultExecutionMode)) {
    allowedExecutionModes.push(defaultExecutionMode);
  }
  return {
    defaultExecutionMode,
    allowedExecutionModes,
    decisionMode: asString(decision.mode) || "llm_first",
    planningMode: asString(planner.mode) || "adaptive",
    skipPlannerForDirectReply: asBoolean(planner.skipPlannerForDirectReply, true),
    skipPlannerForSingleWorkflow: asBoolean(planner.skipPlannerForSingleWorkflow, true),
    maxLlmCallsPerRun: asBoundedNumber(execution.maxLlmCallsPerRun, 6, 1, 50),
    interactiveMaxIterations: asBoundedNumber(interactiveLoop.maxIterations, 12, 1, 50),
    interactiveMaxLlmCalls: Math.min(
      asBoundedNumber(interactiveLoop.maxLlmCalls, 6, 1, 50),
      asBoundedNumber(execution.maxLlmCallsPerRun, 6, 1, 50)
    ),
    interactiveMaxToolCalls: asBoundedNumber(interactiveLoop.maxToolCalls, 16, 1, 100),
    interactiveMaxTokens: asBoundedNumber(interactiveLoop.maxTokens, 50000, 1, 2000000),
    interactiveMaxCostAmount: asBoundedNumber(interactiveLoop.maxCostAmount, 5, 0, 100000),
    duplicateToolCallLimit: asBoundedNumber(interactiveLoop.duplicateToolCallLimit, 2, 1, 10),
    decisionModel: asString(stageModels.decision),
    planningModel: asString(stageModels.planning),
    replyModel: asString(stageModels.reply),
    reflectionModel: asString(stageModels.reflection),
    memoryModel: asString(stageModels.memory),
    delegationModel: asString(stageModels.delegation),
    autoReplanEnabled: asBoolean(planner.autoReplanEnabled, true),
    maxReplanTimes: asNonNegativeNumber(planner.maxReplanTimes, 2),
    replanCircuitBreakerThreshold: asNonNegativeNumber(planner.replanCircuitBreakerThreshold, 3),
    minimumReplanQualityScore: asBoundedNumber(planner.minimumReplanQualityScore, 60, 0, 100),
    decisionLlmFailureAction: asString(decision.llmFailureAction) || "rule_fallback",
    enableParallel: asBoolean(execution.enableParallel, true),
    enableReflection: asBoolean(execution.enableReflection, true),
    enableDelegation: asBoolean(execution.enableDelegation, false),
    delegationMaxDepth: asBoundedNumber(delegation.maxDepth, 3, 1, 10),
    delegationMaxChildren: asBoundedNumber(delegation.maxChildrenPerTask, 5, 1, 50),
    delegationTimeoutSeconds: asBoundedNumber(delegation.timeoutSeconds, 120, 5, 3600),
    allowAgentReentry: asBoolean(delegation.allowAgentReentry, false),
    clarifyOnMissing: asBoolean(execution.clarifyOnMissing, true),
    memoryStrategy: asString(memory.strategy) || "auto",
    userMemoryEnabled: asBoolean(memory.userMemoryEnabled, true),
    userMemoryMode: normalizeUserMemoryMode(memory.userMemoryMode),
    userMemoryWritebackEnabled: asBoolean(memory.userMemoryWritebackEnabled, true),
    preferenceTtlDays: asBoundedNumber(memory.preferenceTtlDays, 180, 0, 3650),
    attentionTtlDays: asBoundedNumber(memory.attentionTtlDays, 30, 0, 3650),
    factTtlDays: asBoundedNumber(memory.factTtlDays, 180, 0, 3650),
    goalTtlDays: asBoundedNumber(memory.goalTtlDays, 180, 0, 3650),
    preferSummaryOverRawHistory: asBoolean(compaction.preferSummaryOverRawHistory, true),
    maxRecentMessages: asNonNegativeNumber(compaction.maxRecentMessages, 6),
    maxRecentUserMessages: asNonNegativeNumber(compaction.maxRecentUserMessages, 4),
    preferStateFirst: asBoolean(compaction.preferStateFirst, true),
    historySummaryThresholdMessages: asNonNegativeNumber(compaction.historySummaryThresholdMessages, 6),
    minRetentionRatio: asRatio(compaction.minRetentionRatio, 0.18),
    qualityWarningThreshold: asBoundedNumber(compaction.qualityWarningThreshold, 75, 1, 100),
    toolFallbackAction: asString(tool.fallbackAction) || "switch_candidate",
    replyStyle: asString(reply.style) || "default"
  };
}

function buildAgentMetadata(baseMetadata: JsonObject): JsonObject {
  return {
    ...baseMetadata,
    plannerPolicy: {
      ...asObject(baseMetadata.plannerPolicy),
      mode: editForm.planningMode || "adaptive",
      skipPlannerForDirectReply: editForm.skipPlannerForDirectReply,
      skipPlannerForSingleWorkflow: editForm.skipPlannerForSingleWorkflow,
      autoReplanEnabled: editForm.autoReplanEnabled,
      maxReplanTimes: editForm.maxReplanTimes,
      replanCircuitBreakerThreshold: editForm.replanCircuitBreakerThreshold,
      minimumReplanQualityScore: editForm.minimumReplanQualityScore
    },
    decisionPolicy: {
      ...asObject(baseMetadata.decisionPolicy),
      mode: editForm.decisionMode || "llm_first",
      llmFailureAction: editForm.decisionLlmFailureAction || "rule_fallback"
    },
    executionPolicy: {
      ...asObject(baseMetadata.executionPolicy),
      defaultMode: editForm.defaultExecutionMode || "workflow_plan",
      allowedModes: Array.from(new Set([
        "workflow_plan",
        editForm.defaultExecutionMode || "workflow_plan",
        ...editForm.allowedExecutionModes
      ])),
      enableParallel: editForm.enableParallel,
      enableReflection: editForm.enableReflection,
      enableDelegation: editForm.enableDelegation,
      clarifyOnMissing: editForm.clarifyOnMissing,
      maxLlmCallsPerRun: editForm.maxLlmCallsPerRun,
      interactiveLoop: {
        ...asObject(asObject(baseMetadata.executionPolicy).interactiveLoop),
        maxIterations: editForm.interactiveMaxIterations,
        maxLlmCalls: Math.min(editForm.interactiveMaxLlmCalls, editForm.maxLlmCallsPerRun),
        maxToolCalls: editForm.interactiveMaxToolCalls,
        maxTokens: editForm.interactiveMaxTokens,
        maxCostAmount: editForm.interactiveMaxCostAmount,
        duplicateToolCallLimit: editForm.duplicateToolCallLimit
      },
      stageModels: {
        ...asObject(asObject(baseMetadata.executionPolicy).stageModels),
        decision: editForm.decisionModel,
        planning: editForm.planningModel,
        reply: editForm.replyModel,
        reflection: editForm.reflectionModel,
        memory: editForm.memoryModel,
        delegation: editForm.delegationModel
      }
    },
    delegationPolicy: {
      ...asObject(baseMetadata.delegationPolicy),
      maxDepth: editForm.delegationMaxDepth,
      maxChildrenPerTask: editForm.delegationMaxChildren,
      timeoutSeconds: editForm.delegationTimeoutSeconds,
      allowAgentReentry: editForm.allowAgentReentry
    },
    memoryPolicy: {
      ...asObject(baseMetadata.memoryPolicy),
      strategy: editForm.memoryStrategy || "auto",
      userMemoryEnabled: editForm.userMemoryEnabled,
      userMemoryMode: normalizeUserMemoryMode(editForm.userMemoryMode),
      userMemoryWritebackEnabled: editForm.userMemoryWritebackEnabled,
      preferenceTtlDays: editForm.preferenceTtlDays,
      attentionTtlDays: editForm.attentionTtlDays,
      factTtlDays: editForm.factTtlDays,
      goalTtlDays: editForm.goalTtlDays
    },
    compactionPolicy: {
      ...asObject(baseMetadata.compactionPolicy),
      enabled: true,
      preferSummaryOverRawHistory: editForm.preferSummaryOverRawHistory,
      maxRecentMessages: editForm.maxRecentMessages,
      maxRecentUserMessages: editForm.maxRecentUserMessages,
      preferStateFirst: editForm.preferStateFirst,
      historySummaryThresholdMessages: editForm.historySummaryThresholdMessages,
      minRetentionRatio: editForm.minRetentionRatio,
      qualityWarningThreshold: editForm.qualityWarningThreshold
    },
    toolPolicy: {
      ...asObject(baseMetadata.toolPolicy),
      fallbackAction: editForm.toolFallbackAction || "switch_candidate"
    },
    replyPolicy: {
      ...asObject(baseMetadata.replyPolicy),
      style: editForm.replyStyle || "default"
    },
    trustPolicy: {
      ...asObject(baseMetadata.trustPolicy),
      enabled: editForm.trustPolicyEnabled,
      minimumConfidence: editForm.minimumTrustConfidence,
      minimumEvidenceSources: editForm.minimumEvidenceSources,
      requireCitation: editForm.requireCitation,
      requireClaimEvidence: editForm.requireClaimEvidence,
      detectContradictions: editForm.detectContradictions,
      minimumAuthorityScore: editForm.minimumAuthorityScore,
      verifier: {
        enabled: editForm.trustVerifierEnabled,
        mode: editForm.trustVerifierMode || "llm",
        toolCode: editForm.trustVerifierToolCode.trim(),
        errorAction: editForm.trustVerifierErrorAction || "fail_closed"
      },
      failureAction: editForm.trustFailureAction || "warn"
    },
    releaseGate: {
      ...asObject(baseMetadata.releaseGate),
      enabled: editForm.releaseGateEnabled,
      minimumAverageScore: editForm.minimumAverageScore,
      minimumPassRate: editForm.minimumPassRate,
      minimumCases: editForm.minimumEvalCases,
      maximumAgeHours: editForm.maximumEvalAgeHours
    }
  };
}

function extractReleaseGate(metadata: JsonObject): {
  enabled: boolean;
  minimumAverageScore: number;
  minimumPassRate: number;
  minimumCases: number;
  maximumAgeHours: number;
} {
  const gate = asObject(metadata.releaseGate);
  return {
    enabled: asBoolean(gate.enabled, false),
    minimumAverageScore: asBoundedNumber(gate.minimumAverageScore, 80, 0, 100),
    minimumPassRate: asBoundedNumber(gate.minimumPassRate, 90, 0, 100),
    minimumCases: asBoundedNumber(gate.minimumCases, 1, 1, 100000),
    maximumAgeHours: asBoundedNumber(gate.maximumAgeHours, 168, 1, 8760)
  };
}

interface TrustPolicyForm {
  enabled: boolean;
  minimumConfidence: number;
  minimumEvidenceSources: number;
  requireCitation: boolean;
  requireClaimEvidence: boolean;
  detectContradictions: boolean;
  minimumAuthorityScore: number;
  verifierEnabled: boolean;
  verifierMode: string;
  verifierToolCode: string;
  verifierErrorAction: string;
  failureAction: string;
}

function extractTrustPolicy(metadata: JsonObject): TrustPolicyForm {
  const policy = asObject(metadata.trustPolicy);
  const verifier = asObject(policy.verifier);
  return {
    enabled: asBoolean(policy.enabled, false),
    minimumConfidence: asBoundedNumber(policy.minimumConfidence, 0.65, 0, 1),
    minimumEvidenceSources: asBoundedNumber(policy.minimumEvidenceSources, 1, 0, 20),
    requireCitation: asBoolean(policy.requireCitation, false),
    requireClaimEvidence: asBoolean(policy.requireClaimEvidence, false),
    detectContradictions: asBoolean(policy.detectContradictions, false),
    minimumAuthorityScore: asBoundedNumber(policy.minimumAuthorityScore, 0, 0, 1),
    verifierEnabled: asBoolean(verifier.enabled, false),
    verifierMode: asString(verifier.mode) || "llm",
    verifierToolCode: asString(verifier.toolCode),
    verifierErrorAction: asString(verifier.errorAction) || "fail_closed",
    failureAction: asString(policy.failureAction) || "warn"
  };
}

function applyTrustPolicy(policy: TrustPolicyForm): void {
  editForm.trustPolicyEnabled = policy.enabled;
  editForm.minimumTrustConfidence = policy.minimumConfidence;
  editForm.minimumEvidenceSources = policy.minimumEvidenceSources;
  editForm.requireCitation = policy.requireCitation;
  editForm.requireClaimEvidence = policy.requireClaimEvidence;
  editForm.detectContradictions = policy.detectContradictions;
  editForm.minimumAuthorityScore = policy.minimumAuthorityScore;
  editForm.trustVerifierEnabled = policy.verifierEnabled;
  editForm.trustVerifierMode = policy.verifierMode;
  editForm.trustVerifierToolCode = policy.verifierToolCode;
  editForm.trustVerifierErrorAction = policy.verifierErrorAction;
  editForm.trustFailureAction = policy.failureAction;
}

function buildAgentPayload(metadata: JsonObject) {
  return {
    agentCode: editForm.agentCode,
    agentName: editForm.agentName,
    description: editForm.description,
    version: editForm.version,
    defaultModel: editForm.defaultModel,
    defaultPromptId: editForm.defaultPromptId,
    decisionPromptId: editForm.decisionPromptId,
    plannerPromptId: editForm.plannerPromptId,
    toolScopeMode: editForm.toolScopeMode,
    status: editForm.status,
    metadata,
    remark: editForm.remark
  };
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

function asBoundedNumber(value: unknown, defaultValue: number, minValue: number, maxValue: number): number {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return defaultValue;
  }
  return Math.min(maxValue, Math.max(minValue, numeric));
}

function asRatio(value: unknown, defaultValue: number): number {
  return asBoundedNumber(value, defaultValue, 0.1, 0.9);
}

function formatModelCode(modelCode: string | null | undefined): string {
  if (!modelCode) {
    return "-";
  }
  const matched = modelOptions.value.find((item) => item.value === modelCode);
  return matched?.label || modelCode;
}

function toggleToolSelection(toolId: number, checked: boolean | string | number): void {
  const nextChecked = Boolean(checked);
  if (nextChecked) {
    if (!selectedToolIds.value.includes(toolId)) {
      selectedToolIds.value = [...selectedToolIds.value, toolId];
    }
    return;
  }
  selectedToolIds.value = selectedToolIds.value.filter((item) => item !== toolId);
}

function toggleWorkflowSelection(workflowId: number, checked: boolean | string | number): void {
  const nextChecked = Boolean(checked);
  if (nextChecked) {
    if (!selectedWorkflowIds.value.includes(workflowId)) {
      selectedWorkflowIds.value = [...selectedWorkflowIds.value, workflowId];
    }
    return;
  }
  selectedWorkflowIds.value = selectedWorkflowIds.value.filter((item) => item !== workflowId);
}

onMounted(() => {
  void Promise.all([loadDictOptionMap(), loadAgentPromptOptions(), loadModelOptions(), loadAgents()]);
});
</script>

<style scoped>
.agent-page {
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

.drawer-footer {
  margin-top: 20px;
}

.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #64768a;
  line-height: 1.5;
}

.tool-scope-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 240px;
}

.tool-scope-mode-group {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.tool-scope-mode-tip {
  margin-top: 10px;
}

.tool-scope-panel {
  border: 1px solid #dbe5ef;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f5f9fd 100%);
  padding: 16px;
}

.tool-scope-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.tool-scope-title {
  font-size: 15px;
  font-weight: 700;
  color: #17324d;
}

.tool-scope-count {
  margin-top: 4px;
  font-size: 13px;
  color: #52657a;
}

.tool-scope-list {
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}

.tool-option-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-option-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: stretch;
  gap: 12px;
}

.tool-option-row--active .tool-option-card {
  border-color: #7eb2ff;
  box-shadow: 0 10px 22px rgba(44, 104, 196, 0.12);
  background: linear-gradient(180deg, #f8fbff 0%, #f1f7ff 100%);
}

.tool-option-check {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 14px;
}

.tool-option-card {
  width: 100%;
  border: 1px solid #dbe5ef;
  border-radius: 14px;
  padding: 12px 14px;
  background: #f8fbff;
  box-sizing: border-box;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.tool-option-card:hover {
  border-color: #8eb8ff;
  box-shadow: 0 8px 18px rgba(44, 104, 196, 0.08);
  transform: translateY(-1px);
}

.tool-option-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tool-option-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.tool-option-name {
  font-weight: 600;
  color: #17324d;
}

.tool-option-code {
  font-size: 12px;
  color: #64768a;
  background: #edf4fb;
  border-radius: 999px;
  padding: 2px 8px;
}

.tool-option-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #64768a;
}

.tool-option-desc {
  margin-top: 8px;
  font-size: 13px;
  color: #415467;
  line-height: 1.6;
}

.tool-scope-placeholder {
  padding: 20px 0 8px;
}
</style>

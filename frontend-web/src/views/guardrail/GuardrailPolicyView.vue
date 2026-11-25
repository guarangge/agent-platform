<template>
  <section class="guardrail-page">
    <el-tabs v-model="activeTab" class="guardrail-tabs">
      <el-tab-pane label="策略配置" name="config">
        <el-card shadow="never" class="query-card">
          <el-form :model="queryForm" inline class="query-form">
            <el-form-item label="策略编码">
              <el-input v-model="queryForm.policyCode" placeholder="请输入策略编码" clearable />
            </el-form-item>
            <el-form-item label="策略名称">
              <el-input v-model="queryForm.policyName" placeholder="请输入策略名称" clearable />
            </el-form-item>
            <el-form-item label="动作类型">
              <el-select v-model="queryForm.actionType" clearable style="width: 180px;">
                <el-option label="允许" value="allow" />
                <el-option label="隔离执行" value="sandbox" />
                <el-option label="阻断" value="block" />
                <el-option label="审批" value="approval" />
                <el-option label="脱敏" value="redact" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryForm.status" clearable style="width: 180px;">
                <el-option label="草稿" value="draft" />
                <el-option label="启用" value="enabled" />
                <el-option label="停用" value="disabled" />
              </el-select>
            </el-form-item>
            <div class="query-actions">
              <el-button @click="resetQuery">重置</el-button>
              <el-button type="primary" @click="loadPolicies">查询</el-button>
              <el-button type="primary" plain @click="openCreateDialog">新建策略</el-button>
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <div>
                <div class="card-title">Guardrails 多层策略中心</div>
                <div class="card-subtitle">按 Agent / Tool / 用户 / 角色 / 环境独立配置策略，支持版本、灰度、审批人和调试。</div>
              </div>
            </div>
          </template>

          <el-table :data="policyRecords" border stripe v-loading="loading">
            <el-table-column prop="policyName" label="策略名称" min-width="180" />
            <el-table-column prop="policyCode" label="策略编码" min-width="180" />
            <el-table-column prop="policyVersion" label="版本" min-width="80" />
            <el-table-column prop="priority" label="优先级" min-width="90" />
            <el-table-column prop="actionType" label="动作" min-width="90">
              <template #default="{ row }">
                <el-tag :type="row.actionType === 'approval' ? 'warning' : row.actionType === 'redact' ? 'info' : row.actionType === 'allow' ? 'success' : row.actionType === 'sandbox' ? 'primary' : 'danger'">
                  {{ actionTypeLabel(row.actionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rolloutMode" label="灰度" min-width="110" />
            <el-table-column prop="status" label="状态" min-width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作用域摘要" min-width="240">
              <template #default="{ row }">
                用户 {{ row.scopeUserCount }} / 角色 {{ row.scopeRoleCount }} / Agent {{ row.scopeAgentCount }} / Tool {{ row.scopeToolCount }} / Workflow {{ row.scopeWorkflowCount }} / 环境 {{ row.scopeEnvCount }}
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="220">
              <template #default="{ row }">
                {{ row.description || "-" }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
            <el-table-column label="操作" min-width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditDialog(row.policyId)">编辑</el-button>
                <el-button link type="primary" @click="openDetailDrawer(row.policyId)">详情</el-button>
                <el-button link :type="row.status === 'enabled' ? 'warning' : 'success'" @click="togglePolicyStatus(row)">
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
      </el-tab-pane>

      <el-tab-pane label="命中记录" name="hits">
        <el-card shadow="never" class="query-card">
          <el-form :model="hitQueryForm" inline class="query-form">
            <el-form-item label="策略编码">
              <el-input v-model="hitQueryForm.policyCode" clearable />
            </el-form-item>
            <el-form-item label="决策">
              <el-select v-model="hitQueryForm.decisionType" clearable style="width: 180px;">
                <el-option label="阻断" value="block" />
                <el-option label="审批" value="approval" />
                <el-option label="脱敏" value="redact" />
              </el-select>
            </el-form-item>
            <el-form-item label="Trace ID">
              <el-input v-model="hitQueryForm.traceId" clearable />
            </el-form-item>
            <el-form-item label="Agent">
              <el-select v-model="hitQueryForm.agentId" clearable filterable style="width: 220px;">
                <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <div class="query-actions">
              <el-button @click="resetHitQuery">重置</el-button>
              <el-button type="primary" @click="loadHits">查询</el-button>
            </div>
          </el-form>
        </el-card>

        <el-card shadow="never" class="table-card">
          <el-table :data="hitRecords" border stripe v-loading="hitLoading">
            <el-table-column prop="policyCode" label="策略编码" min-width="180" />
            <el-table-column prop="policyName" label="策略名称" min-width="180" />
            <el-table-column prop="decisionType" label="决策" min-width="90" />
            <el-table-column prop="resourceCode" label="资源编码" min-width="180" />
            <el-table-column prop="resourceName" label="资源名称" min-width="180" />
            <el-table-column prop="traceId" label="Trace ID" min-width="180" />
            <el-table-column prop="hitDetail" label="命中说明" min-width="260" />
            <el-table-column prop="createdAt" label="命中时间" min-width="180" :formatter="formatTableDateTime" />
          </el-table>
          <div class="pagination-row">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="hitTotal"
              :current-page="hitQueryForm.pageNum"
              :page-size="hitQueryForm.pageSize"
              @current-change="handleHitPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="策略调试" name="debug">
        <el-card shadow="never" class="debug-card">
          <el-form :model="debugForm" label-width="110px">
            <el-form-item label="用户">
              <el-select v-model="debugForm.userId" clearable filterable style="width: 100%;">
                <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="角色">
              <el-select v-model="debugForm.roleCodes" multiple clearable filterable style="width: 100%;">
                <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Agent">
              <el-select v-model="debugForm.agentId" clearable filterable style="width: 100%;">
                <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Tool">
              <el-select v-model="debugForm.toolId" clearable filterable style="width: 100%;">
                <el-option v-for="option in toolOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Workflow">
              <el-select v-model="debugForm.workflowId" clearable filterable style="width: 100%;">
                <el-option v-for="option in workflowOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="环境">
              <el-input v-model="debugForm.environment" placeholder="例如 dev / test / prod" />
            </el-form-item>
            <el-form-item label="输入文本">
              <el-input v-model="debugForm.inputText" type="textarea" :rows="5" placeholder="请输入待调试的输入文本" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="debugLoading" @click="runDebug">执行调试</el-button>
            </el-form-item>
          </el-form>

          <div v-if="debugResult" class="debug-result">
            <el-alert :title="`最终决策：${debugResult.finalDecision}`" :type="debugResult.finalDecision === 'approval' ? 'warning' : debugResult.finalDecision === 'block' ? 'error' : 'success'" show-icon />
            <div class="debug-panels">
              <div class="debug-panel">
                <div class="debug-title">主命中策略</div>
                <pre class="json-block">{{ formatJson(debugResult.primaryPolicy) }}</pre>
              </div>
              <div class="debug-panel">
                <div class="debug-title">命中策略列表</div>
                <pre class="json-block">{{ formatJson(debugResult.matchedPolicies) }}</pre>
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 审批工作台已迁移到任务运行中心，保留旧模板代码仅用于兼容当前发布周期。 -->
      <el-tab-pane v-if="false" label="审批工作台" name="approvals">
        <el-card shadow="never" class="query-card">
          <el-form :model="approvalQueryForm" inline class="query-form">
            <el-form-item label="状态">
              <el-select v-model="approvalQueryForm.status" clearable style="width: 180px;">
                <el-option label="待审批" value="pending" />
                <el-option label="已通过" value="approved" />
                <el-option label="已拒绝" value="rejected" />
              </el-select>
            </el-form-item>
            <el-form-item label="Agent">
              <el-select v-model="approvalQueryForm.agentId" clearable filterable style="width: 220px;">
                <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="会话编码">
              <el-input v-model="approvalQueryForm.conversationCode" clearable style="width: 220px;" />
            </el-form-item>
            <el-form-item label="任务编码">
              <el-input v-model="approvalQueryForm.taskCode" clearable style="width: 220px;" />
            </el-form-item>
            <div class="query-actions">
              <el-button @click="resetApprovalQuery">重置</el-button>
              <el-button type="primary" @click="loadApprovalWorkbench">查询</el-button>
            </div>
          </el-form>
        </el-card>
        <el-card shadow="never" class="table-card">
          <el-table :data="approvalRecords" border stripe v-loading="approvalLoading">
            <el-table-column prop="conversationCode" label="会话" min-width="160" />
            <el-table-column prop="taskCode" label="任务" min-width="160" />
            <el-table-column prop="agentName" label="Agent" min-width="160" />
            <el-table-column prop="nodeName" label="审批节点" min-width="180" />
            <el-table-column label="审批对象" min-width="220">
              <template #default="{ row }">
                {{ formatApprovalAssignees(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="approvalStatusTagType(row.status)">{{ approvalStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="已等待" min-width="120">
              <template #default="{ row }">
                {{ formatWaitingMinutes(row.waitingMinutes) }}
              </template>
            </el-table-column>
            <el-table-column prop="deadlineAt" label="截止时间" min-width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.deadlineAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
            <el-table-column label="操作" min-width="240" fixed="right">
              <template #default="{ row }">
                <el-space>
                  <el-button link type="primary" @click="openApprovalDetail(row)">详情</el-button>
                  <el-button link type="success" :disabled="row.status !== 'pending'" @click="openApprovalAction('approve', row)">通过</el-button>
                  <el-button link type="danger" :disabled="row.status !== 'pending'" @click="openApprovalAction('reject', row)">驳回</el-button>
                </el-space>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-row">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="approvalTotal"
              :current-page="approvalQueryForm.pageNum"
              :page-size="approvalQueryForm.pageSize"
              @current-change="handleApprovalPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="editDialogVisible" :title="editingPolicyId ? '编辑策略' : '新建策略'" width="900px">
      <el-form :model="editForm" label-width="130px">
        <el-form-item label="策略编码"><el-input v-model="editForm.policyCode" :disabled="Boolean(editingPolicyId)" /></el-form-item>
        <el-form-item label="策略名称"><el-input v-model="editForm.policyName" /></el-form-item>
        <el-form-item label="版本 / 优先级" class="inline-row">
          <el-input-number v-model="editForm.policyVersion" :min="1" />
          <el-input-number v-model="editForm.priority" :min="0" />
        </el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="editForm.status"><el-radio value="draft">草稿</el-radio><el-radio value="enabled">启用</el-radio><el-radio value="disabled">停用</el-radio></el-radio-group></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="灰度模式"><el-select v-model="editForm.rolloutMode" style="width: 100%;"><el-option label="全量" value="all" /><el-option label="百分比灰度" value="percentage" /><el-option label="用户白名单" value="user_whitelist" /></el-select></el-form-item>
        <el-form-item v-if="editForm.rolloutMode === 'percentage'" label="灰度百分比"><el-input-number v-model="editForm.rolloutPercentage" :min="0" :max="100" style="width: 100%;" /></el-form-item>
        <el-form-item v-if="editForm.rolloutMode === 'user_whitelist'" label="灰度用户">
          <el-select v-model="editForm.rolloutUserIds" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域用户">
          <el-select v-model="editForm.scopeUserIds" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域角色">
          <el-select v-model="editForm.scopeRoleCodes" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域 Agent">
          <el-select v-model="editForm.scopeAgentIds" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域 Tool">
          <el-select v-model="editForm.scopeToolIds" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in toolOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域 Workflow">
          <el-select v-model="editForm.scopeWorkflowIds" multiple filterable clearable style="width: 100%;">
            <el-option v-for="option in workflowOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="作用域环境">
          <el-input v-model="editForm.scopeEnvsText" type="textarea" :rows="2" placeholder="一行一个环境，例如 prod" />
        </el-form-item>
        <el-form-item label="执行阶段">
          <el-select v-model="editForm.stage" style="width: 100%;">
            <el-option label="输入阶段" value="input" />
            <el-option label="输出阶段" value="output" />
            <el-option label="输入与输出" value="both" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配模式">
          <el-select v-model="editForm.matchMode" style="width: 100%;">
            <el-option label="常驻匹配" value="always" />
            <el-option label="关键词匹配" value="keyword_match" />
          </el-select>
        </el-form-item>
        <el-form-item label="要求非空"><el-switch v-model="editForm.requireNonEmptyInput" /></el-form-item>
        <el-form-item label="最大输入长度"><el-input-number v-model="editForm.maxInputLength" :min="0" style="width: 100%;" /></el-form-item>
        <el-form-item label="阻断关键词"><el-input v-model="editForm.blockedKeywordsText" type="textarea" :rows="3" placeholder="一行一个" /></el-form-item>
        <el-form-item label="匹配关键词"><el-input v-model="editForm.keywordsText" type="textarea" :rows="3" placeholder="一行一个" /></el-form-item>
        <el-form-item label="动作类型"><el-select v-model="editForm.actionType" style="width: 100%;"><el-option label="允许" value="allow" /><el-option label="隔离执行" value="sandbox" /><el-option label="阻断" value="block" /><el-option label="审批" value="approval" /><el-option label="脱敏" value="redact" /></el-select></el-form-item>
        <template v-if="editForm.actionType === 'approval'">
          <el-form-item label="审批节点名称"><el-input v-model="editForm.approvalNodeName" /></el-form-item>
          <el-form-item label="审批用户">
            <el-select v-model="editForm.approvalUserIds" multiple filterable clearable style="width: 100%;">
              <el-option v-for="option in userOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="审批角色">
            <el-select v-model="editForm.approvalRoleCodes" multiple filterable clearable style="width: 100%;">
              <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
        </template>
        <template v-if="editForm.stage !== 'input'">
          <el-form-item label="输出正则"><el-input v-model="editForm.outputPatternsText" type="textarea" :rows="3" placeholder="一行一个 Java 正则表达式" /></el-form-item>
          <el-form-item v-if="editForm.actionType === 'redact'" label="替换文本"><el-input v-model="editForm.outputReplacement" placeholder="默认 [REDACTED]" /></el-form-item>
          <el-form-item v-if="editForm.actionType === 'block'" label="安全响应"><el-input v-model="editForm.outputSafeResponse" type="textarea" :rows="2" placeholder="命中后返回给用户的安全文本" /></el-form-item>
        </template>
        <el-form-item label="原因说明"><el-input v-model="editForm.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="扩展 Metadata"><el-input v-model="editForm.metadataText" type="textarea" :rows="4" placeholder="请输入合法 JSON，例如 {}" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="策略详情" size="720px">
      <div v-if="detailRecord" class="detail-grid">
        <div class="detail-item"><strong>策略编码：</strong>{{ detailRecord.policyCode }}</div>
        <div class="detail-item"><strong>策略名称：</strong>{{ detailRecord.policyName }}</div>
        <div class="detail-item"><strong>版本：</strong>{{ detailRecord.policyVersion }}</div>
        <div class="detail-item"><strong>优先级：</strong>{{ detailRecord.priority }}</div>
        <div class="detail-item"><strong>动作：</strong>{{ actionTypeLabel(detailRecord.actionType) }}</div>
        <div class="detail-item"><strong>状态：</strong>{{ detailRecord.status }}</div>
        <div class="detail-item detail-item--full"><strong>描述：</strong>{{ detailRecord.description || "-" }}</div>
        <div class="detail-item detail-item--full"><strong>作用域环境：</strong>{{ detailRecord.scopeEnvs.join(" / ") || "-" }}</div>
        <div class="detail-item detail-item--full"><strong>原因说明：</strong>{{ detailRecord.reason || "-" }}</div>
        <div class="detail-item detail-item--full"><strong>Metadata：</strong><pre class="json-block">{{ formatJson(detailRecord.metadata) }}</pre></div>
      </div>
    </el-drawer>

    <el-drawer v-model="approvalDetailDrawerVisible" title="审批详情" size="720px">
      <div v-if="selectedApprovalRecord" class="detail-grid">
        <div class="detail-item"><strong>会话：</strong>{{ selectedApprovalRecord.conversationCode }}</div>
        <div class="detail-item"><strong>任务：</strong>{{ selectedApprovalRecord.taskCode }}</div>
        <div class="detail-item"><strong>Agent：</strong>{{ selectedApprovalRecord.agentName }}</div>
        <div class="detail-item"><strong>审批节点：</strong>{{ selectedApprovalRecord.nodeName || "-" }}</div>
        <div class="detail-item"><strong>状态：</strong>{{ approvalStatusText(selectedApprovalRecord.status) }}</div>
        <div class="detail-item"><strong>来源类型：</strong>{{ selectedApprovalRecord.approvalSourceType || "-" }}</div>
        <div class="detail-item"><strong>已等待：</strong>{{ formatWaitingMinutes(selectedApprovalRecord.waitingMinutes) }}</div>
        <div class="detail-item"><strong>截止时间：</strong>{{ formatDateTime(selectedApprovalRecord.deadlineAt) }}</div>
        <div class="detail-item detail-item--full"><strong>审批对象：</strong>{{ formatApprovalAssignees(selectedApprovalRecord) }}</div>
        <div class="detail-item"><strong>下一计划步骤：</strong>{{ selectedApprovalRecord.nextPlanStepNo ?? "-" }}</div>
        <div class="detail-item"><strong>审批说明：</strong>{{ selectedApprovalRecord.approvalComment || "-" }}</div>
        <div class="detail-item"><strong>审批人：</strong>{{ selectedApprovalRecord.approvedBy ?? "-" }}</div>
        <div class="detail-item"><strong>审批时间：</strong>{{ formatDateTime(selectedApprovalRecord.approvedAt) }}</div>
        <div class="detail-item detail-item--full"><strong>Metadata：</strong><pre class="json-block">{{ formatJsonText(selectedApprovalRecord.metadata) }}</pre></div>
      </div>
    </el-drawer>

    <el-dialog v-model="approvalActionDialogVisible" :title="approvalActionMode === 'approve' ? '审批通过' : '审批驳回'" width="560px">
      <el-form :model="approvalActionForm" label-width="110px">
        <el-form-item label="审批说明">
          <el-input
            v-model="approvalActionForm.comment"
            type="textarea"
            :rows="4"
            :placeholder="approvalActionMode === 'approve' ? '可填写通过说明' : '建议填写驳回原因'"
          />
        </el-form-item>
        <el-form-item v-if="approvalActionMode === 'reject'" label="拒绝后重规划">
          <el-switch v-model="approvalActionForm.replanAfterReject" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approvalActionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="approvalActionSubmitting" @click="submitApprovalAction">确认</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import {
  fetchApprovalWorkbenchPage,
  createGuardrailPolicy,
  debugGuardrailPolicy,
  disableGuardrailPolicy,
  enableGuardrailPolicy,
  fetchGuardrailHitPage,
  fetchGuardrailPolicyDetail,
  fetchGuardrailPolicyPage,
  type ApprovalWorkbenchRecord,
  type GuardrailHitRecord,
  type GuardrailPolicyDebugResult,
  type GuardrailPolicyDetail,
  type GuardrailPolicyPayload,
  type GuardrailPolicyRecord,
  updateGuardrailPolicy
} from "@/api/guardrail";
import { fetchAgentPage } from "@/api/agent";
import { fetchToolPage } from "@/api/tool";
import { fetchWorkflowPage } from "@/api/workflow";
import { fetchUserPage } from "@/api/user";
import { fetchRolePage } from "@/api/role";
import { approveConversationTask, rejectConversationTask } from "@/api/conversation";

interface OptionItem {
  value: number | string;
  label: string;
}

const activeTab = ref("config");
const loading = ref(false);
const hitLoading = ref(false);
const approvalLoading = ref(false);
const debugLoading = ref(false);
const submitting = ref(false);
const approvalActionSubmitting = ref(false);
const editDialogVisible = ref(false);
const detailDrawerVisible = ref(false);
const approvalDetailDrawerVisible = ref(false);
const approvalActionDialogVisible = ref(false);
const editingPolicyId = ref<number | null>(null);
const detailRecord = ref<GuardrailPolicyDetail | null>(null);
const debugResult = ref<GuardrailPolicyDebugResult | null>(null);
const selectedApprovalRecord = ref<ApprovalWorkbenchRecord | null>(null);
const approvalActionMode = ref<"approve" | "reject">("approve");
const total = ref(0);
const hitTotal = ref(0);
const approvalTotal = ref(0);
const policyRecords = ref<GuardrailPolicyRecord[]>([]);
const hitRecords = ref<GuardrailHitRecord[]>([]);
const approvalRecords = ref<ApprovalWorkbenchRecord[]>([]);
const agentOptions = ref<OptionItem[]>([]);
const toolOptions = ref<OptionItem[]>([]);
const workflowOptions = ref<OptionItem[]>([]);
const userOptions = ref<OptionItem[]>([]);
const roleOptions = ref<Array<{ value: string; label: string }>>([]);

const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  policyCode: "",
  policyName: "",
  status: "",
  actionType: ""
});

const hitQueryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  policyCode: "",
  decisionType: "",
  traceId: "",
  taskId: undefined as number | undefined,
  agentId: undefined as number | undefined
});

const debugForm = reactive({
  userId: undefined as number | undefined,
  roleCodes: [] as string[],
  agentId: undefined as number | undefined,
  toolId: undefined as number | undefined,
  workflowId: undefined as number | undefined,
  environment: "dev",
  inputText: ""
});

const approvalQueryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  status: "pending",
  agentId: undefined as number | undefined,
  conversationCode: "",
  taskCode: ""
});

const approvalActionForm = reactive({
  comment: "",
  replanAfterReject: true
});

const editForm = reactive({
  policyCode: "",
  policyName: "",
  policyVersion: 1,
  priority: 100,
  status: "draft",
  description: "",
  rolloutMode: "all",
  rolloutPercentage: 100,
  rolloutUserIds: [] as number[],
  scopeUserIds: [] as number[],
  scopeRoleCodes: [] as string[],
  scopeAgentIds: [] as number[],
  scopeToolIds: [] as number[],
  scopeWorkflowIds: [] as number[],
  scopeEnvsText: "",
  stage: "input",
  matchMode: "always",
  requireNonEmptyInput: false,
  maxInputLength: 0,
  blockedKeywordsText: "",
  keywordsText: "",
  actionType: "block",
  outputPatternsText: "",
  outputReplacement: "[REDACTED]",
  outputSafeResponse: "响应内容命中安全策略，已停止输出。",
  approvalNodeName: "",
  approvalUserIds: [] as number[],
  approvalRoleCodes: [] as string[],
  reason: "",
  metadataText: "{}",
  remark: ""
});

onMounted(async () => {
  await Promise.all([
    loadPolicies(),
    loadHits(),
    loadOptions()
  ]);
});

async function loadOptions(): Promise<void> {
  const [agentPage, toolPage, workflowPage, userPage, rolePage] = await Promise.all([
    fetchAgentPage({ pageNum: 1, pageSize: 200 }),
    fetchToolPage({ pageNum: 1, pageSize: 200 }),
    fetchWorkflowPage({ pageNum: 1, pageSize: 200 }),
    fetchUserPage({ pageNum: 1, pageSize: 200 }),
    fetchRolePage({ pageNum: 1, pageSize: 200 })
  ]);
  agentOptions.value = agentPage.records.map((item) => ({ value: item.agentId, label: `${item.agentName} / ${item.agentCode}` }));
  toolOptions.value = toolPage.records.map((item) => ({ value: item.toolId, label: `${item.toolName} / ${item.toolCode}` }));
  workflowOptions.value = workflowPage.records.map((item) => ({ value: item.workflowId, label: `${item.workflowName} / ${item.workflowCode}` }));
  userOptions.value = userPage.records.map((item) => ({ value: item.userId, label: `${item.displayName} / ${item.username}` }));
  roleOptions.value = rolePage.records.map((item) => ({ value: item.roleCode, label: `${item.roleName} / ${item.roleCode}` }));
}

async function loadPolicies(): Promise<void> {
  loading.value = true;
  try {
    const result = await fetchGuardrailPolicyPage({
      pageNum: queryForm.pageNum,
      pageSize: queryForm.pageSize,
      policyCode: queryForm.policyCode || undefined,
      policyName: queryForm.policyName || undefined,
      status: queryForm.status || undefined,
      actionType: queryForm.actionType || undefined
    });
    policyRecords.value = result.records;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadHits(): Promise<void> {
  hitLoading.value = true;
  try {
    const result = await fetchGuardrailHitPage({
      pageNum: hitQueryForm.pageNum,
      pageSize: hitQueryForm.pageSize,
      policyCode: hitQueryForm.policyCode || undefined,
      decisionType: hitQueryForm.decisionType || undefined,
      traceId: hitQueryForm.traceId || undefined,
      taskId: hitQueryForm.taskId,
      agentId: hitQueryForm.agentId
    });
    hitRecords.value = result.records;
    hitTotal.value = result.total;
  } finally {
    hitLoading.value = false;
  }
}

async function runDebug(): Promise<void> {
  debugLoading.value = true;
  try {
    debugResult.value = await debugGuardrailPolicy({
      userId: debugForm.userId,
      roleCodes: debugForm.roleCodes,
      agentId: debugForm.agentId,
      toolId: debugForm.toolId,
      workflowId: debugForm.workflowId,
      environment: debugForm.environment,
      inputText: debugForm.inputText
    });
    ElMessage.success("策略调试完成");
  } finally {
    debugLoading.value = false;
  }
}

async function loadApprovalWorkbench(): Promise<void> {
  approvalLoading.value = true;
  try {
    const result = await fetchApprovalWorkbenchPage({
      pageNum: approvalQueryForm.pageNum,
      pageSize: approvalQueryForm.pageSize,
      status: approvalQueryForm.status || undefined,
      agentId: approvalQueryForm.agentId,
      conversationCode: approvalQueryForm.conversationCode || undefined,
      taskCode: approvalQueryForm.taskCode || undefined
    });
    approvalRecords.value = result.records;
    approvalTotal.value = result.total;
  } finally {
    approvalLoading.value = false;
  }
}

function resetEditForm(): void {
  editForm.policyCode = "";
  editForm.policyName = "";
  editForm.policyVersion = 1;
  editForm.priority = 100;
  editForm.status = "draft";
  editForm.description = "";
  editForm.rolloutMode = "all";
  editForm.rolloutPercentage = 100;
  editForm.rolloutUserIds = [];
  editForm.scopeUserIds = [];
  editForm.scopeRoleCodes = [];
  editForm.scopeAgentIds = [];
  editForm.scopeToolIds = [];
  editForm.scopeWorkflowIds = [];
  editForm.scopeEnvsText = "";
  editForm.stage = "input";
  editForm.matchMode = "always";
  editForm.requireNonEmptyInput = false;
  editForm.maxInputLength = 0;
  editForm.blockedKeywordsText = "";
  editForm.keywordsText = "";
  editForm.actionType = "block";
  editForm.outputPatternsText = "";
  editForm.outputReplacement = "[REDACTED]";
  editForm.outputSafeResponse = "响应内容命中安全策略，已停止输出。";
  editForm.approvalNodeName = "";
  editForm.approvalUserIds = [];
  editForm.approvalRoleCodes = [];
  editForm.reason = "";
  editForm.metadataText = "{}";
  editForm.remark = "";
}

function actionTypeLabel(actionType: string): string {
  return ({
    allow: "允许",
    sandbox: "隔离执行",
    block: "阻断",
    approval: "审批",
    redact: "脱敏"
  } as Record<string, string>)[actionType] || actionType;
}

function openCreateDialog(): void {
  editingPolicyId.value = null;
  resetEditForm();
  editDialogVisible.value = true;
}

async function openEditDialog(policyId: number): Promise<void> {
  editingPolicyId.value = policyId;
  const detail = await fetchGuardrailPolicyDetail(policyId);
  fillEditForm(detail);
  editDialogVisible.value = true;
}

async function openDetailDrawer(policyId: number): Promise<void> {
  detailRecord.value = await fetchGuardrailPolicyDetail(policyId);
  detailDrawerVisible.value = true;
}

function fillEditForm(detail: GuardrailPolicyDetail): void {
  editForm.policyCode = detail.policyCode;
  editForm.policyName = detail.policyName;
  editForm.policyVersion = detail.policyVersion;
  editForm.priority = detail.priority;
  editForm.status = detail.status;
  editForm.description = detail.description || "";
  editForm.rolloutMode = detail.rolloutMode;
  editForm.rolloutPercentage = detail.rolloutPercentage || 100;
  editForm.rolloutUserIds = [...detail.rolloutUserIds];
  editForm.scopeUserIds = [...detail.scopeUserIds];
  editForm.scopeRoleCodes = [...detail.scopeRoleCodes];
  editForm.scopeAgentIds = [...detail.scopeAgentIds];
  editForm.scopeToolIds = [...detail.scopeToolIds];
  editForm.scopeWorkflowIds = [...detail.scopeWorkflowIds];
  editForm.scopeEnvsText = joinLines(detail.scopeEnvs);
  const metadata = asRecord(detail.metadata);
  const output = asRecord(metadata.output);
  editForm.stage = typeof metadata.stage === "string" ? metadata.stage : "input";
  editForm.outputPatternsText = joinLines(asStringList(output.patterns));
  editForm.outputReplacement = typeof output.replacement === "string" ? output.replacement : "[REDACTED]";
  editForm.outputSafeResponse = typeof output.safeResponse === "string" ? output.safeResponse : "响应内容命中安全策略，已停止输出。";
  editForm.matchMode = detail.matchMode;
  editForm.requireNonEmptyInput = detail.requireNonEmptyInput;
  editForm.maxInputLength = detail.maxInputLength || 0;
  editForm.blockedKeywordsText = joinLines(detail.blockedKeywords);
  editForm.keywordsText = joinLines(detail.keywords);
  editForm.actionType = detail.actionType;
  editForm.approvalNodeName = detail.approvalNodeName || "";
  editForm.approvalUserIds = [...detail.approvalUserIds];
  editForm.approvalRoleCodes = [...detail.approvalRoleCodes];
  editForm.reason = detail.reason || "";
  editForm.metadataText = formatJson(detail.metadata || {});
  editForm.remark = detail.remark || "";
}

async function submitPolicy(): Promise<void> {
  const metadata = parseJsonText(editForm.metadataText);
  metadata.stage = editForm.stage;
  metadata.output = {
    ...asRecord(metadata.output),
    patterns: parseLines(editForm.outputPatternsText),
    replacement: editForm.outputReplacement.trim() || "[REDACTED]",
    safeResponse: editForm.outputSafeResponse.trim() || "响应内容命中安全策略，已停止输出。"
  };
  const payload: GuardrailPolicyPayload = {
    policyCode: editForm.policyCode.trim(),
    policyName: editForm.policyName.trim(),
    policyVersion: editForm.policyVersion,
    priority: editForm.priority,
    status: editForm.status,
    description: editForm.description.trim(),
    rolloutMode: editForm.rolloutMode,
    rolloutPercentage: editForm.rolloutMode === "percentage" ? editForm.rolloutPercentage : null,
    rolloutUserIds: editForm.rolloutMode === "user_whitelist" ? editForm.rolloutUserIds : [],
    scopeUserIds: editForm.scopeUserIds,
    scopeRoleCodes: editForm.scopeRoleCodes,
    scopeAgentIds: editForm.scopeAgentIds,
    scopeToolIds: editForm.scopeToolIds,
    scopeWorkflowIds: editForm.scopeWorkflowIds,
    scopeEnvs: parseLines(editForm.scopeEnvsText),
    matchMode: editForm.matchMode,
    requireNonEmptyInput: editForm.requireNonEmptyInput,
    maxInputLength: editForm.maxInputLength > 0 ? editForm.maxInputLength : null,
    blockedKeywords: parseLines(editForm.blockedKeywordsText),
    keywords: parseLines(editForm.keywordsText),
    actionType: editForm.actionType,
    approvalNodeName: editForm.approvalNodeName.trim(),
    approvalUserIds: editForm.actionType === "approval" ? editForm.approvalUserIds : [],
    approvalRoleCodes: editForm.actionType === "approval" ? editForm.approvalRoleCodes : [],
    reason: editForm.reason.trim(),
    metadata,
    remark: editForm.remark.trim()
  };
  submitting.value = true;
  try {
    if (editingPolicyId.value) {
      await updateGuardrailPolicy(editingPolicyId.value, payload);
      ElMessage.success("策略更新成功");
    } else {
      await createGuardrailPolicy(payload);
      ElMessage.success("策略创建成功");
    }
    editDialogVisible.value = false;
    await loadPolicies();
  } finally {
    submitting.value = false;
  }
}

async function togglePolicyStatus(record: GuardrailPolicyRecord): Promise<void> {
  if (record.status === "enabled") {
    await disableGuardrailPolicy(record.policyId);
    ElMessage.success("策略已停用");
  } else {
    await enableGuardrailPolicy(record.policyId);
    ElMessage.success("策略已启用");
  }
  await loadPolicies();
}

function handlePageChange(page: number): void {
  queryForm.pageNum = page;
  void loadPolicies();
}

function handleHitPageChange(page: number): void {
  hitQueryForm.pageNum = page;
  void loadHits();
}

function handleApprovalPageChange(page: number): void {
  approvalQueryForm.pageNum = page;
  void loadApprovalWorkbench();
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.policyCode = "";
  queryForm.policyName = "";
  queryForm.status = "";
  queryForm.actionType = "";
  void loadPolicies();
}

function resetHitQuery(): void {
  hitQueryForm.pageNum = 1;
  hitQueryForm.policyCode = "";
  hitQueryForm.decisionType = "";
  hitQueryForm.traceId = "";
  hitQueryForm.taskId = undefined;
  hitQueryForm.agentId = undefined;
  void loadHits();
}

function resetApprovalQuery(): void {
  approvalQueryForm.pageNum = 1;
  approvalQueryForm.status = "pending";
  approvalQueryForm.agentId = undefined;
  approvalQueryForm.conversationCode = "";
  approvalQueryForm.taskCode = "";
  void loadApprovalWorkbench();
}

function openApprovalDetail(record: ApprovalWorkbenchRecord): void {
  selectedApprovalRecord.value = record;
  approvalDetailDrawerVisible.value = true;
}

function openApprovalAction(mode: "approve" | "reject", record: ApprovalWorkbenchRecord): void {
  approvalActionMode.value = mode;
  selectedApprovalRecord.value = record;
  approvalActionForm.comment = "";
  approvalActionForm.replanAfterReject = true;
  approvalActionDialogVisible.value = true;
}

async function submitApprovalAction(): Promise<void> {
  if (!selectedApprovalRecord.value) {
    return;
  }
  approvalActionSubmitting.value = true;
  try {
    if (approvalActionMode.value === "approve") {
      await approveConversationTask(
        selectedApprovalRecord.value.conversationId,
        selectedApprovalRecord.value.taskId,
        selectedApprovalRecord.value.approvalId,
        { comment: approvalActionForm.comment || undefined }
      );
      ElMessage.success("审批已通过");
    } else {
      await rejectConversationTask(
        selectedApprovalRecord.value.conversationId,
        selectedApprovalRecord.value.taskId,
        selectedApprovalRecord.value.approvalId,
        {
          comment: approvalActionForm.comment || undefined,
          replanAfterReject: approvalActionForm.replanAfterReject
        }
      );
      ElMessage.success("审批已驳回");
    }
    approvalActionDialogVisible.value = false;
    await loadApprovalWorkbench();
  } catch {
    ElMessage.error(approvalActionMode.value === "approve" ? "审批通过失败" : "审批驳回失败");
  } finally {
    approvalActionSubmitting.value = false;
  }
}

function parseLines(text: string): string[] {
  return text.split("\n").map((item) => item.trim()).filter(Boolean);
}

function joinLines(values: string[]): string {
  return values.join("\n");
}

function parseJsonText(text: string): Record<string, unknown> {
  if (!text.trim()) {
    return {};
  }
  return JSON.parse(text) as Record<string, unknown>;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function asStringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function formatJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

function formatJsonText(value: string): string {
  if (!value) {
    return "{}";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatApprovalAssignees(record: ApprovalWorkbenchRecord): string {
  const userText = record.approvalUserIds.length > 0 ? `用户(${record.approvalUserIds.join(", ")})` : "";
  const roleText = record.approvalRoleCodes.length > 0 ? `角色(${record.approvalRoleCodes.join(", ")})` : "";
  return [userText, roleText].filter(Boolean).join(" / ") || "未限制";
}

function formatWaitingMinutes(value: number | null): string {
  if (value == null) {
    return "-";
  }
  if (value < 60) {
    return `${value} 分钟`;
  }
  return `${(value / 60).toFixed(1)} 小时`;
}

function approvalStatusText(status: string): string {
  if (status === "pending") {
    return "待审批";
  }
  if (status === "approved") {
    return "已通过";
  }
  if (status === "rejected") {
    return "已拒绝";
  }
  if (status === "timed_out") {
    return "已超时";
  }
  if (status === "revoked") {
    return "已撤回";
  }
  return status || "-";
}

function approvalStatusTagType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "approved") {
    return "success";
  }
  if (status === "pending") {
    return "warning";
  }
  if (status === "rejected" || status === "timed_out") {
    return "danger";
  }
  return "info";
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
</script>

<style scoped>
.guardrail-page {
  min-width: 0;
}

.guardrail-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
}

.query-card,
.table-card,
.debug-card {
  margin-bottom: 20px;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.query-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: #17324d;
}

.card-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #5f7286;
}

.pagination-row {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.debug-result {
  margin-top: 20px;
}

.debug-panels {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.debug-panel,
.detail-item {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #dbe3ec;
  background: #f8fbff;
}

.debug-title {
  margin-bottom: 10px;
  font-weight: 700;
  color: #17324d;
}

.json-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  color: #29445f;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-item--full {
  grid-column: 1 / -1;
}

.inline-row :deep(.el-form-item__content) {
  display: flex;
  gap: 12px;
}

@media (max-width: 1200px) {
  .debug-panels,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>

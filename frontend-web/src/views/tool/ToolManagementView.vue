<template>
  <section class="tool-page">
    <el-card shadow="never" class="query-card">
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Tool 编码">
          <el-input v-model="queryForm.toolCode" placeholder="请输入 Tool 编码" clearable />
        </el-form-item>
        <el-form-item label="Tool 名称">
          <el-input v-model="queryForm.toolName" placeholder="请输入 Tool 名称" clearable />
        </el-form-item>
        <el-form-item label="Tool 类型">
          <el-select v-model="queryForm.toolType" placeholder="请选择 Tool 类型" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('tool_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Tool 分类">
          <el-select v-model="queryForm.toolCategory" placeholder="请选择 Tool 分类" clearable style="width: 220px;">
            <el-option
              v-for="option in getDictOptions('tool_category')"
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
          <el-button type="primary" @click="loadTools">查询</el-button>
          <el-button type="primary" plain @click="openCreateDialog">新建 Tool</el-button>
          <el-button type="warning" plain @click="capabilityPackDrawerVisible = true">能力包</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Tool 管理</div>
            <div class="card-subtitle">支持平台默认 Tool 和行业 Tool 分类维护，并保留 Schema / Metadata 注册能力。</div>
          </div>
        </div>
      </template>

      <el-table :data="toolRecords" border stripe v-loading="loading">
        <el-table-column prop="toolName" label="Tool 名称" min-width="180" />
        <el-table-column prop="toolCode" label="Tool 编码" min-width="160" />
        <el-table-column prop="toolType" label="Tool 类型" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.toolType" effect="plain" type="primary" round>
              {{ dictLabel("tool_type", row.toolType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="toolCategory" label="Tool 分类" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.toolCategory" effect="light" type="success" round>
              {{ dictLabel("tool_category", row.toolCategory) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="providerType" label="Provider 类型" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.providerType" effect="plain" type="warning" round>
              {{ dictLabel("tool_provider_type", row.providerType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="transportType" label="传输协议" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.transportType" effect="light" type="info" round>
              {{ dictLabel("tool_transport_type", row.transportType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="permissionLevel" label="权限等级" min-width="120">
          <template #default="{ row }">
            {{ row.permissionLevel || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="timeoutSeconds" label="超时" min-width="100">
          <template #default="{ row }">
            {{ formatTimeout(row.timeoutSeconds) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="100">
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
        <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="350" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetailDrawer(row.toolId)">查看详情</el-button>
            <el-button link type="primary" @click="openEditDialog(row.toolId)">编辑</el-button>
            <el-button link type="primary" @click="openLifecycleDrawer(row)">生命周期</el-button>
            <el-button link type="primary" @click="runConnectivityTest(row.toolId)">健康检查</el-button>
            <el-button
              link
              :type="row.status === 'enabled' ? 'warning' : 'success'"
              @click="toggleToolStatus(row)"
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

    <el-dialog v-model="editDialogVisible" :title="editingToolId ? '编辑 Tool' : '新建 Tool'" width="760px">
      <el-form :model="editForm" label-width="110px">
        <el-form-item :label="editingToolId ? 'JSON 回填' : 'JSON 导入'">
          <div class="json-import-panel">
            <el-input
              v-model="editForm.importJsonText"
              type="textarea"
              :rows="8"
              placeholder="请输入完整 Tool JSON，点击“自动填充”后回填到下方表单"
            />
            <div class="json-import-actions">
              <el-button type="primary" plain @click="fillFormByImportJson">自动填充</el-button>
              <el-button @click="editForm.importJsonText = ''">清空</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Tool 编码">
          <el-input v-model="editForm.toolCode" placeholder="请输入 Tool 编码" :disabled="Boolean(editingToolId)" />
        </el-form-item>
        <el-form-item label="Tool 名称">
          <el-input v-model="editForm.toolName" placeholder="请输入 Tool 名称" />
        </el-form-item>
        <el-form-item label="Tool 类型">
          <el-select v-model="editForm.toolType" placeholder="请选择 Tool 类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Tool 分类">
          <el-select v-model="editForm.toolCategory" placeholder="请选择 Tool 分类" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_category')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="运行时能力">
          <el-select
            v-model="editForm.runtimeCapabilities"
            multiple
            clearable
            placeholder="请选择 Tool 可能触达的安全边界"
            style="width: 100%;"
          >
            <el-option
              v-for="option in runtimeCapabilityOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <div class="form-tip">仅声明可能触达的边界，不代表已获得运行授权。</div>
        </el-form-item>
        <el-divider content-position="left">Provider 配置</el-divider>
        <el-form-item label="Provider 编码">
          <el-input v-model="editForm.providerCode" placeholder="请输入 Provider 编码" />
        </el-form-item>
        <el-form-item label="Provider 名称">
          <el-input v-model="editForm.providerName" placeholder="请输入 Provider 名称" />
        </el-form-item>
        <el-form-item label="Provider 类型">
          <el-select v-model="editForm.providerType" placeholder="请选择 Provider 类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_provider_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="传输协议">
          <el-select v-model="editForm.transportType" placeholder="请选择传输协议" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_transport_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="editForm.providerType === 'mcp'"
          :title="mcpTransportHint()"
          type="info"
          :closable="false"
          show-icon
          class="transport-hint"
        />
        <el-form-item label="Provider 状态">
          <el-radio-group v-model="editForm.providerStatus">
            <el-radio value="draft">草稿</el-radio>
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="配置 Schema">
          <el-input
            v-model="editForm.providerConfigSchemaText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="实例编码">
          <el-input v-model="editForm.providerInstanceCode" placeholder="请输入 Provider 实例编码" />
        </el-form-item>
        <el-form-item label="实例名称">
          <el-input v-model="editForm.providerInstanceName" placeholder="请输入 Provider 实例名称" />
        </el-form-item>
        <el-form-item label="环境编码">
          <el-input v-model="editForm.environmentCode" placeholder="例如 default / prod / test" />
        </el-form-item>
        <el-form-item label="Endpoint">
          <el-input v-model="editForm.endpoint" placeholder="请输入 endpoint 或服务地址" />
        </el-form-item>
        <el-form-item label="认证类型">
          <el-select v-model="editForm.authType" placeholder="请选择认证类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_auth_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="认证配置">
          <el-input
            v-model="editForm.authConfigText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="默认请求头">
          <el-input
            v-model="editForm.headersText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="实例超时">
          <el-input-number v-model="editForm.providerTimeoutSeconds" :min="1" :max="3600" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="editForm.retryTimes" :min="0" :max="10" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="实例状态">
          <el-radio-group v-model="editForm.providerInstanceStatus">
            <el-radio value="draft">草稿</el-radio>
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">Binding 配置</el-divider>
        <el-form-item label="操作名称">
          <el-input v-model="editForm.operationName" placeholder="internal Tool / MCP Tool / API operation name" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="editForm.operationType" placeholder="请选择操作类型" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('tool_operation_type')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定状态">
          <el-radio-group v-model="editForm.bindingStatus">
            <el-radio value="draft">草稿</el-radio>
            <el-radio value="enabled">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调用配置">
          <el-input
            v-model="editForm.invokeConfigText"
            type="textarea"
            :rows="6"
            :placeholder="mcpInvokeConfigPlaceholder()"
          />
        </el-form-item>
        <el-form-item label="参数映射">
          <el-input
            v-model="editForm.argumentMappingText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="输出映射">
          <el-input
            v-model="editForm.outputMappingText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="权限等级">
          <el-select v-model="editForm.permissionLevel" placeholder="请选择权限等级" style="width: 100%;">
            <el-option
              v-for="option in getDictOptions('permission_level')"
              :key="option.itemCode"
              :label="option.itemName"
              :value="option.itemCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超时秒数">
          <el-input-number v-model="editForm.timeoutSeconds" :min="1" :max="3600" style="width: 100%;" />
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
        <el-form-item label="执行 Prompt">
          <el-select
            v-model="editForm.executionPromptId"
            placeholder="请选择执行 Prompt"
            clearable
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="option in promptOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="能力说明">
          <el-input
            v-model="editForm.capabilitiesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个能力说明"
          />
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input
            v-model="editForm.triggerConditionsText"
            type="textarea"
            :rows="3"
            placeholder="一行一个触发条件"
          />
        </el-form-item>
        <el-form-item label="限制条件">
          <el-input
            v-model="editForm.limitationsText"
            type="textarea"
            :rows="3"
            placeholder="一行一个限制条件"
          />
        </el-form-item>
        <el-form-item label="输入前提">
          <el-input
            v-model="editForm.inputPrerequisitesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个输入前提"
          />
        </el-form-item>
        <el-form-item label="副作用">
          <el-input
            v-model="editForm.sideEffects"
            type="textarea"
            :rows="2"
            placeholder="例如：只读 / 写状态 / 外部调用"
          />
        </el-form-item>
        <el-form-item label="副作用等级">
          <el-select v-model="editForm.sideEffectLevel" style="width: 100%;">
            <el-option label="无副作用" value="none" />
            <el-option label="幂等副作用" value="idempotent" />
            <el-option label="可补偿副作用" value="compensatable" />
            <el-option label="不可逆副作用" value="irreversible" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.sideEffectLevel !== 'none'" label="幂等键模式">
          <el-select v-model="editForm.idempotencyMode" style="width: 100%;">
            <el-option label="平台自动生成，可由调用方覆盖" value="generated" />
            <el-option label="调用方必须提供 _idempotencyKey" value="required_custom" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.sideEffectLevel === 'compensatable'" label="补偿 Tool Code">
          <el-input v-model="editForm.compensationToolCode" placeholder="原任务最终失败时逆序调用的补偿 Tool" />
        </el-form-item>
        <el-form-item label="风险说明">
          <el-input
            v-model="editForm.riskNotesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个风险说明"
          />
        </el-form-item>
        <el-form-item label="拦截关键词">
          <el-input
            v-model="editForm.blockedKeywordsText"
            type="textarea"
            :rows="3"
            placeholder="一行一个命中即阻断的关键词"
          />
        </el-form-item>
        <el-form-item label="最大输入长度">
          <el-input-number v-model="editForm.maxInputLength" :min="0" :max="200000" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="执行前审批">
          <el-switch v-model="editForm.approvalRequired" />
        </el-form-item>
        <el-form-item label="审批模式">
          <el-select v-model="editForm.approvalMode" style="width: 100%;">
            <el-option label="总是审批" value="always" />
            <el-option label="关键词命中审批" value="keyword_match" />
          </el-select>
        </el-form-item>
        <el-form-item label="审批关键词">
          <el-input
            v-model="editForm.approvalKeywordsText"
            type="textarea"
            :rows="3"
            placeholder="审批模式为关键词命中时生效，一行一个关键词"
          />
        </el-form-item>
        <el-form-item label="审批原因">
          <el-input
            v-model="editForm.approvalReason"
            type="textarea"
            :rows="2"
            placeholder="例如：会修改外部状态，需要人工确认"
          />
        </el-form-item>
        <el-divider content-position="left">治理策略</el-divider>
        <el-form-item label="Fallback 动作">
          <el-select v-model="editForm.fallbackAction" style="width: 100%;" placeholder="请选择失败后的处理动作">
            <el-option label="切换候选 Tool" value="switch_candidate" />
            <el-option label="重试当前 Tool" value="retry_same_tool" />
            <el-option label="触发 Replan" value="replan" />
            <el-option label="直接失败" value="fail_fast" />
          </el-select>
        </el-form-item>
        <el-form-item label="回退候选 Tool">
          <el-input
            v-model="editForm.backupToolCodesText"
            type="textarea"
            :rows="3"
            placeholder="一行一个 Tool Code，用于主 Tool 失败后的回退候选"
          />
        </el-form-item>
        <el-form-item label="运行时偏好">
          <el-input
            v-model="editForm.runtimeSelectionHint"
            type="textarea"
            :rows="2"
            placeholder="例如：优先在高置信度知识库命中时使用；外部依赖抖动时降级到摘要回答"
          />
        </el-form-item>
        <el-form-item label="降级提示">
          <el-input
            v-model="editForm.degradePrompt"
            type="textarea"
            :rows="2"
            placeholder="Tool 不可用或失败时给 Planner / Decision 的降级提示"
          />
        </el-form-item>
        <el-form-item label="连续失败告警阈值">
          <el-input-number
            v-model="editForm.consecutiveFailureAlertThreshold"
            :min="0"
            :max="999"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="SLA 成功率目标">
          <el-input-number
            v-model="editForm.slaTargetSuccessRate"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="Planner 优先选择">
          <el-switch v-model="editForm.preferredForPlanner" />
        </el-form-item>
        <el-form-item label="Decision 优先选择">
          <el-switch v-model="editForm.preferredForDecision" />
        </el-form-item>
        <el-form-item label="Input Schema">
          <el-input
            v-model="editForm.inputSchemaText"
            type="textarea"
            :rows="6"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="Output Schema">
          <el-input
            v-model="editForm.outputSchemaText"
            type="textarea"
            :rows="6"
            placeholder="请输入合法 JSON，例如 {}"
          />
        </el-form-item>
        <el-form-item label="高级 Metadata">
          <el-input
            v-model="editForm.metadataText"
            type="textarea"
            :rows="4"
            placeholder="请输入合法 JSON，例如 {}"
          />
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

    <el-drawer v-model="detailDrawerVisible" title="Tool 详情" size="620px">
      <div v-if="detailRecord" class="runtime-governance-card">
        <div class="runtime-governance-card__header">
          <div>
            <div class="runtime-governance-card__title">运行治理</div>
            <div class="runtime-governance-card__subtitle">查看最新健康检查结果，确认协议、Schema 和实例连通性。</div>
          </div>
          <el-button type="primary" plain :loading="healthChecking" @click="runDetailHealthCheck">健康检查</el-button>
        </div>
        <div class="runtime-governance-grid">
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">最新状态</div>
            <div class="runtime-governance-item__value">{{ latestHealthResult?.healthStatus || healthMetrics?.latestRecord?.healthStatus || "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">传输协议</div>
            <div class="runtime-governance-item__value">{{ latestHealthResult?.transportType || healthMetrics?.latestRecord?.transportType || detailRecord.transportType || "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">Schema 版本</div>
            <div class="runtime-governance-item__value">{{ latestHealthResult?.schemaVersion || extractSchemaVersion(detailRecord) }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">最近结果</div>
            <div class="runtime-governance-item__value">{{ latestHealthResult?.success ? "成功" : latestHealthResult ? "失败" : healthMetrics?.latestRecord ? (healthMetrics.latestRecord.success ? "成功" : "失败") : "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">历史成功率</div>
            <div class="runtime-governance-item__value">{{ healthMetrics ? `${healthMetrics.successRate.toFixed(2)}%` : "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">平均耗时</div>
            <div class="runtime-governance-item__value">{{ formatLatency(healthMetrics?.avgLatencyMs) }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">最近检查</div>
            <div class="runtime-governance-item__value runtime-governance-item__value--small">{{ formatDateTime(healthMetrics?.lastCheckedAt) }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">最近成功</div>
            <div class="runtime-governance-item__value runtime-governance-item__value--small">{{ formatDateTime(healthMetrics?.lastSuccessAt) }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">SLA 目标</div>
            <div class="runtime-governance-item__value">{{ healthMetrics ? `${healthMetrics.slaTargetSuccessRate.toFixed(2)}%` : "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">SLA 状态</div>
            <div class="runtime-governance-item__value">{{ healthMetrics ? (healthMetrics.slaBreached ? "已失守" : "正常") : "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">连续失败次数</div>
            <div class="runtime-governance-item__value">{{ healthMetrics?.consecutiveFailureCount ?? "-" }}</div>
          </div>
          <div class="runtime-governance-item">
            <div class="runtime-governance-item__label">告警阈值</div>
            <div class="runtime-governance-item__value">{{ healthMetrics?.consecutiveFailureAlertThreshold ?? "-" }}</div>
          </div>
        </div>
        <div v-if="healthMetrics?.latestFailureReason" class="runtime-governance-failure">
          <strong>最近失败原因：</strong>{{ healthMetrics.latestFailureReason }}
        </div>
        <div
          v-if="healthMetrics?.consecutiveFailureAlertTriggered || healthMetrics?.slaBreached"
          class="runtime-governance-alert"
        >
          <strong>实例级告警：</strong>
          <span v-if="healthMetrics?.consecutiveFailureAlertTriggered">
            连续失败 {{ healthMetrics?.consecutiveFailureCount }} 次，已达到阈值 {{ healthMetrics?.consecutiveFailureAlertThreshold }}。
          </span>
          <span v-if="healthMetrics?.slaBreached">
            当前成功率 {{ healthMetrics?.successRate.toFixed(2) }}%，低于 SLA 目标 {{ healthMetrics?.slaTargetSuccessRate.toFixed(2) }}%。
          </span>
        </div>
        <div v-if="healthMetrics?.trendBuckets?.length" class="runtime-governance-trend">
          <div class="runtime-governance-section-title">最近 7 天实例趋势</div>
          <div class="runtime-governance-trend-grid">
            <div v-for="bucket in healthMetrics.trendBuckets" :key="bucket.bucketKey" class="runtime-governance-trend-item">
              <div class="runtime-governance-trend-item__label">{{ bucket.bucketLabel }}</div>
              <div class="runtime-governance-trend-item__meta">
                <span>总 {{ bucket.totalCount }}</span>
                <span>成功 {{ bucket.successCount }}</span>
                <span>失败 {{ bucket.failedCount }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="latestHealthResult" class="runtime-governance-result">
          <div><strong>结果说明：</strong>{{ latestHealthResult.message || "-" }}</div>
          <pre class="json-block">{{ formatJson(latestHealthResult.output) }}</pre>
        </div>
        <div v-if="healthHistoryRecords.length" class="runtime-governance-history">
          <div class="runtime-governance-section-title">健康检查历史</div>
          <el-table :data="healthHistoryRecords" size="small" border>
            <el-table-column prop="checkedAt" label="检查时间" min-width="180" :formatter="formatTableDateTime" />
            <el-table-column prop="healthStatus" label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="healthTagType(row.healthStatus)">{{ row.healthStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latencyMs" label="耗时" min-width="100">
              <template #default="{ row }">
                {{ formatLatency(row.latencyMs) }}
              </template>
            </el-table-column>
            <el-table-column prop="statusCode" label="状态码" min-width="90" />
            <el-table-column prop="failureReason" label="失败原因" min-width="220" />
          </el-table>
          <div class="pagination-row runtime-governance-pagination">
            <el-pagination
              background
              layout="total, prev, pager, next"
              :total="healthHistoryTotal"
              :current-page="healthHistoryPageNum"
              :page-size="healthHistoryPageSize"
              @current-change="handleHealthHistoryPageChange"
            />
          </div>
        </div>
        <div v-if="healthMetrics?.relatedTraces?.length" class="runtime-governance-history">
          <div class="runtime-governance-section-title">最近相关 Trace</div>
          <el-table :data="healthMetrics.relatedTraces" size="small" border>
            <el-table-column prop="conversationCode" label="会话" min-width="150" />
            <el-table-column prop="taskCode" label="任务" min-width="150" />
            <el-table-column prop="traceId" label="Trace ID" min-width="180" />
            <el-table-column prop="spanName" label="Span" min-width="180" />
            <el-table-column prop="traceStatus" label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="traceTagType(row.traceStatus)">{{ row.traceStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开始时间" min-width="180" :formatter="formatTableDateTime" />
            <el-table-column label="操作" min-width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRelatedTrace(row)">查看 Trace</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      <el-descriptions :column="1" border v-if="detailRecord">
        <el-descriptions-item label="Tool 编码">{{ detailRecord.toolCode }}</el-descriptions-item>
        <el-descriptions-item label="Tool 名称">{{ detailRecord.toolName }}</el-descriptions-item>
        <el-descriptions-item label="Tool 类型">
          <el-tag v-if="detailRecord.toolType" effect="plain" type="primary" round>
            {{ dictLabel("tool_type", detailRecord.toolType) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Tool 分类">
          <el-tag v-if="detailRecord.toolCategory" effect="light" type="success" round>
            {{ dictLabel("tool_category", detailRecord.toolCategory) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="运行时能力">
          {{ detailRecord.runtimeCapabilities?.join(", ") || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="副作用等级">
          {{ detailRecord.sideEffectLevel || "none" }}
        </el-descriptions-item>
        <el-descriptions-item label="Provider 类型">
          <el-tag v-if="detailRecord.providerType" effect="plain" type="warning" round>
            {{ dictLabel("tool_provider_type", detailRecord.providerType) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="传输协议">
          <el-tag v-if="detailRecord.transportType" effect="light" type="info" round>
            {{ dictLabel("tool_transport_type", detailRecord.transportType) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="Provider 编码">{{ detailRecord.providerCode || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Provider 名称">{{ detailRecord.providerName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Provider 状态">{{ statusText(detailRecord.providerStatus) }}</el-descriptions-item>
        <el-descriptions-item label="配置 Schema">
          <pre class="json-block">{{ formatJson(detailRecord.providerConfigSchema) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="实例编码">{{ detailRecord.providerInstanceCode || "-" }}</el-descriptions-item>
        <el-descriptions-item label="实例名称">{{ detailRecord.providerInstanceName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="环境编码">{{ detailRecord.environmentCode || "-" }}</el-descriptions-item>
        <el-descriptions-item label="Endpoint">{{ detailRecord.endpoint || "-" }}</el-descriptions-item>
        <el-descriptions-item label="认证类型">
          {{ dictLabel("tool_auth_type", detailRecord.authType) }}
        </el-descriptions-item>
        <el-descriptions-item label="认证配置">
          <pre class="json-block">{{ formatJson(detailRecord.authConfig) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="默认请求头">
          <pre class="json-block">{{ formatJson(detailRecord.headers) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="实例超时">{{ detailRecord.providerTimeoutSeconds || "-" }}</el-descriptions-item>
        <el-descriptions-item label="重试次数">{{ detailRecord.retryTimes || 0 }}</el-descriptions-item>
        <el-descriptions-item label="实例状态">{{ statusText(detailRecord.providerInstanceStatus) }}</el-descriptions-item>
        <el-descriptions-item label="操作名称">{{ detailRecord.operationName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          {{ dictLabel("tool_operation_type", detailRecord.operationType) }}
        </el-descriptions-item>
        <el-descriptions-item label="绑定状态">{{ statusText(detailRecord.bindingStatus) }}</el-descriptions-item>
        <el-descriptions-item label="调用配置">
          <pre class="json-block">{{ formatJson(detailRecord.invokeConfig) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="参数映射">
          <pre class="json-block">{{ formatJson(detailRecord.argumentMapping) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输出映射">
          <pre class="json-block">{{ formatJson(detailRecord.outputMapping) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="权限等级">{{ dictLabel("permission_level", detailRecord.permissionLevel) }}</el-descriptions-item>
        <el-descriptions-item label="超时秒数">{{ formatTimeout(detailRecord.timeoutSeconds) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detailRecord.status) }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailRecord.description || "-" }}</el-descriptions-item>
        <el-descriptions-item label="执行 Prompt">
          {{ promptLabel(extractExecutionPromptId(asObject(detailRecord.metadata))) }}
        </el-descriptions-item>
        <el-descriptions-item label="能力说明">
          <pre class="json-block">{{ formatLineList(extractToolRouting(asObject(detailRecord.metadata)).capabilities) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="触发条件">
          <pre class="json-block">{{ formatLineList(extractToolRouting(asObject(detailRecord.metadata)).triggerConditions) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="限制条件">
          <pre class="json-block">{{ formatLineList(extractToolRouting(asObject(detailRecord.metadata)).limitations) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输入前提">
          <pre class="json-block">{{ formatLineList(extractToolRouting(asObject(detailRecord.metadata)).inputPrerequisites) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="副作用">
          {{ extractToolRouting(asObject(detailRecord.metadata)).sideEffects || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="风险说明">
          <pre class="json-block">{{ formatLineList(extractToolRouting(asObject(detailRecord.metadata)).riskNotes) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="拦截关键词">
          <pre class="json-block">{{ formatLineList(extractToolGuardrails(asObject(detailRecord.metadata)).blockedKeywords) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="最大输入长度">
          {{ extractToolGuardrails(asObject(detailRecord.metadata)).maxInputLength || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="执行前审批">
          {{ extractToolApproval(asObject(detailRecord.metadata)).required ? "是" : "否" }}
        </el-descriptions-item>
        <el-descriptions-item label="审批模式">
          {{ extractToolApproval(asObject(detailRecord.metadata)).mode || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="审批关键词">
          <pre class="json-block">{{ formatLineList(extractToolApproval(asObject(detailRecord.metadata)).keywords) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="审批原因">
          {{ extractToolApproval(asObject(detailRecord.metadata)).reason || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="Fallback 动作">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).fallbackAction || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="回退候选 Tool">
          <pre class="json-block">{{ formatLineList(extractToolGovernance(asObject(detailRecord.metadata)).backupToolCodes) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="运行时偏好">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).runtimeSelectionHint || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="降级提示">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).degradePrompt || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="连续失败告警阈值">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).consecutiveFailureAlertThreshold || "-" }}
        </el-descriptions-item>
        <el-descriptions-item label="SLA 成功率目标">
          {{
            extractToolGovernance(asObject(detailRecord.metadata)).slaTargetSuccessRate
              ? `${extractToolGovernance(asObject(detailRecord.metadata)).slaTargetSuccessRate}%`
              : "-"
          }}
        </el-descriptions-item>
        <el-descriptions-item label="Planner 优先选择">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).preferredForPlanner ? "是" : "否" }}
        </el-descriptions-item>
        <el-descriptions-item label="Decision 优先选择">
          {{ extractToolGovernance(asObject(detailRecord.metadata)).preferredForDecision ? "是" : "否" }}
        </el-descriptions-item>
        <el-descriptions-item label="Input Schema">
          <pre class="json-block">{{ formatJson(detailRecord.inputSchema) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="Output Schema">
          <pre class="json-block">{{ formatJson(detailRecord.outputSchema) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="高级 Metadata">
          <pre class="json-block">{{ formatJson(detailRecord.metadata) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailRecord.remark || "-" }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无详情数据" />
    </el-drawer>
    <ToolLifecycleDrawer
      v-model="lifecycleDrawerVisible"
      :tool-id="lifecycleToolId"
      :tool-name="lifecycleToolName"
    />
    <ToolCapabilityPackDrawer v-model="capabilityPackDrawerVisible" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime } from "@/utils/dateTime";
import ToolLifecycleDrawer from "@/views/tool/ToolLifecycleDrawer.vue";
import ToolCapabilityPackDrawer from "@/views/tool/ToolCapabilityPackDrawer.vue";
import { fetchDictOptions, type DictOptionItem } from "@/api/dict";
import { fetchPromptDetail, fetchPromptPage } from "@/api/prompt";
import {
  debugTool,
  fetchToolHealthHistoryMetrics,
  fetchToolHealthHistoryPage,
  healthCheckTool,
  type ToolHealthCheckHistoryRecord,
  type ToolHealthCheckMetrics
} from "@/api/toolDebug";
import {
  createTool,
  disableTool,
  enableTool,
  fetchToolDetail,
  fetchToolRuntimeDetail,
  fetchToolPage,
  updateTool,
  type ToolDetail,
  type ToolRecord
} from "@/api/tool";

interface QueryForm {
  pageNum: number;
  pageSize: number;
  toolCode: string;
  toolName: string;
  toolType: string;
  toolCategory: string;
  status: string;
}

interface EditForm {
  importJsonText: string;
  toolCode: string;
  toolName: string;
  toolType: string;
  toolCategory: string;
  runtimeCapabilities: string[];
  description: string;
  providerCode: string;
  providerName: string;
  providerType: string;
  transportType: string;
  providerConfigSchemaText: string;
  providerStatus: string;
  providerInstanceCode: string;
  providerInstanceName: string;
  environmentCode: string;
  endpoint: string;
  authType: string;
  authConfigText: string;
  headersText: string;
  providerTimeoutSeconds: number;
  retryTimes: number;
  providerInstanceStatus: string;
  operationName: string;
  operationType: string;
  bindingStatus: string;
  invokeConfigText: string;
  argumentMappingText: string;
  outputMappingText: string;
  executionPromptId: number | null;
  capabilitiesText: string;
  triggerConditionsText: string;
  limitationsText: string;
  inputPrerequisitesText: string;
  sideEffects: string;
  sideEffectLevel: string;
  idempotencyMode: string;
  compensationToolCode: string;
  riskNotesText: string;
  blockedKeywordsText: string;
  maxInputLength: number;
  approvalRequired: boolean;
  approvalMode: string;
  approvalKeywordsText: string;
  approvalReason: string;
  fallbackAction: string;
  backupToolCodesText: string;
  runtimeSelectionHint: string;
  degradePrompt: string;
  consecutiveFailureAlertThreshold: number;
  slaTargetSuccessRate: number;
  preferredForPlanner: boolean;
  preferredForDecision: boolean;
  inputSchemaText: string;
  outputSchemaText: string;
  permissionLevel: string;
  timeoutSeconds: number;
  status: string;
  metadataText: string;
  remark: string;
}

interface ResourceOption {
  value: number;
  label: string;
}

const runtimeCapabilityOptions = [
  { value: "filesystem.read", label: "文件读取 (filesystem.read)" },
  { value: "filesystem.write", label: "文件写入 (filesystem.write)" },
  { value: "network.read", label: "网络读取 (network.read)" },
  { value: "network.write", label: "网络写入 (network.write)" },
  { value: "process.execute", label: "进程执行 (process.execute)" },
  { value: "database.read", label: "数据库读取 (database.read)" },
  { value: "database.write", label: "数据库写入 (database.write)" },
  { value: "artifact.publish", label: "产物发布 (artifact.publish)" },
  { value: "external.side_effect", label: "外部副作用 (external.side_effect)" }
] as const;

type JsonObject = Record<string, unknown>;

const loading = ref(false);
const total = ref(0);
const toolRecords = ref<ToolRecord[]>([]);
const detailRecord = ref<ToolDetail | null>(null);
const detailDrawerVisible = ref(false);
const editDialogVisible = ref(false);
const editingToolId = ref<number | null>(null);
const lifecycleDrawerVisible = ref(false);
const capabilityPackDrawerVisible = ref(false);
const lifecycleToolId = ref<number | null>(null);
const lifecycleToolName = ref("");
const saving = ref(false);
const healthChecking = ref(false);
const latestHealthResult = ref<Awaited<ReturnType<typeof healthCheckTool>> | null>(null);
const healthMetrics = ref<ToolHealthCheckMetrics | null>(null);
const healthHistoryRecords = ref<ToolHealthCheckHistoryRecord[]>([]);
const healthHistoryTotal = ref(0);
const healthHistoryPageNum = ref(1);
const healthHistoryPageSize = ref(10);
const dictOptionMap = ref<Record<string, DictOptionItem[]>>({});
const promptOptions = ref<ResourceOption[]>([]);

const queryForm = reactive<QueryForm>({
  pageNum: 1,
  pageSize: 10,
  toolCode: "",
  toolName: "",
  toolType: "",
  toolCategory: "",
  status: ""
});

const editForm = reactive<EditForm>({
  importJsonText: "",
  toolCode: "",
  toolName: "",
  toolType: "",
  toolCategory: "",
  runtimeCapabilities: [],
  description: "",
  providerCode: "",
  providerName: "",
  providerType: "internal",
  transportType: "in_process",
  providerConfigSchemaText: "{}",
  providerStatus: "enabled",
  providerInstanceCode: "",
  providerInstanceName: "",
  environmentCode: "default",
  endpoint: "",
  authType: "none",
  authConfigText: "{}",
  headersText: "{}",
  providerTimeoutSeconds: 30,
  retryTimes: 0,
  providerInstanceStatus: "enabled",
  operationName: "",
  operationType: "internal_method",
  bindingStatus: "enabled",
  invokeConfigText: "{}",
  argumentMappingText: "{}",
  outputMappingText: "{}",
  executionPromptId: null,
  capabilitiesText: "",
  triggerConditionsText: "",
  limitationsText: "",
  inputPrerequisitesText: "",
  sideEffects: "",
  sideEffectLevel: "none",
  idempotencyMode: "generated",
  compensationToolCode: "",
  riskNotesText: "",
  blockedKeywordsText: "",
  maxInputLength: 0,
  approvalRequired: false,
  approvalMode: "always",
  approvalKeywordsText: "",
  approvalReason: "",
  fallbackAction: "switch_candidate",
  backupToolCodesText: "",
  runtimeSelectionHint: "",
  degradePrompt: "",
  consecutiveFailureAlertThreshold: 0,
  slaTargetSuccessRate: 0,
  preferredForPlanner: false,
  preferredForDecision: false,
  inputSchemaText: "{}",
  outputSchemaText: "{}",
  permissionLevel: "default",
  timeoutSeconds: 30,
  status: "draft",
  metadataText: "{}",
  remark: ""
});

function mcpTransportHint(): string {
  if (editForm.transportType === "stdio") {
    return "stdio 不使用 Endpoint；请在调用配置中填写 command、args、environment、workingDirectory，运行时使用 ProcessBuilder 直接启动，不经过 Shell。";
  }
  if (editForm.transportType === "sse") {
    return "legacy SSE 的 Endpoint 填长期 GET 事件流地址；运行时从 endpoint 事件发现 JSON-RPC POST 地址。";
  }
  return "Streamable HTTP 的 Endpoint 填 MCP 单一 HTTP 地址，运行时完成 initialize、initialized 和 session 管理。";
}

function mcpInvokeConfigPlaceholder(): string {
  if (editForm.providerType === "mcp" && editForm.transportType === "stdio") {
    return '{\n  "command": "npx",\n  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/data"],\n  "environment": {},\n  "inheritEnvironment": true,\n  "workingDirectory": "/opt/agent"\n}';
  }
  return "请输入合法 JSON，例如 {}";
}

async function loadTools(): Promise<void> {
  loading.value = true;
  try {
    const page = await fetchToolPage(queryForm);
    toolRecords.value = page.records;
    total.value = page.total;
  } catch (error) {
    toolRecords.value = [];
    total.value = 0;
    ElMessage.error("Tool 列表加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadDictOptionMap(): Promise<void> {
  try {
    dictOptionMap.value = await fetchDictOptions([
      "tool_type",
      "tool_category",
      "tool_provider_type",
      "tool_transport_type",
      "tool_auth_type",
      "tool_operation_type",
      "permission_level",
      "prompt_type",
      "prompt_category"
    ]) || {};
  } catch (error) {
    dictOptionMap.value = {};
    ElMessage.error("字典选项加载失败");
  }
}

async function loadPromptOptions(): Promise<void> {
  try {
    const page = await fetchPromptPage({
      pageNum: 1,
      pageSize: 100,
      promptType: "tool_execution",
      status: "enabled"
    });
    promptOptions.value = page.records.map((item) => ({
      value: item.promptId,
      label: formatPromptOptionLabel(item.promptName, item.promptCode, item.promptType, item.promptCategory)
    }));
  } catch (error) {
    promptOptions.value = [];
    ElMessage.error("Prompt 下拉选项加载失败");
  }
}

async function ensurePromptOption(promptId: number | null): Promise<void> {
  if (!promptId || promptOptions.value.some((item) => item.value === promptId)) {
    return;
  }
  const detail = await fetchPromptDetail(promptId);
  promptOptions.value = [
    ...promptOptions.value,
    {
      value: detail.promptId,
      label: formatPromptOptionLabel(detail.promptName, detail.promptCode, detail.promptType, detail.promptCategory)
    }
  ];
}

async function openDetailDrawer(toolId: number): Promise<void> {
  detailDrawerVisible.value = true;
  latestHealthResult.value = null;
  healthMetrics.value = null;
  healthHistoryRecords.value = [];
  healthHistoryTotal.value = 0;
  healthHistoryPageNum.value = 1;
  try {
    detailRecord.value = await fetchToolDetail(toolId);
    await Promise.all([
      loadPromptOptions(),
      ensurePromptOption(extractExecutionPromptId(asObject(detailRecord.value?.metadata))),
      loadHealthGovernance(toolId, 1)
    ]);
  } catch (error) {
    detailRecord.value = null;
    ElMessage.error("Tool 详情加载失败");
  }
}

function openLifecycleDrawer(tool: ToolRecord): void {
  lifecycleToolId.value = tool.toolId;
  lifecycleToolName.value = tool.toolName;
  lifecycleDrawerVisible.value = true;
}

async function runDetailHealthCheck(): Promise<void> {
  if (!detailRecord.value) {
    return;
  }
  healthChecking.value = true;
  try {
    latestHealthResult.value = await healthCheckTool(detailRecord.value.toolId);
    await loadHealthGovernance(detailRecord.value.toolId, healthHistoryPageNum.value);
    ElMessage.success(latestHealthResult.value.message || "健康检查成功");
  } catch (error) {
    latestHealthResult.value = null;
    ElMessage.error("健康检查失败");
  } finally {
    healthChecking.value = false;
  }
}

async function openEditDialog(toolId: number): Promise<void> {
  editingToolId.value = toolId;
  editDialogVisible.value = true;
  try {
    const [detail] = await Promise.all([
      fetchToolRuntimeDetail(toolId),
      loadPromptOptions()
    ]);
    const metadata = asObject(detail.metadata);
    const routing = extractToolRouting(metadata);
    const guardrails = extractToolGuardrails(metadata);
    const approval = extractToolApproval(metadata);
    const governance = extractToolGovernance(metadata);
    const sideEffectPolicy = extractSideEffectPolicy(metadata);
    const executionPromptId = extractExecutionPromptId(metadata);
    await ensurePromptOption(executionPromptId);
    editForm.importJsonText = "";
    editForm.toolCode = detail.toolCode;
    editForm.toolName = detail.toolName;
    editForm.toolType = detail.toolType || "";
    editForm.toolCategory = detail.toolCategory || "";
    editForm.runtimeCapabilities = detail.runtimeCapabilities || [];
    editForm.description = detail.description || "";
    editForm.providerCode = detail.providerCode || "";
    editForm.providerName = detail.providerName || "";
    editForm.providerType = detail.providerType || "internal";
    editForm.transportType = detail.transportType || "in_process";
    editForm.providerConfigSchemaText = stringifyJson(detail.providerConfigSchema);
    editForm.providerStatus = detail.providerStatus || "enabled";
    editForm.providerInstanceCode = detail.providerInstanceCode || "";
    editForm.providerInstanceName = detail.providerInstanceName || "";
    editForm.environmentCode = detail.environmentCode || "default";
    editForm.endpoint = detail.endpoint || "";
    editForm.authType = detail.authType || "none";
    editForm.authConfigText = stringifyJson(detail.authConfig);
    editForm.headersText = stringifyJson(detail.headers);
    editForm.providerTimeoutSeconds = detail.providerTimeoutSeconds || 30;
    editForm.retryTimes = detail.retryTimes || 0;
    editForm.providerInstanceStatus = detail.providerInstanceStatus || "enabled";
    editForm.operationName = detail.operationName || "";
    editForm.operationType = detail.operationType || "internal_method";
    editForm.bindingStatus = detail.bindingStatus || "enabled";
    editForm.invokeConfigText = stringifyJson(detail.invokeConfig);
    editForm.argumentMappingText = stringifyJson(detail.argumentMapping);
    editForm.outputMappingText = stringifyJson(detail.outputMapping);
    editForm.executionPromptId = executionPromptId;
    editForm.capabilitiesText = joinLines(routing.capabilities);
    editForm.triggerConditionsText = joinLines(routing.triggerConditions);
    editForm.limitationsText = joinLines(routing.limitations);
    editForm.inputPrerequisitesText = joinLines(routing.inputPrerequisites);
    editForm.sideEffects = routing.sideEffects;
    editForm.sideEffectLevel = detail.sideEffectLevel || sideEffectPolicy.level;
    editForm.idempotencyMode = sideEffectPolicy.idempotencyMode;
    editForm.compensationToolCode = sideEffectPolicy.compensationToolCode;
    editForm.riskNotesText = joinLines(routing.riskNotes);
    editForm.blockedKeywordsText = joinLines(guardrails.blockedKeywords);
    editForm.maxInputLength = guardrails.maxInputLength;
    editForm.approvalRequired = approval.required;
    editForm.approvalMode = approval.mode;
    editForm.approvalKeywordsText = joinLines(approval.keywords);
    editForm.approvalReason = approval.reason;
    editForm.fallbackAction = governance.fallbackAction;
    editForm.backupToolCodesText = joinLines(governance.backupToolCodes);
    editForm.runtimeSelectionHint = governance.runtimeSelectionHint;
    editForm.degradePrompt = governance.degradePrompt;
    editForm.consecutiveFailureAlertThreshold = governance.consecutiveFailureAlertThreshold;
    editForm.slaTargetSuccessRate = governance.slaTargetSuccessRate;
    editForm.preferredForPlanner = governance.preferredForPlanner;
    editForm.preferredForDecision = governance.preferredForDecision;
    editForm.inputSchemaText = stringifyJson(detail.inputSchema);
    editForm.outputSchemaText = stringifyJson(detail.outputSchema);
    editForm.permissionLevel = detail.permissionLevel || "default";
    editForm.timeoutSeconds = detail.timeoutSeconds || 30;
    editForm.status = detail.status;
    editForm.metadataText = stringifyJson(detail.metadata);
    editForm.remark = detail.remark || "";
  } catch (error) {
    ElMessage.error("Tool 详情加载失败");
  }
}

function openCreateDialog(): void {
  editingToolId.value = null;
  editDialogVisible.value = true;
  void loadPromptOptions();
  editForm.importJsonText = "";
  editForm.toolCode = "";
  editForm.toolName = "";
  editForm.toolType = "";
  editForm.toolCategory = "";
  editForm.runtimeCapabilities = [];
  editForm.description = "";
  editForm.providerCode = "";
  editForm.providerName = "";
  editForm.providerType = "internal";
  editForm.transportType = "in_process";
  editForm.providerConfigSchemaText = "{}";
  editForm.providerStatus = "enabled";
  editForm.providerInstanceCode = "";
  editForm.providerInstanceName = "";
  editForm.environmentCode = "default";
  editForm.endpoint = "";
  editForm.authType = "none";
  editForm.authConfigText = "{}";
  editForm.headersText = "{}";
  editForm.providerTimeoutSeconds = 30;
  editForm.retryTimes = 0;
  editForm.providerInstanceStatus = "enabled";
  editForm.operationName = "";
  editForm.operationType = "internal_method";
  editForm.bindingStatus = "enabled";
  editForm.invokeConfigText = "{}";
  editForm.argumentMappingText = "{}";
  editForm.outputMappingText = "{}";
  editForm.executionPromptId = null;
  editForm.capabilitiesText = "";
  editForm.triggerConditionsText = "";
  editForm.limitationsText = "";
  editForm.inputPrerequisitesText = "";
  editForm.sideEffects = "";
  editForm.sideEffectLevel = "none";
  editForm.idempotencyMode = "generated";
  editForm.compensationToolCode = "";
  editForm.riskNotesText = "";
  editForm.blockedKeywordsText = "";
  editForm.maxInputLength = 0;
  editForm.approvalRequired = false;
  editForm.approvalMode = "always";
  editForm.approvalKeywordsText = "";
  editForm.approvalReason = "";
  editForm.fallbackAction = "switch_candidate";
  editForm.backupToolCodesText = "";
  editForm.runtimeSelectionHint = "";
  editForm.degradePrompt = "";
  editForm.consecutiveFailureAlertThreshold = 0;
  editForm.slaTargetSuccessRate = 0;
  editForm.preferredForPlanner = false;
  editForm.preferredForDecision = false;
  editForm.inputSchemaText = "{}";
  editForm.outputSchemaText = "{}";
  editForm.permissionLevel = "default";
  editForm.timeoutSeconds = 30;
  editForm.status = "draft";
  editForm.metadataText = "{}";
  editForm.remark = "";
}

async function fillFormByImportJson(): Promise<void> {
  const importData = parseToolImportJson(editForm.importJsonText);
  if (!importData) {
    return;
  }

  const metadata = asObject(importData.metadata);
  const routing = extractToolRouting(metadata);
  const guardrails = extractToolGuardrails(metadata);
  const approval = extractToolApproval(metadata);
  const governance = extractToolGovernance(metadata);
  const sideEffectPolicy = extractSideEffectPolicy(metadata);
  const executionPromptId = extractExecutionPromptId(metadata);
  await ensurePromptOption(executionPromptId);

  editForm.toolCode = asString(importData.toolCode);
  editForm.toolName = asString(importData.toolName);
  editForm.toolType = asString(importData.toolType);
  editForm.toolCategory = asString(importData.toolCategory);
  editForm.runtimeCapabilities = asStringArray(importData.runtimeCapabilities);
  editForm.description = asString(importData.description);
  editForm.providerCode = asString(importData.providerCode);
  editForm.providerName = asString(importData.providerName);
  editForm.providerType = asString(importData.providerType) || "internal";
  editForm.transportType = asString(importData.transportType) || "in_process";
  editForm.providerConfigSchemaText = stringifyJson(asObject(importData.providerConfigSchema));
  editForm.providerStatus = asString(importData.providerStatus) || "enabled";
  editForm.providerInstanceCode = asString(importData.providerInstanceCode);
  editForm.providerInstanceName = asString(importData.providerInstanceName);
  editForm.environmentCode = asString(importData.environmentCode) || "default";
  editForm.endpoint = asString(importData.endpoint);
  editForm.authType = asString(importData.authType) || "none";
  editForm.authConfigText = stringifyJson(asObject(importData.authConfig));
  editForm.headersText = stringifyJson(asObject(importData.headers));
  editForm.providerTimeoutSeconds = toPositiveNumber(importData.providerTimeoutSeconds, 30);
  editForm.retryTimes = toNonNegativeNumber(importData.retryTimes, 0);
  editForm.providerInstanceStatus = asString(importData.providerInstanceStatus) || "enabled";
  editForm.operationName = asString(importData.operationName);
  editForm.operationType = asString(importData.operationType) || "internal_method";
  editForm.bindingStatus = asString(importData.bindingStatus) || "enabled";
  editForm.invokeConfigText = stringifyJson(asObject(importData.invokeConfig));
  editForm.argumentMappingText = stringifyJson(asObject(importData.argumentMapping));
  editForm.outputMappingText = stringifyJson(asObject(importData.outputMapping));
  editForm.executionPromptId = executionPromptId;
  editForm.capabilitiesText = joinLines(routing.capabilities);
  editForm.triggerConditionsText = joinLines(routing.triggerConditions);
  editForm.limitationsText = joinLines(routing.limitations);
  editForm.inputPrerequisitesText = joinLines(routing.inputPrerequisites);
  editForm.sideEffects = routing.sideEffects;
  editForm.sideEffectLevel = asString(importData.sideEffectLevel) || sideEffectPolicy.level;
  editForm.idempotencyMode = sideEffectPolicy.idempotencyMode;
  editForm.compensationToolCode = sideEffectPolicy.compensationToolCode;
  editForm.riskNotesText = joinLines(routing.riskNotes);
  editForm.blockedKeywordsText = joinLines(guardrails.blockedKeywords);
  editForm.maxInputLength = guardrails.maxInputLength;
  editForm.approvalRequired = approval.required;
  editForm.approvalMode = approval.mode;
  editForm.approvalKeywordsText = joinLines(approval.keywords);
  editForm.approvalReason = approval.reason;
  editForm.fallbackAction = governance.fallbackAction;
  editForm.backupToolCodesText = joinLines(governance.backupToolCodes);
  editForm.runtimeSelectionHint = governance.runtimeSelectionHint;
  editForm.degradePrompt = governance.degradePrompt;
  editForm.consecutiveFailureAlertThreshold = governance.consecutiveFailureAlertThreshold;
  editForm.slaTargetSuccessRate = governance.slaTargetSuccessRate;
  editForm.preferredForPlanner = governance.preferredForPlanner;
  editForm.preferredForDecision = governance.preferredForDecision;
  editForm.inputSchemaText = stringifyJson(asObject(importData.inputSchema));
  editForm.outputSchemaText = stringifyJson(asObject(importData.outputSchema));
  editForm.permissionLevel = asString(importData.permissionLevel) || "default";
  editForm.timeoutSeconds = toPositiveNumber(importData.timeoutSeconds, 30);
  editForm.status = asString(importData.status) || "draft";
  editForm.metadataText = stringifyJson(metadata);
  editForm.remark = asString(importData.remark);

  ElMessage.success("JSON 已自动填充到表单");
}

async function submitEditForm(): Promise<void> {
  if (!editForm.toolCode.trim() && !editingToolId.value) {
    ElMessage.warning("请输入 Tool 编码");
    return;
  }
  if (!editForm.toolName.trim()) {
    ElMessage.warning("请输入 Tool 名称");
    return;
  }
  if (!editForm.toolType.trim()) {
    ElMessage.warning("请输入 Tool 类型");
    return;
  }
  if (!editForm.toolCategory.trim()) {
    ElMessage.warning("请输入 Tool 分类");
    return;
  }
  if (!editForm.providerCode.trim()) {
    ElMessage.warning("请输入 Provider 编码");
    return;
  }
  if (!editForm.providerName.trim()) {
    ElMessage.warning("请输入 Provider 名称");
    return;
  }
  if (!editForm.providerInstanceCode.trim()) {
    ElMessage.warning("请输入 Provider 实例编码");
    return;
  }
  if (!editForm.providerInstanceName.trim()) {
    ElMessage.warning("请输入 Provider 实例名称");
    return;
  }
  if (!editForm.operationName.trim()) {
    ElMessage.warning("请输入绑定操作名称");
    return;
  }
  if (!editForm.permissionLevel.trim()) {
    ElMessage.warning("请输入权限等级");
    return;
  }
  if (editForm.sideEffectLevel === "compensatable" && !editForm.compensationToolCode.trim()) {
    ElMessage.warning("可补偿副作用必须配置补偿 Tool Code");
    return;
  }

  const inputSchema = parseJsonText(editForm.inputSchemaText, "Input Schema");
  if (!inputSchema) {
    return;
  }
  const outputSchema = parseJsonText(editForm.outputSchemaText, "Output Schema");
  if (!outputSchema) {
    return;
  }
  const providerConfigSchema = parseJsonText(editForm.providerConfigSchemaText, "Provider Config Schema");
  if (!providerConfigSchema) {
    return;
  }
  const authConfig = parseJsonText(editForm.authConfigText, "Auth Config");
  if (!authConfig) {
    return;
  }
  const headers = parseJsonText(editForm.headersText, "Headers");
  if (!headers) {
    return;
  }
  const invokeConfig = parseJsonText(editForm.invokeConfigText, "Invoke Config");
  if (!invokeConfig) {
    return;
  }
  const argumentMapping = parseJsonText(editForm.argumentMappingText, "Argument Mapping");
  if (!argumentMapping) {
    return;
  }
  const outputMapping = parseJsonText(editForm.outputMappingText, "Output Mapping");
  if (!outputMapping) {
    return;
  }
  const metadata = parseJsonText(editForm.metadataText, "Metadata");
  if (!metadata) {
    return;
  }
  const mergedMetadata = buildToolMetadata(metadata);

  saving.value = true;
  try {
    const payload = {
      toolCode: editForm.toolCode,
      toolName: editForm.toolName,
      toolType: editForm.toolType,
      toolCategory: editForm.toolCategory,
      runtimeCapabilities: editForm.runtimeCapabilities,
      sideEffectLevel: editForm.sideEffectLevel,
      description: editForm.description,
      inputSchema,
      outputSchema,
      providerCode: editForm.providerCode,
      providerName: editForm.providerName,
      providerType: editForm.providerType,
      transportType: editForm.transportType,
      providerConfigSchema,
      providerStatus: editForm.providerStatus,
      providerInstanceCode: editForm.providerInstanceCode,
      providerInstanceName: editForm.providerInstanceName,
      environmentCode: editForm.environmentCode,
      endpoint: editForm.endpoint,
      authType: editForm.authType,
      authConfig,
      headers,
      providerTimeoutSeconds: editForm.providerTimeoutSeconds,
      retryTimes: editForm.retryTimes,
      providerInstanceStatus: editForm.providerInstanceStatus,
      operationName: editForm.operationName,
      operationType: editForm.operationType,
      invokeConfig,
      argumentMapping,
      outputMapping,
      bindingStatus: editForm.bindingStatus,
      permissionLevel: editForm.permissionLevel,
      timeoutSeconds: editForm.timeoutSeconds,
      status: editForm.status,
      metadata: mergedMetadata,
      remark: editForm.remark
    };
    if (editingToolId.value) {
      await updateTool(editingToolId.value, payload);
      ElMessage.success("Tool 更新成功");
    } else {
      await createTool(payload);
      ElMessage.success("Tool 创建成功");
    }
    editDialogVisible.value = false;
    await loadTools();
  } catch (error) {
    ElMessage.error("Tool 保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggleToolStatus(row: ToolRecord): Promise<void> {
  try {
    if (row.status === "enabled") {
      await disableTool(row.toolId);
      ElMessage.success("Tool 已停用");
    } else {
      await enableTool(row.toolId);
      ElMessage.success("Tool 已启用");
    }
    await loadTools();
  } catch (error) {
    ElMessage.error("Tool 状态更新失败");
  }
}

async function runConnectivityTest(toolId: number): Promise<void> {
  try {
    const result = await healthCheckTool(toolId);
    if (result.success) {
      ElMessage.success(`${result.message || "健康检查成功"} / ${result.transportType || "-"} / ${result.schemaVersion || "v1"}`);
      return;
    }
    ElMessage.error(result.message || "连通性测试失败");
  } catch (error) {
    ElMessage.error("连通性测试失败");
  }
}

async function loadHealthGovernance(toolId: number, pageNum = 1): Promise<void> {
  const [metrics, page] = await Promise.all([
    fetchToolHealthHistoryMetrics(toolId),
    fetchToolHealthHistoryPage(toolId, pageNum, healthHistoryPageSize.value)
  ]);
  healthMetrics.value = metrics;
  healthHistoryRecords.value = page.records;
  healthHistoryTotal.value = page.total;
  healthHistoryPageNum.value = page.pageNum;
}

async function handleHealthHistoryPageChange(pageNum: number): Promise<void> {
  if (!detailRecord.value) {
    return;
  }
  await loadHealthGovernance(detailRecord.value.toolId, pageNum);
}

function extractSchemaVersion(detail: ToolDetail): string {
  const metadata = asObject(detail.metadata);
  const schemaVersion = typeof metadata.schemaVersion === "string" ? metadata.schemaVersion : null;
  if (schemaVersion) {
    return schemaVersion;
  }
  const invokeConfig = asObject(detail.invokeConfig);
  return typeof invokeConfig.schemaVersion === "string" ? invokeConfig.schemaVersion : "v1";
}

function formatLatency(value: number | null | undefined): string {
  if (value == null || value <= 0) {
    return "-";
  }
  if (value < 1000) {
    return `${value} ms`;
  }
  return `${(value / 1000).toFixed(2)} s`;
}

function healthTagType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "healthy") {
    return "success";
  }
  if (status === "unhealthy") {
    return "danger";
  }
  if (status === "unknown") {
    return "warning";
  }
  return "info";
}

function traceTagType(status: string): "success" | "warning" | "danger" | "info" {
  if (status === "success") {
    return "success";
  }
  if (status === "failed") {
    return "danger";
  }
  if (status === "waiting_approval") {
    return "warning";
  }
  return "info";
}

function openRelatedTrace(record: { taskId: number; traceId: string }): void {
  const url = `/traces?taskId=${record.taskId}&traceId=${encodeURIComponent(record.traceId)}`;
  window.open(url, "_blank");
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 10;
  queryForm.toolCode = "";
  queryForm.toolName = "";
  queryForm.toolType = "";
  queryForm.toolCategory = "";
  queryForm.status = "";
  void loadTools();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadTools();
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

function formatTimeout(timeoutSeconds: number | null): string {
  return timeoutSeconds ? `${timeoutSeconds}s` : "-";
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

function parseToolImportJson(text: string): Record<string, unknown> | null {
  if (!text.trim()) {
    ElMessage.warning("请输入用于导入的 Tool JSON");
    return null;
  }
  return parseJsonText(text, "导入 JSON");
}

function asObject(value: unknown): JsonObject {
  if (!value || Array.isArray(value) || typeof value !== "object") {
    return {};
  }
  return value as JsonObject;
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function toPositiveNumber(value: unknown, defaultValue: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : defaultValue;
}

function toNonNegativeNumber(value: unknown, defaultValue: number): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric >= 0 ? numeric : defaultValue;
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((item): item is string => typeof item === "string")
    .map((item) => item.trim())
    .filter(Boolean);
}

function extractExecutionPromptId(metadata: JsonObject): number | null {
  const promptRefs = asObject(metadata.promptRefs);
  const promptId = promptRefs.executionPromptId;
  return typeof promptId === "number" && promptId > 0 ? promptId : null;
}

function extractToolRouting(metadata: JsonObject): {
  capabilities: string[];
  triggerConditions: string[];
  limitations: string[];
  inputPrerequisites: string[];
  sideEffects: string;
  riskNotes: string[];
} {
  const routing = asObject(metadata.routing);
  return {
    capabilities: asStringArray(routing.capabilities),
    triggerConditions: asStringArray(routing.triggerConditions),
    limitations: asStringArray(routing.limitations),
    inputPrerequisites: asStringArray(routing.inputPrerequisites),
    sideEffects: asString(routing.sideEffects),
    riskNotes: asStringArray(routing.riskNotes)
  };
}

function extractToolGuardrails(metadata: JsonObject): {
  blockedKeywords: string[];
  maxInputLength: number;
} {
  const guardrails = asObject(metadata.guardrails);
  const maxInputLength = Number(guardrails.maxInputLength);
  return {
    blockedKeywords: asStringArray(guardrails.blockedKeywords),
    maxInputLength: Number.isFinite(maxInputLength) && maxInputLength > 0 ? maxInputLength : 0
  };
}

function extractToolApproval(metadata: JsonObject): {
  required: boolean;
  mode: string;
  keywords: string[];
  reason: string;
} {
  const approval = asObject(metadata.approval);
  return {
    required: Boolean(approval.required),
    mode: asString(approval.mode) || "always",
    keywords: asStringArray(approval.keywords),
    reason: asString(approval.reason)
  };
}

function extractToolGovernance(metadata: JsonObject): {
  fallbackAction: string;
  backupToolCodes: string[];
  runtimeSelectionHint: string;
  degradePrompt: string;
  consecutiveFailureAlertThreshold: number;
  slaTargetSuccessRate: number;
  preferredForPlanner: boolean;
  preferredForDecision: boolean;
} {
  const fallbackPolicy = asObject(metadata.fallbackPolicy);
  const runtimeSelection = asObject(metadata.runtimeSelection);
  const toolGovernance = asObject(metadata.toolGovernance);
  const consecutiveFailureAlertThreshold = Number(toolGovernance.consecutiveFailureAlertThreshold);
  const slaTargetSuccessRate = Number(toolGovernance.slaTargetSuccessRate);
  return {
    fallbackAction: asString(fallbackPolicy.action) || "switch_candidate",
    backupToolCodes: asStringArray(fallbackPolicy.backupToolCodes),
    runtimeSelectionHint: asString(runtimeSelection.hint),
    degradePrompt: asString(fallbackPolicy.degradePrompt),
    consecutiveFailureAlertThreshold:
      Number.isFinite(consecutiveFailureAlertThreshold) && consecutiveFailureAlertThreshold > 0
        ? consecutiveFailureAlertThreshold
        : 0,
    slaTargetSuccessRate:
      Number.isFinite(slaTargetSuccessRate) && slaTargetSuccessRate > 0
        ? slaTargetSuccessRate
        : 0,
    preferredForPlanner: Boolean(runtimeSelection.preferredForPlanner),
    preferredForDecision: Boolean(runtimeSelection.preferredForDecision)
  };
}

function extractSideEffectPolicy(metadata: JsonObject): {
  level: string;
  idempotencyMode: string;
  compensationToolCode: string;
} {
  const policy = asObject(metadata.sideEffectPolicy);
  return {
    level: asString(policy.level) || "none",
    idempotencyMode: asString(policy.idempotencyMode) || "generated",
    compensationToolCode: asString(policy.compensationToolCode)
  };
}

function joinLines(values: string[]): string {
  return values.join("\n");
}

function parseLines(text: string): string[] {
  return text
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean);
}

function buildToolMetadata(baseMetadata: JsonObject): JsonObject {
  const metadata: JsonObject = {
    ...baseMetadata,
    promptRefs: {
      ...asObject(baseMetadata.promptRefs),
      executionPromptId: editForm.executionPromptId
    },
    routing: {
      ...asObject(baseMetadata.routing),
      capabilities: parseLines(editForm.capabilitiesText),
      triggerConditions: parseLines(editForm.triggerConditionsText),
      limitations: parseLines(editForm.limitationsText),
      inputPrerequisites: parseLines(editForm.inputPrerequisitesText),
      sideEffects: editForm.sideEffects.trim(),
      riskNotes: parseLines(editForm.riskNotesText)
    },
    guardrails: {
      ...asObject(baseMetadata.guardrails),
      blockedKeywords: parseLines(editForm.blockedKeywordsText),
      maxInputLength: editForm.maxInputLength > 0 ? editForm.maxInputLength : 0
    },
    approval: {
      ...asObject(baseMetadata.approval),
      required: editForm.approvalRequired,
      mode: editForm.approvalMode || "always",
      keywords: parseLines(editForm.approvalKeywordsText),
      reason: editForm.approvalReason.trim()
    },
    fallbackPolicy: {
      ...asObject(baseMetadata.fallbackPolicy),
      action: editForm.fallbackAction || "switch_candidate",
      backupToolCodes: parseLines(editForm.backupToolCodesText),
      degradePrompt: editForm.degradePrompt.trim()
    },
    runtimeSelection: {
      ...asObject(baseMetadata.runtimeSelection),
      hint: editForm.runtimeSelectionHint.trim(),
      preferredForPlanner: editForm.preferredForPlanner,
      preferredForDecision: editForm.preferredForDecision
    },
    toolGovernance: {
      ...asObject(baseMetadata.toolGovernance),
      consecutiveFailureAlertThreshold:
        editForm.consecutiveFailureAlertThreshold > 0 ? editForm.consecutiveFailureAlertThreshold : 0,
      slaTargetSuccessRate: editForm.slaTargetSuccessRate > 0 ? editForm.slaTargetSuccessRate : 0
    },
    sideEffectPolicy: {
      ...asObject(baseMetadata.sideEffectPolicy),
      level: editForm.sideEffectLevel || "none",
      idempotencyMode: editForm.idempotencyMode || "generated",
      compensationToolCode: editForm.compensationToolCode.trim()
    }
  };
  return metadata;
}

function promptLabel(promptId: number | null): string {
  if (!promptId) {
    return "-";
  }
  return promptOptions.value.find((item) => item.value === promptId)?.label || String(promptId);
}

function formatLineList(values: string[]): string {
  return values.length ? values.join("\n") : "-";
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

onMounted(() => {
  void Promise.all([loadDictOptionMap(), loadTools()]);
});
</script>

<style scoped>
.tool-page {
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

.json-import-panel {
  width: 100%;
}

.json-import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.runtime-governance-card {
  margin-bottom: 16px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #dbe5ef;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.runtime-governance-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.runtime-governance-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #17324d;
}

.runtime-governance-card__subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #64768a;
}

.runtime-governance-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.runtime-governance-item {
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #dbe5ef;
}

.runtime-governance-item__label {
  font-size: 12px;
  color: #64768a;
}

.runtime-governance-item__value {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 700;
  color: #17324d;
}

.runtime-governance-item__value--small {
  font-size: 13px;
  line-height: 1.5;
}

.runtime-governance-section-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #17324d;
}

.runtime-governance-failure {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.runtime-governance-alert {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.runtime-governance-trend,
.runtime-governance-history,
.runtime-governance-result {
  margin-top: 14px;
}

.runtime-governance-trend-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.runtime-governance-trend-item {
  padding: 10px;
  border-radius: 10px;
  border: 1px solid #dbe5ef;
  background: #ffffff;
}

.runtime-governance-trend-item__label {
  font-size: 12px;
  color: #64768a;
}

.runtime-governance-trend-item__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #17324d;
}

.runtime-governance-pagination {
  margin-top: 12px;
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

.form-tip {
  margin-top: 6px;
  color: #8292a6;
  font-size: 12px;
  line-height: 1.5;
}
</style>

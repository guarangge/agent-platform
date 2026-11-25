<template>
  <section class="trace-page">
    <el-card shadow="never" class="query-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Tracing 运营中心</div>
            <div class="card-subtitle">独立按时间范围、Agent、状态和 Trace 检索运行轨迹，并直接做慢步骤、失败率和审批等待分析。</div>
          </div>
        </div>
      </template>
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="Trace ID">
          <el-input v-model="queryForm.traceId" clearable placeholder="支持模糊检索" />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-input v-model="queryForm.eventType" clearable placeholder="例如 planning / tool_execute" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.traceStatus" clearable style="width: 180px;">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="待审批" value="waiting_approval" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent">
          <el-select v-model="queryForm.agentId" clearable filterable style="width: 220px;">
            <el-option v-for="option in agentOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务 / 会话">
          <el-input v-model="queryForm.keyword" clearable placeholder="TaskCode / ConversationCode / Span / 错误" style="width: 280px;" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="queryTimeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>
        <div class="query-actions">
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button type="primary" plain @click="handleExport">导出 CSV</el-button>
        </div>
      </el-form>
    </el-card>

    <div class="metrics-grid">
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">事件总数</div>
        <div class="metric-value">{{ metrics.totalCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">Trace 数</div>
        <div class="metric-value">{{ metrics.traceCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">失败事件</div>
        <div class="metric-value">{{ metrics.failedCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">失败率</div>
        <div class="metric-value">{{ metrics.failureRate.toFixed(2) }}%</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均耗时</div>
        <div class="metric-value">{{ formatDuration(metrics.avgDurationMs) }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">审批平均等待</div>
        <div class="metric-value">{{ formatDuration(metrics.approvalAvgWaitMs) }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">压缩观测 Span</div>
        <div class="metric-value">{{ metrics.compactionObservedCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">压缩 Critical</div>
        <div class="metric-value">{{ metrics.compactionCriticalCount }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均压缩质量</div>
        <div class="metric-value">{{ metrics.compactionAvgQualityScore }}</div>
      </el-card>
      <el-card shadow="never" class="metric-card">
        <div class="metric-label">平均节省 Token</div>
        <div class="metric-value">{{ metrics.compactionAvgSavedTokens }}</div>
      </el-card>
    </div>

    <div class="analysis-grid">
      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-title">聚合分布</div>
        </template>
        <div class="bucket-section">
          <div class="bucket-title">按 Agent 聚合</div>
          <el-table :data="metrics.agentBuckets" size="small" border>
            <el-table-column prop="bucketLabel" label="Agent" min-width="180" />
            <el-table-column prop="countValue" label="事件数" min-width="100" />
          </el-table>
        </div>
        <div class="bucket-section">
          <div class="bucket-title">按状态聚合</div>
          <el-table :data="metrics.statusBuckets" size="small" border>
            <el-table-column prop="bucketLabel" label="状态" min-width="180" />
            <el-table-column prop="countValue" label="事件数" min-width="100" />
          </el-table>
        </div>
        <div class="bucket-section">
          <div class="bucket-title">按事件类型聚合</div>
          <el-table :data="metrics.eventTypeBuckets" size="small" border>
            <el-table-column prop="bucketLabel" label="事件类型" min-width="180" />
            <el-table-column prop="countValue" label="事件数" min-width="100" />
          </el-table>
        </div>
      </el-card>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-title">慢步骤排行 / 审批等待</div>
        </template>
        <div class="slow-summary">
          <div>审批最长等待：{{ formatDuration(metrics.approvalMaxWaitMs) }}</div>
        </div>
        <el-table :data="metrics.slowSpans" size="small" border>
          <el-table-column prop="taskCode" label="任务" min-width="160" />
          <el-table-column prop="spanName" label="Span" min-width="180" />
          <el-table-column prop="eventType" label="事件类型" min-width="120" />
          <el-table-column prop="traceStatus" label="状态" min-width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.traceStatus)">{{ row.traceStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时(ms)" min-width="110" />
          <el-table-column prop="startedAt" label="开始时间" min-width="180" :formatter="formatTableDateTime" />
        </el-table>
      </el-card>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">Trace 检索结果</div>
            <div class="card-subtitle">点击“排查”可直接查看输入快照、输出快照和 metadata。</div>
          </div>
        </div>
      </template>

      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="conversationCode" label="会话" min-width="160" />
        <el-table-column prop="taskCode" label="任务" min-width="160" />
        <el-table-column prop="agentName" label="Agent" min-width="160" />
        <el-table-column prop="traceId" label="Trace ID" min-width="180" />
        <el-table-column prop="eventType" label="事件类型" min-width="120" />
        <el-table-column prop="spanName" label="Span" min-width="180" />
        <el-table-column prop="traceStatus" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.traceStatus)">{{ row.traceStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" min-width="110" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="220" />
        <el-table-column prop="startedAt" label="开始时间" min-width="180" :formatter="formatTableDateTime" />
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button link type="primary" @click="openTroubleshoot(row)">排查</el-button>
              <el-button link @click="openTopology(row)">拓扑</el-button>
            </el-space>
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

    <el-drawer v-model="drawerVisible" title="Trace 问题排查" size="50%">
      <template v-if="selectedRecord">
        <div class="drawer-section">
          <div class="drawer-title">基础信息</div>
          <div class="info-grid">
            <div>会话：{{ selectedRecord.conversationCode }}</div>
            <div>任务：{{ selectedRecord.taskCode }}</div>
            <div>Agent：{{ selectedRecord.agentName }}</div>
            <div>Trace：{{ selectedRecord.traceId }}</div>
            <div>事件：{{ selectedRecord.eventType }}</div>
            <div>Span：{{ selectedRecord.spanName }}</div>
            <div>状态：{{ selectedRecord.traceStatus }}</div>
            <div>耗时：{{ formatDuration(selectedRecord.durationMs) }}</div>
          </div>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">上下文压缩观测</div>
          <template v-if="selectedRecordCompaction">
            <div class="compaction-summary-grid">
              <div class="compaction-summary-item">
                <span>阶段</span>
                <strong>{{ selectedRecordCompaction.stage || "-" }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>Context Window</span>
                <strong>{{ selectedRecordCompaction.contextWindow || "-" }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>原始字符数</span>
                <strong>{{ selectedRecordCompaction.originalChars || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>压缩后字符数</span>
                <strong>{{ selectedRecordCompaction.compactedChars || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>节省字符数</span>
                <strong>{{ selectedRecordCompaction.savedChars || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>原始 Token 估算</span>
                <strong>{{ selectedRecordCompaction.estimatedOriginalTokens || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>压缩后 Token 估算</span>
                <strong>{{ selectedRecordCompaction.estimatedCompactedTokens || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>节省 Token</span>
                <strong>{{ selectedRecordCompaction.savedTokens || 0 }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>摘要压缩</span>
                <strong>{{ selectedRecordCompaction.summarized ? "是" : "否" }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>质量评分</span>
                <strong>{{ selectedRecordCompaction.qualityScore ?? "-" }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>质量状态</span>
                <strong>{{ compactionQualityText(selectedRecordCompaction.qualityStatus) }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>Tokenizer</span>
                <strong>{{ selectedRecordCompaction.tokenizerFamily || "-" }}</strong>
              </div>
              <div class="compaction-summary-item">
                <span>估算模式</span>
                <strong>{{ selectedRecordCompaction.tokenEstimateMode || "-" }}</strong>
              </div>
            </div>
            <div v-if="selectedRecordCompaction.explain" class="compaction-warning-list">
              <div class="compaction-warning-item">{{ selectedRecordCompaction.explain }}</div>
            </div>
            <div v-if="selectedRecordCompaction.validation" class="compaction-warning-list">
              <div class="compaction-warning-item">
                校验：{{ formatJson(JSON.stringify(selectedRecordCompaction.validation)) }}
              </div>
            </div>
            <div v-if="selectedRecordCompaction.qualityWarnings?.length" class="compaction-warning-list">
              <div
                v-for="item in selectedRecordCompaction.qualityWarnings"
                :key="item"
                class="compaction-warning-item"
              >
                {{ item }}
              </div>
            </div>
            <div v-if="selectedRecordCompaction.promptAssembly" class="drawer-section-inner">
              <div class="drawer-title">Prompt 组装块</div>
              <div class="prompt-preview-grid">
                <el-card shadow="never" class="prompt-preview-card">
                  <div class="bucket-title">System Prompt 预览</div>
                  <pre class="json-block">{{ selectedRecordCompaction.promptAssembly.systemPromptPreview || "-" }}</pre>
                </el-card>
                <el-card shadow="never" class="prompt-preview-card">
                  <div class="bucket-title">最终 User Prompt 预览</div>
                  <pre class="json-block">{{ selectedRecordCompaction.promptAssembly.finalUserPromptPreview || "-" }}</pre>
                </el-card>
              </div>
              <div class="compaction-warning-list" v-if="selectedRecordCompaction.promptAssembly.preview">
                <div class="compaction-warning-item">{{ selectedRecordCompaction.promptAssembly.preview }}</div>
              </div>
              <div class="compaction-warning-list" v-if="selectedRecordCompaction.promptAssembly.diff">
                <div class="compaction-warning-item">
                  组装对比：{{ formatPromptDiff(selectedRecordCompaction.promptAssembly.diff) }}
                </div>
              </div>
              <el-table :data="selectedRecordCompaction.promptAssembly.blocks || []" size="small" border>
                <el-table-column prop="label" label="块" min-width="140" />
                <el-table-column prop="source" label="来源" min-width="90" />
                <el-table-column prop="layerKey" label="层Key" min-width="160" />
                <el-table-column prop="compactedChars" label="字符数" min-width="100" />
                <el-table-column prop="compactedTokens" label="Token" min-width="100" />
                <el-table-column label="存在" min-width="80">
                  <template #default="{ row }">
                    {{ row.present ? "是" : "否" }}
                  </template>
                </el-table-column>
                <el-table-column label="截断" min-width="80">
                  <template #default="{ row }">
                    {{ row.truncated ? "是" : "否" }}
                  </template>
                </el-table-column>
                <el-table-column label="摘要" min-width="80">
                  <template #default="{ row }">
                    {{ row.summarized ? "是" : "否" }}
                  </template>
                </el-table-column>
                <el-table-column prop="sample" label="样本" min-width="260" show-overflow-tooltip />
              </el-table>
            </div>
            <div v-if="selectedRecordCompaction.promptSnapshot" class="drawer-section-inner">
              <div class="drawer-title">最终 Prompt Snapshot</div>
              <pre class="json-block">{{ formatJson(JSON.stringify(selectedRecordCompaction.promptSnapshot)) }}</pre>
            </div>
            <el-table :data="selectedRecordCompaction.layers || []" size="small" border>
              <el-table-column prop="key" label="层" min-width="180" />
              <el-table-column prop="strategy" label="策略" min-width="120" />
              <el-table-column prop="originalChars" label="原始字符数" min-width="120" />
              <el-table-column prop="compactedChars" label="压缩后字符数" min-width="140" />
              <el-table-column prop="budgetChars" label="预算字符数" min-width="120" />
              <el-table-column prop="originalTokens" label="原始 Token" min-width="110" />
              <el-table-column prop="compactedTokens" label="压缩后 Token" min-width="120" />
              <el-table-column prop="budgetTokens" label="预算 Token" min-width="110" />
              <el-table-column prop="itemCount" label="条目数" min-width="90" />
              <el-table-column prop="sampleBefore" label="压缩前片段" min-width="200" show-overflow-tooltip />
              <el-table-column prop="sampleAfter" label="压缩后片段" min-width="200" show-overflow-tooltip />
              <el-table-column label="摘要压缩" min-width="100">
                <template #default="{ row }">
                  {{ row.summarized ? "是" : "否" }}
                </template>
              </el-table-column>
              <el-table-column label="发生截断" min-width="100">
                <template #default="{ row }">
                  {{ row.truncated ? "是" : "否" }}
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="当前 Trace 未记录上下文压缩信息" :image-size="72" />
        </div>
        <div class="drawer-section">
          <div class="drawer-title">错误信息</div>
          <pre class="json-block">{{ selectedRecord.errorMessage || "无" }}</pre>
        </div>
        <div class="drawer-section" v-loading="investigationLoading">
          <div class="drawer-title">Prompt / Tool 联动与回放</div>
          <template v-if="selectedInvestigation">
            <div class="info-grid">
              <div>可回放：{{ selectedInvestigation.replayable ? "是" : "否" }}</div>
              <div>关联 Prompt：{{ selectedInvestigation.promptReferences.length }}</div>
              <div>关联 Tool：{{ selectedInvestigation.toolReferences.length }}</div>
              <div v-if="selectedInvestigation.replayBlockReason">原因：{{ selectedInvestigation.replayBlockReason }}</div>
            </div>
            <el-table :data="selectedInvestigation.toolReferences" size="small" border class="resource-table">
              <el-table-column prop="toolCode" label="关联 Tool Code" min-width="220" />
            </el-table>
            <el-form label-position="top" class="replay-form">
              <el-form-item label="回放模型编码（为空使用 Agent 当前默认模型）">
                <el-input v-model="replayForm.modelCode" clearable placeholder="例如 MINIMAX_DEFAULT" />
              </el-form-item>
              <el-form-item label="System Prompt">
                <el-input v-model="replayForm.systemPrompt" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="User Prompt">
                <el-input v-model="replayForm.userPrompt" type="textarea" :rows="8" />
              </el-form-item>
              <el-button
                type="primary"
                :loading="replayLoading"
                :disabled="!selectedInvestigation.replayable"
                @click="handleReplay"
              >执行 Prompt Replay</el-button>
            </el-form>
            <div v-if="replayResult" class="drawer-section-inner">
              <div class="drawer-title">回放结果与差异</div>
              <div class="info-grid">
                <div>Replay：{{ replayResult.replayCode }}</div>
                <div>模式：{{ replayResult.replayMode }}</div>
                <div>Replay Trace：{{ replayResult.replayTraceId }}</div>
                <div>状态：{{ replayResult.status }}</div>
                <div>模型：{{ replayResult.modelIdentifier }}</div>
                <div>耗时：{{ formatDuration(replayResult.durationMs) }}</div>
                <div>输入 Token：{{ replayResult.promptTokens ?? "-" }}</div>
                <div>输出 Token：{{ replayResult.completionTokens ?? "-" }}</div>
              </div>
              <div class="prompt-preview-grid">
                <el-card shadow="never" class="prompt-preview-card">
                  <div class="bucket-title">原输出</div>
                  <pre class="json-block">{{ replayResult.originalOutput || "-" }}</pre>
                </el-card>
                <el-card shadow="never" class="prompt-preview-card">
                  <div class="bucket-title">回放输出</div>
                  <pre class="json-block">{{ replayResult.replayOutput || "-" }}</pre>
                </el-card>
              </div>
              <pre class="json-block">{{ JSON.stringify(replayResult.diff, null, 2) }}</pre>
            </div>
          </template>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">输入快照</div>
          <pre class="json-block">{{ formatJson(selectedRecord.inputSnapshot) }}</pre>
        </div>
        <div class="drawer-section" v-if="parseToolInputObservation(selectedRecord.inputSnapshot)">
          <div class="drawer-title">Tool 输入观测</div>
          <pre class="json-block">{{ formatToolInputObservation(selectedRecord.inputSnapshot) }}</pre>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">输出快照</div>
          <pre class="json-block">{{ formatJson(selectedRecord.outputSnapshot) }}</pre>
        </div>
        <div class="drawer-section" v-if="parseStepPromptTrace(selectedRecord.outputSnapshot)">
          <div class="drawer-title">步骤 Prompt Trace</div>
          <div class="prompt-preview-grid">
            <el-card shadow="never" class="prompt-preview-card">
              <div class="bucket-title">System Prompt 预览</div>
              <pre class="json-block">{{ parseStepPromptTrace(selectedRecord.outputSnapshot)?.systemPromptPreview || "-" }}</pre>
            </el-card>
            <el-card shadow="never" class="prompt-preview-card">
              <div class="bucket-title">最终 User Prompt 预览</div>
              <pre class="json-block">{{ parseStepPromptTrace(selectedRecord.outputSnapshot)?.finalUserPromptPreview || "-" }}</pre>
            </el-card>
          </div>
          <pre class="json-block">{{ formatStepPromptTrace(selectedRecord.outputSnapshot) }}</pre>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">Metadata</div>
          <pre class="json-block">{{ formatJson(selectedRecord.metadata) }}</pre>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="topologyDrawerVisible" title="Trace 拓扑" size="55%">
      <template v-if="selectedTopology">
        <div class="metrics-grid topology-metrics-grid">
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">Span 数</div>
            <div class="metric-value">{{ selectedTopology.summary.spanCount }}</div>
          </el-card>
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">失败 Span</div>
            <div class="metric-value">{{ selectedTopology.summary.failedSpanCount }}</div>
          </el-card>
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">审批节点</div>
            <div class="metric-value">{{ selectedTopology.summary.approvalCount }}</div>
          </el-card>
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">委派节点</div>
            <div class="metric-value">{{ selectedTopology.summary.delegationCount }}</div>
          </el-card>
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">累计耗时</div>
            <div class="metric-value">{{ formatDuration(selectedTopology.summary.totalDurationMs) }}</div>
          </el-card>
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">链路时长</div>
            <div class="metric-value">{{ formatDuration(selectedTopology.summary.wallClockDurationMs) }}</div>
          </el-card>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">Span 链路</div>
          <el-timeline>
            <el-timeline-item
              v-for="item in selectedTopology.spans"
              :key="item.taskTraceId"
              :timestamp="formatDateTime(item.startedAt, '')"
              :type="statusTagType(item.traceStatus)"
            >
              <div class="timeline-title">{{ item.eventType }} / {{ item.spanName }}</div>
              <div class="timeline-desc">状态：{{ item.traceStatus }} ｜耗时：{{ formatDuration(item.durationMs) }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">审批节点</div>
          <el-table :data="selectedTopology.approvals" size="small" border>
            <el-table-column prop="nodeName" label="节点" min-width="180" />
            <el-table-column prop="status" label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="approvalStatusTagType(row.status)">{{ approvalStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approvedBy" label="审批人" min-width="100" />
            <el-table-column prop="approvedAt" label="审批时间" min-width="180" :formatter="formatTableDateTime" />
            <el-table-column prop="approvalComment" label="审批说明" min-width="220" />
          </el-table>
        </div>
        <div class="drawer-section">
          <div class="drawer-title">委派节点</div>
          <el-table :data="selectedTopology.delegations" size="small" border>
            <el-table-column prop="delegateAgentName" label="目标 Agent" min-width="180" />
            <el-table-column prop="status" label="状态" min-width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="childTaskId" label="子任务 ID" min-width="120" />
            <el-table-column prop="errorMessage" label="错误信息" min-width="220" />
            <el-table-column prop="createdAt" label="创建时间" min-width="180" :formatter="formatTableDateTime" />
          </el-table>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { formatDateTime, formatTableDateTime, toBackendUtcDateTime } from "@/utils/dateTime";
import { fetchAgentPage } from "@/api/agent";
import {
  exportTraceCsv,
  fetchTraceInvestigation,
  fetchTraceMetrics,
  fetchTracePage,
  fetchTraceTopology,
  replayTracePrompt,
  type TraceInvestigation,
  type TraceMetrics,
  type TraceRecord,
  type TraceReplayResult,
  type TraceTopology
} from "@/api/trace";

interface SelectOption {
  label: string;
  value: number;
}

interface TraceQueryForm {
  pageNum: number;
  pageSize: number;
  traceId: string;
  eventType: string;
  traceStatus: string;
  agentId: number | null;
  taskId: number | null;
  keyword: string;
}

const route = useRoute();
const loading = ref(false);
const records = ref<TraceRecord[]>([]);
const total = ref(0);
const agentOptions = ref<SelectOption[]>([]);
const drawerVisible = ref(false);
const selectedRecord = ref<TraceRecord | null>(null);
const selectedInvestigation = ref<TraceInvestigation | null>(null);
const investigationLoading = ref(false);
const replayLoading = ref(false);
const replayResult = ref<TraceReplayResult | null>(null);
const replayForm = reactive({ modelCode: "", systemPrompt: "", userPrompt: "" });
const topologyDrawerVisible = ref(false);
const selectedTopology = ref<TraceTopology | null>(null);
const queryTimeRange = ref<[string, string] | []>([]);

const metrics = ref<TraceMetrics>({
  totalCount: 0,
  traceCount: 0,
  failedCount: 0,
  failureRate: 0,
  avgDurationMs: 0,
  approvalAvgWaitMs: 0,
  approvalMaxWaitMs: 0,
  compactionObservedCount: 0,
  compactionCriticalCount: 0,
  compactionAvgQualityScore: 0,
  compactionAvgSavedTokens: 0,
  agentBuckets: [],
  statusBuckets: [],
  eventTypeBuckets: [],
  slowSpans: []
});

const queryForm = reactive<TraceQueryForm>({
  pageNum: 1,
  pageSize: 20,
  traceId: "",
  eventType: "",
  traceStatus: "",
  agentId: null,
  taskId: null,
  keyword: ""
});

const selectedRecordCompaction = ref<Record<string, any> | null>(null);

async function loadAgents(): Promise<void> {
  const page = await fetchAgentPage({
    pageNum: 1,
    pageSize: 100,
    status: "enabled"
  });
  agentOptions.value = page.records.map((item) => ({
    label: `${item.agentName}（${item.agentCode}）`,
    value: item.agentId
  }));
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const params = buildQueryParams();
    const [page, metricData] = await Promise.all([
      fetchTracePage(params),
      fetchTraceMetrics(params)
    ]);
    records.value = page.records;
    total.value = page.total;
    metrics.value = metricData;
  } catch (error) {
    ElMessage.error("Tracing 数据加载失败");
  } finally {
    loading.value = false;
  }
}

async function handleExport(): Promise<void> {
  try {
    await exportTraceCsv(buildQueryParams());
    ElMessage.success("导出成功");
  } catch {
    ElMessage.error("导出失败");
  }
}

function buildQueryParams() {
  const [startedFrom, startedTo] = Array.isArray(queryTimeRange.value) ? queryTimeRange.value : [];
  return {
    pageNum: queryForm.pageNum,
    pageSize: queryForm.pageSize,
    traceId: queryForm.traceId || undefined,
    eventType: queryForm.eventType || undefined,
    traceStatus: queryForm.traceStatus || undefined,
    agentId: queryForm.agentId || undefined,
    taskId: queryForm.taskId || undefined,
    keyword: queryForm.keyword || undefined,
    startedFrom: toBackendUtcDateTime(startedFrom),
    startedTo: toBackendUtcDateTime(startedTo)
  };
}

function resetQuery(): void {
  queryForm.pageNum = 1;
  queryForm.pageSize = 20;
  queryForm.traceId = "";
  queryForm.eventType = "";
  queryForm.traceStatus = "";
  queryForm.agentId = null;
  queryForm.taskId = null;
  queryForm.keyword = "";
  queryTimeRange.value = [];
  void loadData();
}

function handlePageChange(pageNum: number): void {
  queryForm.pageNum = pageNum;
  void loadData();
}

async function openTroubleshoot(record: TraceRecord): Promise<void> {
  selectedRecord.value = record;
  selectedRecordCompaction.value = parseContextCompaction(record.metadata);
  selectedInvestigation.value = null;
  replayResult.value = null;
  replayForm.modelCode = "";
  replayForm.systemPrompt = "";
  replayForm.userPrompt = "";
  drawerVisible.value = true;
  investigationLoading.value = true;
  try {
    const investigation = await fetchTraceInvestigation(record.taskTraceId);
    selectedInvestigation.value = investigation;
    replayForm.systemPrompt = String(investigation.promptSnapshot.systemPrompt || "");
    replayForm.userPrompt = String(investigation.promptSnapshot.userPrompt || "");
  } catch {
    ElMessage.error("Trace 关联资源加载失败");
  } finally {
    investigationLoading.value = false;
  }
}

async function handleReplay(): Promise<void> {
  if (!selectedRecord.value || !selectedInvestigation.value?.replayable) {
    return;
  }
  replayLoading.value = true;
  try {
    replayResult.value = await replayTracePrompt(selectedRecord.value.taskTraceId, {
      modelCode: replayForm.modelCode || undefined,
      systemPrompt: replayForm.systemPrompt,
      userPrompt: replayForm.userPrompt
    });
    ElMessage.success("Prompt 快照回放完成");
    await loadData();
  } catch {
    ElMessage.error("Prompt 快照回放失败");
  } finally {
    replayLoading.value = false;
  }
}

async function openTopology(record: TraceRecord): Promise<void> {
  try {
    selectedTopology.value = await fetchTraceTopology(record.conversationId, record.taskId, record.traceId);
    topologyDrawerVisible.value = true;
  } catch {
    ElMessage.error("拓扑加载失败");
  }
}

function formatJson(value: string): string {
  if (!value) {
    return "{}";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function parseContextCompaction(metadata: string): Record<string, any> | null {
  if (!metadata) {
    return null;
  }
  try {
    const parsed = JSON.parse(metadata) as Record<string, any>;
    if (parsed && typeof parsed === "object" && parsed.contextCompaction && typeof parsed.contextCompaction === "object") {
      return parsed.contextCompaction as Record<string, any>;
    }
    return null;
  } catch {
    return null;
  }
}

function parseStepPromptTrace(outputSnapshot: string): Record<string, any> | null {
  if (!outputSnapshot) {
    return null;
  }
  try {
    const parsed = JSON.parse(outputSnapshot) as Record<string, any>;
    const directPromptTrace = parsed.promptTrace;
    if (directPromptTrace && typeof directPromptTrace === "object") {
      return directPromptTrace as Record<string, any>;
    }
    const metadataPromptTrace = parsed.metadata?.promptTrace;
    if (metadataPromptTrace && typeof metadataPromptTrace === "object") {
      return metadataPromptTrace as Record<string, any>;
    }
    return null;
  } catch {
    return null;
  }
}

function formatStepPromptTrace(outputSnapshot: string): string {
  const promptTrace = parseStepPromptTrace(outputSnapshot);
  if (!promptTrace) {
    return "-";
  }
  return JSON.stringify({
    stage: promptTrace.stage,
    blockOrder: promptTrace.blockOrder,
    diff: promptTrace.diff,
    estimatedSystemTokens: promptTrace.estimatedSystemTokens,
    estimatedFinalPromptTokens: promptTrace.estimatedFinalPromptTokens
  }, null, 2);
}

function parseToolInputObservation(inputSnapshot: string): Record<string, any> | null {
  if (!inputSnapshot) {
    return null;
  }
  try {
    const parsed = JSON.parse(inputSnapshot) as Record<string, any>;
    const observation = parsed.inputObservation;
    return observation && typeof observation === "object" ? observation as Record<string, any> : null;
  } catch {
    return null;
  }
}

function formatToolInputObservation(inputSnapshot: string): string {
  const observation = parseToolInputObservation(inputSnapshot);
  if (!observation) {
    return "-";
  }
  return JSON.stringify(observation, null, 2);
}

function compactionQualityText(status: unknown): string {
  if (status === "good") {
    return "良好";
  }
  if (status === "warning") {
    return "告警";
  }
  if (status === "critical") {
    return "严重";
  }
  return "-";
}

function formatPromptDiff(diff: Record<string, any> | null | undefined): string {
  if (!diff) {
    return "-";
  }
  const changed = diff.changed ? "是" : "否";
  return `changed=${changed}\nassembled=${diff.assembledUserPreview || "-"}\nfinal=${diff.finalUserPromptPreview || "-"}`;
}

function formatDuration(value: number | null | undefined): string {
  if (value == null || value <= 0) {
    return "0 ms";
  }
  if (value < 1000) {
    return `${value} ms`;
  }
  return `${(value / 1000).toFixed(2)} s`;
}

function statusTagType(status: string): "success" | "info" | "warning" | "danger" {
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
  return status;
}

function approvalStatusTagType(status: string): "success" | "info" | "warning" | "danger" {
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

onMounted(async () => {
  if (route.query.traceId) {
    queryForm.traceId = String(route.query.traceId);
  }
  if (route.query.taskId) {
    const parsedTaskId = Number(route.query.taskId);
    queryForm.taskId = Number.isFinite(parsedTaskId) ? parsedTaskId : null;
  }
  await loadAgents();
  await loadData();
});
</script>

<style scoped>
.trace-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-card,
.table-card,
.metric-card {
  border-radius: 18px;
  border: 1px solid #dbe5ef;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #17324d;
}

.card-subtitle {
  margin-top: 6px;
  font-size: 12px;
  color: #64768a;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.query-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.metric-label {
  font-size: 12px;
  color: #64768a;
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: #17324d;
}

.analysis-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}

.bucket-section + .bucket-section {
  margin-top: 14px;
}

.bucket-title,
.drawer-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #17324d;
}

.slow-summary {
  margin-bottom: 10px;
  font-size: 13px;
  color: #52657a;
}

.pagination-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.drawer-section + .drawer-section {
  margin-top: 16px;
}

.drawer-section-inner {
  margin: 12px 0 16px;
}

.prompt-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.prompt-preview-card {
  border: 1px solid #dbe5ef;
}

.compaction-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.compaction-summary-item {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #e2ebf4;
  background: #f8fbff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.compaction-summary-item span {
  font-size: 12px;
  color: #64768a;
}

.compaction-summary-item strong {
  color: #17324d;
  font-size: 16px;
}

.compaction-warning-list {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.compaction-warning-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #f2d5a9;
  background: #fff7e8;
  color: #8a5a12;
  font-size: 12px;
  line-height: 1.6;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  font-size: 13px;
  color: #415467;
}

.json-block {
  margin: 0;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #e2ebf4;
  background: #f7fbff;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  color: #415467;
  font-family: "SFMono-Regular", "Menlo", "Consolas", monospace;
}

@media (max-width: 1280px) {
  .metrics-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .analysis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .metrics-grid,
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .compaction-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

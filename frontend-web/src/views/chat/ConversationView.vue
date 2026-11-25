<template>
  <section class="conversation-workspace">
    <aside class="conversation-rail">
      <div class="conversation-list" v-loading="conversationLoading">
        <section v-for="group in conversationGroups" :key="group.agentId" class="agent-conversation-group">
          <div class="agent-group-header" :class="{ active: group.agentId === activeAgentId }">
            <button class="agent-group-toggle" type="button" @click="toggleAgentGroup(group.agentId)">
              <span class="agent-group-avatar">{{ agentAvatarText(group.agentName) }}</span>
              <span class="agent-group-copy">
                <strong>{{ group.agentName }}</strong>
                <small>{{ group.conversations.length }} 个会话 · {{ group.enabled ? "可用" : "已停用" }}</small>
              </span>
              <el-icon class="agent-group-arrow" :class="{ expanded: isAgentExpanded(group.agentId) }"><ArrowRight /></el-icon>
            </button>
            <el-tooltip v-if="group.enabled" content="在此 Agent 下新建会话" placement="right">
              <el-button class="agent-group-create" text circle :icon="Plus" @click.stop="prepareNewConversation(group.agentId)" />
            </el-tooltip>
          </div>

          <div v-show="isAgentExpanded(group.agentId)" class="agent-conversation-list">
            <button
              v-for="item in group.conversations"
              :key="item.conversationId"
              class="conversation-item"
              :class="{ active: item.conversationId === activeConversationId }"
              @click="selectConversation(item.conversationId)"
            >
              <span class="conversation-title-row">
                <strong>{{ item.title || `${item.agentName} 会话` }}</strong>
                <el-icon
                  class="conversation-rename"
                  role="button"
                  tabindex="0"
                  title="修改会话名称"
                  @click.stop="renameConversation(item)"
                  @keydown.enter.stop="renameConversation(item)"
                ><Edit /></el-icon>
              </span>
              <span>{{ statusText(item.status) }}</span>
              <p>{{ item.lastMessagePreview || "暂无消息" }}</p>
            </button>
            <button v-if="!group.conversations.length && group.enabled" class="agent-empty-action" type="button" @click="prepareNewConversation(group.agentId)">
              暂无会话，点击开始
            </button>
            <p v-else-if="!group.conversations.length" class="agent-empty-text">暂无历史会话</p>
          </div>
        </section>
        <el-empty v-if="!conversationLoading && !conversationGroups.length" description="暂无可用 Agent" :image-size="76" />
      </div>
    </aside>

    <main class="conversation-stage">
      <header class="stage-header">
        <div>
          <h1>{{ activeConversationDetail?.title || "新建会话" }}</h1>
          <p>{{ activeConversationDetail?.agentName || draftAgentDetail?.agentName || "选择 Agent 并确认会话策略后开始对话" }}</p>
        </div>
        <div class="stage-actions">
          <el-button v-if="pendingApproval && !approvalSubmitting" type="warning" plain @click="approvalDialogVisible = true">待审批</el-button>
          <el-button v-if="activeConversationDetail" @click="memoryDrawerVisible = true">会话策略</el-button>
        </div>
      </header>

      <div ref="messageViewport" class="message-viewport" v-loading="messageLoading || draftPolicyLoading">
        <section v-if="!activeConversationDetail && draftAgentDetail" class="new-conversation-setup" v-loading="draftPolicyLoading">
          <header>
            <div><span>NEW CONVERSATION</span><h2>配置后开始对话</h2></div>
            <p>会话策略会在创建时固化为本会话的运行配置，后续仍可单独调整。</p>
          </header>

          <el-form label-position="top">
            <div class="selected-agent-card">
              <span class="agent-group-avatar">{{ agentAvatarText(draftAgentDetail.agentName) }}</span>
              <div><strong>{{ draftAgentDetail.agentName }}</strong><small>{{ draftAgentDetail.agentCode }} · {{ draftAgentDetail.version }}</small></div>
              <el-tag size="small" type="success">当前 Agent</el-tag>
            </div>

            <el-form-item label="会话名称" required>
              <el-input v-model="conversationTitle" maxlength="128" show-word-limit placeholder="请输入会话名称" />
            </el-form-item>

            <template v-if="draftAgentDetail">
              <el-form-item label="会话策略模式" required>
                <el-radio-group v-model="policyMode" class="policy-mode-options">
                  <el-radio value="inherit"><strong>继承 Agent 默认策略</strong><span>使用已发布的 Agent 记忆与上下文配置</span></el-radio>
                  <el-radio value="custom"><strong>自定义当前会话</strong><span>仅覆盖当前会话，不修改 Agent 配置</span></el-radio>
                </el-radio-group>
              </el-form-item>

              <div v-if="policyMode" class="policy-summary-grid">
                <article><span>历史记忆</span><strong>{{ memoryPolicy.userMemoryEnabled ? "开启" : "关闭" }}</strong></article>
                <article><span>记忆模式</span><strong>{{ memoryModeText(memoryPolicy.userMemoryMode) }}</strong></article>
                <article><span>最近消息</span><strong>{{ memoryPolicy.maxRecentMessages }} 条</strong></article>
                <article><span>状态优先</span><strong>{{ memoryPolicy.preferStateFirst ? "开启" : "关闭" }}</strong></article>
              </div>

              <div v-if="policyMode === 'custom'" class="draft-policy-editor">
                <div class="policy-switch-row"><div><strong>启用用户历史记忆</strong><span>向执行上下文注入用户长期记忆</span></div><el-switch v-model="memoryPolicy.userMemoryEnabled" /></div>
                <el-form-item label="记忆注入模式"><el-select v-model="memoryPolicy.userMemoryMode" style="width:100%"><el-option label="关闭" value="off" /><el-option label="仅摘要" value="summary_only" /><el-option label="摘要 + 长期事实" value="summary_and_facts" /></el-select></el-form-item>
                <div class="policy-switch-row"><div><strong>允许写回长期记忆</strong><span>本轮结束后更新用户级记忆</span></div><el-switch v-model="memoryPolicy.userMemoryWritebackEnabled" /></div>
                <div class="policy-switch-row"><div><strong>摘要优先</strong><span>长会话优先使用压缩摘要</span></div><el-switch v-model="memoryPolicy.preferSummaryOverRawHistory" /></div>
                <div class="policy-switch-row"><div><strong>状态优先注入</strong><span>结构化状态优先于原始历史</span></div><el-switch v-model="memoryPolicy.preferStateFirst" /></div>
                <div class="policy-grid">
                  <el-form-item label="最近消息窗口"><el-input-number v-model="memoryPolicy.maxRecentMessages" :min="1" :max="50" /></el-form-item>
                  <el-form-item label="最近用户消息"><el-input-number v-model="memoryPolicy.maxRecentUserMessages" :min="1" :max="20" /></el-form-item>
                  <el-form-item label="摘要触发阈值"><el-input-number v-model="memoryPolicy.historySummaryThresholdMessages" :min="1" :max="100" /></el-form-item>
                  <el-form-item label="最低保留比例"><el-input-number v-model="memoryPolicy.minRetentionRatio" :min="0.1" :max="0.9" :step="0.05" /></el-form-item>
                  <el-form-item label="质量告警阈值"><el-input-number v-model="memoryPolicy.qualityWarningThreshold" :min="1" :max="100" /></el-form-item>
                </div>
              </div>
            </template>
          </el-form>

          <footer>
            <span>{{ draftSetupHint }}</span>
            <el-button type="primary" :loading="conversationCreating" :disabled="!draftAgentDetail || !conversationTitle.trim() || !policyMode" @click="createConfiguredConversation">确认策略并创建会话</el-button>
          </footer>
        </section>

        <template v-else-if="visibleMessageRecords.length || sending || approvalResuming">
          <article v-for="message in visibleMessageRecords" :key="message.messageId" class="message" :class="`message--${message.messageType}`">
            <div class="message-avatar" :title="messageRoleName(message.messageType)">
              <svg v-if="message.messageType !== 'user'" class="agent-avatar-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3v3M9 3h6" />
                <rect x="4" y="6" width="16" height="14" rx="4" />
                <circle cx="9" cy="12" r="1.25" />
                <circle cx="15" cy="12" r="1.25" />
                <path d="M8.5 16h7M2.5 11v4M21.5 11v4" />
              </svg>
              <span v-else>{{ messageAvatarText(message.messageType) }}</span>
            </div>
            <div class="message-content">
              <header class="message-meta">
                <strong>{{ messageRoleName(message.messageType) }}</strong>
                <span>{{ formatDateTime(message.createdAt) }}</span>
                <el-tag v-if="message.responseStatus !== 'success'" size="small" type="danger">{{ message.responseStatus }}</el-tag>
              </header>
              <div class="message-bubble">
                <div v-if="message.attachments?.length" class="message-attachments">
                  <button v-for="attachment in message.attachments" :key="attachment.attachmentId"
                    class="message-attachment" type="button" @click="downloadAttachment(attachment)">
                    <img v-if="attachmentPreviewUrls[attachment.attachmentId]"
                      :src="attachmentPreviewUrls[attachment.attachmentId]" :alt="attachment.fileName" />
                    <span v-else>图片加载中</span>
                    <small>{{ attachment.fileName }}</small>
                  </button>
                </div>
                <div v-if="message.messageType === 'assistant'" class="markdown-body" v-html="renderAssistantMessage(message.content)" @click="handleAssistantLinkClick"></div>
                <div v-else class="plain-content">{{ message.content }}</div>
                <p v-if="message.responseStatus === 'failed' && message.errorMessage" class="message-error">
                  {{ message.errorMessage }}
                </p>
              </div>
            </div>
          </article>
          <article v-if="sending || approvalResuming" class="message message--assistant message--pending">
            <div class="message-avatar" :title="activeConversationDetail?.agentName || 'Agent'">
              <svg class="agent-avatar-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3v3M9 3h6" />
                <rect x="4" y="6" width="16" height="14" rx="4" />
                <circle cx="9" cy="12" r="1.25" />
                <circle cx="15" cy="12" r="1.25" />
                <path d="M8.5 16h7M2.5 11v4M21.5 11v4" />
              </svg>
            </div>
            <div class="message-content">
              <header class="message-meta">
                <strong>{{ activeConversationDetail?.agentName || "Agent" }}</strong>
                <el-tag size="small" type="info">{{ activeRunStatusText }}</el-tag>
              </header>
              <div class="message-bubble" :class="{ 'pending-response': !streamingAssistantContent }">
                <div v-if="streamingAssistantContent" class="markdown-body streaming-content" v-html="renderAssistantMessage(streamingAssistantContent)"></div>
                <div v-else-if="visibleRunProgress.length" class="run-progress-list">
                  <div v-for="(progress, index) in visibleRunProgress" :key="progress.sequenceNo" class="run-progress-item"
                    :class="{ 'is-current': index === visibleRunProgress.length - 1 }">
                    <span v-if="index < visibleRunProgress.length - 1" class="run-progress-check">✓</span>
                    <span v-else class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
                    <span>{{ progress.message }}</span>
                  </div>
                </div>
                <template v-else>
                  <span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>
                  <span>{{ activeRunHint }}</span>
                </template>
              </div>
            </div>
          </article>
        </template>
        <div v-else class="empty-conversation">
          <template v-if="!activeConversationDetail">
            <span>SELECT AGENT</span><h2>从 Agent 开始新会话</h2><p>在左侧选择一个可用 Agent，点击该 Agent 右侧的 + 创建会话。</p>
          </template>
          <template v-else>
            <span>READY</span><h2>把目标交给 Agent</h2><p>高风险执行会在当前对话中弹出审批；任务拆解与链路排查可在对应运营中心查看。</p>
          </template>
        </div>
      </div>

      <div v-if="activeConversationDetail" class="composer">
        <div v-if="pendingAttachments.length" class="pending-attachments">
          <div v-for="attachment in pendingAttachments" :key="attachment.attachmentId" class="pending-attachment">
            <img :src="attachment.previewUrl" :alt="attachment.fileName" />
            <span>{{ attachment.fileName }}</span>
            <button type="button" :disabled="attachmentRemovingId === attachment.attachmentId"
              @click="removePendingAttachment(attachment)">×</button>
          </div>
        </div>
        <el-input v-model="messageContent" type="textarea" :rows="3" resize="none" placeholder="描述你的目标或继续补充信息…" @keydown="handleComposerKeydown" />
        <div class="composer-footer">
          <div class="composer-preferences">
            <input ref="attachmentInput" class="attachment-file-input" type="file" accept="image/jpeg,image/png,image/webp"
              multiple @change="handleAttachmentSelection" />
            <el-button size="small" :loading="attachmentUploading" :disabled="sending || pendingAttachments.length >= 5"
              @click="attachmentInput?.click()">添加图片</el-button>
            <span>Enter 发送 · Shift + Enter 换行</span>
            <span class="streaming-switch-label">流式响应</span>
            <el-switch
              :model-value="activeConversationDetail.responseStreamingEnabled"
              :loading="streamPreferenceSaving"
              :disabled="sending || approvalResuming"
              size="small"
              @change="handleStreamingPreferenceChange"
            />
          </div>
          <el-button type="primary" :loading="sending || approvalResuming"
            :disabled="(!messageContent.trim() && !pendingAttachments.length) || approvalResuming || attachmentUploading"
            @click="submitMessage">发送</el-button>
        </div>
      </div>
    </main>

    <el-drawer v-model="memoryDrawerVisible" title="当前会话记忆与上下文策略" size="520px">
      <div class="policy-intro">这里只配置当前会话覆盖项；Agent 默认策略请在 Agent 管理中维护，压缩指标请到压缩治理查看。</div>
      <div v-if="activeConversationDetail" class="runtime-config-summary">
        <h3>当前运行配置</h3>
        <dl>
          <div><dt>Agent</dt><dd>{{ activeConversationDetail.agentName }}</dd></div>
          <div><dt>Model</dt><dd>{{ activeConversationDetail.defaultModel || "默认" }}</dd></div>
          <div><dt>Prompt</dt><dd>{{ activeConversationDetail.defaultPromptName || "默认" }}</dd></div>
          <div><dt>Planner</dt><dd>{{ activeConversationDetail.plannerPromptName || "默认" }}</dd></div>
        </dl>
      </div>
      <el-form label-position="top" class="policy-form">
        <div class="policy-switch-row"><div><strong>启用用户历史记忆</strong><span>向执行上下文注入用户长期记忆</span></div><el-switch v-model="memoryPolicy.userMemoryEnabled" /></div>
        <el-form-item label="记忆注入模式"><el-select v-model="memoryPolicy.userMemoryMode" style="width:100%"><el-option label="关闭" value="off" /><el-option label="仅摘要" value="summary_only" /><el-option label="摘要 + 长期事实" value="summary_and_facts" /></el-select></el-form-item>
        <div class="policy-switch-row"><div><strong>允许写回长期记忆</strong><span>本轮结束后更新用户级记忆</span></div><el-switch v-model="memoryPolicy.userMemoryWritebackEnabled" /></div>
        <div class="policy-switch-row"><div><strong>摘要优先</strong><span>长会话优先使用压缩摘要</span></div><el-switch v-model="memoryPolicy.preferSummaryOverRawHistory" /></div>
        <div class="policy-switch-row"><div><strong>状态优先注入</strong><span>槽位与会话状态优先于原始历史</span></div><el-switch v-model="memoryPolicy.preferStateFirst" /></div>
        <div class="policy-grid">
          <el-form-item label="最近消息窗口"><el-input-number v-model="memoryPolicy.maxRecentMessages" :min="1" :max="50" /></el-form-item>
          <el-form-item label="最近用户消息"><el-input-number v-model="memoryPolicy.maxRecentUserMessages" :min="1" :max="20" /></el-form-item>
          <el-form-item label="摘要触发阈值"><el-input-number v-model="memoryPolicy.historySummaryThresholdMessages" :min="1" :max="100" /></el-form-item>
          <el-form-item label="最低保留比例"><el-input-number v-model="memoryPolicy.minRetentionRatio" :min="0.1" :max="0.9" :step="0.05" /></el-form-item>
          <el-form-item label="质量告警阈值"><el-input-number v-model="memoryPolicy.qualityWarningThreshold" :min="1" :max="100" /></el-form-item>
        </div>
      </el-form>
      <div v-if="activeConversationDetail" class="memory-snapshot">
        <h3>会话记忆摘要</h3><p>{{ activeConversationDetail.memorySummary || "尚未生成会话摘要" }}</p>
        <h3>用户事实 / 目标</h3><p>{{ activeConversationDetail.userFactSummary || "暂无用户事实" }}<br />{{ activeConversationDetail.userGoalSummary || "暂无用户目标" }}</p>
      </div>
      <template #footer><el-button @click="memoryDrawerVisible=false">关闭</el-button><el-button v-if="activeConversationDetail" type="primary" :loading="policySaving" @click="saveMemoryPolicy">保存当前会话策略</el-button></template>
    </el-drawer>

    <el-dialog
      v-model="approvalDialogVisible"
      title="高风险操作需要审批"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!approvalSubmitting"
      :show-close="!approvalSubmitting"
    >
      <div v-if="pendingApproval && pendingApprovalTask" class="conversation-approval" v-loading="approvalLoading">
        <el-alert
          title="Agent 已暂停执行，确认后才会调用该 Tool"
          type="warning"
          show-icon
          :closable="false"
        />
        <dl class="approval-summary">
          <div><dt>审批节点</dt><dd>{{ pendingApproval.nodeName }}</dd></div>
          <div><dt>执行任务</dt><dd>{{ pendingApprovalTask.taskCode }}</dd></div>
          <div><dt>执行步骤</dt><dd>{{ pendingApprovalStepName }}</dd></div>
          <div><dt>目标 Tool</dt><dd>{{ pendingApprovalToolCode }}</dd></div>
        </dl>
        <p class="approval-risk-note">该操作命中了平台高风险 Tool 策略。通过后将从当前执行断点继续，驳回后将终止或按恢复策略重新规划。</p>
        <el-form label-position="top">
          <el-form-item label="审批意见（可选）">
            <el-input v-model="approvalComment" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写通过或驳回原因" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="approvalSubmitting" @click="approvalDialogVisible = false">稍后处理</el-button>
        <el-button type="danger" plain :loading="approvalSubmitting && approvalAction === 'reject'" :disabled="approvalSubmitting" @click="handleConversationApproval('reject')">驳回</el-button>
        <el-button type="primary" :loading="approvalSubmitting && approvalAction === 'approve'" :disabled="approvalSubmitting" @click="handleConversationApproval('approve')">通过并继续</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import MarkdownIt from "markdown-it";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowRight, Edit, Plus } from "@element-plus/icons-vue";
import { fetchAgentDetail, fetchAgentPage, type AgentDetail, type AgentRecord } from "@/api/agent";
import { useAuthStore } from "@/stores/auth";
import { generateUuid } from "@/utils/uuid";
import { formatDateTime } from "@/utils/dateTime";
import {
  approveConversationTask,
  createConversation,
  deleteConversationAttachment,
  downloadTaskArtifact,
  enqueueConversationMessage,
  fetchAgentRun,
  fetchConversationDetail,
  fetchConversationExecutions,
  fetchConversationAttachmentBlob,
  fetchConversationMessages,
  fetchConversationPage,
  rejectConversationTask,
  uploadConversationAttachment,
  updateConversationInteractionPreference,
  updateConversationTitle,
  updateConversationMemoryPolicy,
  type AgentRunResult,
  type AgentRunProgress,
  type AttachmentRecord,
  type ConversationDetail,
  type ConversationRecord,
  type CreateConversationPayload,
  type MessageRecord,
  type ApprovalRecord,
  type TaskRecord,
  type UpdateConversationMemoryPolicyPayload
} from "@/api/conversation";
import { streamRunEvents } from "@/runtime/runStream";
import type { RuntimeEventEnvelope, RuntimeEventProgress } from "@/runtime/types";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true });
const conversationLoading = ref(false);
const conversationCreating = ref(false);
const draftPolicyLoading = ref(false);
const messageLoading = ref(false);
const sending = ref(false);
const policySaving = ref(false);
const streamPreferenceSaving = ref(false);
const memoryDrawerVisible = ref(false);
const approvalDialogVisible = ref(false);
const approvalLoading = ref(false);
const approvalSubmitting = ref(false);
const approvalResuming = ref(false);
const approvalAction = ref<"approve" | "reject" | null>(null);
const pendingApprovalTask = ref<TaskRecord | null>(null);
const pendingApproval = ref<ApprovalRecord | null>(null);
const approvalComment = ref("");
const agentOptions = ref<AgentRecord[]>([]);
const conversationRecords = ref<ConversationRecord[]>([]);
const messageRecords = ref<MessageRecord[]>([]);
const activeConversationId = ref<number | null>(null);
const activeConversationDetail = ref<ConversationDetail | null>(null);
const selectedAgentId = ref<number | null>(null);
const draftAgentDetail = ref<AgentDetail | null>(null);
const policyMode = ref<"" | "inherit" | "custom">("");
const activeRun = ref<AgentRunResult | null>(null);
const streamingAssistantContent = ref("");
const runProgressItems = ref<AgentRunProgress[]>([]);
const conversationTitle = ref("");
const messageContent = ref("");
const attachmentInput = ref<HTMLInputElement | null>(null);
const attachmentUploading = ref(false);
const attachmentRemovingId = ref<number | null>(null);
const pendingAttachments = ref<Array<AttachmentRecord & { previewUrl: string }>>([]);
const attachmentPreviewUrls = reactive<Record<number, string>>({});
const messageViewport = ref<HTMLElement | null>(null);
const expandedAgentIds = ref<number[]>([]);
let activeRuntimeStreamController: AbortController | null = null;

interface ConversationAgentGroup {
  agentId: number;
  agentCode: string;
  agentName: string;
  enabled: boolean;
  conversations: ConversationRecord[];
  lastActivityAt: string;
}

const DEFAULT_MEMORY_POLICY: UpdateConversationMemoryPolicyPayload = {
  userMemoryEnabled: true,
  userMemoryMode: "summary_only",
  userMemoryWritebackEnabled: true,
  preferSummaryOverRawHistory: true,
  maxRecentMessages: 6,
  maxRecentUserMessages: 4,
  preferStateFirst: true,
  historySummaryThresholdMessages: 6,
  minRetentionRatio: 0.18,
  qualityWarningThreshold: 75
};
const memoryPolicy = reactive<UpdateConversationMemoryPolicyPayload>({ ...DEFAULT_MEMORY_POLICY });
const activeAgentId = computed(() => activeConversationDetail.value?.agentId ?? selectedAgentId.value);
const conversationGroups = computed<ConversationAgentGroup[]>(() => {
  const groups = new Map<number, ConversationAgentGroup>();
  for (const agent of agentOptions.value) {
    groups.set(agent.agentId, {
      agentId: agent.agentId,
      agentCode: agent.agentCode,
      agentName: agent.agentName,
      enabled: agent.status === "enabled",
      conversations: [],
      lastActivityAt: agent.createdAt
    });
  }
  for (const conversation of conversationRecords.value) {
    const group = groups.get(conversation.agentId) ?? {
      agentId: conversation.agentId,
      agentCode: conversation.agentCode,
      agentName: conversation.agentName,
      enabled: false,
      conversations: [],
      lastActivityAt: conversation.createdAt
    };
    group.conversations.push(conversation);
    group.lastActivityAt = laterDateTime(group.lastActivityAt, conversation.lastMessageAt || conversation.createdAt);
    groups.set(conversation.agentId, group);
  }
  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      conversations: group.conversations.slice().sort((left, right) =>
        dateTimeValue(right.lastMessageAt || right.createdAt) - dateTimeValue(left.lastMessageAt || left.createdAt))
    }))
    .sort((left, right) => dateTimeValue(right.lastActivityAt) - dateTimeValue(left.lastActivityAt));
});
const activeRunStatusText = computed(() => {
  if (approvalResuming.value) return approvalAction.value === "reject" ? "处理中" : "继续执行中";
  if (!activeRun.value) return "接收中";
  return activeRun.value.status === "queued" ? "排队中" : "处理中";
});
const activeRunHint = computed(() => {
  if (approvalResuming.value) {
    return approvalAction.value === "reject"
      ? "审批已驳回，正在执行任务恢复策略"
      : "审批已通过，正在从执行断点继续";
  }
  if (!activeRun.value) return "正在接收你的消息";
  return activeRun.value.status === "queued" ? "任务已进入执行队列" : "正在分析并执行任务";
});
const visibleRunProgress = computed(() => runProgressItems.value.slice(-4));
const visibleMessageRecords = computed(() => messageRecords.value.filter((message) => !isRuntimeControlMessage(message)));
const pendingApprovalStep = computed(() => pendingApprovalTask.value?.steps.find(
  (item) => item.taskStepId === pendingApproval.value?.taskStepId
));
const pendingApprovalStepName = computed(() => pendingApprovalStep.value?.stepName || "高风险 Tool 执行");
const pendingApprovalToolCode = computed(() => {
  const task = pendingApprovalTask.value;
  const stepNo = pendingApprovalStep.value?.stepNo;
  if (!task || !stepNo) return "-";
  const currentPlan = task.plans.find((item) => item.planVersion === task.currentPlanVersion)
    || task.plans.slice().sort((left, right) => right.planVersion - left.planVersion)[0];
  return currentPlan?.steps.find((item) => item.stepNo === stepNo)?.targetToolCode || "-";
});
const draftSetupHint = computed(() => {
  if (!draftAgentDetail.value) return "请先选择一个可用 Agent";
  if (!conversationTitle.value.trim()) return "请输入会话名称";
  if (!policyMode.value) return "请选择继承默认策略或自定义当前会话";
  return policyMode.value === "inherit" ? "将使用 Agent 已发布的默认策略" : "自定义配置只影响当前会话";
});

async function loadAgents(): Promise<void> {
  const result = await fetchAgentPage({ pageNum: 1, pageSize: 100 });
  agentOptions.value = result.records;
}

async function handleDraftAgentChange(value: number | null | undefined): Promise<void> {
  const previousDefaultTitle = draftAgentDetail.value ? `${draftAgentDetail.value.agentName} 会话` : "";
  const shouldApplyDefaultTitle = !conversationTitle.value.trim() || conversationTitle.value === previousDefaultTitle;
  draftAgentDetail.value = null;
  policyMode.value = "";
  resetMemoryPolicy();
  if (!value) return;
  draftPolicyLoading.value = true;
  try {
    const detail = await fetchAgentDetail(value);
    draftAgentDetail.value = detail;
    expandAgentGroup(detail.agentId);
    if (shouldApplyDefaultTitle) conversationTitle.value = `${detail.agentName} 会话`;
    applyAgentDefaultPolicy(detail);
  } catch (error) {
    selectedAgentId.value = null;
    ElMessage.error(errorMessage(error, "Agent 默认策略加载失败"));
  } finally {
    draftPolicyLoading.value = false;
  }
}

async function loadConversations(): Promise<void> {
  conversationLoading.value = true;
  try {
    const result = await fetchConversationPage({ pageNum: 1, pageSize: 100 });
    conversationRecords.value = result.records;
  } finally { conversationLoading.value = false; }
}

async function selectConversation(conversationId: number, showLoading = true): Promise<void> {
  if (activeConversationId.value !== null && activeConversationId.value !== conversationId) {
    activeRuntimeStreamController?.abort();
    activeRuntimeStreamController = null;
    await discardPendingAttachments();
    clearAttachmentPreviewUrls();
  }
  clearPendingApproval();
  activeConversationId.value = conversationId;
  if (showLoading) messageLoading.value = true;
  try {
    const [detail, messages] = await Promise.all([
      fetchConversationDetail(conversationId), fetchConversationMessages(conversationId)
    ]);
    activeConversationDetail.value = detail;
    selectedAgentId.value = detail.agentId;
    expandAgentGroup(detail.agentId);
    draftAgentDetail.value = null;
    policyMode.value = "";
    messageRecords.value = messages;
    await loadMessageAttachmentPreviews(messages);
    activeRun.value = null;
    applyPolicy(detail);
    await router.replace({ path: "/chat", query: { conversationId: String(conversationId) } });
    await scrollToBottom();
    await loadPendingApproval(conversationId);
  } catch (error) {
    ElMessage.error(errorMessage(error, "会话加载失败"));
  } finally {
    if (showLoading) messageLoading.value = false;
  }
}

/**
 * 加载当前会话待审批任务。审批属于对话执行主链，因此发现 pending 记录后直接在会话页弹出。
 */
async function loadPendingApproval(conversationId: number, preferredTaskId?: number | null): Promise<void> {
  approvalLoading.value = true;
  try {
    const execution = await fetchConversationExecutions(conversationId);
    if (activeConversationId.value !== conversationId) return;
    const pendingTasks = execution.tasks
      .filter((task) => task.approvals.some((item) => item.status === "pending"))
      .sort((left, right) => right.taskId - left.taskId);
    const task = pendingTasks.find((item) => item.taskId === preferredTaskId) || pendingTasks[0];
    const approval = task?.approvals.find((item) => item.status === "pending") || null;
    if (!task || !approval) {
      clearPendingApproval();
      return;
    }
    pendingApprovalTask.value = task;
    pendingApproval.value = approval;
    approvalComment.value = "";
    approvalDialogVisible.value = true;
  } catch (error) {
    ElMessage.warning(errorMessage(error, "待审批任务加载失败"));
  } finally {
    approvalLoading.value = false;
  }
}

/**
 * 在对话窗口完成审批并沿原 Task Checkpoint 继续执行，不要求用户跳转到运营后台。
 */
async function handleConversationApproval(mode: "approve" | "reject"): Promise<void> {
  const conversationId = activeConversationId.value;
  const task = pendingApprovalTask.value;
  const approval = pendingApproval.value;
  if (!conversationId || !task || !approval || approvalSubmitting.value) return;
  if (mode === "reject") {
    try {
      await ElMessageBox.confirm("驳回后任务将终止或按恢复策略重新规划，确认继续？", "确认驳回", {
        confirmButtonText: "确认驳回",
        cancelButtonText: "取消",
        type: "warning"
      });
    } catch {
      return;
    }
  }
  approvalSubmitting.value = true;
  approvalResuming.value = true;
  approvalAction.value = mode;
  approvalDialogVisible.value = false;
  await nextTick();
  await scrollToBottom();
  try {
    const payload = { comment: approvalComment.value.trim() || undefined };
    const updatedTask = mode === "approve"
      ? await approveConversationTask(conversationId, task.taskId, approval.approvalId, payload)
      : await rejectConversationTask(conversationId, task.taskId, approval.approvalId, { ...payload, replanAfterReject: true });
    const nextApproval = updatedTask.approvals.find((item) => item.status === "pending") || null;
    if (nextApproval) {
      pendingApprovalTask.value = updatedTask;
      pendingApproval.value = nextApproval;
      approvalComment.value = "";
      approvalDialogVisible.value = true;
      ElMessage.warning("当前任务进入下一审批节点，请继续确认");
      return;
    }
    clearPendingApproval();
    await Promise.all([selectConversation(conversationId, false), loadConversations()]);
    ElMessage.success(mode === "approve" ? "审批已通过，任务已继续执行" : "审批已驳回");
  } catch (error) {
    approvalDialogVisible.value = true;
    ElMessage.error(errorMessage(error, mode === "approve" ? "审批通过失败" : "审批驳回失败"));
  } finally {
    approvalResuming.value = false;
    approvalSubmitting.value = false;
    approvalAction.value = null;
  }
}

function clearPendingApproval(): void {
  approvalDialogVisible.value = false;
  pendingApprovalTask.value = null;
  pendingApproval.value = null;
  approvalComment.value = "";
}

function prepareNewConversation(agentId: number): void {
  activeRuntimeStreamController?.abort();
  activeRuntimeStreamController = null;
  void discardPendingAttachments();
  clearAttachmentPreviewUrls();
  activeConversationId.value = null;
  activeConversationDetail.value = null;
  activeRun.value = null;
  messageRecords.value = [];
  selectedAgentId.value = null;
  draftAgentDetail.value = null;
  policyMode.value = "";
  resetMemoryPolicy();
  conversationTitle.value = "";
  messageContent.value = "";
  void router.replace("/chat");
  selectedAgentId.value = agentId;
  expandAgentGroup(agentId);
  void handleDraftAgentChange(agentId);
}

async function createConfiguredConversation(): Promise<void> {
  if (!selectedAgentId.value || !draftAgentDetail.value || !conversationTitle.value.trim() || !policyMode.value) {
    ElMessage.warning("请先填写会话名称并确认 Agent 和会话策略");
    return;
  }
  conversationCreating.value = true;
  try {
    const payload: CreateConversationPayload = {
      agentId: selectedAgentId.value,
      title: conversationTitle.value.trim()
    };
    if (policyMode.value === "custom") Object.assign(payload, { ...memoryPolicy });
    const detail = await createConversation(payload);
    activeConversationId.value = detail.conversationId;
    activeConversationDetail.value = detail;
    expandAgentGroup(detail.agentId);
    messageRecords.value = [];
    applyPolicy(detail);
    await Promise.all([
      loadConversations(),
      router.replace({ path: "/chat", query: { conversationId: String(detail.conversationId) } })
    ]);
    ElMessage.success("会话策略已确认，可以开始对话");
  } catch (error) {
    ElMessage.error(errorMessage(error, "会话创建失败"));
  } finally {
    conversationCreating.value = false;
  }
}

function ensureConversation(): number {
  if (!activeConversationId.value) throw new Error("请先配置策略并创建会话");
  return activeConversationId.value;
}

async function submitMessage(): Promise<void> {
  const content = messageContent.value.trim();
  const attachments = pendingAttachments.value.slice();
  if ((!content && !attachments.length) || sending.value || approvalResuming.value) return;
  const conversationId = ensureConversation();
  const optimisticMessageId = -Date.now();
  const optimisticMessage: MessageRecord = {
    messageId: optimisticMessageId,
    conversationId,
    messageType: "user",
    sequenceNo: (messageRecords.value[messageRecords.value.length - 1]?.sequenceNo || 0) + 1,
    content: content || "请分析上传的图片",
    responseStatus: "success",
    errorMessage: null,
    createdAt: new Date().toISOString(),
    attachments
  };
  let runAccepted = false;
  const streamingEnabled = activeConversationDetail.value?.responseStreamingEnabled === true;
  sending.value = true;
  activeRun.value = null;
  streamingAssistantContent.value = "";
  runProgressItems.value = [];
  messageContent.value = "";
  messageRecords.value.push(optimisticMessage);
  await scrollToBottom();
  try {
    let run = await enqueueConversationMessage(conversationId, {
      content,
      attachmentIds: attachments.map((item) => item.attachmentId)
    }, generateUuid());
    runAccepted = true;
    for (const attachment of attachments) {
      attachmentPreviewUrls[attachment.attachmentId] = attachment.previewUrl;
    }
    pendingAttachments.value = [];
    activeRun.value = run;
    await scrollToBottom();
    if (streamingEnabled) {
      const streamController = new AbortController();
      activeRuntimeStreamController?.abort();
      activeRuntimeStreamController = streamController;
      try {
        const streamResult = await streamRunEvents(
          conversationId,
          activeConversationDetail.value?.agentId,
          run.runCode,
          {
            onStatus: (status, taskId) => {
              run = { ...run, status: normalizeRunStatus(status), taskId: taskId ?? run.taskId };
              activeRun.value = run;
            },
            onProgress: appendRunProgress,
            onDelta: (delta) => {
              streamingAssistantContent.value += delta;
              void scrollToBottom();
            },
            onReset: () => {
              streamingAssistantContent.value = "";
            },
            onReplace: (replacement) => {
              streamingAssistantContent.value = replacement;
              void scrollToBottom();
            },
            onEvent: (event) => {
              const progress = toLifecycleProgress(event);
              if (progress) appendRunProgress(progress);
            },
            onApprovalRequested: (event) => {
              void loadPendingApproval(conversationId, event.taskId ?? run.taskId);
            },
            onArtifactPublished: () => {
              void scrollToBottom();
            }
          },
          { signal: streamController.signal }
        );
        run = {
          ...run,
          status: normalizeRunStatus(streamResult.status),
          taskId: streamResult.taskId ?? run.taskId,
          errorMessage: streamResult.errorMessage
        };
      } catch (streamError) {
        if (isAbortError(streamError)) return;
        streamingAssistantContent.value = "";
        ElMessage.warning(errorMessage(streamError, "流式连接中断，已切换普通响应"));
        run = await waitForRun(conversationId, run);
      } finally {
        if (activeRuntimeStreamController === streamController) activeRuntimeStreamController = null;
      }
    } else {
      run = await waitForRun(conversationId, run);
    }
    activeRun.value = run;
    await Promise.all([selectConversation(conversationId, false), loadConversations()]);
    if (run.status === "failed") ElMessage.error(run.errorMessage || "Agent 执行失败");
  } catch (error) {
    if (isAbortError(error)) return;
    if (!runAccepted) {
      const failedMessage = messageRecords.value.find((item) => item.messageId === optimisticMessageId);
      if (failedMessage) {
        failedMessage.responseStatus = "failed";
        failedMessage.errorMessage = errorMessage(error, "消息发送失败");
      }
      messageContent.value = content;
    }
    ElMessage.error(errorMessage(error, "消息发送失败"));
  } finally {
    sending.value = false;
    activeRun.value = null;
    streamingAssistantContent.value = "";
    await scrollToBottom();
  }
}

async function handleAttachmentSelection(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  if (!files.length || attachmentUploading.value) return;
  const availableCount = Math.max(0, 5 - pendingAttachments.value.length);
  if (files.length > availableCount) ElMessage.warning("单次消息最多上传 5 张图片");
  attachmentUploading.value = true;
  try {
    for (const file of files.slice(0, availableCount)) {
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        ElMessage.warning(`${file.name} 格式不支持`);
        continue;
      }
      if (file.size > 10 * 1024 * 1024) {
        ElMessage.warning(`${file.name} 超过 10 MB`);
        continue;
      }
      try {
        const attachment = await uploadConversationAttachment(ensureConversation(), file);
        pendingAttachments.value.push({ ...attachment, previewUrl: URL.createObjectURL(file) });
      } catch (error) {
        ElMessage.error(errorMessage(error, `${file.name} 上传失败`));
      }
    }
  } finally {
    attachmentUploading.value = false;
  }
}

async function removePendingAttachment(attachment: AttachmentRecord & { previewUrl: string }): Promise<void> {
  if (attachmentRemovingId.value !== null) return;
  attachmentRemovingId.value = attachment.attachmentId;
  try {
    await deleteConversationAttachment(attachment.conversationId, attachment.attachmentId);
    pendingAttachments.value = pendingAttachments.value.filter(
      (item) => item.attachmentId !== attachment.attachmentId
    );
    URL.revokeObjectURL(attachment.previewUrl);
  } catch (error) {
    ElMessage.error(errorMessage(error, "附件删除失败"));
  } finally {
    attachmentRemovingId.value = null;
  }
}

async function discardPendingAttachments(): Promise<void> {
  const attachments = pendingAttachments.value.slice();
  pendingAttachments.value = [];
  for (const attachment of attachments) URL.revokeObjectURL(attachment.previewUrl);
  await Promise.allSettled(attachments.map((attachment) =>
    deleteConversationAttachment(attachment.conversationId, attachment.attachmentId)));
}

async function loadMessageAttachmentPreviews(messages: MessageRecord[]): Promise<void> {
  const attachments = messages.flatMap((message) => message.attachments || []);
  await Promise.allSettled(attachments.map(async (attachment) => {
    if (attachmentPreviewUrls[attachment.attachmentId]) return;
    const blob = await fetchConversationAttachmentBlob(attachment.conversationId, attachment.attachmentId);
    attachmentPreviewUrls[attachment.attachmentId] = URL.createObjectURL(blob);
  }));
}

async function downloadAttachment(attachment: AttachmentRecord): Promise<void> {
  try {
    const blob = await fetchConversationAttachmentBlob(attachment.conversationId, attachment.attachmentId);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = attachment.fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(errorMessage(error, "图片下载失败"));
  }
}

function clearAttachmentPreviewUrls(): void {
  for (const url of Object.values(attachmentPreviewUrls)) URL.revokeObjectURL(url);
  for (const key of Object.keys(attachmentPreviewUrls)) delete attachmentPreviewUrls[Number(key)];
}

function appendRunProgress(progress: AgentRunProgress | RuntimeEventProgress): void {
  if (runProgressItems.value.some((item) => item.sequenceNo === progress.sequenceNo)) return;
  runProgressItems.value = [...runProgressItems.value, progress].slice(-8);
  void scrollToBottom();
}

/** 将统一生命周期事件转换为会话内的可读执行进度，避免把控制消息写入聊天记录。 */
function toLifecycleProgress(event: RuntimeEventEnvelope): AgentRunProgress | null {
  const payload = event.payload;
  const metadata = event.metadata;
  const stepNo = nullablePositiveNumber(metadata.stepNo ?? payload.stepNo);
  const stepCount = nullablePositiveNumber(metadata.stepCount ?? payload.stepCount);
  const stepName = textValue(payload.stepName ?? metadata.stepName);
  const toolCode = textValue(payload.toolCode ?? metadata.toolCode);
  const fileName = textValue(payload.fileName ?? payload.artifactName ?? metadata.fileName);
  const messages: Record<string, string> = {
    "plan.created": "执行计划已生成",
    "plan.replanned": "执行计划已更新",
    "checkpoint.created": "已保存执行断点",
    "checkpoint.restored": "已从执行断点恢复",
    "step.started": stepName ? `正在执行步骤${stepNo ? ` ${stepNo}${stepCount ? `/${stepCount}` : ""}` : ""}：${stepName}` : "正在执行任务步骤",
    "step.completed": stepName ? `步骤已完成：${stepName}` : "任务步骤已完成",
    "step.failed": stepName ? `步骤执行失败：${stepName}` : "任务步骤执行失败",
    "tool.call.started": toolCode ? `正在调用 Tool：${toolCode}` : "正在调用 Tool",
    "tool.call.completed": toolCode ? `Tool 调用完成：${toolCode}` : "Tool 调用完成",
    "tool.call.failed": toolCode ? `Tool 调用失败：${toolCode}` : "Tool 调用失败",
    "approval.requested": `等待审批：${stepName || textValue(payload.nodeName) || "高风险 Tool"}`,
    "approval.resolved": "审批已处理，任务继续执行",
    "artifact.published": fileName ? `产物已生成：${fileName}` : "任务产物已生成"
  };
  const message = messages[event.eventType];
  if (!message) return null;
  return {
    stage: event.eventType.split(".")[0] || "execution",
    message,
    sequenceNo: event.sequenceNo,
    stepNo,
    stepCount,
    stepType: textValue(metadata.stepType) || null
  };
}

async function handleStreamingPreferenceChange(value: string | number | boolean): Promise<void> {
  if (!activeConversationId.value || !activeConversationDetail.value) return;
  streamPreferenceSaving.value = true;
  try {
    activeConversationDetail.value = await updateConversationInteractionPreference(activeConversationId.value, {
      responseStreamingEnabled: value === true
    });
    ElMessage.success(activeConversationDetail.value.responseStreamingEnabled ? "已开启当前会话流式响应" : "已关闭当前会话流式响应");
  } catch (error) {
    ElMessage.error(errorMessage(error, "流式响应设置保存失败"));
  } finally {
    streamPreferenceSaving.value = false;
  }
}

function handleComposerKeydown(event: KeyboardEvent): void {
  if (event.key !== "Enter" || event.shiftKey || event.isComposing) return;
  event.preventDefault();
  void submitMessage();
}

async function waitForRun(conversationId: number, initial: AgentRunResult): Promise<AgentRunResult> {
  let run = initial;
  const deadline = Date.now() + 15 * 60 * 1000;
  while (!isTerminalRun(run.status) && Date.now() < deadline) {
    await delay(1500);
    run = await fetchAgentRun(conversationId, run.runCode);
    activeRun.value = run;
  }
  if (!isTerminalRun(run.status)) throw new Error("运行仍在后台执行，请稍后在任务运行中心查看");
  return run;
}

async function saveMemoryPolicy(): Promise<void> {
  if (!activeConversationId.value) return;
  policySaving.value = true;
  try {
    activeConversationDetail.value = await updateConversationMemoryPolicy(activeConversationId.value, { ...memoryPolicy });
    ElMessage.success("当前会话策略已保存");
  } catch (error) {
    ElMessage.error(errorMessage(error, "会话策略保存失败"));
  } finally { policySaving.value = false; }
}

async function renameConversation(item: ConversationRecord): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的会话名称", "修改会话名称", {
      inputValue: item.title || `${item.agentName} 会话`,
      inputPlaceholder: "请输入会话名称",
      inputValidator: (input) => {
        const title = input.trim();
        if (!title) return "会话名称不能为空";
        if (title.length > 128) return "会话名称长度不能超过128个字符";
        return true;
      },
      confirmButtonText: "保存",
      cancelButtonText: "取消"
    });
    const detail = await updateConversationTitle(item.conversationId, { title: value.trim() });
    if (activeConversationId.value === item.conversationId) activeConversationDetail.value = detail;
    await loadConversations();
    ElMessage.success("会话名称已更新");
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(errorMessage(error, "会话名称修改失败"));
  }
}

function resetMemoryPolicy(): void {
  Object.assign(memoryPolicy, DEFAULT_MEMORY_POLICY);
}

function applyAgentDefaultPolicy(detail: AgentDetail): void {
  const metadata = objectValue(detail.metadata);
  const memory = objectValue(metadata.memoryPolicy);
  const compaction = objectValue(metadata.compactionPolicy);
  Object.assign(memoryPolicy, {
    userMemoryEnabled: booleanValue(memory.userMemoryEnabled, DEFAULT_MEMORY_POLICY.userMemoryEnabled),
    userMemoryMode: normalizeMemoryMode(memory.userMemoryMode),
    userMemoryWritebackEnabled: booleanValue(memory.userMemoryWritebackEnabled, DEFAULT_MEMORY_POLICY.userMemoryWritebackEnabled),
    preferSummaryOverRawHistory: booleanValue(compaction.preferSummaryOverRawHistory, DEFAULT_MEMORY_POLICY.preferSummaryOverRawHistory ?? true),
    maxRecentMessages: positiveInteger(compaction.maxRecentMessages, DEFAULT_MEMORY_POLICY.maxRecentMessages ?? 6),
    maxRecentUserMessages: positiveInteger(compaction.maxRecentUserMessages, DEFAULT_MEMORY_POLICY.maxRecentUserMessages ?? 4),
    preferStateFirst: booleanValue(compaction.preferStateFirst, DEFAULT_MEMORY_POLICY.preferStateFirst ?? true),
    historySummaryThresholdMessages: positiveInteger(compaction.historySummaryThresholdMessages, DEFAULT_MEMORY_POLICY.historySummaryThresholdMessages ?? 6),
    minRetentionRatio: boundedNumber(compaction.minRetentionRatio, DEFAULT_MEMORY_POLICY.minRetentionRatio ?? 0.18, 0.1, 0.9),
    qualityWarningThreshold: boundedNumber(compaction.qualityWarningThreshold, DEFAULT_MEMORY_POLICY.qualityWarningThreshold ?? 75, 1, 100)
  });
}

function memoryModeText(value: string): string {
  return ({ off: "关闭", summary_only: "仅摘要", summary_and_facts: "摘要 + 长期事实" } as Record<string, string>)[value] || value;
}

function normalizeMemoryMode(value: unknown): string {
  return value === "off" || value === "summary_and_facts" ? value : "summary_only";
}

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function booleanValue(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function positiveInteger(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function boundedNumber(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.min(max, Math.max(min, parsed)) : fallback;
}

function applyPolicy(detail: ConversationDetail): void {
  Object.assign(memoryPolicy, {
    userMemoryEnabled: detail.userMemoryEnabled,
    userMemoryMode: detail.userMemoryMode,
    userMemoryWritebackEnabled: detail.userMemoryWritebackEnabled,
    preferSummaryOverRawHistory: detail.preferSummaryOverRawHistory,
    maxRecentMessages: detail.maxRecentMessages,
    maxRecentUserMessages: detail.maxRecentUserMessages,
    preferStateFirst: detail.preferStateFirst,
    historySummaryThresholdMessages: detail.historySummaryThresholdMessages,
    minRetentionRatio: detail.minRetentionRatio,
    qualityWarningThreshold: detail.qualityWarningThreshold
  });
}

function renderAssistantMessage(content: string): string { return markdown.render(content || ""); }

/**
 * 兼容历史数据：运行控制状态由 Task、Run 和审批弹窗承载，不作为 Agent 对话内容展示。
 */
function isRuntimeControlMessage(message: MessageRecord): boolean {
  if (message.messageType !== "assistant") return false;
  const content = message.content.trim();
  return (content.includes("审批节点") && content.includes("等待人工审批"))
    || content === "当前任务等待审批"
    || (content.startsWith("审批节点「") && (content.includes("当前任务结束") || content.includes("二次规划")))
    || (content.startsWith("# 当前进度汇报")
      && content.includes("高风险Tool人工审批")
      && (content.includes("等待中") || content.includes("暂停")))
    || content.includes("任务已在安全步骤边界暂停")
    || content.includes("任务等待外部事件");
}

async function handleAssistantLinkClick(event: MouseEvent): Promise<void> {
  const target = event.target instanceof Element ? event.target.closest("a") : null;
  const href = target?.getAttribute("href") || "";
  if (!href.startsWith("/api/core/conversations/") || !href.includes("/artifacts?")) return;
  event.preventDefault();
  try {
    await downloadTaskArtifact(href);
  } catch (error) {
    ElMessage.error(errorMessage(error, "文件下载失败"));
  }
}
function agentAvatarText(agentName: string): string { return Array.from(agentName.trim())[0] || "A"; }
function isAgentExpanded(agentId: number): boolean { return expandedAgentIds.value.includes(agentId); }
function expandAgentGroup(agentId: number): void {
  if (!isAgentExpanded(agentId)) expandedAgentIds.value = [...expandedAgentIds.value, agentId];
}
function toggleAgentGroup(agentId: number): void {
  expandedAgentIds.value = isAgentExpanded(agentId)
    ? expandedAgentIds.value.filter((item) => item !== agentId)
    : [...expandedAgentIds.value, agentId];
}
function messageRoleName(messageType: string): string {
  return messageType === "user" ? authStore.currentUser?.displayName || "我" : activeConversationDetail.value?.agentName || "Agent";
}
function messageAvatarText(messageType: string): string {
  const name = messageRoleName(messageType).trim();
  return name ? Array.from(name)[0] : messageType === "user" ? "我" : "A";
}
function isTerminalRun(status: string): boolean { return ["completed", "waiting_approval", "waiting_event", "paused", "failed", "cancelled"].includes(status); }
function normalizeRunStatus(status: string): AgentRunResult["status"] {
  return isTerminalRun(status) || status === "queued" || status === "running"
    ? status as AgentRunResult["status"]
    : "failed";
}
function statusText(value?: string): string { return ({ queued: "排队中", running: "执行中", processing: "执行中", completed: "已完成", success: "成功", failed: "失败", waiting_approval: "待审批", waiting_event: "等待事件", paused: "已暂停", cancelled: "已取消" } as Record<string, string>)[value || ""] || value || "-"; }
function dateTimeValue(value?: string | null): number {
  const timestamp = value ? new Date(value).getTime() : 0;
  return Number.isNaN(timestamp) ? 0 : timestamp;
}
function laterDateTime(current: string, candidate: string): string {
  return dateTimeValue(candidate) > dateTimeValue(current) ? candidate : current;
}
function delay(ms: number): Promise<void> { return new Promise((resolve) => window.setTimeout(resolve, ms)); }
function nullablePositiveNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}
function textValue(value: unknown): string { return typeof value === "string" ? value.trim() : ""; }
function isAbortError(error: unknown): boolean { return error instanceof DOMException && error.name === "AbortError"; }
function errorMessage(error: unknown, fallback: string): string { return error instanceof Error && error.message ? error.message : fallback; }
async function scrollToBottom(): Promise<void> { await nextTick(); if (messageViewport.value) messageViewport.value.scrollTop = messageViewport.value.scrollHeight; }

watch(() => route.query.conversationId, (value) => {
  const conversationId = Number(value || 0);
  if (conversationId > 0 && conversationId !== activeConversationId.value) void selectConversation(conversationId);
});

onMounted(async () => {
  try {
    await Promise.all([loadAgents(), loadConversations()]);
    if (!expandedAgentIds.value.length && conversationGroups.value.length) {
      expandedAgentIds.value = [conversationGroups.value[0].agentId];
    }
    const queryAgentId = Number(route.query.agentId || 0);
    const conversationId = Number(route.query.conversationId || 0);
    if (typeof route.query.message === "string") messageContent.value = route.query.message;
    if (conversationId > 0) {
      await selectConversation(conversationId);
    } else if (queryAgentId > 0) {
      selectedAgentId.value = queryAgentId;
      await handleDraftAgentChange(queryAgentId);
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "会话中心初始化失败"));
  }
});

onBeforeUnmount(() => {
  activeRuntimeStreamController?.abort();
  activeRuntimeStreamController = null;
  clearAttachmentPreviewUrls();
  const attachments = pendingAttachments.value.slice();
  for (const attachment of attachments) URL.revokeObjectURL(attachment.previewUrl);
  void Promise.allSettled(attachments.map((attachment) =>
    deleteConversationAttachment(attachment.conversationId, attachment.attachmentId)));
});
</script>

<style scoped>
.conversation-workspace{display:grid;grid-template-columns:290px minmax(0,1fr);min-width:0;height:calc(100vh - 112px);overflow:hidden;border:1px solid #d7e0dc;border-radius:22px;background:#f5f7f4;color:#27373c}.conversation-rail{display:flex;flex-direction:column;min-height:0;padding:20px 14px;border-right:1px solid #d9e1dd;background:linear-gradient(180deg,#eaf0ec,#f5f7f4)}.conversation-rail header{display:flex;align-items:center;justify-content:space-between;padding:0 7px 16px}.conversation-rail header span,.eyebrow{font:700 10px Georgia,serif;letter-spacing:.18em;color:#bc693e}.conversation-rail h2{margin:3px 0 0;font:700 25px Georgia,serif;color:#24443f}.conversation-list{display:flex;flex:1;flex-direction:column;gap:8px;overflow:auto}.conversation-item{display:flex;flex-direction:column;gap:5px;width:100%;padding:14px;text-align:left;border:1px solid transparent;border-radius:14px;background:transparent;color:#30433f;cursor:pointer}.conversation-item:hover{background:#fff}.conversation-item.active{border-color:#bdd0c8;background:#fff;box-shadow:0 8px 24px rgba(42,70,62,.08)}.conversation-item span{font-size:12px;color:#708078}.conversation-item p{overflow:hidden;margin:2px 0 0;color:#829088;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.conversation-stage{display:flex;min-width:0;min-height:0;flex-direction:column;padding:22px 26px;background:radial-gradient(circle at 90% 0,rgba(205,143,94,.11),transparent 28%),#fff}.stage-header{display:flex;justify-content:space-between;gap:20px}.stage-header h1{margin:5px 0;font:700 29px Georgia,serif;color:#203c39}.stage-header p{margin:0;color:#77847f}.stage-actions{display:flex;align-items:flex-start;gap:8px}.agent-selector{display:flex;align-items:center;gap:18px;margin-top:17px}.binding-summary{display:flex;gap:14px;color:#687973;font-size:12px}.message-viewport{flex:1;min-height:0;margin-top:15px;padding:12px 8px;overflow:auto}.message{display:grid;grid-template-columns:70px minmax(0,1fr);gap:12px;margin:14px 0}.message-role{padding-top:10px;color:#7d8b85;font:700 10px Georgia,serif;letter-spacing:.1em}.message-bubble{max-width:880px;padding:15px 18px;border:1px solid #dce5e1;border-radius:4px 17px 17px 17px;background:#f6f8f7}.message--user .message-bubble{justify-self:end;border-color:#c8d9d1;background:#eaf3ef}.message-bubble footer{display:flex;justify-content:flex-end;gap:8px;margin-top:10px;color:#89958f;font-size:11px}.plain-content{white-space:pre-wrap;line-height:1.7}.markdown-body{line-height:1.75}.markdown-body :deep(table){width:100%;border-collapse:collapse}.markdown-body :deep(th),.markdown-body :deep(td){padding:8px 10px;border:1px solid #cedbd5;text-align:left}.markdown-body :deep(pre){overflow:auto;padding:12px;border-radius:9px;background:#1b2d31;color:#e2ece8}.empty-conversation{display:flex;height:100%;min-height:260px;align-items:center;justify-content:center;flex-direction:column;text-align:center;color:#75847e}.empty-conversation span{color:#bf7147;font:700 11px Georgia,serif;letter-spacing:.2em}.empty-conversation h2{margin:8px 0;font:700 27px Georgia,serif;color:#31504b}.empty-conversation p{max-width:520px;line-height:1.7}.composer{padding:14px;border:1px solid #d6e0db;border-radius:16px;background:#f9faf9;box-shadow:0 12px 30px rgba(36,64,56,.08)}.composer :deep(.el-textarea__inner){border:0;box-shadow:none;background:transparent}.composer-footer{display:flex;align-items:center;justify-content:space-between;margin-top:9px;color:#87948e;font-size:12px}.policy-intro{margin-bottom:18px;padding:13px;border-radius:10px;background:#eef4f1;color:#60726a;line-height:1.6}.policy-form{display:grid;gap:10px}.policy-switch-row{display:flex;align-items:center;justify-content:space-between;padding:12px 0;border-bottom:1px solid #edf0ee}.policy-switch-row div{display:flex;flex-direction:column;gap:4px}.policy-switch-row span{color:#7b8983;font-size:12px}.policy-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 14px}.memory-snapshot{margin-top:20px;padding:16px;border-radius:12px;background:#f5f7f6}.memory-snapshot h3{font:700 15px Georgia,serif;color:#31504b}.memory-snapshot p{color:#65746e;line-height:1.7}@media(max-width:1000px){.conversation-workspace{grid-template-columns:1fr;height:auto;overflow:visible}.conversation-rail{max-height:280px;border-right:0;border-bottom:1px solid #d9e1dd}.conversation-stage{min-height:700px}.stage-header,.agent-selector{align-items:flex-start;flex-direction:column}.message{grid-template-columns:1fr}.message-role{padding:0}}

/* 会话页优先保证消息阅读空间，滚动只发生在会话列表和消息列表内部。 */
.conversation-workspace {
  grid-template-columns: 280px minmax(0, 1fr);
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  border-radius: 18px;
}

.agent-conversation-group {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.agent-group-header {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 3px;
  border: 1px solid transparent;
  border-radius: 13px;
  transition: border-color 150ms ease, background-color 150ms ease;
}

.agent-group-header:hover,
.agent-group-header.active {
  border-color: #d1ddd8;
  background: rgba(255, 255, 255, .72);
}

.agent-group-toggle {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 9px;
  padding: 6px;
  border: 0;
  background: transparent;
  color: #2c413d;
  text-align: left;
  cursor: pointer;
}

.agent-group-avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(145deg, #2e5e57, #4a8379);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.agent-group-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.agent-group-copy strong,
.agent-group-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-group-copy strong {
  font-size: 13px;
}

.agent-group-copy small {
  color: #7c8984;
  font-size: 10px;
}

.agent-group-arrow {
  flex: 0 0 auto;
  color: #83918b;
  font-size: 12px;
  transition: transform 160ms ease;
}

.agent-group-arrow.expanded {
  transform: rotate(90deg);
}

.agent-group-create {
  flex: 0 0 auto;
  opacity: 0;
}

.agent-group-header:hover .agent-group-create,
.agent-group-header.active .agent-group-create,
.agent-group-create:focus-visible {
  opacity: 1;
}

.agent-conversation-list {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-left: 20px;
  padding: 2px 0 7px 12px;
  border-left: 1px solid #cdd9d4;
}

.agent-empty-action,
.agent-empty-text {
  margin: 3px 0;
  padding: 8px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #7c8b85;
  font-size: 11px;
  text-align: left;
}

.agent-empty-action {
  cursor: pointer;
}

.agent-empty-action:hover {
  background: rgba(255, 255, 255, .78);
  color: #32685f;
}

.selected-agent-card {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid #d5e1dc;
  border-radius: 13px;
  background: rgba(255, 255, 255, .78);
}

.selected-agent-card > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.selected-agent-card strong,
.selected-agent-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-agent-card strong {
  color: #2c4843;
  font-size: 14px;
}

.selected-agent-card small {
  color: #7b8a84;
  font-size: 11px;
}

.conversation-item .conversation-title-row {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: inherit;
  font-size: inherit;
}

.conversation-title-row strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-rename {
  flex: 0 0 auto;
  opacity: 0;
  color: #6d7d77;
  cursor: pointer;
  transition: color 150ms ease, opacity 150ms ease;
}

.conversation-item:hover .conversation-rename,
.conversation-item:focus-visible .conversation-rename,
.conversation-rename:focus-visible {
  opacity: 1;
}

.conversation-rename:hover {
  color: #1f655d;
}

.conversation-rail {
  box-sizing: border-box;
  padding: 14px 10px;
}

.conversation-rail header {
  padding: 0 5px 10px;
}

.conversation-rail h2 {
  font-size: 22px;
}

.conversation-list {
  gap: 5px;
}

.conversation-item {
  gap: 3px;
  padding: 10px;
  border-radius: 11px;
}

.conversation-stage {
  box-sizing: border-box;
  padding: 16px 20px 14px;
}

.stage-header {
  min-height: 54px;
  align-items: center;
  gap: 14px;
}

.stage-header > div:first-child {
  min-width: 0;
}

.stage-header h1 {
  overflow: hidden;
  margin: 2px 0;
  font-size: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-header p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stage-actions {
  flex: 0 0 auto;
  align-items: center;
}

.agent-selector {
  min-width: 0;
  gap: 12px;
  margin-top: 9px;
}

.agent-select {
  flex: 0 0 260px;
}

.message-viewport {
  margin-top: 9px;
  padding: 8px 6px;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.message {
  display: flex;
  grid-template-columns: none;
  align-items: flex-start;
  gap: 9px;
  margin: 14px 0;
}

.message--user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: flex;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border: 1px solid #b9d0c7;
  border-radius: 12px;
  background: linear-gradient(145deg, #285d55, #438178);
  box-shadow: 0 5px 14px rgba(40, 80, 70, .16);
  color: #fff;
  font-weight: 700;
}

.message--user .message-avatar {
  border-color: #d9b58e;
  background: linear-gradient(145deg, #b76639, #d38a58);
  box-shadow: 0 5px 14px rgba(156, 87, 46, .16);
}

.agent-avatar-icon {
  width: 23px;
  height: 23px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.message-content {
  display: flex;
  min-width: 0;
  max-width: min(920px, calc(100% - 50px));
  flex-direction: column;
  align-items: flex-start;
}

.message--user .message-content {
  align-items: flex-end;
}

.message-meta {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin: 0 4px 5px;
  color: #89958f;
  font-size: 11px;
}

.message-meta strong {
  overflow: hidden;
  max-width: 260px;
  color: #536760;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message--user .message-meta {
  flex-direction: row-reverse;
}

.message-bubble {
  width: fit-content;
  max-width: 100%;
  box-sizing: border-box;
  padding: 13px 16px;
}

.message--assistant .message-bubble {
  border-radius: 4px 17px 17px 17px;
}

.message--user .message-bubble {
  border-radius: 17px 4px 17px 17px;
}

.message-error {
  margin: 10px 0 0;
  padding-top: 9px;
  border-top: 1px solid #efc7c7;
  color: #b42318;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.message--pending .message-avatar {
  animation: pending-avatar-pulse 1.8s ease-in-out infinite;
}

.pending-response {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #657670;
  font-size: 13px;
}

.run-progress-list {
  display: grid;
  min-width: min(360px, 66vw);
  gap: 7px;
}

.run-progress-item {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #8a9893;
  line-height: 1.35;
}

.run-progress-item.is-current {
  color: #526c64;
  font-weight: 600;
}

.run-progress-check {
  width: 15px;
  color: #4d8a76;
  text-align: center;
}

.streaming-content {
  min-width: min(560px, 60vw);
}

.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.typing-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #4d766e;
  animation: typing-dot 1.1s ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
  animation-delay: 140ms;
}

.typing-dots i:nth-child(3) {
  animation-delay: 280ms;
}

@keyframes typing-dot {
  0%, 60%, 100% { opacity: .35; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
}

@keyframes pending-avatar-pulse {
  0%, 100% { box-shadow: 0 5px 14px rgba(40, 80, 70, .16); }
  50% { box-shadow: 0 5px 20px rgba(40, 112, 98, .34); }
}

.composer {
  flex: 0 0 auto;
  padding: 10px 12px;
  border-radius: 13px;
}

.composer-footer {
  margin-top: 5px;
}

.composer-preferences {
  display: flex;
  align-items: center;
  gap: 9px;
}

.streaming-switch-label {
  margin-left: 6px;
  padding-left: 12px;
  border-left: 1px solid #d5dfdb;
  color: #5f746d;
}

.new-conversation-setup {
  width: min(760px, calc(100% - 20px));
  box-sizing: border-box;
  margin: 8px auto 20px;
  padding: 22px;
  border: 1px solid #d5e1dc;
  border-radius: 18px;
  background: linear-gradient(145deg, #fbfcfb, #f3f7f5);
  box-shadow: 0 14px 36px rgba(39, 69, 61, .08);
}

.new-conversation-setup > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.new-conversation-setup > header span {
  color: #b8663c;
  font: 700 10px Georgia, serif;
  letter-spacing: .16em;
}

.new-conversation-setup > header h2 {
  margin: 5px 0 0;
  color: #284b45;
  font: 700 23px Georgia, serif;
}

.new-conversation-setup > header p {
  max-width: 360px;
  margin: 2px 0 0;
  color: #74837d;
  font-size: 12px;
  line-height: 1.6;
}

.policy-mode-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
}

.policy-mode-options :deep(.el-radio) {
  height: auto;
  min-height: 64px;
  box-sizing: border-box;
  align-items: flex-start;
  margin: 0;
  padding: 13px 14px;
  border: 1px solid #d6e1dc;
  border-radius: 12px;
  background: #fff;
  white-space: normal;
}

.policy-mode-options :deep(.el-radio.is-checked) {
  border-color: #4c8176;
  background: #edf6f2;
}

.policy-mode-options :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #304a44;
}

.policy-mode-options :deep(.el-radio__label span) {
  color: #788781;
  font-size: 12px;
}

.policy-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 9px;
  margin: 4px 0 16px;
}

.policy-summary-grid article {
  padding: 11px 12px;
  border: 1px solid #dce5e1;
  border-radius: 11px;
  background: #fff;
}

.policy-summary-grid span,
.policy-summary-grid strong {
  display: block;
}

.policy-summary-grid span {
  color: #7c8984;
  font-size: 11px;
}

.policy-summary-grid strong {
  overflow: hidden;
  margin-top: 5px;
  color: #315b53;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-policy-editor {
  margin-top: 8px;
  padding: 8px 16px 2px;
  border: 1px solid #dce5e1;
  border-radius: 14px;
  background: #fff;
}

.new-conversation-setup > footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 4px;
}

.new-conversation-setup > footer span {
  color: #7b8983;
  font-size: 12px;
}

.runtime-config-summary {
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #dce5e1;
  border-radius: 12px;
  background: #f7f9f8;
}

.runtime-config-summary h3 {
  margin: 0 0 10px;
  color: #31504b;
  font: 700 15px Georgia, serif;
}

.runtime-config-summary dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.runtime-config-summary dl > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
}

.runtime-config-summary dt {
  color: #7b8983;
}

.runtime-config-summary dd {
  overflow-wrap: anywhere;
  margin: 0;
  color: #40544d;
}

.conversation-approval {
  display: grid;
  gap: 18px;
}

.approval-summary {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 14px 16px;
  border: 1px solid #ead9c9;
  border-radius: 12px;
  background: #fffaf5;
}

.approval-summary > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
}

.approval-summary dt {
  color: #8b7768;
}

.approval-summary dd {
  overflow-wrap: anywhere;
  margin: 0;
  color: #4c3d34;
  font-weight: 600;
}

.approval-risk-note {
  margin: 0;
  color: #765d4d;
  line-height: 1.7;
}

@media (max-width: 1000px) {
  .conversation-workspace {
    height: auto;
  }

  .conversation-rail {
    max-height: 220px;
  }

  .conversation-stage {
    min-height: 680px;
  }

  .stage-header,
  .agent-selector {
    align-items: stretch;
  }

  .stage-header h1,
  .stage-header p {
    white-space: normal;
  }

  .agent-select {
    flex-basis: auto;
    width: 100%;
  }

  .message-content {
    max-width: calc(100% - 46px);
  }

  .new-conversation-setup > header,
  .new-conversation-setup > footer {
    align-items: stretch;
    flex-direction: column;
  }

  .policy-mode-options,
  .policy-summary-grid {
    grid-template-columns: 1fr;
  }

}
.attachment-file-input{display:none}.pending-attachments,.message-attachments{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:10px}.pending-attachment{position:relative;display:grid;width:112px;gap:5px;padding:7px;border:1px solid #d7e1dc;border-radius:10px;background:#fff}.pending-attachment img,.message-attachment img{width:96px;height:76px;border-radius:7px;object-fit:cover}.pending-attachment span,.message-attachment small{overflow:hidden;color:#60716a;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.pending-attachment button{position:absolute;top:-7px;right:-7px;width:22px;height:22px;padding:0;border:0;border-radius:50%;background:#5c6e67;color:#fff;cursor:pointer}.message-attachment{display:grid;max-width:180px;gap:5px;padding:7px;border:1px solid #d2ddd8;border-radius:10px;background:#fff;text-align:left;cursor:pointer}.message-attachment img{width:160px;height:120px}.message-attachment>span{display:grid;width:160px;height:120px;place-items:center;color:#89968f;font-size:12px;background:#f1f4f2}
</style>

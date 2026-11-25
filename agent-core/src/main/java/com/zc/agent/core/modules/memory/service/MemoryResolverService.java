package com.zc.agent.core.modules.memory.service;

import com.zc.agent.core.modules.conversation.model.ConversationMemoryRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskMemorySnapshotRecord;
import com.zc.agent.core.modules.conversation.model.UserMemoryRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Memory 读取服务。
 */
@Service
public class MemoryResolverService {

    private final ConversationRepository conversationRepository;
    private final MemoryGovernanceService memoryGovernanceService;

    public MemoryResolverService(ConversationRepository conversationRepository,
                                 MemoryGovernanceService memoryGovernanceService) {
        this.conversationRepository = conversationRepository;
        this.memoryGovernanceService = memoryGovernanceService;
    }

    /**
     * 查询会话级记忆。
     *
     * @param conversationId 会话 ID
     * @return 会话级记忆
     */
    public Optional<ConversationMemoryRecord> resolveConversationMemory(Long conversationId) {
        return conversationRepository.findConversationMemoryByConversationId(conversationId);
    }

    /**
     * 构建执行上下文中的会话记忆。
     *
     * @param conversationId 会话 ID
     * @return 会话记忆上下文
     */
    public ExecutionContext.ConversationMemoryContext resolveConversationMemoryContext(Long conversationId) {
        ConversationMemoryRecord conversationMemoryRecord = resolveConversationMemory(conversationId).orElse(null);
        if (conversationMemoryRecord == null) {
            return new ExecutionContext.ConversationMemoryContext(null, null, null, null, null);
        }
        return new ExecutionContext.ConversationMemoryContext(
                conversationMemoryRecord.memorySummary(),
                conversationMemoryRecord.preferenceSummary(),
                conversationMemoryRecord.lastUserMessage(),
                conversationMemoryRecord.lastAssistantSummary(),
                conversationMemoryRecord.metadata()
        );
    }

    /**
     * 查询用户级记忆。
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @return 用户级记忆
     */
    public Optional<UserMemoryRecord> resolveUserMemory(Long userId, Long agentId) {
        return conversationRepository.findUserMemoryByUserIdAndAgentId(userId, agentId);
    }

    /**
     * 构建执行上下文中的用户记忆。
     *
     * @param userId  用户 ID
     * @param agentId Agent ID
     * @return 用户记忆上下文
     */
    public ExecutionContext.UserMemoryContext resolveUserMemoryContext(Long userId, Long agentId) {
        UserMemoryRecord userMemoryRecord = resolveUserMemory(userId, agentId).orElse(null);
        var governed = memoryGovernanceService.findActive(userId, agentId);
        boolean governanceEnabled = memoryGovernanceService.hasHistory(userId, agentId);
        if (userMemoryRecord == null && governed.isEmpty()) {
            return new ExecutionContext.UserMemoryContext(null, null, null, null);
        }
        return new ExecutionContext.UserMemoryContext(
                governedValue(governed, "preference", governanceEnabled || userMemoryRecord == null ? null : userMemoryRecord.preferenceSummary()),
                governedValue(governed, "attention", governanceEnabled || userMemoryRecord == null ? null : userMemoryRecord.attentionSummary()),
                governedValue(governed, "fact", governanceEnabled || userMemoryRecord == null ? null : userMemoryRecord.factSummary()),
                governedValue(governed, "goal", governanceEnabled || userMemoryRecord == null ? null : userMemoryRecord.goalSummary())
        );
    }

    private String governedValue(java.util.List<MemoryGovernanceService.MemoryItem> items,
                                 String type,
                                 String fallback) {
        return items.stream().filter(item -> type.equals(item.memoryType()))
                .map(MemoryGovernanceService.MemoryItem::content).findFirst().orElse(fallback);
    }

    public ExecutionContext.UserMemoryContext resolveUserMemoryContext(Long userId, Long agentId, String userMemoryMode) {
        return ConversationMemoryPolicyResolver.applyMode(resolveUserMemoryContext(userId, agentId), userMemoryMode);
    }

    /**
     * 查询执行级记忆。
     *
     * @param taskId 任务 ID
     * @return 执行级记忆
     */
    public Optional<TaskMemorySnapshotRecord> resolveTaskMemorySnapshot(Long taskId) {
        return conversationRepository.findTaskMemorySnapshotByTaskId(taskId);
    }

    /**
     * 构建执行上下文中的最近一次任务记忆。
     *
     * @param conversationId 会话 ID
     * @param currentTaskId  当前任务 ID
     * @return 最近一次任务记忆上下文
     */
    public ExecutionContext.RecentTaskMemoryContext resolveRecentTaskMemoryContext(Long conversationId, Long currentTaskId) {
        for (TaskRecord taskRecord : conversationRepository.findTasksByConversationId(conversationId)) {
            if (currentTaskId != null && currentTaskId.equals(taskRecord.taskId())) {
                continue;
            }
            TaskMemorySnapshotRecord taskMemorySnapshotRecord = resolveTaskMemorySnapshot(taskRecord.taskId()).orElse(null);
            if (taskMemorySnapshotRecord != null) {
                return new ExecutionContext.RecentTaskMemoryContext(
                        taskMemorySnapshotRecord.taskId(),
                        taskMemorySnapshotRecord.memorySummary(),
                        taskMemorySnapshotRecord.keyFindings(),
                        taskMemorySnapshotRecord.finalResponseSummary()
                );
            }
        }
        return new ExecutionContext.RecentTaskMemoryContext(null, null, null, null);
    }
}

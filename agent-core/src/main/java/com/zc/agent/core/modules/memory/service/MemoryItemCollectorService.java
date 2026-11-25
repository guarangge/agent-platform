package com.zc.agent.core.modules.memory.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

/**
 * Memory 候选项收集服务。
 * 负责从 ExecutionContext 中提取标准化的候选记忆项，
 * 供上层选择策略按需挑选。
 */
@Service
public class MemoryItemCollectorService {

    /**
     * 收集当前上下文可用的标准化记忆项。
     *
     * @param executionContext 运行时上下文
     * @return 记忆候选集合
     */
    public CollectedMemoryItems collect(ExecutionContext executionContext) {
        return new CollectedMemoryItems(
                executionContext.conversationMemoryText(),
                prefixedText("用户偏好：", executionContext.userMemoryContext() == null
                        ? null
                        : executionContext.userMemoryContext().preferenceSummary()),
                prefixedText("用户关注点：", executionContext.userMemoryContext() == null
                        ? null
                        : executionContext.userMemoryContext().attentionSummary()),
                prefixedText("用户长期事实：", executionContext.userMemoryContext() == null
                        ? null
                        : executionContext.userMemoryContext().factSummary()),
                prefixedText("用户长期目标：", executionContext.userMemoryContext() == null
                        ? null
                        : executionContext.userMemoryContext().goalSummary()),
                prefixedText("最近任务摘要：", executionContext.recentTaskMemoryContext() == null
                        ? null
                        : executionContext.recentTaskMemoryContext().memorySummary()),
                prefixedText("最近关键发现：", executionContext.recentTaskMemoryContext() == null
                        ? null
                        : executionContext.recentTaskMemoryContext().keyFindings())
        );
    }

    private String prefixedText(String prefix, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return prefix + value;
    }

    /**
     * 标准化记忆候选集合。
     *
     * @param conversationSummary 会话摘要
     * @param userPreference      用户偏好
     * @param userAttention       用户关注点
     * @param userFact            用户长期事实
     * @param userGoal            用户长期目标
     * @param recentTaskSummary   最近任务摘要
     * @param recentKeyFindings   最近关键发现
     */
    public record CollectedMemoryItems(
            String conversationSummary,
            String userPreference,
            String userAttention,
            String userFact,
            String userGoal,
            String recentTaskSummary,
            String recentKeyFindings
    ) {
    }
}

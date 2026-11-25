package com.zc.agent.core.modules.runtime.model;

/**
 * ExecutionContext 构建选项。
 *
 * @param skipConversationMemory 是否跳过会话记忆
 * @param skipUserMemory         是否跳过用户记忆
 * @param skipRecentTaskMemory   是否跳过最近任务记忆
 * @param skipConversationHistory 是否跳过最近对话窗口
 */
public record ExecutionContextBuildOptions(
        boolean skipConversationMemory,
        boolean skipUserMemory,
        boolean skipRecentTaskMemory,
        boolean skipConversationHistory
) {

    public static ExecutionContextBuildOptions defaults() {
        return new ExecutionContextBuildOptions(false, false, false, false);
    }

    public static ExecutionContextBuildOptions evalIsolation() {
        return new ExecutionContextBuildOptions(true, true, true, true);
    }
}

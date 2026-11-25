package com.zc.agent.core.modules.conversation.model;

import com.zc.agent.core.modules.runtime.model.ExecutionContextBuildOptions;

/**
 * 会话执行选项。
 *
 * @param contextBuildOptions 上下文构建选项
 * @param skipMemoryUpdate    是否跳过执行后 Memory 写回
 * @param traceEntry          Trace 入口标识
 */
public record ConversationExecutionOptions(
        ExecutionContextBuildOptions contextBuildOptions,
        boolean skipMemoryUpdate,
        String traceEntry
) {

    public static ConversationExecutionOptions defaults() {
        return new ConversationExecutionOptions(ExecutionContextBuildOptions.defaults(), false, "send_message");
    }

    public static ConversationExecutionOptions evalIsolation() {
        return new ConversationExecutionOptions(ExecutionContextBuildOptions.evalIsolation(), true, "eval_run");
    }
}

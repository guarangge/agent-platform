package com.zc.agent.core.modules.cost.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;

/** 当前执行线程的成本归属信息，供统一资源网关计量使用。 */
public final class UsageAttributionContext {
    private static final ThreadLocal<Attribution> CURRENT = new ThreadLocal<>();

    private UsageAttributionContext() { }

    /** 使用当前 ExecutionContext 刷新归属信息。 */
    public static void bind(ExecutionContext context) {
        if (context == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(new Attribution(
                context.currentUser() == null ? null : context.currentUser().userId(),
                context.agentContext() == null ? null : context.agentContext().agentId(),
                context.conversationContext() == null ? null : context.conversationContext().conversationId(),
                context.taskContext() == null ? null : context.taskContext().taskId()
        ));
    }

    public static Attribution current() { return CURRENT.get(); }

    public static void clear() { CURRENT.remove(); }

    /** 用量归属维度。 */
    public record Attribution(Long userId, Long agentId, Long conversationId, Long taskId) { }
}

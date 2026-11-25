package com.zc.agent.core.modules.runtime.model;

/**
 * Agent Loop 和会话主链共享的稳定执行聚合结果。
 *
 * @param assistantContent 最终助手内容或运行控制说明
 * @param waitingApproval 是否等待审批
 * @param executionPlan 实际执行计划
 * @param suspensionType 挂起类型；非挂起场景为空字符串
 */
public record RuntimeExecutionAggregate(
        String assistantContent,
        boolean waitingApproval,
        ExecutionPlan executionPlan,
        String suspensionType
) {

    private static final String STATUS_WAITING_APPROVAL = "waiting_approval";
    private static final String STATUS_WAITING_EVENT = "waiting_event";

    /** 根据 waitingApproval 生成兼容的默认挂起类型。 */
    public RuntimeExecutionAggregate(
            String assistantContent,
            boolean waitingApproval,
            ExecutionPlan executionPlan) {
        this(assistantContent, waitingApproval, executionPlan,
                waitingApproval ? STATUS_WAITING_APPROVAL : "");
    }

    /** 是否正在等待外部事件。 */
    public boolean waitingEvent() {
        return STATUS_WAITING_EVENT.equals(suspensionType);
    }

    /** 是否处于人工暂停状态。 */
    public boolean paused() {
        return "paused".equals(suspensionType);
    }
}

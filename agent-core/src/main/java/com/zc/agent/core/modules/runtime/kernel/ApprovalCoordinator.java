package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.model.ApprovalRecord;

/** 可替换的审批记录、状态事件和原 Run 上下文协调契约。 */
public interface ApprovalCoordinator {

    /** 返回稳定协调器编码。 */
    String coordinatorCode();

    /** 原子创建 Approval 记录并发布 approval.requested。 */
    Long request(ApprovalRequest command);

    /** 原子迁移 Approval 状态并发布 resolved / revoked 事件。 */
    void transition(ApprovalTransition command);

    /** 审批续跑前恢复原 Run 上下文，关闭 Scope 时恢复调用线程现场。 */
    Scope openRunScope(Long taskId);

    /**
     * Approval 创建命令。
     *
     * @param conversationId 会话 ID
     * @param taskId Task ID
     * @param taskStepId Task Step ID
     * @param nodeId 审批节点 ID
     * @param nodeName 审批节点名称
     * @param approvalSourceType 审批来源类型
     * @param metadataJson 审批恢复元数据 JSON
     * @param traceId Trace ID
     * @param operatorId 操作人 ID
     */
    record ApprovalRequest(
            Long conversationId,
            Long taskId,
            Long taskStepId,
            String nodeId,
            String nodeName,
            String approvalSourceType,
            String metadataJson,
            String traceId,
            Long operatorId
    ) {
    }

    /**
     * Approval 状态迁移命令。
     *
     * @param approval 审批记录
     * @param status 目标状态
     * @param approvalComment 审批说明
     * @param approvalSourceType 审批来源类型
     * @param traceId Trace ID
     * @param operatorId 操作人 ID
     */
    record ApprovalTransition(
            ApprovalRecord approval,
            String status,
            String approvalComment,
            String approvalSourceType,
            String traceId,
            Long operatorId
    ) {
    }

    /** 可自动关闭的原 Run 上下文边界。 */
    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

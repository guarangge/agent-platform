package com.zc.agent.core.modules.runtime.capability;

import java.util.regex.Pattern;

/**
 * 审批恢复期间向 Tool 调用链传递一次已批准 Capability 节点。
 */
public final class CapabilityApprovalContext {

    private static final Pattern TOOL_CALL_ID_PATTERN = Pattern.compile("TOOL_CALL_[A-F0-9]{20}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final ThreadLocal<ApprovalGrant> APPROVAL_GRANT = new ThreadLocal<>();

    private CapabilityApprovalContext() {
    }

    /** 打开审批授权作用域，关闭时恢复上一个作用域。 */
    public static Scope open(String approvalNodeId,
                             String approvalStatus,
                             String toolCallId,
                             String parameterHash) {
        ApprovalGrant previous = APPROVAL_GRANT.get();
        if ("approved".equals(approvalStatus) && approvalNodeId != null && !approvalNodeId.isBlank()) {
            APPROVAL_GRANT.set(new ApprovalGrant(approvalNodeId, toolCallId, parameterHash));
        } else {
            APPROVAL_GRANT.remove();
        }
        return () -> {
            if (previous == null) {
                APPROVAL_GRANT.remove();
            } else {
                APPROVAL_GRANT.set(previous);
            }
        };
    }

    /** 判断当前恢复链是否已批准同一稳定策略节点。 */
    public static boolean isApproved(String approvalNodeId) {
        ApprovalGrant grant = APPROVAL_GRANT.get();
        return grant != null && approvalNodeId != null && approvalNodeId.equals(grant.approvalNodeId());
    }

    /** 判断当前恢复链是否携带 Capability 审批授权。 */
    public static boolean hasApprovedCapability() {
        ApprovalGrant grant = APPROVAL_GRANT.get();
        return grant != null && grant.approvalNodeId().startsWith("capability:");
    }

    /** 仅在审批节点和参数哈希均一致时返回原 Tool Call ID。 */
    public static String approvedToolCallId(String approvalNodeId, String parameterHash) {
        ApprovalGrant grant = APPROVAL_GRANT.get();
        if (grant == null
                || !grant.approvalNodeId().equals(approvalNodeId)
                || grant.parameterHash() == null
                || !grant.parameterHash().equals(parameterHash)
                || !SHA256_PATTERN.matcher(grant.parameterHash()).matches()
                || grant.toolCallId() == null
                || !TOOL_CALL_ID_PATTERN.matcher(grant.toolCallId()).matches()) {
            return null;
        }
        return grant.toolCallId();
    }

    private record ApprovalGrant(String approvalNodeId, String toolCallId, String parameterHash) {
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}

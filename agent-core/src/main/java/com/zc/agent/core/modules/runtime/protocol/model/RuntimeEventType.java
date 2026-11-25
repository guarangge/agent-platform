package com.zc.agent.core.modules.runtime.protocol.model;

import java.util.Arrays;

/**
 * Runtime Event 首批标准类型，同时兼容历史 Run Stream 事件名。
 */
public enum RuntimeEventType {

    MESSAGE_RESET("message.reset", "reset"),
    MESSAGE_DELTA("message.delta", "delta"),
    RUN_PROGRESS("run.progress", "progress"),
    RUN_QUEUED("run.queued"),
    RUN_STARTED("run.started"),
    RUN_MODE_SELECTED("run.mode.selected"),
    RUN_LOOP_STOPPED("run.loop.stopped"),
    RUN_WAITING("run.waiting"),
    RUN_COMPLETED("run.completed"),
    RUN_FAILED("run.failed"),
    RUN_CANCELLED("run.cancelled"),
    STEP_STARTED("step.started"),
    STEP_COMPLETED("step.completed"),
    STEP_FAILED("step.failed"),
    STEP_WAITING("step.waiting"),
    TOOL_CALL_REQUESTED("tool.call.requested"),
    TOOL_CALL_STARTED("tool.call.started"),
    TOOL_CALL_COMPLETED("tool.call.completed"),
    TOOL_CALL_FAILED("tool.call.failed"),
    POLICY_DECIDED("policy.decided"),
    APPROVAL_REQUESTED("approval.requested"),
    APPROVAL_RESOLVED("approval.resolved"),
    APPROVAL_REVOKED("approval.revoked"),
    ARTIFACT_CREATED("artifact.created"),
    ARTIFACT_PUBLISHED("artifact.published"),
    INPUT_ATTACHMENT_RESOLVED("input.attachment.resolved"),
    INPUT_ATTACHMENT_ANALYZED("input.attachment.analyzed"),
    CHECKPOINT_CREATED("checkpoint.created"),
    CHECKPOINT_RESTORED("checkpoint.restored"),
    PLAN_CREATED("plan.created"),
    PLAN_REPLANNED("plan.replanned");

    private final String code;
    private final String legacyCode;

    RuntimeEventType(String code, String legacyCode) {
        this.code = code;
        this.legacyCode = legacyCode;
    }

    RuntimeEventType(String code) {
        this(code, null);
    }

    /** 返回统一协议事件编码。 */
    public String code() {
        return code;
    }

    /** 判断持久化编码是否属于当前类型，包括历史兼容编码。 */
    public boolean matches(String storedCode) {
        return code.equals(storedCode) || legacyCode != null && legacyCode.equals(storedCode);
    }

    /** 将持久化事件编码解析为统一协议类型。 */
    public static RuntimeEventType fromStoredCode(String storedCode) {
        return Arrays.stream(values())
                .filter(type -> type.matches(storedCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知 Runtime Event 类型: " + storedCode));
    }
}

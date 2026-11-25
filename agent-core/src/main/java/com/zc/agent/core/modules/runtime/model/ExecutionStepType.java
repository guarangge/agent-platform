package com.zc.agent.core.modules.runtime.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plan 步骤与 Workflow 节点共享的执行类型契约。
 */
public enum ExecutionStepType {
    TOOL("tool", true, true, false, true, "tool"),
    WORKFLOW("workflow", true, false, false, true, "workflow"),
    REPLY("reply", true, true, true, false, "system"),
    CONDITION("condition", true, true, false, false, "condition"),
    APPROVAL("approval", true, true, true, false, "approval"),
    HANDOFF("handoff", true, true, true, true, "handoff"),
    REFLECTION("reflection", true, true, false, true, "reflection"),
    CLARIFY("clarify", true, true, true, false, "system"),
    JUDGE("judge", true, true, false, true, "system"),
    WAIT("wait", true, true, true, false, "system"),
    MEMORY_WRITE("memory_write", true, true, false, true, "system"),
    PARALLEL("parallel", true, true, false, true, "tool"),
    JOIN("join", true, true, false, true, "tool"),
    START("start", false, true, false, false, "system"),
    END("end", false, true, true, false, "system");

    private final String code;
    private final boolean planExecutable;
    private final boolean workflowExecutable;
    private final boolean terminal;
    private final boolean factProducing;
    private final String traceType;

    ExecutionStepType(String code, boolean planExecutable, boolean workflowExecutable,
                      boolean terminal, boolean factProducing, String traceType) {
        this.code = code;
        this.planExecutable = planExecutable;
        this.workflowExecutable = workflowExecutable;
        this.terminal = terminal;
        this.factProducing = factProducing;
        this.traceType = traceType;
    }

    public String code() {
        return code;
    }

    public boolean terminal() {
        return terminal;
    }

    public boolean factProducing() {
        return factProducing;
    }

    public String traceType() {
        return traceType;
    }

    public static ExecutionStepType requirePlanType(String value) {
        ExecutionStepType type = fromCode(value);
        if (!type.planExecutable) {
            throw new IllegalArgumentException("Plan 不支持的步骤类型: " + value);
        }
        return type;
    }

    public static ExecutionStepType requireWorkflowType(String value) {
        ExecutionStepType type = fromCode(value);
        if (!type.workflowExecutable) {
            throw new IllegalArgumentException("Workflow 不支持的节点类型: " + value);
        }
        return type;
    }

    public static Set<String> planCodes() {
        return Arrays.stream(values()).filter(item -> item.planExecutable)
                .map(ExecutionStepType::code).collect(Collectors.toUnmodifiableSet());
    }

    public static String planCodesForPrompt() {
        return Arrays.stream(values()).filter(item -> item.planExecutable)
                .map(ExecutionStepType::code).collect(Collectors.joining("|"));
    }

    private static ExecutionStepType fromCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(item -> item.code.equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知执行步骤类型: " + value));
    }
}

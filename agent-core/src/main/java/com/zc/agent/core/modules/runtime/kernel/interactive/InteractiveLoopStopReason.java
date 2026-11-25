package com.zc.agent.core.modules.runtime.kernel.interactive;

/** Interactive Loop 稳定停止原因。 */
public enum InteractiveLoopStopReason {

    COMPLETED("completed"),
    MAX_ITERATIONS("max_iterations"),
    MAX_LLM_CALLS("max_llm_calls"),
    MAX_TOOL_CALLS("max_tool_calls"),
    MAX_TOKENS("max_tokens"),
    MAX_COST("max_cost"),
    DUPLICATE_TOOL_CALL("duplicate_tool_call"),
    CAPABILITY_POLICY("capability_policy"),
    APPROVAL_REQUIRED("approval_required"),
    CANCELLED("cancelled"),
    FAILED("failed");

    private final String code;

    InteractiveLoopStopReason(String code) {
        this.code = code;
    }

    /** 返回稳定协议编码。 */
    public String code() {
        return code;
    }
}

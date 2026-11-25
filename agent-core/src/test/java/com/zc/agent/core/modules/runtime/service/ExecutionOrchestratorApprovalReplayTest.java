package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionOrchestratorApprovalReplayTest {

    @Test
    void shouldOnlyApproveMatchingGuardrailAtResumeStep() {
        RuntimeExecutionRequest.ApprovalReplaySnapshot replay =
                new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                        2L, "guardrail:tool_1", "高风险审批", "批准", "approved", null, null);
        ExecutionPlan.ExecutionPlanStep step = new ExecutionPlan.ExecutionPlanStep(
                1, "tool_1", "tool", "写文件", "写入文件", List.of(),
                "MCP_FS_WRITE_FILE", null, Map.of(), "写入结果", "fail_fast", true, "draft", "");

        assertThat(replay.approvesPlanStep(step, 1)).isTrue();
        assertThat(replay.approvesPlanStep(step, 2)).isFalse();
        assertThat(new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                2L, "guardrail:other", "高风险审批", "批准", "approved", null, null)
                .approvesPlanStep(step, 1)).isFalse();
        assertThat(new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                2L, "guardrail:tool_1", "高风险审批", "批准", "pending", null, null)
                .approvesPlanStep(step, 1)).isFalse();
        assertThat(new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                3L, "capability:MCP_FS_WRITE_FILE:a1b2c3", "能力审批", "批准", "approved",
                "TOOL_CALL_1", "parameter-hash")
                .approvesPlanStep(step, 1)).isTrue();
    }
}

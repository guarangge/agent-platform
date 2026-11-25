package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryValidationServiceTest {

    private final RecoveryValidationService service = new RecoveryValidationService(new ObjectMapper());

    @Test
    void shouldUseStableStepIdentityInsteadOfDisplayName() {
        TaskStepRecord taskStep = taskStep("运行时展示名称", "tool_1", "MCP_FS_WRITE_FILE");
        ExecutionPlan.ExecutionPlanStep planStep = planStep("计划展示名称", "tool_1", "MCP_FS_WRITE_FILE");

        assertThatCode(() -> service.validateRecoverablePlan(taskStep, planStep)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectChangedStepId() {
        TaskStepRecord taskStep = taskStep("Write File", "tool_1", "MCP_FS_WRITE_FILE");
        ExecutionPlan.ExecutionPlanStep planStep = planStep("Write File", "tool_2", "MCP_FS_WRITE_FILE");

        assertThatThrownBy(() -> service.validateRecoverablePlan(taskStep, planStep))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("步骤标识不一致");
    }

    @Test
    void shouldRejectChangedTargetTool() {
        TaskStepRecord taskStep = taskStep("Write File", "tool_1", "MCP_FS_WRITE_FILE");
        ExecutionPlan.ExecutionPlanStep planStep = planStep("Write File", "tool_1", "SANDBOX_EXECUTE");

        assertThatThrownBy(() -> service.validateRecoverablePlan(taskStep, planStep))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tool 已变化");
    }

    @Test
    void shouldResolveReplannedStepByStableStepIdWhenTaskStepNoHasOffset() {
        TaskStepRecord taskStep = new TaskStepRecord(
                1L, 1L, 4, "tool", "重规划后的运行步骤", "waiting_approval", 0,
                "{\"currentStep\":{\"stepId\":\"tool_1\",\"targetToolCode\":\"SANDBOX_EXECUTE\"}}",
                "{}", null, null, null, null
        );
        ExecutionPlan executionPlan = new ExecutionPlan(
                "plan-1", "dynamic", "生成文件", "draft", List.of(), List.of(), List.of(),
                List.of(
                        planStep("重规划步骤", "tool_1", "SANDBOX_EXECUTE"),
                        new ExecutionPlan.ExecutionPlanStep(
                                2, "reply_2", "reply", "输出结果", "回复用户", List.of(), null, null,
                                Map.of(), "最终回复", "fail_fast", false, "draft", ""
                        )
                )
        );

        ExecutionPlan.ExecutionPlanStep resolved = service.resolveRecoverablePlanStep(taskStep, executionPlan);

        assertThat(resolved.stepId()).isEqualTo("tool_1");
        assertThat(resolved.stepNo()).isEqualTo(1);
    }

    private TaskStepRecord taskStep(String stepName, String stepId, String toolCode) {
        String inputSnapshot = "{\"currentStep\":{\"stepId\":\"" + stepId
                + "\",\"targetToolCode\":\"" + toolCode + "\"}}";
        return new TaskStepRecord(1L, 1L, 1, "tool", stepName, "waiting_approval", 0,
                inputSnapshot, "{}", null, null, null, null);
    }

    private ExecutionPlan.ExecutionPlanStep planStep(String stepName, String stepId, String toolCode) {
        return new ExecutionPlan.ExecutionPlanStep(
                1, stepId, "tool", stepName, "执行工具", List.of(), toolCode, null,
                Map.of(), "工具结果", "fail_fast", true, "draft", "");
    }
}

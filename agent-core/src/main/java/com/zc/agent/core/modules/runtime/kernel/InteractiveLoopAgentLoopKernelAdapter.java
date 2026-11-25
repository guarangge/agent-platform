package com.zc.agent.core.modules.runtime.kernel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopController;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopResult;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopStopReason;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * 将 Interactive Loop 接入 AgentLoopKernel、Task Step、Approval 和通用 Checkpoint 主链。
 */
@Service("interactiveLoopAgentLoopKernel")
public class InteractiveLoopAgentLoopKernelAdapter implements AgentLoopKernel {

    public static final String KERNEL_CODE = "interactive_loop_kernel_v1";
    private static final String WAITING_APPROVAL = "waiting_approval";

    private final InteractiveLoopController controller;
    private final RuntimeEventSink runtimeEventSink;
    private final ApprovalCoordinator approvalCoordinator;
    private final CheckpointStore checkpointStore;
    private final ObjectMapper objectMapper;

    public InteractiveLoopAgentLoopKernelAdapter(InteractiveLoopController controller,
                                                 RuntimeEventSink runtimeEventSink,
                                                 ApprovalCoordinator approvalCoordinator,
                                                 CheckpointStore checkpointStore,
                                                 ObjectMapper objectMapper) {
        this.controller = controller;
        this.runtimeEventSink = runtimeEventSink;
        this.approvalCoordinator = approvalCoordinator;
        this.checkpointStore = checkpointStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public String kernelCode() {
        return KERNEL_CODE;
    }

    /** 执行动态循环，并将审批挂起映射回现有会话状态机。 */
    @Override
    public AgentLoopExecutionResult execute(AgentLoopExecutionRequest kernelRequest) {
        RuntimeExecutionRequest request = kernelRequest.executionRequest();
        ExecutionPlan.ExecutionPlanStep anchor = resolveAnchorStep(request);
        Long taskStepId = runtimeEventSink.stepStarted(new RuntimeEventSink.StepStartedEvent(
                request.taskId(), request.stepNoOffset() + anchor.stepNo(), anchor.stepId(), anchor.stepNo(),
                request.executionPlan().steps().size(), anchor.stepType(), anchor.stepName(),
                request.inputSnapshot(), request.operatorId()));
        checkpointStore.save(new CheckpointSaveRequest(
                request, taskStepId, anchor.stepNo(), anchor.stepNo(), "step_started", request.recoveredStepResults()));
        try {
            InteractiveLoopResult result = request.approvalReplaySnapshot() == null
                    ? controller.execute(request.executionContext(), request.taskId(), request.traceId())
                    : controller.resumeAfterApproval(request.executionContext(), request.taskId(), request.traceId(),
                            request.approvalReplaySnapshot());
            if (result.stopReason() == InteractiveLoopStopReason.FAILED) {
                runtimeEventSink.stepTransitioned(new RuntimeEventSink.StepTransitionedEvent(
                        request.taskId(), taskStepId, anchor.stepId(), anchor.stepNo(),
                        request.executionPlan().steps().size(), anchor.stepType(), anchor.stepName(), "failed",
                        json(Map.of("executionMode", "interactive_loop", "stopReason", result.stopReason().code())),
                        result.assistantContent(), "interactive_loop", false, request.operatorId()));
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, anchor.stepNo(), anchor.stepNo(),
                        "step_failed", request.recoveredStepResults()));
                throw new BusinessException(result.assistantContent());
            }
            runtimeEventSink.stepTransitioned(new RuntimeEventSink.StepTransitionedEvent(
                    request.taskId(), taskStepId, anchor.stepId(), anchor.stepNo(),
                    request.executionPlan().steps().size(), anchor.stepType(), anchor.stepName(), "success",
                    json(Map.of("executionMode", "interactive_loop", "stopReason", result.stopReason().code(),
                            "assistantContent", result.assistantContent())),
                    null, null, false, request.operatorId()));
            checkpointStore.save(new CheckpointSaveRequest(
                    request, taskStepId, anchor.stepNo(), anchor.stepNo() + 1,
                    "step_succeeded", request.recoveredStepResults()));
            return new AgentLoopExecutionResult(kernelCode(), new RuntimeExecutionAggregate(
                    result.assistantContent(), false, request.executionPlan()));
        } catch (CancellationException cancellation) {
            runtimeEventSink.stepTransitioned(new RuntimeEventSink.StepTransitionedEvent(
                    request.taskId(), taskStepId, anchor.stepId(), anchor.stepNo(),
                    request.executionPlan().steps().size(), anchor.stepType(), anchor.stepName(), "failed",
                    json(Map.of("executionMode", "interactive_loop", "stopReason", "cancelled")),
                    cancellation.getMessage(), "cancelled", false, request.operatorId()));
            checkpointStore.save(new CheckpointSaveRequest(
                    request, taskStepId, anchor.stepNo(), anchor.stepNo(),
                    "step_failed", request.recoveredStepResults()));
            throw cancellation;
        } catch (ApprovalRequiredException approval) {
            String outputSnapshot = json(Map.of(
                    "assistantMessage", "当前任务等待审批",
                    "approvalNodeId", approval.approvalNodeId(),
                    "approvalNodeName", approval.approvalNodeName(),
                    "approvalStatus", "pending",
                    "approvalSourceType", approval.approvalSourceType(),
                    "policyMetadata", approval.approvalMetadata()
            ));
            runtimeEventSink.stepTransitioned(new RuntimeEventSink.StepTransitionedEvent(
                    request.taskId(), taskStepId, anchor.stepId(), anchor.stepNo(),
                    request.executionPlan().steps().size(), anchor.stepType(), anchor.stepName(), WAITING_APPROVAL,
                    outputSnapshot, null, null, false, request.operatorId()));
            approvalCoordinator.request(new ApprovalCoordinator.ApprovalRequest(
                    request.executionContext().conversationContext().conversationId(), request.taskId(), taskStepId,
                    approval.approvalNodeId(), approval.approvalNodeName(), approval.approvalSourceType(),
                    json(approval.approvalMetadata()), request.traceId(), request.operatorId()));
            checkpointStore.save(new CheckpointSaveRequest(
                    request, taskStepId, anchor.stepNo(), anchor.stepNo(), WAITING_APPROVAL,
                    request.recoveredStepResults()));
            return new AgentLoopExecutionResult(kernelCode(), new RuntimeExecutionAggregate(
                    "当前任务等待人工审批，审批通过后将继续执行。", true,
                    request.executionPlan(), WAITING_APPROVAL));
        }
    }

    private ExecutionPlan.ExecutionPlanStep resolveAnchorStep(RuntimeExecutionRequest request) {
        if (request.executionPlan() == null || request.executionPlan().steps() == null) {
            throw new IllegalArgumentException("Interactive Loop 缺少执行计划锚点");
        }
        return request.executionPlan().steps().stream()
                .filter(step -> step != null && Objects.equals(step.stepNo(), request.startStepNo()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Interactive Loop 不存在起始计划步骤"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Interactive Loop 运行快照序列化失败", exception);
        }
    }
}

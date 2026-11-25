package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.service.GuardrailPolicyHitService;
import com.zc.agent.core.modules.memory.service.MemoryInjectionService;
import com.zc.agent.core.modules.runtime.capability.CapabilityApprovalContext;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.kernel.ApprovalCoordinator;
import com.zc.agent.core.modules.runtime.kernel.CheckpointSaveRequest;
import com.zc.agent.core.modules.runtime.kernel.CheckpointStore;
import com.zc.agent.core.modules.runtime.kernel.ObservationPolicy;
import com.zc.agent.core.modules.runtime.kernel.ObservationPolicyRequest;
import com.zc.agent.core.modules.runtime.kernel.RuntimeStepExecutionRequest;
import com.zc.agent.core.modules.runtime.kernel.RuntimeStepExecutor;
import com.zc.agent.core.modules.runtime.kernel.RuntimeEventSink;
import com.zc.agent.core.modules.runtime.kernel.RuntimeResponseAssembler;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import com.zc.agent.core.modules.workflow.service.WorkflowExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行主流程编排服务。
 * 负责按计划依次执行步骤、落步骤状态、处理重试和汇总最终响应。
 */
@Service
public class ExecutionOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestratorService.class);
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_WAITING_APPROVAL = "waiting_approval";
    private static final String STATUS_WAITING_EVENT = "waiting_event";

    private final ConversationRepository conversationRepository;
    private final ConditionStepService conditionStepService;
    private final GuardrailPolicyService guardrailPolicyService;
    private final ExecutionInputPolicyService executionInputPolicyService;
    private final RuntimeStepExecutor runtimeStepExecutor;
    private final ObservationPolicy observationPolicy;
    private final StepOutputContractService stepOutputContractService;
    private final StepDataContractService stepDataContractService;
    private final TaskPlanControlService taskPlanControlService;
    private final ExecutionFailureClassifierService failureClassifierService;
    private final ResultTrustService resultTrustService;
    private final MemoryInjectionService memoryInjectionService;
    private final TaskTraceService taskTraceService;
    private final GuardrailPolicyHitService guardrailPolicyHitService;
    private final CheckpointStore checkpointStore;
    private final RuntimeEventSink runtimeEventSink;
    private final ApprovalCoordinator approvalCoordinator;
    private final RuntimeResponseAssembler runtimeResponseAssembler;
    private final ObjectMapper objectMapper;

    public ExecutionOrchestratorService(ConversationRepository conversationRepository,
                                        ConditionStepService conditionStepService,
                                        GuardrailPolicyService guardrailPolicyService,
                                        ExecutionInputPolicyService executionInputPolicyService,
                                        RuntimeStepExecutor runtimeStepExecutor,
                                        ObservationPolicy observationPolicy,
                                        StepOutputContractService stepOutputContractService,
                                        StepDataContractService stepDataContractService,
                                        TaskPlanControlService taskPlanControlService,
                                        ExecutionFailureClassifierService failureClassifierService,
                                        ResultTrustService resultTrustService,
                                        MemoryInjectionService memoryInjectionService,
                                        TaskTraceService taskTraceService,
                                        GuardrailPolicyHitService guardrailPolicyHitService,
                                        CheckpointStore checkpointStore,
                                        RuntimeEventSink runtimeEventSink,
                                        ApprovalCoordinator approvalCoordinator,
                                        RuntimeResponseAssembler runtimeResponseAssembler,
                                        ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.conditionStepService = conditionStepService;
        this.guardrailPolicyService = guardrailPolicyService;
        this.executionInputPolicyService = executionInputPolicyService;
        this.runtimeStepExecutor = runtimeStepExecutor;
        this.observationPolicy = observationPolicy;
        this.stepOutputContractService = stepOutputContractService;
        this.stepDataContractService = stepDataContractService;
        this.taskPlanControlService = taskPlanControlService;
        this.failureClassifierService = failureClassifierService;
        this.resultTrustService = resultTrustService;
        this.memoryInjectionService = memoryInjectionService;
        this.taskTraceService = taskTraceService;
        this.guardrailPolicyHitService = guardrailPolicyHitService;
        this.checkpointStore = checkpointStore;
        this.runtimeEventSink = runtimeEventSink;
        this.approvalCoordinator = approvalCoordinator;
        this.runtimeResponseAssembler = runtimeResponseAssembler;
        this.objectMapper = objectMapper;
    }

    /**
     * 按执行计划编排并执行步骤。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public RuntimeExecutionAggregate executePlan(RuntimeExecutionRequest request) {
        ArrayList<String> responseParts = new ArrayList<>();
        ArrayList<RuntimeStepResultContext> previousStepResults = new ArrayList<>(request.recoveredStepResults());
        for (ExecutionPlan.ExecutionPlanStep rawStep : request.executionPlan().steps()) {
            ExecutionPlan.ExecutionPlanStep step = stepDataContractService.resolveInputs(rawStep, previousStepResults);
            if (step.stepNo() < request.startStepNo()) {
                continue;
            }
            runtimeEventSink.progress(new RuntimeEventSink.ProgressEvent(
                    "step", "正在执行步骤 " + step.stepNo() + "/"
                    + request.executionPlan().steps().size() + "：" + step.stepName(), Map.of(
                    "stepNo", step.stepNo(),
                    "stepCount", request.executionPlan().steps().size(),
                    "stepType", step.stepType()
            )));
            var stepStartedAt = java.time.LocalDateTime.now();
            MemoryInjectionService.ResolvedMemoryInput resolvedMemoryInput = resolveStepMemoryInput(
                    request.executionContext(),
                    step,
                    previousStepResults
            );
            String stepExecutionInputText = buildStepExecutionInputText(
                    request.executionContext(),
                    step,
                    previousStepResults
            );
            String rawStepExecutionInputText = stepExecutionInputText;
            stepExecutionInputText = executionInputPolicyService.clampPlanStepExecutionInput(
                    request.executionContext(),
                    step,
                    stepExecutionInputText
            );
            Map<String, Object> stepInputObservation = executionInputPolicyService.buildPlanStepInputObservation(
                    request.executionContext(),
                    step,
                    rawStepExecutionInputText,
                    stepExecutionInputText
            );
            String stepInputSnapshot = buildStepInputSnapshot(
                    request.inputSnapshot(),
                    step,
                    previousStepResults,
                    resolvedMemoryInput,
                    stepExecutionInputText,
                    stepInputObservation
            );
            if (request.recoveryReplaySnapshot() != null && step.stepNo().equals(request.startStepNo())) {
                stepInputSnapshot = buildStepInputSnapshot(
                        request.inputSnapshot(),
                        step,
                        previousStepResults,
                        resolvedMemoryInput,
                        stepExecutionInputText,
                        stepInputObservation,
                        request.recoveryReplaySnapshot(),
                        request.approvalReplaySnapshot()
                );
            }
            Long taskStepId = runtimeEventSink.stepStarted(
                    new RuntimeEventSink.StepStartedEvent(
                            request.taskId(),
                            request.stepNoOffset() + step.stepNo(),
                            step.stepId(),
                            step.stepNo(),
                            request.executionPlan().steps().size(),
                            resolveStepType(step),
                            step.stepName(),
                            stepInputSnapshot,
                            request.operatorId()
                    ));
            checkpointStore.save(new CheckpointSaveRequest(
                    request, taskStepId, step.stepNo(), step.stepNo(), "step_started", previousStepResults));
            try {
                TaskPlanControlService.BoundaryDecision controlDecision = taskPlanControlService.beforeStep(request.taskId(), step.stepNo());
                RuntimeExecutionRequest.ApprovalReplaySnapshot approvalReplay = request.approvalReplaySnapshot();
                if (approvalReplay == null || !approvalReplay.approvesPlanStep(step, request.startStepNo())) {
                    guardrailPolicyService.validatePlanStep(
                            request.executionContext(),
                            step,
                            stepExecutionInputText
                    );
                }
                if (controlDecision.skip() || !conditionStepService.shouldExecute(step, previousStepResults)) {
                    String skippedOutputSnapshot = toJsonText(new ExecutionOutputSnapshot(
                            controlDecision.skip() ? "步骤被人工跳过" : "步骤因条件未命中已跳过",
                            controlDecision.skip() ? controlDecision.reason() : "条件未命中，跳过当前步骤"
                    ));
                    transitionStep(request, step, taskStepId, STATUS_SUCCESS,
                            skippedOutputSnapshot, null, null, true);
                    previousStepResults.add(new RuntimeStepResultContext(
                            step.stepNo(),
                            step.stepId(),
                            step.stepType(),
                            step.stepName(),
                            controlDecision.skip() ? controlDecision.reason() : "条件未命中，跳过当前步骤",
                            controlDecision.skip() ? "步骤被人工跳过" : "步骤因条件未命中已跳过",
                            skippedOutputSnapshot
                    ));
                    if (hasLaterStep(request.executionPlan(), step.stepNo())) {
                        checkpointStore.save(new CheckpointSaveRequest(
                                request, taskStepId, step.stepNo(), step.stepNo() + 1,
                                "step_succeeded", previousStepResults));
                    }
                    taskTraceService.recordSpan(
                            request.taskId(),
                            request.traceId(),
                            taskStepId,
                            "step_execution",
                            "步骤" + step.stepNo() + "「" + step.stepName() + "」",
                            STATUS_SUCCESS,
                            safeJson(stepInputSnapshot),
                            safeJson(skippedOutputSnapshot),
                            null,
                            stepStartedAt,
                            java.time.LocalDateTime.now(),
                            Map.of(
                                    "stepType", step.stepType(),
                                    "stepNo", step.stepNo(),
                                    "skipped", true,
                                    "manualControl", controlDecision.skip()
                            ),
                            request.operatorId()
                    );
                    continue;
                }
                RuntimeStepExecutionOutcome stepExecutionResult;
                RuntimeExecutionRequest.ApprovalReplaySnapshot capabilityApproval =
                        step.stepNo().equals(request.startStepNo()) ? request.approvalReplaySnapshot() : null;
                try (AgentRunStreamingContext.Scope ignored = AgentRunStreamingContext.openFinalResponseScope(
                        shouldStreamStep(request.executionPlan(), step));
                     CapabilityApprovalContext.Scope ignoredCapability = CapabilityApprovalContext.open(
                             capabilityApproval == null ? null : capabilityApproval.approvalNodeId(),
                             capabilityApproval == null ? null : capabilityApproval.approvalStatus(),
                             capabilityApproval == null ? null : capabilityApproval.toolCallId(),
                             capabilityApproval == null ? null : capabilityApproval.parameterHash())) {
                    stepExecutionResult = runtimeStepExecutor.execute(new RuntimeStepExecutionRequest(
                            new RuntimeStepExecutionCommand(
                                    request.executionContext(),
                                    request.executionDecision(),
                                    request.executionPlan(),
                                    step,
                                    previousStepResults,
                                    stepExecutionInputText,
                                    stepInputObservation,
                                    request.authorization(),
                                    taskStepId,
                                    request.operatorId()
                            ),
                            request.workflowResumeContext() != null && step.stepNo().equals(request.startStepNo())
                                    ? request.workflowResumeContext()
                                    : null
                    ));
                }
                stepDataContractService.validateOutput(step, stepExecutionResult.outputSnapshot());
                ResultTrustService.TrustAssessment trustAssessment = resultTrustService.evaluate(
                        request.executionContext(), step, stepExecutionResult.outputSnapshot());
                resultTrustService.enforce(trustAssessment, step);
                stepInputObservation = new LinkedHashMap<>(stepInputObservation);
                stepInputObservation.put("resultTrust", trustAssessment.toMetadata());
                transitionStep(request, step, taskStepId, STATUS_SUCCESS,
                        stepExecutionResult.outputSnapshot(), null, null, false);
                responseParts.add(formatStepResponse(request.executionPlan().steps().size(), step, stepExecutionResult.responseText()));
                previousStepResults.add(new RuntimeStepResultContext(
                        step.stepNo(),
                        step.stepId(),
                        step.stepType(),
                        step.stepName(),
                        stepExecutionResult.summary(),
                        stepExecutionResult.responseText(),
                        stepExecutionResult.outputSnapshot()
                ));
                taskTraceService.recordSpan(
                        request.taskId(),
                        request.traceId(),
                        taskStepId,
                        "step_execution",
                        "步骤" + step.stepNo() + "「" + step.stepName() + "」",
                        STATUS_SUCCESS,
                        safeJson(stepInputSnapshot),
                        safeJson(stepExecutionResult.outputSnapshot()),
                        null,
                        stepStartedAt,
                        java.time.LocalDateTime.now(),
                        buildStepTraceMetadata(request.executionContext(), step, stepInputObservation),
                        request.operatorId()
                );
                ObservationPolicy.Decision observationDecision = observationPolicy.evaluate(new ObservationPolicyRequest(
                        request.executionContext(),
                        request.executionPlan(),
                        step,
                        previousStepResults,
                        stepExecutionResult.outputSnapshot()
                ));
                taskTraceService.recordSpan(
                        request.taskId(),
                        request.traceId(),
                        taskStepId,
                        "step_decision",
                        "步骤" + step.stepNo() + "「" + step.stepName() + "」后续决策",
                        STATUS_SUCCESS,
                        Map.of(
                                "stepId", step.stepId(),
                                "stepType", step.stepType(),
                                "stepNo", step.stepNo()
                        ),
                        Map.of(
                                "action", observationDecision.action(),
                                "reason", observationDecision.reason()
                        ),
                        null,
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now(),
                        observationDecision.metadata(),
                        request.operatorId()
                );
                if ("finish".equals(observationDecision.action())) {
                    break;
                }
                if ("replan".equals(observationDecision.action())) {
                    throw new ObservationReplanRequiredException(
                            step,
                            taskStepId,
                            List.copyOf(previousStepResults),
                            observationDecision.reason()
                    );
                }
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, step.stepNo(), step.stepNo() + 1,
                        "step_succeeded", previousStepResults));
            } catch (ObservationReplanRequiredException observationReplanRequiredException) {
                throw observationReplanRequiredException;
            } catch (ExecutionSuspendedException suspendedException) {
                String outputSnapshot = stepOutputContractService.buildGenericSnapshot(
                        "wait", suspendedException.getMessage(), "任务等待外部事件", suspendedException.getMessage(),
                        1.0d, false, false, "等待信号后自动恢复", List.of(),
                        Map.of("eventKey", suspendedException.eventKey()), Map.of(), Map.of("durable", true));
                transitionStep(request, step, taskStepId, STATUS_WAITING_EVENT,
                        outputSnapshot, null, null, false);
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, step.stepNo(), step.stepNo(), STATUS_WAITING_EVENT, previousStepResults));
                return new RuntimeExecutionAggregate(suspendedException.getMessage(), false, request.executionPlan(), STATUS_WAITING_EVENT);
            } catch (PlanControlPauseException pauseException) {
                String outputSnapshot = stepOutputContractService.buildGenericSnapshot(
                        "human_control", pauseException.getMessage(), "任务已在安全步骤边界暂停", pauseException.getMessage(),
                        1.0d, false, false, "可从当前步骤恢复执行", List.of(),
                        Map.of("pausedStepNo", step.stepNo()), Map.of(), Map.of("manualControl", true));
                transitionStep(request, step, taskStepId, "paused", outputSnapshot, null, null, false);
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, step.stepNo(), step.stepNo(), "paused", previousStepResults));
                return new RuntimeExecutionAggregate(pauseException.getMessage(), false, request.executionPlan(), "paused");
            } catch (ApprovalRequiredException approvalRequiredException) {
                String approvalOutputSnapshot = toJsonText(new ApprovalPendingSnapshot(
                        "当前任务等待审批",
                        approvalRequiredException.approvalNodeId(),
                        approvalRequiredException.approvalNodeName(),
                        approvalRequiredException.nextNodeId(),
                        approvalRequiredException.executedNodes(),
                        approvalRequiredException.previousNodeResults(),
                        "pending",
                        approvalRequiredException.approvalSourceType(),
                        approvalRequiredException.nextPlanStepNo(),
                        approvalRequiredException.approvalMetadata()
                ));
                transitionStep(request, step, taskStepId, STATUS_WAITING_APPROVAL,
                        approvalOutputSnapshot, null, null, false);
                approvalCoordinator.request(new ApprovalCoordinator.ApprovalRequest(
                        request.executionContext().conversationContext().conversationId(),
                        request.taskId(),
                        taskStepId,
                        approvalRequiredException.approvalNodeId(),
                        approvalRequiredException.approvalNodeName(),
                        approvalRequiredException.approvalSourceType(),
                        toJsonText(approvalRequiredException.approvalMetadata()),
                        request.traceId(),
                        request.operatorId()
                ));
                guardrailPolicyHitService.recordHit(
                        request.executionContext().conversationContext().conversationId(),
                        request.taskId(),
                        taskStepId,
                        request.executionContext().agentContext().agentId(),
                        request.traceId(),
                        "approval",
                        approvalRequiredException.approvalMetadata(),
                        request.operatorId()
                );
                taskTraceService.recordSpan(
                        request.taskId(),
                        request.traceId(),
                        taskStepId,
                        "step_execution",
                        "步骤" + step.stepNo() + "「" + step.stepName() + "」",
                        STATUS_WAITING_APPROVAL,
                        safeJson(stepInputSnapshot),
                        safeJson(approvalOutputSnapshot),
                        null,
                        stepStartedAt,
                        java.time.LocalDateTime.now(),
                        buildApprovalTraceMetadata(step, approvalRequiredException),
                        request.operatorId()
                );
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, step.stepNo(), step.stepNo(),
                        "waiting_approval", previousStepResults));
                return new RuntimeExecutionAggregate(
                        "当前任务在审批节点「" + approvalRequiredException.approvalNodeName() + "」暂停，等待人工审批后继续执行。",
                        true,
                        request.executionPlan()
                );
            } catch (Exception exception) {
                ExecutionFailureClassifierService.FailureSemantics failureSemantics = failureClassifierService.classify(step, exception);
                log.error("步骤执行失败, taskId={}, stepNo={}, stepType={}, message={}",
                        request.taskId(), step.stepNo(), step.stepType(), exception.getMessage(), exception);
                String failureOutputSnapshot = toJsonText(new StepFailureSnapshot(
                                "步骤执行失败",
                                exception.getMessage(),
                                failureSemantics.category(),
                                resolveRetryCount(taskStepId),
                                buildRecoveryMetadata(step, exception),
                                failureSemantics.toMetadata()
                        ));
                transitionStep(request, step, taskStepId, STATUS_FAILED,
                        failureOutputSnapshot, exception.getMessage(), failureSemantics.category(), false);
                checkpointStore.save(new CheckpointSaveRequest(
                        request, taskStepId, step.stepNo(), step.stepNo(),
                        "step_failed", previousStepResults));
                taskTraceService.recordSpan(
                        request.taskId(),
                        request.traceId(),
                        taskStepId,
                        "step_execution",
                        "步骤" + step.stepNo() + "「" + step.stepName() + "」",
                        STATUS_FAILED,
                        safeJson(stepInputSnapshot),
                        Map.of(),
                        exception.getMessage(),
                        stepStartedAt,
                        java.time.LocalDateTime.now(),
                        Map.of(
                                "stepType", step.stepType(),
                                "stepNo", step.stepNo(),
                                "failureType", failureSemantics.category(),
                                "failureSemantics", failureSemantics.toMetadata(),
                                "policyMetadata", exception instanceof PolicyViolationException policyViolationException
                                        ? policyViolationException.policyMetadata()
                                        : Map.of()
                        ),
                        request.operatorId()
                );
                if (exception instanceof PolicyViolationException policyViolationException) {
                    guardrailPolicyHitService.recordHit(
                            request.executionContext().conversationContext().conversationId(),
                            request.taskId(),
                            taskStepId,
                            request.executionContext().agentContext().agentId(),
                            request.traceId(),
                            "block",
                            policyViolationException.policyMetadata(),
                            request.operatorId()
                    );
                }
                throw new PlanStepExecutionException(
                        step,
                        taskStepId,
                        List.copyOf(previousStepResults),
                        exception.getMessage(),
                        exception,
                        failureSemantics
                );
            }
        }
        String assistantContent;
        runtimeEventSink.progress(new RuntimeEventSink.ProgressEvent(
                "response", "正在校验并整理最终回复", Map.of(
                "stepCount", request.executionPlan().steps().size()
        )));
        java.time.LocalDateTime responseStartedAt = java.time.LocalDateTime.now();
        assistantContent = runtimeResponseAssembler.assemble(new RuntimeResponseAssembler.ResponseAssemblyRequest(
                request.executionContext(),
                request.executionDecision(),
                request.executionPlan(),
                responseParts,
                previousStepResults,
                request.executionContext().conversationContext().conversationId(),
                request.taskId()
        ));
        taskTraceService.recordSpan(
                request.taskId(), request.traceId(), null,
                "response_assembly", "整理最终回复", STATUS_SUCCESS,
                Map.of("responsePartCount", responseParts.size()),
                Map.of("contentLength", assistantContent.length()),
                null, responseStartedAt, java.time.LocalDateTime.now(),
                Map.of("scope", "response_assembly"), request.operatorId()
        );
        return new RuntimeExecutionAggregate(assistantContent, false, request.executionPlan());
    }

    private void transitionStep(RuntimeExecutionRequest request,
                                ExecutionPlan.ExecutionPlanStep step,
                                Long taskStepId,
                                String status,
                                String outputSnapshot,
                                String errorMessage,
                                String failureType,
                                boolean skipped) {
        runtimeEventSink.stepTransitioned(new RuntimeEventSink.StepTransitionedEvent(
                request.taskId(),
                taskStepId,
                step.stepId(),
                step.stepNo(),
                request.executionPlan().steps().size(),
                resolveStepType(step),
                step.stepName(),
                status,
                outputSnapshot,
                errorMessage,
                failureType,
                skipped,
                request.operatorId()
        ));
    }

    private boolean shouldStreamStep(ExecutionPlan plan, ExecutionPlan.ExecutionPlanStep step) {
        if (plan.steps().size() == 1) return true;
        if (hasLaterStep(plan, step.stepNo())) return false;
        try {
            ExecutionStepType type = ExecutionStepType.requirePlanType(step.stepType());
            return type == ExecutionStepType.REPLY
                    || type == ExecutionStepType.CLARIFY
                    || type == ExecutionStepType.WORKFLOW;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasLaterStep(ExecutionPlan plan, Integer currentStepNo) {
        return plan.steps().stream().anyMatch(step -> step.stepNo() > currentStepNo);
    }

    private Object buildRecoveryMetadata(ExecutionPlan.ExecutionPlanStep step, Exception exception) {
        if (exception instanceof PolicyViolationException policyViolationException) {
            return policyViolationException.policyMetadata();
        }
        if (exception instanceof ToolExecutionException toolExecutionException) {
            return buildToolRecoveryMetadata(toolExecutionException);
        }
        if (!"workflow".equals(step.stepType()) || !(exception instanceof WorkflowExecutionException workflowExecutionException)) {
            return null;
        }
        return new WorkflowFailureRecoveryMetadata(
                workflowExecutionException.failedNodeId(),
                workflowExecutionException.failedNodeType(),
                workflowExecutionException.executedNodes(),
                workflowExecutionException.previousNodeResults()
        );
    }

    private String classifyFailureType(ExecutionPlan.ExecutionPlanStep step, Exception exception) {
        if (exception instanceof PolicyViolationException) {
            return "guardrail_blocked";
        }
        if ("tool".equals(step.stepType())) {
            if (exception instanceof ToolExecutionException toolExecutionException) {
                return "tool_" + toolExecutionException.errorType();
            }
            if (exception.getMessage() != null && exception.getMessage().contains("当前不可用")) {
                return "tool_unavailable";
            }
            return "tool_execution_failed";
        }
        if ("workflow".equals(step.stepType())) {
            return "workflow_execution_failed";
        }
        if ("condition".equals(step.stepType())) {
            return "condition_execution_failed";
        }
        return "direct_reply_failed";
    }

    private Map<String, Object> resolveToolTraceMetadata(ExecutionContext executionContext, String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return Map.of();
        }
        return executionContext.availableTools().stream()
                .filter(item -> toolCode.equals(item.toolCode()))
                .findFirst()
                .map(tool -> Map.<String, Object>of(
                        "providerType", tool.providerType() == null ? "" : tool.providerType(),
                        "transportType", tool.transportType() == null ? "" : tool.transportType(),
                        "operationType", tool.operationType() == null ? "" : tool.operationType(),
                        "operationName", tool.operationName() == null ? "" : tool.operationName(),
                        "endpoint", tool.endpoint() == null ? "" : tool.endpoint()
                ))
                .orElse(Map.of());
    }

    private Integer resolveRetryCount(Long taskStepId) {
        return conversationRepository.findTaskStepById(taskStepId)
                .map(item -> item.retryCount())
                .orElse(0);
    }

    private String resolveStepType(ExecutionPlan.ExecutionPlanStep step) {
        return ExecutionStepType.requirePlanType(step.stepType()).code();
    }

    private String formatStepResponse(int totalStepCount,
                                      ExecutionPlan.ExecutionPlanStep step,
                                      String responseText) {
        if (totalStepCount == 1) {
            return responseText;
        }
        return "步骤" + step.stepNo() + "「" + step.stepName() + "」结果：\n" + responseText;
    }

    private Map<String, Object> buildStepTraceMetadata(ExecutionContext executionContext,
                                                       ExecutionPlan.ExecutionPlanStep step,
                                                       Map<String, Object> stepInputObservation) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stepType", step.stepType());
        metadata.put("stepNo", step.stepNo());
        putIfNotNull(metadata, "targetToolCode", step.targetToolCode());
        putIfNotNull(metadata, "targetWorkflowId", step.targetWorkflowId());
        putIfNotNull(metadata, "toolProvider", resolveToolTraceMetadata(executionContext, step.targetToolCode()));
        putIfNotNull(metadata, "toolInputObservation", stepInputObservation);
        return metadata;
    }

    private Map<String, Object> buildApprovalTraceMetadata(ExecutionPlan.ExecutionPlanStep step,
                                                           ApprovalRequiredException approvalRequiredException) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stepType", step.stepType());
        metadata.put("stepNo", step.stepNo());
        putIfNotNull(metadata, "approvalNodeId", approvalRequiredException.approvalNodeId());
        putIfNotNull(metadata, "approvalNodeName", approvalRequiredException.approvalNodeName());
        putIfNotNull(metadata, "approvalPolicy", approvalRequiredException.approvalMetadata());
        return metadata;
    }

    private Map<String, Object> buildToolRecoveryMetadata(ToolExecutionException toolExecutionException) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "errorCode", toolExecutionException.errorCode());
        putIfNotNull(metadata, "errorType", toolExecutionException.errorType());
        putIfNotNull(metadata, "retryable", toolExecutionException.retryable());
        putIfNotNull(metadata, "providerStatusCode", toolExecutionException.providerStatusCode());
        putIfNotNull(metadata, "metadata", toolExecutionException.metadata());
        return metadata;
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String buildStepInputSnapshot(String inputSnapshot,
                                          ExecutionPlan.ExecutionPlanStep step,
                                          List<RuntimeStepResultContext> previousStepResults,
                                          MemoryInjectionService.ResolvedMemoryInput resolvedMemoryInput,
                                          String executionInputText) {
        return buildStepInputSnapshot(inputSnapshot, step, previousStepResults, resolvedMemoryInput, executionInputText, null, null, null);
    }

    private String buildStepInputSnapshot(String inputSnapshot,
                                          ExecutionPlan.ExecutionPlanStep step,
                                          List<RuntimeStepResultContext> previousStepResults,
                                          MemoryInjectionService.ResolvedMemoryInput resolvedMemoryInput,
                                          String executionInputText,
                                          Map<String, Object> inputObservation) {
        return buildStepInputSnapshot(inputSnapshot, step, previousStepResults, resolvedMemoryInput, executionInputText, inputObservation, null, null);
    }

    private String buildStepInputSnapshot(String inputSnapshot,
                                          ExecutionPlan.ExecutionPlanStep step,
                                          List<RuntimeStepResultContext> previousStepResults,
                                          MemoryInjectionService.ResolvedMemoryInput resolvedMemoryInput) {
        return buildStepInputSnapshot(inputSnapshot, step, previousStepResults, resolvedMemoryInput, null, null, null, null);
    }

    private String buildStepInputSnapshot(String inputSnapshot,
                                          ExecutionPlan.ExecutionPlanStep step,
                                          List<RuntimeStepResultContext> previousStepResults,
                                          MemoryInjectionService.ResolvedMemoryInput resolvedMemoryInput,
                                          String executionInputText,
                                          Map<String, Object> inputObservation,
                                          RuntimeExecutionRequest.RecoveryReplaySnapshot recoveryReplaySnapshot,
                                          RuntimeExecutionRequest.ApprovalReplaySnapshot approvalReplaySnapshot) {
        return toJsonText(new StepInputSnapshot(
                safeJson(inputSnapshot),
                new StepPlanSnapshot(
                        step.stepNo(),
                        step.stepId(),
                        step.stepType(),
                        step.stepName(),
                        step.objective(),
                        step.dependsOnStepIds(),
                        step.targetToolCode(),
                        step.targetWorkflowId(),
                        step.inputs(),
                        step.expectedOutput(),
                        step.failurePolicy(),
                        step.description()
                ),
                previousStepResults,
                resolvedMemoryInput.resolvedInputText(),
                executionInputText,
                inputObservation,
                resolvedMemoryInput.memoryStrategy(),
                resolvedMemoryInput.selectedMemoryItems(),
                recoveryReplaySnapshot,
                approvalReplaySnapshot
        ));
    }

    private String buildStepExecutionInputText(ExecutionContext executionContext,
                                               ExecutionPlan.ExecutionPlanStep step,
                                               List<RuntimeStepResultContext> previousStepResults) {
        List<String> previousSummaries = previousStepResults.stream()
                .map(RuntimeStepResultContext::summary)
                .filter(item -> item != null && !item.isBlank())
                .toList();
        return executionInputPolicyService.buildPlanStepExecutionInput(executionContext, step, previousSummaries);
    }

    private MemoryInjectionService.ResolvedMemoryInput resolveStepMemoryInput(ExecutionContext executionContext,
                                                                              ExecutionPlan.ExecutionPlanStep step,
                                                                              List<RuntimeStepResultContext> previousStepResults) {
        List<String> previousResultLines = previousStepResults.stream()
                .map(item -> item.stepNo() + ". " + item.stepName() + " - " + item.summary())
                .toList();
        if ("tool".equals(step.stepType())) {
            return memoryInjectionService.resolveToolInput(executionContext, step.targetToolCode(), previousResultLines);
        }
        if ("workflow".equals(step.stepType())) {
            return memoryInjectionService.resolveWorkflowInput(executionContext, step.targetWorkflowId(), previousResultLines);
        }
        if ("condition".equals(step.stepType())) {
            return memoryInjectionService.resolveDirectReplyInput(executionContext);
        }
        return memoryInjectionService.resolveDirectReplyInput(executionContext);
    }

    private Object safeJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", jsonText);
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("执行快照序列化失败");
        }
    }

    private record ExecutionOutputSnapshot(
            String assistantMessage,
            String summary
    ) {
    }

    private record StepFailureSnapshot(
            String assistantMessage,
            String errorMessage,
            String failureType,
            Integer retryCount,
            Object recoveryMetadata,
            Map<String, Object> failureSemantics
    ) {
    }

    private record ApprovalPendingSnapshot(
            String assistantMessage,
            String approvalNodeId,
            String approvalNodeName,
            String nextNodeId,
            List<Map<String, Object>> executedNodes,
            List<Map<String, Object>> previousNodeResults,
            String approvalStatus,
            String approvalSourceType,
            Integer nextPlanStepNo,
            Map<String, Object> policyMetadata
    ) {
    }

    private record StepInputSnapshot(
            Object baseInputSnapshot,
            StepPlanSnapshot currentStep,
            List<RuntimeStepResultContext> previousStepResults,
            String resolvedInputText,
            String executionInputText,
            Map<String, Object> inputObservation,
            String memoryStrategy,
            List<String> selectedMemoryItems,
            RuntimeExecutionRequest.RecoveryReplaySnapshot recoveryReplay,
            RuntimeExecutionRequest.ApprovalReplaySnapshot approvalReplay
    ) {
    }

    private record WorkflowFailureRecoveryMetadata(
            String failedNodeId,
            String failedNodeType,
            List<Map<String, Object>> executedNodes,
            List<Map<String, Object>> previousNodeResults
    ) {
    }

    private record StepPlanSnapshot(
            Integer stepNo,
            String stepId,
            String stepType,
            String stepName,
            String objective,
            List<String> dependsOnStepIds,
            String targetToolCode,
            Long targetWorkflowId,
            Map<String, Object> inputs,
            String expectedOutput,
            String failurePolicy,
            String description
    ) {
    }
}

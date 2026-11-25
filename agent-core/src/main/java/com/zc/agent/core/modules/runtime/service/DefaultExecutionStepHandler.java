package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台内置标准步骤处理器。
 * 编排器只负责流程推进，本类统一承载 Plan 与 Workflow 可共享的节点执行语义。
 */
@Service
public class DefaultExecutionStepHandler implements ExecutionStepHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutionStepHandler.class);
    private static final int MAX_TOOL_RETRY_COUNT = 1;
    private static final Set<ExecutionStepType> SUPPORTED_TYPES = Set.of(
            ExecutionStepType.TOOL,
            ExecutionStepType.REPLY,
            ExecutionStepType.CONDITION,
            ExecutionStepType.HANDOFF,
            ExecutionStepType.REFLECTION,
            ExecutionStepType.CLARIFY,
            ExecutionStepType.JUDGE,
            ExecutionStepType.WAIT,
            ExecutionStepType.MEMORY_WRITE,
            ExecutionStepType.PARALLEL,
            ExecutionStepType.JOIN
    );

    private final ConversationRepository conversationRepository;
    private final ToolRouterService toolRouterService;
    private final ConditionStepService conditionStepService;
    private final AgentDelegationService agentDelegationService;
    private final AgenticPrimitiveStepService agenticPrimitiveStepService;
    private final ParallelJoinStepService parallelJoinStepService;
    private final StepOutputContractService stepOutputContractService;
    private final ExecutionResponseAssemblerService executionResponseAssemblerService;

    public DefaultExecutionStepHandler(ConversationRepository conversationRepository,
                                       ToolRouterService toolRouterService,
                                       ConditionStepService conditionStepService,
                                       AgentDelegationService agentDelegationService,
                                       AgenticPrimitiveStepService agenticPrimitiveStepService,
                                       ParallelJoinStepService parallelJoinStepService,
                                       StepOutputContractService stepOutputContractService,
                                       ExecutionResponseAssemblerService executionResponseAssemblerService) {
        this.conversationRepository = conversationRepository;
        this.toolRouterService = toolRouterService;
        this.conditionStepService = conditionStepService;
        this.agentDelegationService = agentDelegationService;
        this.agenticPrimitiveStepService = agenticPrimitiveStepService;
        this.parallelJoinStepService = parallelJoinStepService;
        this.stepOutputContractService = stepOutputContractService;
        this.executionResponseAssemblerService = executionResponseAssemblerService;
    }

    @Override
    public Set<ExecutionStepType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public RuntimeStepExecutionOutcome execute(RuntimeStepExecutionCommand request) {
        ExecutionStepType type = ExecutionStepType.requirePlanType(request.step().stepType());
        return switch (type) {
            case TOOL -> executeTool(request);
            case CONDITION -> fromCondition(conditionStepService.execute(
                    request.step(), request.executionContext(), request.inputText()));
            case HANDOFF -> executeHandoff(request);
            case CLARIFY -> fromPrimitive(agenticPrimitiveStepService.executeClarify(
                    request.executionContext(), request.step(), request.previousStepResults()));
            case JUDGE -> fromPrimitive(agenticPrimitiveStepService.executeJudge(
                    request.executionContext(), request.step(), request.previousStepResults()));
            case WAIT -> fromPrimitive(agenticPrimitiveStepService.executeWait(
                    request.executionContext(), request.step(), request.operatorId()));
            case MEMORY_WRITE -> fromPrimitive(agenticPrimitiveStepService.executeMemoryWrite(
                    request.executionContext(), request.step(), request.previousStepResults(), request.operatorId()));
            case PARALLEL -> fromPrimitive(parallelJoinStepService.executeParallel(
                    request.executionContext(), request.step(), request.inputText()));
            case JOIN -> fromPrimitive(parallelJoinStepService.executeJoin(
                    request.executionContext(), request.step(), request.previousStepResults()));
            case REFLECTION -> executeReflection(request);
            case REPLY -> executeReply(request);
            default -> throw new BusinessException("内置步骤处理器不支持类型: " + type.code());
        };
    }

    private RuntimeStepExecutionOutcome executeTool(RuntimeStepExecutionCommand request) {
        ToolExecutionResult result = executeToolWithRetry(request);
        String outputSnapshot = stepOutputContractService.buildToolSnapshot(
                request.step().stepId(),
                result,
                Map.of("toolInputObservation", request.inputObservation() == null ? Map.of() : request.inputObservation())
        );
        return new RuntimeStepExecutionOutcome(result.responseText(), outputSnapshot, result.summary());
    }

    private ToolExecutionResult executeToolWithRetry(RuntimeStepExecutionCommand request) {
        int retryCount = 0;
        while (true) {
            try {
                Map<String, Object> arguments = request.step().inputs() == null ? Map.of() : request.step().inputs();
                return toolRouterService.execute(
                                request.executionContext(),
                                request.step().targetToolCode(),
                                request.inputText(),
                                arguments
                        )
                        .orElseThrow(() -> new BusinessException("Tool 执行失败: " + request.step().targetToolCode()));
            } catch (Exception exception) {
                boolean retryable = !(exception instanceof ApprovalRequiredException)
                        && !(exception instanceof PolicyViolationException)
                        && (!(exception instanceof ToolExecutionException toolException) || toolException.retryable());
                if (!retryable || retryCount >= MAX_TOOL_RETRY_COUNT) {
                    log.error("Tool 执行最终失败, taskStepId={}, toolCode={}, retryCount={}, message={}",
                            request.taskStepId(), request.step().targetToolCode(), retryCount, exception.getMessage(), exception);
                    throw exception;
                }
                retryCount++;
                log.warn("Tool 执行失败后准备重试, taskStepId={}, toolCode={}, retryCount={}, maxRetryCount={}, message={}",
                        request.taskStepId(), request.step().targetToolCode(), retryCount, MAX_TOOL_RETRY_COUNT, exception.getMessage());
                if (request.taskStepId() != null) {
                    conversationRepository.incrementTaskStepRetryCount(request.taskStepId(), request.operatorId());
                }
            }
        }
    }

    private RuntimeStepExecutionOutcome executeHandoff(RuntimeStepExecutionCommand request) {
        AgentDelegationService.DelegationExecutionResult result = agentDelegationService.execute(
                request.executionContext(),
                request.step(),
                request.previousStepResults(),
                request.authorization(),
                request.executionContext().taskContext().taskId(),
                request.taskStepId(),
                request.operatorId()
        );
        String outputSnapshot = stepOutputContractService.buildGenericSnapshot(
                "handoff",
                result.responseText(),
                result.summary(),
                result.summary(),
                0.8d,
                false,
                false,
                "继续执行后续步骤",
                List.of(Map.of("sourceStepId", request.step().stepId(), "point", result.summary())),
                Map.of("delegateStepId", request.step().stepId()),
                result.outputSnapshot(),
                Map.of("delegateStepId", request.step().stepId())
        );
        return new RuntimeStepExecutionOutcome(result.responseText(), outputSnapshot, result.summary());
    }

    private RuntimeStepExecutionOutcome executeReflection(RuntimeStepExecutionCommand request) {
        requirePlanContext(request, "reflection");
        ExecutionResponseAssemblerService.StructuredStepResult result = executionResponseAssemblerService.buildReflectionStepResult(
                request.executionContext(), request.executionDecision(), request.executionPlan(), request.step(), request.previousStepResults());
        return new RuntimeStepExecutionOutcome(result.assistantMessage(), result.outputSnapshot(), result.summary());
    }

    private RuntimeStepExecutionOutcome executeReply(RuntimeStepExecutionCommand request) {
        requirePlanContext(request, "reply");
        ExecutionResponseAssemblerService.StructuredStepResult result = executionResponseAssemblerService.buildReplyStepResult(
                request.executionContext(), request.executionDecision(), request.executionPlan(), request.step(), request.previousStepResults());
        return new RuntimeStepExecutionOutcome(result.assistantMessage(), result.outputSnapshot(), result.summary());
    }

    private void requirePlanContext(RuntimeStepExecutionCommand request, String stepType) {
        if (request.executionDecision() == null || request.executionPlan() == null) {
            throw new BusinessException(stepType + " 步骤缺少 Plan 执行上下文");
        }
    }

    private RuntimeStepExecutionOutcome fromPrimitive(AgenticPrimitiveStepService.PrimitiveStepResult result) {
        return new RuntimeStepExecutionOutcome(result.responseText(), result.outputSnapshot(), result.summary());
    }

    private RuntimeStepExecutionOutcome fromCondition(ConditionStepService.ConditionExecutionResult result) {
        return new RuntimeStepExecutionOutcome(result.responseText(), result.outputSnapshot(), result.summary());
    }
}

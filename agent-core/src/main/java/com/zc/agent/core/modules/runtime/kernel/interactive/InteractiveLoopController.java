package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.zc.agent.core.modules.cost.service.BudgetExceededException;
import com.zc.agent.core.modules.runtime.capability.CapabilityApprovalContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.protocol.service.InteractiveLoopRuntimeEventService;
import com.zc.agent.core.modules.runtime.service.PolicyViolationException;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * 最小 Interactive Loop 控制器。
 * 仅编排结构化 Action、预算和现有 Tool Runtime，不复制 Tool、Capability 或审批实现。
 */
@Service
public class InteractiveLoopController {

    private static final int MAX_OBSERVATION_CHARS = 4_000;
    private final InteractiveLoopBudgetPolicyResolver budgetPolicyResolver;
    private final InteractiveLoopActionDecider actionDecider;
    private final InteractiveLoopParameterHasher parameterHasher;
    private final ToolRouterService toolRouterService;
    private final InteractiveLoopRuntimeEventService eventService;
    private final InteractiveLoopCheckpointStore checkpointStore;
    private final InteractiveLoopCancellationSignal cancellationSignal;

    public InteractiveLoopController(InteractiveLoopBudgetPolicyResolver budgetPolicyResolver,
                                     InteractiveLoopActionDecider actionDecider,
                                     InteractiveLoopParameterHasher parameterHasher,
                                     ToolRouterService toolRouterService,
                                     InteractiveLoopRuntimeEventService eventService,
                                     InteractiveLoopCheckpointStore checkpointStore,
                                     InteractiveLoopCancellationSignal cancellationSignal) {
        this.budgetPolicyResolver = budgetPolicyResolver;
        this.actionDecider = actionDecider;
        this.parameterHasher = parameterHasher;
        this.toolRouterService = toolRouterService;
        this.eventService = eventService;
        this.checkpointStore = checkpointStore;
        this.cancellationSignal = cancellationSignal;
    }

    /** 执行一次受限动态循环；生产路由开关在后续切片接入。 */
    public InteractiveLoopResult execute(ExecutionContext context, Long taskId, String traceId) {
        if (context == null) {
            throw new IllegalArgumentException("Interactive Loop ExecutionContext 不能为空");
        }
        InteractiveLoopBudgetState budget = new InteractiveLoopBudgetState(budgetPolicyResolver.resolve(context));
        ArrayList<InteractiveLoopObservation> observations = new ArrayList<>();
        return continueLoop(context, taskId, traceId, budget, observations);
    }

    /** 从审批 Checkpoint 重放待执行 Tool，再从下一轮继续，不重新调用此前已成功的 Tool。 */
    public InteractiveLoopResult resumeAfterApproval(ExecutionContext context,
                                                     Long taskId,
                                                     String traceId,
                                                     RuntimeExecutionRequest.ApprovalReplaySnapshot approval) {
        if (context == null || approval == null) {
            throw new IllegalArgumentException("Interactive Loop 审批恢复参数不能为空");
        }
        InteractiveLoopCheckpoint checkpoint = checkpointStore.latestWaitingApproval(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在待审批的 Interactive Loop Checkpoint"));
        validateApproval(checkpoint, traceId, approval);
        InteractiveLoopBudgetState budget = new InteractiveLoopBudgetState(checkpoint.budgetState());
        ArrayList<InteractiveLoopObservation> observations = new ArrayList<>(checkpoint.observations());
        if (checkpoint.pendingAction() == null
                || !isAllowedTool(context.availableTools(), normalize(checkpoint.pendingAction().toolCode()))) {
            throw new IllegalArgumentException("Interactive Loop Checkpoint 中的 Tool 已不在 Agent 授权范围");
        }
        try (CapabilityApprovalContext.Scope ignored = CapabilityApprovalContext.open(
                approval.approvalNodeId(), approval.approvalStatus(),
                approval.toolCallId(), approval.parameterHash())) {
            executePendingAction(context, checkpoint.pendingAction(), budget, observations);
            ensureNotCancelled(taskId, traceId, budget);
        } catch (ApprovalRequiredException exception) {
            budget.stop(InteractiveLoopStopReason.APPROVAL_REQUIRED);
            eventService.stopped(taskId, traceId, budget.snapshot());
            throw exception;
        } catch (PolicyViolationException exception) {
            budget.stop(InteractiveLoopStopReason.CAPABILITY_POLICY);
            return stopped(taskId, traceId, budget, observations,
                    "Tool 被 Capability Policy 阻断，Interactive Loop 已停止。");
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (cancellationSignal.isCancellationRequested()) {
                cancel(taskId, traceId, budget);
            }
            return failed(taskId, traceId, budget, observations);
        }
        checkpointStore.markRestored(checkpoint.id());
        return continueLoop(context, taskId, traceId, budget, observations);
    }

    private InteractiveLoopResult continueLoop(ExecutionContext context,
                                                Long taskId,
                                                String traceId,
                                                InteractiveLoopBudgetState budget,
                                                ArrayList<InteractiveLoopObservation> observations) {
        while (true) {
            ensureNotCancelled(taskId, traceId, budget);
            InteractiveLoopBudgetState.ConsumptionResult iteration = budget.consumeIteration();
            if (!iteration.allowed()) {
                return stopped(taskId, traceId, budget, observations,
                        stopMessage(iteration.stopReason()));
            }
            InteractiveLoopActionRequest request = new InteractiveLoopActionRequest(
                    context, iteration.snapshot().iterations(), List.copyOf(observations));
            InteractiveLoopActionDecider.ProjectedUsage projectedUsage;
            try {
                projectedUsage = actionDecider.estimate(request);
            } catch (RuntimeException exception) {
                return failed(taskId, traceId, budget, observations);
            }
            InteractiveLoopBudgetState.ConsumptionResult llm = budget.consumeLlmCall(
                    projectedUsage.tokens(), projectedUsage.costAmount());
            if (!llm.allowed()) {
                return stopped(taskId, traceId, budget, observations, stopMessage(llm.stopReason()));
            }
            InteractiveLoopAction action;
            try {
                action = actionDecider.decide(request);
            } catch (BudgetExceededException exception) {
                return failed(taskId, traceId, budget, observations);
            } catch (Exception exception) {
                boolean interrupted = causedByInterruption(exception);
                if (cancellationSignal.isCancellationRequested() || interrupted) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    cancel(taskId, traceId, budget);
                }
                return failed(taskId, traceId, budget, observations);
            }
            ensureNotCancelled(taskId, traceId, budget);
            if (action == null || action.type() == null) {
                return failed(taskId, traceId, budget, observations);
            }
            if (action.type() == InteractiveLoopAction.Type.FINAL_ANSWER) {
                if (action.content() == null || action.content().isBlank()) {
                    return failed(taskId, traceId, budget, observations);
                }
                budget.stop(InteractiveLoopStopReason.COMPLETED);
                return stopped(taskId, traceId, budget, observations, action.content().trim());
            }
            String toolCode = normalize(action.toolCode());
            if (!isAllowedTool(context.availableTools(), toolCode)) {
                budget.stop(InteractiveLoopStopReason.CAPABILITY_POLICY);
                return stopped(taskId, traceId, budget, observations,
                        "Interactive Loop 请求了未授权 Tool，执行已停止。");
            }
            Map<String, Object> arguments = action.arguments() == null ? Map.of() : action.arguments();
            InteractiveLoopBudgetState.ConsumptionResult toolBudget = budget.consumeToolCall(
                    toolCode, parameterHasher.hash(arguments));
            if (!toolBudget.allowed()) {
                return stopped(taskId, traceId, budget, observations, stopMessage(toolBudget.stopReason()));
            }
            try {
                executeTool(context, action, toolBudget.snapshot().iterations(), observations);
            } catch (ApprovalRequiredException exception) {
                checkpointStore.saveWaitingApproval(taskId, traceId, budget.persistentSnapshot(),
                        observations, action, exception);
                budget.stop(InteractiveLoopStopReason.APPROVAL_REQUIRED);
                eventService.stopped(taskId, traceId, budget.snapshot());
                throw exception;
            } catch (PolicyViolationException exception) {
                budget.stop(InteractiveLoopStopReason.CAPABILITY_POLICY);
                return stopped(taskId, traceId, budget, observations,
                        "Tool 被 Capability Policy 阻断，Interactive Loop 已停止。");
            } catch (ToolExecutionException exception) {
                observations.add(new InteractiveLoopObservation(
                        toolBudget.snapshot().iterations(), toolCode, "failed",
                        "Tool 调用失败", trim(exception.getMessage())));
            } catch (CancellationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (cancellationSignal.isCancellationRequested()) {
                    cancel(taskId, traceId, budget);
                }
                return failed(taskId, traceId, budget, observations);
            }
            ensureNotCancelled(taskId, traceId, budget);
        }
    }

    private void ensureNotCancelled(Long taskId, String traceId, InteractiveLoopBudgetState budget) {
        if (cancellationSignal.isCancellationRequested()) {
            cancel(taskId, traceId, budget);
        }
    }

    private void cancel(Long taskId, String traceId, InteractiveLoopBudgetState budget) {
        budget.stop(InteractiveLoopStopReason.CANCELLED);
        eventService.stopped(taskId, traceId, budget.snapshot());
        throw new CancellationException("Interactive Loop 运行已取消");
    }

    private boolean causedByInterruption(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void executePendingAction(ExecutionContext context,
                                      InteractiveLoopAction action,
                                      InteractiveLoopBudgetState budget,
                                      List<InteractiveLoopObservation> observations) {
        if (action == null || action.type() != InteractiveLoopAction.Type.TOOL_CALL) {
            throw new IllegalStateException("Interactive Loop Checkpoint 待执行 Action 非法");
        }
        executeTool(context, action, budget.snapshot().iterations(), observations);
    }

    private void executeTool(ExecutionContext context,
                             InteractiveLoopAction action,
                             int iteration,
                             List<InteractiveLoopObservation> observations) {
        String toolCode = normalize(action.toolCode());
        Map<String, Object> arguments = action.arguments() == null ? Map.of() : action.arguments();
        try {
            Optional<ToolExecutionResult> toolResult = toolRouterService.execute(
                    context, toolCode, context.userMessage(), arguments);
            observations.add(toObservation(iteration, toolCode, toolResult));
        } catch (ToolExecutionException exception) {
            observations.add(new InteractiveLoopObservation(
                    iteration, toolCode, "failed", "Tool 调用失败", trim(exception.getMessage())));
        }
    }

    private void validateApproval(InteractiveLoopCheckpoint checkpoint,
                                  String traceId,
                                  RuntimeExecutionRequest.ApprovalReplaySnapshot approval) {
        boolean matches = "approved".equals(approval.approvalStatus())
                && traceId != null && !traceId.isBlank()
                && checkpoint.toolCallId() != null && !checkpoint.toolCallId().isBlank()
                && checkpoint.parameterHash() != null && !checkpoint.parameterHash().isBlank()
                && java.util.Objects.equals(checkpoint.traceId(), traceId)
                && java.util.Objects.equals(checkpoint.approvalNodeId(), approval.approvalNodeId())
                && java.util.Objects.equals(checkpoint.toolCallId(), approval.toolCallId())
                && java.util.Objects.equals(checkpoint.parameterHash(), approval.parameterHash());
        if (!matches) {
            throw new IllegalArgumentException("审批结果与 Interactive Loop Checkpoint 绑定不一致");
        }
    }

    private InteractiveLoopObservation toObservation(int iteration,
                                                     String toolCode,
                                                     Optional<ToolExecutionResult> result) {
        if (result.isEmpty()) {
            return new InteractiveLoopObservation(iteration, toolCode, "unavailable",
                    "Tool 当前不可用", "");
        }
        ToolExecutionResult value = result.get();
        return new InteractiveLoopObservation(iteration, toolCode, "success",
                trim(value.summary()), trim(value.responseText()));
    }

    private boolean isAllowedTool(List<RegistryAgentTool> tools, String toolCode) {
        return tools != null && tools.stream()
                .anyMatch(tool -> tool != null && toolCode.equals(tool.toolCode()));
    }

    private InteractiveLoopResult failed(Long taskId,
                                         String traceId,
                                         InteractiveLoopBudgetState budget,
                                         List<InteractiveLoopObservation> observations) {
        budget.stop(InteractiveLoopStopReason.FAILED);
        return stopped(taskId, traceId, budget, observations, "Interactive Loop 执行失败。");
    }

    private InteractiveLoopResult stopped(Long taskId,
                                          String traceId,
                                          InteractiveLoopBudgetState budget,
                                          List<InteractiveLoopObservation> observations,
                                          String assistantContent) {
        InteractiveLoopBudgetState.Snapshot snapshot = budget.snapshot();
        eventService.stopped(taskId, traceId, snapshot);
        return new InteractiveLoopResult(assistantContent, snapshot.stopReason(), snapshot,
                List.copyOf(observations));
    }

    private String stopMessage(InteractiveLoopStopReason reason) {
        return "Interactive Loop 已按安全边界停止（" + reason.code() + "）。";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= MAX_OBSERVATION_CHARS
                ? normalized : normalized.substring(0, MAX_OBSERVATION_CHARS);
    }
}

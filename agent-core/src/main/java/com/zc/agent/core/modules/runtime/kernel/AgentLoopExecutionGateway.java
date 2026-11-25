package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionMode;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeExecutionModeEventService;
import com.zc.agent.core.modules.runtime.service.ExecutionOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 会话主链进入执行循环的唯一过渡网关。
 * Feature Flag 关闭时直接调用旧 Orchestrator，开启时通过 AgentLoopKernel Adapter 调用同一实现。
 */
@Service
public class AgentLoopExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopExecutionGateway.class);

    private final ExecutionOrchestratorService legacyOrchestrator;
    private final AgentLoopKernel workflowPlanKernel;
    private final AgentLoopKernel interactiveLoopKernel;
    private final ExecutionModeStrategy executionModeStrategy;
    private final RuntimeContextProvider runtimeContextProvider;
    private final RuntimeExecutionModePolicy runtimeExecutionModePolicy;
    private final RuntimeExecutionModeEventService runtimeExecutionModeEventService;

    public AgentLoopExecutionGateway(ExecutionOrchestratorService legacyOrchestrator,
                                     @Qualifier("legacyAgentLoopKernelAdapter") AgentLoopKernel workflowPlanKernel,
                                     @Qualifier("interactiveLoopAgentLoopKernel") AgentLoopKernel interactiveLoopKernel,
                                     ExecutionModeStrategy executionModeStrategy,
                                     RuntimeContextProvider runtimeContextProvider,
                                     RuntimeExecutionModePolicy runtimeExecutionModePolicy,
                                     RuntimeExecutionModeEventService runtimeExecutionModeEventService) {
        this.legacyOrchestrator = legacyOrchestrator;
        this.workflowPlanKernel = workflowPlanKernel;
        this.interactiveLoopKernel = interactiveLoopKernel;
        this.executionModeStrategy = executionModeStrategy;
        this.runtimeContextProvider = runtimeContextProvider;
        this.runtimeExecutionModePolicy = runtimeExecutionModePolicy;
        this.runtimeExecutionModeEventService = runtimeExecutionModeEventService;
    }

    /** 按稳定灰度决策执行，并保持旧返回契约不变。 */
    public RuntimeExecutionAggregate execute(
            RuntimeExecutionRequest request) {
        RuntimeContextProvider.ResolvedRuntimeContext runtimeContext = runtimeContextProvider.resolve(
                request.executionContext());
        AgentLoopExecutionRequest kernelRequest = new AgentLoopExecutionRequest(request, runtimeContext);
        RuntimeExecutionModePolicy.Selection runtimeMode = runtimeExecutionModePolicy.select(kernelRequest);
        runtimeExecutionModeEventService.selected(request.taskId(), request.traceId(), runtimeMode);
        ExecutionModeStrategy.Selection selection = executionModeStrategy.select(kernelRequest);
        log.info("AgentLoop 执行路由, taskId={}, projectId={}, agentId={}, runtimeMode={}, requestedMode={}, modeReason={}, kernelEnabled={}, reason={}, rolloutBucket={}",
                request.taskId(), runtimeContext.projectId(), runtimeContext.agentId(), runtimeMode.effectiveMode().code(),
                runtimeMode.requestedMode().code(), runtimeMode.reason(),
                selection.kernelEnabled(), selection.reason(),
                runtimeMode.rolloutBucket() == null ? selection.rolloutBucket() : runtimeMode.rolloutBucket());
        if (runtimeMode.effectiveMode() == RuntimeExecutionMode.INTERACTIVE_LOOP) {
            return interactiveLoopKernel.execute(kernelRequest).aggregate();
        }
        if (!selection.kernelEnabled()) {
            return legacyOrchestrator.executePlan(request);
        }
        return workflowPlanKernel.execute(kernelRequest).aggregate();
    }
}

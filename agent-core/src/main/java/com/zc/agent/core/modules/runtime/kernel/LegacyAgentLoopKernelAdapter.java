package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.service.ExecutionOrchestratorService;
import org.springframework.stereotype.Service;

/** 以 Adapter 方式复用现有 Orchestrator，不复制或改写业务执行逻辑。 */
@Service
public class LegacyAgentLoopKernelAdapter implements AgentLoopKernel {

    public static final String KERNEL_CODE = "legacy_orchestrator_adapter_v1";

    private final ExecutionOrchestratorService executionOrchestratorService;

    public LegacyAgentLoopKernelAdapter(ExecutionOrchestratorService executionOrchestratorService) {
        this.executionOrchestratorService = executionOrchestratorService;
    }

    @Override
    public String kernelCode() {
        return KERNEL_CODE;
    }

    @Override
    public AgentLoopExecutionResult execute(AgentLoopExecutionRequest request) {
        return new AgentLoopExecutionResult(
                kernelCode(),
                executionOrchestratorService.executePlan(request.executionRequest())
        );
    }
}

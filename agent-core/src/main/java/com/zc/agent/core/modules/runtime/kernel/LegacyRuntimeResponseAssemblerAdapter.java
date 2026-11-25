package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.runtime.service.ArtifactResponseConsistencyService;
import com.zc.agent.core.modules.runtime.service.ExecutionResponseAssemblerService;
import org.springframework.stereotype.Service;

/** 将现有响应汇总和 Artifact 一致性服务适配为 RuntimeResponseAssembler。 */
@Service
public class LegacyRuntimeResponseAssemblerAdapter implements RuntimeResponseAssembler {

    public static final String ASSEMBLER_CODE = "legacy_runtime_response_assembler_v1";

    private final ExecutionResponseAssemblerService executionResponseAssemblerService;
    private final ArtifactResponseConsistencyService artifactResponseConsistencyService;

    public LegacyRuntimeResponseAssemblerAdapter(
            ExecutionResponseAssemblerService executionResponseAssemblerService,
            ArtifactResponseConsistencyService artifactResponseConsistencyService) {
        this.executionResponseAssemblerService = executionResponseAssemblerService;
        this.artifactResponseConsistencyService = artifactResponseConsistencyService;
    }

    @Override
    public String assemblerCode() {
        return ASSEMBLER_CODE;
    }

    @Override
    public String assemble(ResponseAssemblyRequest request) {
        try (AgentRunStreamingContext.Scope ignored = AgentRunStreamingContext.openFinalResponseScope(true)) {
            String assistantContent = executionResponseAssemblerService.assemblePlanResponse(
                    request.executionContext(),
                    request.executionDecision(),
                    request.executionPlan(),
                    request.responseParts(),
                    request.stepResults()
            );
            return artifactResponseConsistencyService.reconcileAndAppend(
                    assistantContent,
                    request.conversationId(),
                    request.taskId(),
                    request.stepResults()
            );
        }
    }
}

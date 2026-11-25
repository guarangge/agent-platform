package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionDecisionServiceTest {

    @Test
    void shouldPreferSandboxForExplicitPdfGenerationRequest() {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ExecutionDecisionService service = new ExecutionDecisionService(
                new ObjectMapper(), gateway, null, null, null, new AgentExecutionPolicyResolver());
        ExecutionContext context = mock(ExecutionContext.class);
        RegistryAgentTool sandboxTool = new RegistryAgentTool(
                82L, "SANDBOX_EXECUTE", "Sandbox 隔离执行", "system", "general",
                null, null, "builtin", "local", null, null, null, null, null, null,
                "none", null, null, 60, 0, null, null, null, null, null,
                "high", 60, "enabled", "在 Sandbox 中执行命令并生成产物", null);
        when(context.userMessage()).thenReturn("我要你给我生成一个pdf文件");
        when(context.availableTools()).thenReturn(List.of(sandboxTool));

        ExecutionDecision decision = service.decide(context);

        assertThat(decision.decisionType()).isEqualTo("tool");
        assertThat(decision.selectedTools()).extracting(ExecutionDecision.SelectedTool::toolCode)
                .containsExactly("SANDBOX_EXECUTE");
        assertThat(service.buildDecisionTraceMetadata(context))
                .extractingByKey("contextCompaction")
                .isEqualTo(java.util.Map.of("skipped", true, "reason", "explicit_artifact_generation_tool"));
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldUseRuleMatchedWorkflowWithoutCallingLlmInRuleFirstMode() {
        RuntimeLlmGatewayService gateway = mock(RuntimeLlmGatewayService.class);
        ExecutionDecisionService service = new ExecutionDecisionService(
                new ObjectMapper(), gateway, null, null, null, new AgentExecutionPolicyResolver());
        ExecutionContext context = mock(ExecutionContext.class);
        ObjectNode agentMetadata = new ObjectMapper().createObjectNode();
        agentMetadata.putObject("decisionPolicy").put("mode", "rule_first");
        ObjectNode workflowMetadata = new ObjectMapper().createObjectNode();
        workflowMetadata.putObject("routing").put("triggerConditionsText", "旅游,行程");
        when(context.agentContext()).thenReturn(new ExecutionContext.AgentContext(
                1L, "A", "Agent", "MODEL", "enabled", agentMetadata));
        when(context.messageContext()).thenReturn(new ExecutionContext.MessageContext(1L, "帮我规划旅游行程"));
        when(context.userMessage()).thenReturn("帮我规划旅游行程");
        when(context.availableWorkflows()).thenReturn(List.of(new RegistryAgentWorkflow(
                9L, "TRAVEL", "旅行流程", "agentic", "general", "published", "", workflowMetadata)));
        when(context.availableTools()).thenReturn(List.of());

        ExecutionDecision decision = service.decide(context);

        assertThat(decision.decisionType()).isEqualTo("workflow");
        assertThat(decision.selectedWorkflows()).extracting(ExecutionDecision.SelectedWorkflow::workflowId)
                .containsExactly(9L);
        assertThat(service.buildDecisionTraceMetadata(context))
                .extractingByKey("contextCompaction")
                .isEqualTo(java.util.Map.of("skipped", true, "reason", "rule_first_fast_path"));
        verifyNoInteractions(gateway);
    }
}

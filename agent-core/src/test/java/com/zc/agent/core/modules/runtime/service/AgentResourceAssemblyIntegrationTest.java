package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zc.agent.core.config.ConversationContextProperties;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.ConversationRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.memory.service.ConversationStateService;
import com.zc.agent.core.modules.memory.service.MemoryResolverService;
import com.zc.agent.core.modules.knowledge.service.KnowledgeRuntimeContextService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContextBuildOptions;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.security.SecurityUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResourceAssemblyIntegrationTest {

    @Test
    void shouldAssembleAgentPromptsToolsWorkflowsAndModelIntoExecutionContext() {
        RegistryAgentClient registryClient = mock(RegistryAgentClient.class);
        JsonNode metadata = JsonNodeFactory.instance.objectNode().put("source", "registry");
        when(registryClient.getEnabledAgentSnapshot(10L, "Bearer test")).thenReturn(new AgentSnapshot(
                10L, "GENERAL", "通用 Agent", "MODEL_A", 101L, "执行 Prompt",
                102L, "决策 Prompt", 103L, "规划 Prompt", "enabled", metadata
        ));
        when(registryClient.getPromptSnapshot(eq(101L), any())).thenReturn(prompt(101L, "EXECUTION"));
        when(registryClient.getPromptSnapshot(eq(102L), any())).thenReturn(prompt(102L, "DECISION"));
        when(registryClient.getPromptSnapshot(eq(103L), any())).thenReturn(prompt(103L, "PLANNER"));
        when(registryClient.getModelSnapshot(eq("MODEL_A"), any())).thenReturn(model());
        RegistryAgentTool tool = tool();
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                301L, "WF_GENERAL", "通用流程", "orchestration", "general", "published", "test", metadata
        );
        when(registryClient.getEffectiveTools(eq(10L), any())).thenReturn(List.of(tool));
        when(registryClient.getAvailableWorkflows(eq(10L), any())).thenReturn(List.of(workflow));
        when(registryClient.getEnabledAgents(any())).thenReturn(List.of(
                new ExecutionContext.DelegateAgent(10L, "GENERAL", "通用 Agent", "self"),
                new ExecutionContext.DelegateAgent(11L, "REVIEWER", "审查 Agent", "review")
        ));
        when(registryClient.getEffectiveGuardrailPolicies(any())).thenReturn(List.of());
        when(registryClient.attachGuardrailPolicies(anyList(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registryClient.attachGuardrailPoliciesToWorkflows(anyList(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registryClient.attachGuardrailPoliciesToAgent(any(), anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeRuntimeContextService knowledgeRuntimeContextService = mock(KnowledgeRuntimeContextService.class);
        when(knowledgeRuntimeContextService.enrich(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutionContextBuilder builder = new ExecutionContextBuilder(
                mock(ConversationRepository.class), registryClient, mock(MemoryResolverService.class),
                mock(ConversationStateService.class), new ConversationContextProperties(),
                knowledgeRuntimeContextService
        );
        ExecutionContext context = builder.build(
                new SecurityUser(1L, "tester", "Tester", List.of("ADMIN"), List.of("chat")),
                conversation(), 2L, "执行通用任务", 1, 20L, "TASK_20", "TRACE_20", "Bearer test",
                ExecutionContextBuildOptions.evalIsolation()
        );

        assertThat(context.agentContext().metadata().path("source").asText()).isEqualTo("registry");
        assertThat(context.promptContext().promptContent()).isEqualTo("EXECUTION");
        assertThat(context.decisionPromptContext().promptContent()).isEqualTo("DECISION");
        assertThat(context.plannerPromptContext().promptContent()).isEqualTo("PLANNER");
        assertThat(context.modelContext().modelCode()).isEqualTo("MODEL_A");
        assertThat(context.availableTools()).extracting(RegistryAgentTool::toolCode).containsExactly("TOOL_GENERAL");
        assertThat(context.availableWorkflows()).extracting(RegistryAgentWorkflow::workflowCode).containsExactly("WF_GENERAL");
        assertThat(context.availableDelegates()).extracting(ExecutionContext.DelegateAgent::agentCode).containsExactly("REVIEWER");
    }

    private ExecutionContext.PromptContext prompt(Long id, String content) {
        return new ExecutionContext.PromptContext(id, "PROMPT_" + id, "Prompt", "execution", "general", content);
    }

    private ExecutionContext.ModelContext model() {
        return new ExecutionContext.ModelContext(
                "MODEL_A", "Model A", "openai_compatible", "chat", "http://localhost", "key", "/v1/chat/completions",
                "model-a", 0.1d, 1024, 8192, 1024, 256, 0.8d, "cl100k_base", 30, "enabled", null
        );
    }

    private RegistryAgentTool tool() {
        JsonNode empty = JsonNodeFactory.instance.objectNode();
        return new RegistryAgentTool(
                201L, "TOOL_GENERAL", "通用工具", "system", "general", empty, empty,
                "internal", "in_process", "INTERNAL", "Internal", "default", "Default", "default",
                null, "none", empty, empty, 30, 0, "run", "internal_method", empty, empty, empty,
                "default", 30, "enabled", "test", empty
        );
    }

    private ConversationRecord conversation() {
        LocalDateTime now = LocalDateTime.now();
        return new ConversationRecord(
                1L, "CONV_1", 1L, 10L, "GENERAL", "通用 Agent", "MODEL_A",
                101L, "执行 Prompt", 102L, "决策 Prompt", 103L, "规划 Prompt",
                "测试", "enabled", now, null, JsonNodeFactory.instance.objectNode(), now, now
        );
    }
}

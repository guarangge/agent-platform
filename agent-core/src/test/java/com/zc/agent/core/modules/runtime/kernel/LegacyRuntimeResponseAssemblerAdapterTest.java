package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.runtime.service.ArtifactResponseConsistencyService;
import com.zc.agent.core.modules.runtime.service.ExecutionResponseAssemblerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyRuntimeResponseAssemblerAdapterTest {

    private ExecutionResponseAssemblerService executionResponseAssemblerService;
    private ArtifactResponseConsistencyService artifactResponseConsistencyService;
    private LegacyRuntimeResponseAssemblerAdapter adapter;

    @BeforeEach
    void setUp() {
        executionResponseAssemblerService = mock(ExecutionResponseAssemblerService.class);
        artifactResponseConsistencyService = mock(ArtifactResponseConsistencyService.class);
        adapter = new LegacyRuntimeResponseAssemblerAdapter(
                executionResponseAssemblerService,
                artifactResponseConsistencyService
        );
        AgentRunStreamingContext.bind("RUN_1");
    }

    @AfterEach
    void tearDown() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldAssembleAndReconcileFinalResponseInsideStreamingScope() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = new ExecutionDecision("tool", "执行 Tool", List.of(), List.of());
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_1", "tool", "生成文件", "draft", List.of(), List.of(), List.of(), List.of());
        List<String> responseParts = new ArrayList<>(List.of("文件已生成"));
        List<RuntimeStepResultContext> stepResults = new ArrayList<>(List.of(
                new RuntimeStepResultContext(
                        1, "tool_1", "tool", "生成文件", "执行成功", "文件已生成", "{}")
        ));
        RuntimeResponseAssembler.ResponseAssemblyRequest request =
                new RuntimeResponseAssembler.ResponseAssemblyRequest(
                        context, decision, plan, responseParts, stepResults, 1L, 10L);
        when(executionResponseAssemblerService.assemblePlanResponse(
                context, decision, plan, responseParts, stepResults)).thenAnswer(invocation -> {
                    assertThat(AgentRunStreamingContext.isFinalResponseScope()).isTrue();
                    return "原始最终回复";
                });
        when(artifactResponseConsistencyService.reconcileAndAppend(
                "原始最终回复", 1L, 10L, stepResults)).thenAnswer(invocation -> {
                    assertThat(AgentRunStreamingContext.isFinalResponseScope()).isTrue();
                    return "一致性最终回复";
                });

        String response = adapter.assemble(request);

        assertThat(adapter.assemblerCode()).isEqualTo(LegacyRuntimeResponseAssemblerAdapter.ASSEMBLER_CODE);
        assertThat(response).isEqualTo("一致性最终回复");
        assertThat(AgentRunStreamingContext.isFinalResponseScope()).isFalse();
        verify(executionResponseAssemblerService).assemblePlanResponse(
                same(context), same(decision), same(plan), same(responseParts), same(stepResults));
        verify(artifactResponseConsistencyService).reconcileAndAppend(
                eq("原始最终回复"), eq(1L), eq(10L), same(stepResults));
    }

    @Test
    void shouldPropagateAssemblyFailureAndRestoreStreamingScope() {
        ExecutionContext context = mock(ExecutionContext.class);
        ExecutionDecision decision = new ExecutionDecision("direct_reply", "直接回复", List.of(), List.of());
        ExecutionPlan plan = new ExecutionPlan(
                "PLAN_2", "direct_reply", "回答问题", "draft", List.of(), List.of(), List.of(), List.of());
        List<String> responseParts = List.of();
        List<RuntimeStepResultContext> stepResults = List.of();
        RuntimeResponseAssembler.ResponseAssemblyRequest request =
                new RuntimeResponseAssembler.ResponseAssemblyRequest(
                        context, decision, plan, responseParts, stepResults, 1L, 10L);
        IllegalStateException failure = new IllegalStateException("response model unavailable");
        when(executionResponseAssemblerService.assemblePlanResponse(
                context, decision, plan, responseParts, stepResults)).thenThrow(failure);

        assertThatThrownBy(() -> adapter.assemble(request)).isSameAs(failure);
        assertThat(AgentRunStreamingContext.isFinalResponseScope()).isFalse();
        verifyNoInteractions(artifactResponseConsistencyService);
    }
}

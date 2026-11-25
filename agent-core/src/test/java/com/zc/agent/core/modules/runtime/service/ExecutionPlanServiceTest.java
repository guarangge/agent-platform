package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionPlanServiceTest {

    @Test
    void shouldOnlyExecuteHighestPriorityToolWhenFallbackReceivesCandidates() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("写入验收文件");
        ExecutionDecision decision = new ExecutionDecision(
                "tool", "Planner 超时后安全降级",
                List.of(
                        new ExecutionDecision.SelectedTool("MCP_FS_WRITE_FILE", "Write File"),
                        new ExecutionDecision.SelectedTool("TIME_INFO", "时间信息"),
                        new ExecutionDecision.SelectedTool("SANDBOX_EXECUTE", "Sandbox")
                ),
                List.of()
        );

        ExecutionPlan plan = newService().buildPlan(context, decision);

        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).targetToolCode()).isEqualTo("MCP_FS_WRITE_FILE");
        assertThat(plan.steps().get(1).stepType()).isEqualTo("reply");
    }

    @Test
    void shouldNotAppendReplyWhenFallbackWorkflowProducesUserReply() throws Exception {
        ExecutionContext context = mock(ExecutionContext.class);
        RegistryAgentWorkflow workflow = new RegistryAgentWorkflow(
                48L, "general_reply", "通用回复", "chat", "general", "enabled", "生成回复",
                new ObjectMapper().readTree("{\"routing\":{\"outputMode\":\"user_reply\"}}"));
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of(workflow));
        when(context.userMessage()).thenReturn("测试问题");
        ExecutionDecision decision = new ExecutionDecision(
                "workflow", "执行通用回复", List.of(),
                List.of(new ExecutionDecision.SelectedWorkflow(48L, "通用回复")));

        ExecutionPlan plan = newService().buildPlan(context, decision);

        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).stepType()).isEqualTo("workflow");
        assertThat(plan.steps().get(0).failurePolicy()).isEqualTo("replan");
        assertThat(plan.steps().get(0).replannable()).isTrue();
    }

    @Test
    void directReplyShouldAllowObservationReplan() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("人工智能专业比较好的大学有哪些");
        ExecutionDecision decision = new ExecutionDecision(
                "direct_reply", "当前问题可直接回答", List.of(), List.of());

        ExecutionPlan plan = newService().buildPlan(context, decision);

        assertThat(plan.steps()).singleElement().satisfies(step -> {
            assertThat(step.stepType()).isEqualTo("reply");
            assertThat(step.failurePolicy()).isEqualTo("replan");
            assertThat(step.replannable()).isTrue();
        });
    }

    @Test
    void appendedReplyShouldAllowObservationReplan() {
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("查询时间并给出结论");
        ExecutionDecision decision = new ExecutionDecision(
                "tool", "需要调用时间工具",
                List.of(new ExecutionDecision.SelectedTool("TIME_INFO", "时间信息")),
                List.of());

        ExecutionPlan plan = newService().buildPlan(context, decision);

        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(1).stepType()).isEqualTo("reply");
        assertThat(plan.steps().get(1).failurePolicy()).isEqualTo("replan");
        assertThat(plan.steps().get(1).replannable()).isTrue();
    }

    @Test
    void pdfSandboxFallbackShouldProvideExecutableInputs() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("请生成一个 PDF 文件");
        when(context.recentConversationMessages()).thenReturn(List.of(
                new ExecutionContext.ConversationMessageContext(1L, 1, "assistant", "AI 自学路线正文")
        ));
        ExecutionDecision decision = new ExecutionDecision(
                "tool", "PDF 文件生成请求",
                List.of(new ExecutionDecision.SelectedTool("SANDBOX_EXECUTE", "Sandbox 隔离执行")),
                List.of());

        ExecutionPlan plan = new ExecutionPlanService(conversationRepository).buildPlan(context, decision);

        assertThat(plan.steps()).hasSize(2);
        Map<String, Object> inputs = plan.steps().get(0).inputs();
        assertThat(inputs).containsKeys("profileCode", "command", "inputFiles", "artifactPaths");
        assertThat((List<String>) inputs.get("command")).startsWith("python3", "-c");
        assertThat(((List<String>) inputs.get("command")).get(2))
                .contains("reportlab", "wqy-zenhei.ttc");
        assertThat(inputs.get("artifactPaths")).isEqualTo(List.of("Agent生成文档.pdf"));
    }

    @Test
    void pdfSandboxFallbackShouldUseReferencedPlanInsteadOfOperationalMessages() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ExecutionContext context = mock(ExecutionContext.class);
        when(context.availableTools()).thenReturn(List.of());
        when(context.availableWorkflows()).thenReturn(List.of());
        when(context.userMessage()).thenReturn("帮上面的计划给我生成一个pdf文件");
        when(context.conversationContext()).thenReturn(new ExecutionContext.ConversationContext(
                52L, "CONV_TEST", "AI 学习", "processing", null));
        when(context.messageContext()).thenReturn(new ExecutionContext.MessageContext(232L, "帮上面的计划给我生成一个pdf文件"));
        when(conversationRepository.findMessagesByConversationId(52L)).thenReturn(List.of(
                message(216L, 13, "assistant", "## AI 自学路线计划\n\nP1 数理基础\nP2 机器学习\nP3 深度学习\nP4 LLM 与 Agent"),
                message(218L, 15, "assistant", "我已结合你当前会话里的已有信息继续处理。当前这一步的目标是完成当前用户目标。"),
                message(229L, 26, "assistant", "当前任务在审批节点「高风险Tool人工审批」暂停，等待人工审批后继续执行。"),
                message(230L, 27, "assistant", "PDF 文件已生成，当前未返回对外可访问的下载 URL。"),
                message(232L, 29, "user", "帮上面的计划给我生成一个pdf文件")
        ));
        ExecutionDecision decision = new ExecutionDecision(
                "tool", "PDF 文件生成请求",
                List.of(new ExecutionDecision.SelectedTool("SANDBOX_EXECUTE", "Sandbox 隔离执行")),
                List.of());

        ExecutionPlan plan = new ExecutionPlanService(conversationRepository).buildPlan(context, decision);

        Map<String, Object> inputs = plan.steps().get(0).inputs();
        List<Map<String, String>> inputFiles = (List<Map<String, String>>) inputs.get("inputFiles");
        String sourceContent = new String(
                Base64.getDecoder().decode(inputFiles.get(0).get("contentBase64")), StandardCharsets.UTF_8);
        assertThat(sourceContent).contains("AI 自学路线计划", "P4 LLM 与 Agent");
        assertThat(sourceContent).doesNotContain("高风险Tool人工审批", "PDF 文件已生成");
        assertThat(inputs.get("artifactPaths")).isEqualTo(List.of("AI自学路线计划.pdf"));
        assertThat(((List<String>) inputs.get("command")).get(3)).isEqualTo("AI自学路线计划.pdf");
    }

    private ExecutionPlanService newService() {
        return new ExecutionPlanService(mock(ConversationRepository.class));
    }

    private MessageRecord message(Long id, int sequenceNo, String messageType, String content) {
        return new MessageRecord(id, 52L, messageType, sequenceNo, content, "success", null, LocalDateTime.now());
    }
}

package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Agent 原语步骤服务。
 */
@Service
public class AgenticPrimitiveStepService {

    private static final Logger log = LoggerFactory.getLogger(AgenticPrimitiveStepService.class);
    private static final long MAX_WAIT_MILLIS = 365L * 24 * 60 * 60 * 1000;

    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final DurableWaitService durableWaitService;

    public AgenticPrimitiveStepService(RuntimeLlmGatewayService runtimeLlmGatewayService,
                                       ConversationRepository conversationRepository,
                                       ObjectMapper objectMapper,
                                       DurableWaitService durableWaitService) {
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.conversationRepository = conversationRepository;
        this.objectMapper = objectMapper;
        this.durableWaitService = durableWaitService;
    }

    public PrimitiveStepResult executeClarify(ExecutionContext executionContext,
                                              ExecutionPlan.ExecutionPlanStep step,
                                              List<RuntimeStepResultContext> previousStepResults) {
        String question = stringValue(step.inputs() == null ? null : step.inputs().get("question"));
        if (question.isBlank()) {
            question = buildClarifyQuestion(executionContext, step, previousStepResults);
        }
        String summary = "需要补充关键信息后才能继续执行";
        return new PrimitiveStepResult(
                question,
                summary,
                toJsonText(new StandardStepOutputSnapshot(
                        "clarify",
                        question,
                        question,
                        summary,
                        summary,
                        1.0d,
                        false,
                        true,
                        "等待用户补充信息",
                        List.of(),
                        Map.of("question", question),
                        Map.of("question", question),
                        Map.of("stepType", "clarify")
                ))
        );
    }

    public PrimitiveStepResult executeJudge(ExecutionContext executionContext,
                                            ExecutionPlan.ExecutionPlanStep step,
                                            List<RuntimeStepResultContext> previousStepResults) {
        String criteria = stringValue(step.inputs() == null ? null : step.inputs().get("criteria"));
        if (criteria.isBlank()) {
            criteria = "基于前序结果评估当前路径是否合理，并给出最优结论。";
        }
        if (executionContext.hasRunnableModel("reflection")) {
            try {
                String responseText = runtimeLlmGatewayService.chatCompletion(
                        """
                        你是 Agent judge 节点。
                        你要基于已有步骤事实进行评估并返回严格 JSON。
                        输出格式：
                        {
                          "assistantMessage": "结论",
                          "summary": "30字以内摘要",
                          "observation": "主要判断依据",
                          "confidence": 0.0,
                          "needReplan": false,
                          "nextActionHint": "下一步建议"
                        }
                        """,
                        objectMapper.writeValueAsString(Map.of(
                                "userMessage", executionContext.userMessage(),
                                "conversationState", executionContext.conversationStatePayload(),
                                "conversationStateText", executionContext.conversationStatePromptText(),
                                "criteria", criteria,
                                "previousSteps", previousStepResults.stream().map(item -> Map.of(
                                        "stepNo", item.stepNo(),
                                        "stepType", item.stepType(),
                                        "stepName", item.stepName(),
                                        "summary", item.summary(),
                                        "responseText", item.responseText()
                                )).toList()
                        )),
                        executionContext.modelForStage("reflection")
                );
                JsonNode root = objectMapper.readTree(runtimeLlmGatewayService.sanitizeJsonText(responseText));
                StandardStepOutputSnapshot snapshot = new StandardStepOutputSnapshot(
                        "judge",
                        root.path("assistantMessage").asText("已完成 judge 评估"),
                        root.path("assistantMessage").asText("已完成 judge 评估"),
                        root.path("summary").asText("已完成 judge 评估"),
                        root.path("observation").asText(criteria),
                        root.hasNonNull("confidence") ? root.path("confidence").asDouble(0.7d) : 0.7d,
                        root.path("needReplan").asBoolean(false),
                        false,
                        root.path("nextActionHint").asText("按评估结果推进下一步"),
                        List.of(),
                        Map.of("criteria", criteria),
                        Map.of("criteria", criteria),
                        Map.of("stepType", "judge")
                );
                return new PrimitiveStepResult(snapshot.assistantMessage(), snapshot.summary(), toJsonText(snapshot));
            } catch (Exception exception) {
                com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
                log.warn("judge 节点执行失败，回退规则结果：{}", exception.getMessage());
            }
        }
        String message = "已完成 judge 评估，建议按当前已确认事实继续推进。";
        return new PrimitiveStepResult(
                message,
                "已完成 judge 评估",
                toJsonText(new StandardStepOutputSnapshot(
                        "judge",
                        message,
                        message,
                        "已完成 judge 评估",
                        criteria,
                        0.6d,
                        false,
                        false,
                        "按当前评估继续执行",
                        List.of(),
                        Map.of("criteria", criteria),
                        Map.of("criteria", criteria),
                        Map.of("stepType", "judge")
                ))
        );
    }

    public PrimitiveStepResult executeWait(ExecutionContext executionContext,
                                           ExecutionPlan.ExecutionPlanStep step,
                                           Long operatorId) {
        Map<String, Object> inputs = step.inputs() == null ? Map.of() : step.inputs();
        Long waitMillis = resolveWaitMillis(inputs);
        String reason = stringValue(inputs.get("reason"));
        DurableWaitService.WakeResult wakeResult = durableWaitService.await(executionContext, step, waitMillis, operatorId);
        String message = "已完成 wait 步骤" + (reason.isBlank() ? "" : "，原因：" + reason);
        return new PrimitiveStepResult(
                message,
                "已完成等待控制",
                toJsonText(new StandardStepOutputSnapshot(
                        "wait",
                        message,
                        message,
                        "已完成等待控制",
                        reason.isBlank() ? "等待窗口结束" : reason,
                        1.0d,
                        false,
                        false,
                        "继续执行后续步骤",
                        List.of(),
                        Map.of("waitMillis", waitMillis, "eventKey", wakeResult.eventKey()),
                        Map.of("waitMillis", waitMillis, "eventKey", wakeResult.eventKey(), "eventPayload", wakeResult.payloadJson()),
                        Map.of("stepType", "wait", "durable", true)
                ))
        );
    }

    public PrimitiveStepResult executeMemoryWrite(ExecutionContext executionContext,
                                                  ExecutionPlan.ExecutionPlanStep step,
                                                  List<RuntimeStepResultContext> previousStepResults,
                                                  Long operatorId) {
        Map<String, Object> inputs = step.inputs() == null ? Map.of() : step.inputs();
        String scope = stringValue(inputs.get("scope")).toLowerCase(Locale.ROOT);
        if (scope.isBlank()) {
            scope = "task";
        }
        String content = stringValue(inputs.get("content"));
        if (content.isBlank() && !previousStepResults.isEmpty()) {
            RuntimeStepResultContext lastStep = previousStepResults.get(previousStepResults.size() - 1);
            content = lastStep.summary() == null || lastStep.summary().isBlank()
                    ? lastStep.responseText()
                    : lastStep.summary();
        }
        if (content.isBlank()) {
            content = "运行时写入了一条记忆摘要";
        }
        Long safeOperatorId = operatorId == null ? 0L : operatorId;
        if ("conversation".equals(scope) || "all".equals(scope)) {
            conversationRepository.upsertConversationMemory(
                    executionContext.conversationContext().conversationId(),
                    content,
                    content,
                    executionContext.userMessage(),
                    content,
                    safeOperatorId
            );
        }
        if ("user".equals(scope) || "all".equals(scope)) {
            conversationRepository.upsertUserMemory(
                    executionContext.currentUser().userId(),
                    executionContext.agentContext().agentId(),
                    content,
                    content,
                    content,
                    content,
                    safeOperatorId
            );
        }
        if ("task".equals(scope) || "all".equals(scope)) {
            conversationRepository.upsertTaskMemorySnapshot(
                    executionContext.taskContext().taskId(),
                    executionContext.conversationContext().conversationId(),
                    content,
                    content,
                    content,
                    safeOperatorId
            );
        }
        String message = "已写入 " + scope + " 级记忆。";
        return new PrimitiveStepResult(
                message,
                "已完成记忆写入",
                toJsonText(new StandardStepOutputSnapshot(
                        "memory_write",
                        message,
                        message,
                        "已完成记忆写入",
                        content,
                        1.0d,
                        false,
                        false,
                        "继续执行后续步骤",
                        List.of(),
                        Map.of("scope", scope, "content", content),
                        Map.of("scope", scope, "content", content),
                        Map.of("stepType", "memory_write")
                ))
        );
    }

    private String buildClarifyQuestion(ExecutionContext executionContext,
                                        ExecutionPlan.ExecutionPlanStep step,
                                        List<RuntimeStepResultContext> previousStepResults) {
        if (executionContext.hasRunnableModel("reply")) {
            try {
                return runtimeLlmGatewayService.chatCompletion(
                        """
                        你是 Agent clarify 节点。
                        请基于当前任务目标生成一句明确、简洁、便于用户回答的补问。
                        只输出一句自然语言问题，不要解释。
                        """,
                        objectMapper.writeValueAsString(Map.of(
                                "userMessage", executionContext.userMessage(),
                                "stepName", step.stepName(),
                                "stepObjective", step.objective(),
                                "previousSummaries", previousStepResults.stream().map(RuntimeStepResultContext::summary).toList()
                        )),
                        executionContext.modelForStage("reply")
                ).trim();
            } catch (Exception exception) {
                com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
                log.warn("clarify 问题生成失败，回退模板：{}", exception.getMessage());
            }
        }
        return "要继续处理这个任务，还需要你补充更具体的信息。请说明你最关心的目标、范围或限制条件。";
    }

    private Long resolveWaitMillis(Map<String, Object> inputs) {
        Object waitMs = inputs.get("waitMillis");
        if (waitMs instanceof Number number) {
            return Math.max(0L, Math.min(number.longValue(), MAX_WAIT_MILLIS));
        }
        Object waitSeconds = inputs.get("waitSeconds");
        if (waitSeconds instanceof Number number) {
            return Math.max(0L, Math.min(number.longValue() * 1000L, MAX_WAIT_MILLIS));
        }
        return 0L;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("原语步骤输出序列化失败");
        }
    }

    public record PrimitiveStepResult(
            String responseText,
            String summary,
            String outputSnapshot
    ) {
    }
}

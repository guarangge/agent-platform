package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 并行 / 聚合步骤服务。
 */
@Service
public class ParallelJoinStepService {

    private final ToolRouterService toolRouterService;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final StepOutputContractService stepOutputContractService;
    private final ObjectMapper objectMapper;

    public ParallelJoinStepService(ToolRouterService toolRouterService,
                                   RuntimeLlmGatewayService runtimeLlmGatewayService,
                                   StepOutputContractService stepOutputContractService,
                                   ObjectMapper objectMapper) {
        this.toolRouterService = toolRouterService;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.stepOutputContractService = stepOutputContractService;
        this.objectMapper = objectMapper;
    }

    public AgenticPrimitiveStepService.PrimitiveStepResult executeParallel(ExecutionContext executionContext,
                                                                           ExecutionPlan.ExecutionPlanStep step,
                                                                           String inputText) {
        List<String> toolCodes = resolveToolCodes(step.inputs(), "parallelToolCodes");
        if (toolCodes.isEmpty()) {
            throw new BusinessException("parallel 步骤缺少 parallelToolCodes");
        }
        Map<String, Object> stepInputs = step.inputs() == null ? Map.of() : step.inputs();
        List<CompletableFuture<Map<String, Object>>> futures = toolCodes.stream()
                .map(toolCode -> CompletableFuture.supplyAsync(() -> executeSingleTool(
                        executionContext,
                        toolCode,
                        resolveParallelToolInputText(toolCode, inputText, stepInputs),
                        resolveParallelToolArguments(toolCode, stepInputs)
                )))
                .toList();
        List<Map<String, Object>> toolResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        String responseText = buildParallelResponse(toolResults);
        String outputSnapshot = stepOutputContractService.buildGenericSnapshot(
                "parallel",
                responseText,
                "已完成并行工具执行",
                "并行执行了 " + toolResults.size() + " 个工具",
                0.85d,
                false,
                false,
                "如需结论收束，可继续执行 join 步骤",
                buildEvidence(toolResults, "toolCode", "summary"),
                Map.of("toolResults", toolResults),
                Map.of("toolResults", toolResults),
                Map.of("toolCount", toolResults.size())
        );
        return new AgenticPrimitiveStepService.PrimitiveStepResult(responseText, "已完成并行工具执行", outputSnapshot);
    }

    public AgenticPrimitiveStepService.PrimitiveStepResult executeJoin(ExecutionContext executionContext,
                                                                       ExecutionPlan.ExecutionPlanStep step,
                                                                       List<RuntimeStepResultContext> previousStepResults) {
        String joinInstruction = stringValue(step.inputs() == null ? null : step.inputs().get("joinInstruction"));
        if (joinInstruction.isBlank()) {
            joinInstruction = "请归并前序并行结果，输出统一结论。";
        }
        List<Map<String, Object>> previousFacts = previousStepResults.stream()
                .map(item -> {
                    Map<String, Object> fact = new LinkedHashMap<>();
                    fact.put("stepNo", item.stepNo());
                    fact.put("stepType", item.stepType());
                    fact.put("stepName", item.stepName());
                    fact.put("summary", item.summary());
                    fact.put("responseText", item.responseText());
                    return fact;
                })
                .toList();
        String assistantMessage = buildJoinMessage(executionContext, joinInstruction, previousFacts);
        String outputSnapshot = stepOutputContractService.buildGenericSnapshot(
                "join",
                assistantMessage,
                "已完成聚合归并",
                joinInstruction,
                0.8d,
                false,
                false,
                "继续执行后续步骤",
                buildEvidence(previousFacts, "stepNo", "summary"),
                Map.of("joinInstruction", joinInstruction),
                Map.of("previousFacts", previousFacts),
                Map.of("sourceCount", previousFacts.size())
        );
        return new AgenticPrimitiveStepService.PrimitiveStepResult(assistantMessage, "已完成聚合归并", outputSnapshot);
    }

    private Map<String, Object> executeSingleTool(ExecutionContext executionContext,
                                                  String toolCode,
                                                  String inputText,
                                                  Map<String, Object> arguments) {
        ToolExecutionResult result = toolRouterService.execute(executionContext, toolCode, inputText, arguments)
                .orElseThrow(() -> new BusinessException("parallel 子工具不可用: " + toolCode));
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("toolCode", result.toolCode());
        item.put("toolName", result.toolName());
        item.put("summary", result.summary());
        item.put("responseText", result.responseText());
        item.put("outputSnapshot", result.outputSnapshot());
        return item;
    }

    private String buildParallelResponse(List<Map<String, Object>> toolResults) {
        StringBuilder builder = new StringBuilder("已完成并行工具执行。");
        for (int index = 0; index < toolResults.size(); index++) {
            Map<String, Object> item = toolResults.get(index);
            builder.append("\n").append(index + 1).append(". ")
                    .append(item.getOrDefault("toolName", item.getOrDefault("toolCode", "Tool")))
                    .append("：")
                    .append(item.getOrDefault("summary", ""));
        }
        return builder.toString();
    }

    private String buildJoinMessage(ExecutionContext executionContext,
                                    String joinInstruction,
                                    List<Map<String, Object>> previousFacts) {
        if (!executionContext.hasRunnableModel("reply")) {
            return "已完成 join 聚合，可基于前序结果继续输出最终结论。";
        }
        try {
            return runtimeLlmGatewayService.chatCompletion(
                    """
                    你是 Agent join 节点。
                    请基于多个并行结果做统一聚合，只输出面向用户的结论。
                    """,
                    objectMapper.writeValueAsString(Map.of(
                            "joinInstruction", joinInstruction,
                            "conversationState", executionContext.conversationStatePayload(),
                            "conversationStateText", safeText(executionContext.conversationStatePromptText()),
                            "conversationMemory", safeText(executionContext.conversationMemoryText()),
                            "previousFacts", previousFacts
                    )),
                    executionContext.modelForStage("reply")
            ).trim();
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            return "已完成 join 聚合，可基于前序结果继续输出最终结论。";
        }
    }

    private Map<String, Object> resolveParallelToolArguments(String toolCode, Map<String, Object> stepInputs) {
        if (stepInputs == null || stepInputs.isEmpty()) {
            return Map.of();
        }
        Object specificValue = stepInputs.get(toLowerCamel(toolCode) + "Params");
        if (!(specificValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                arguments.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return arguments;
    }

    private String resolveParallelToolInputText(String toolCode, String fallbackInputText, Map<String, Object> stepInputs) {
        Map<String, Object> arguments = resolveParallelToolArguments(toolCode, stepInputs);
        if (arguments.isEmpty()) {
            return fallbackInputText;
        }
        Object userMessage = arguments.get("userMessage");
        if (userMessage != null && !String.valueOf(userMessage).isBlank()) {
            return String.valueOf(userMessage).trim();
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (entry.getValue() == null || String.valueOf(entry.getValue()).isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return builder.isEmpty() ? fallbackInputText : builder.toString();
    }

    private String toLowerCamel(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return "";
        }
        String[] parts = toolCode.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isBlank()) {
                continue;
            }
            if (index == 0) {
                builder.append(part);
            } else {
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1));
                }
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveToolCodes(Map<String, Object> inputs, String fieldName) {
        if (inputs == null) {
            return List.of();
        }
        Object value = inputs.get(fieldName);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> toolCodes = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                toolCodes.add(String.valueOf(item).trim());
            }
        }
        return toolCodes;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private List<Map<String, Object>> buildEvidence(List<Map<String, Object>> sourceItems,
                                                    String idField,
                                                    String pointField) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (Map<String, Object> item : sourceItems) {
            LinkedHashMap<String, Object> evidenceItem = new LinkedHashMap<>();
            evidenceItem.put("sourceStepId", String.valueOf(item.getOrDefault(idField, "")));
            evidenceItem.put("point", String.valueOf(item.getOrDefault(pointField, "")));
            evidence.add(evidenceItem);
        }
        return evidence;
    }
}

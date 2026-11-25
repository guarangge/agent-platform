package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用 LLM 生成并严格解析 Interactive Loop 结构化 Action。 */
@Service
public class LlmInteractiveLoopActionDecider implements InteractiveLoopActionDecider {

    private static final int MAX_OBSERVATIONS = 12;
    private static final int MAX_OBSERVATION_CHARS = 4_000;
    private final RuntimeLlmGatewayService llmGatewayService;
    private final CostGovernanceService costGovernanceService;
    private final ObjectMapper objectMapper;

    public LlmInteractiveLoopActionDecider(RuntimeLlmGatewayService llmGatewayService,
                                           CostGovernanceService costGovernanceService,
                                           ObjectMapper objectMapper) {
        this.llmGatewayService = llmGatewayService;
        this.costGovernanceService = costGovernanceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProjectedUsage estimate(InteractiveLoopActionRequest request) {
        PromptBundle prompts = prompts(request);
        long inputTokens = Math.max(1, (prompts.systemPrompt().length() + prompts.userPrompt().length() + 3L) / 4L);
        ExecutionContext.ModelContext model = resolveModel(request.executionContext());
        long outputTokens = model.maxTokens() == null ? 1024 : Math.max(1, model.maxTokens());
        BigDecimal amount = costGovernanceService.estimateAmount(
                "llm", model.modelCode(), inputTokens, outputTokens, BigDecimal.ONE);
        return new ProjectedUsage(inputTokens + outputTokens, amount);
    }

    @Override
    public InteractiveLoopAction decide(InteractiveLoopActionRequest request) throws Exception {
        PromptBundle prompts = prompts(request);
        LlmCompletionResult result = llmGatewayService.chatCompletionResult(
                prompts.systemPrompt(), prompts.userPrompt(), resolveModel(request.executionContext()));
        return parseAction(llmGatewayService.sanitizeJsonText(result.content()));
    }

    /** 严格解析模型返回，不接受自由文本或未知 Action。 */
    InteractiveLoopAction parseAction(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("Interactive Loop Action 必须为 JSON object");
            }
            InteractiveLoopAction.Type type = InteractiveLoopAction.Type.fromCode(root.path("action").asText(""));
            if (type == InteractiveLoopAction.Type.FINAL_ANSWER) {
                String content = root.path("content").asText("").trim();
                if (content.isBlank()) {
                    throw new IllegalArgumentException("final_answer.content 不能为空");
                }
                return new InteractiveLoopAction(type, null, Map.of(), content);
            }
            String toolCode = root.path("toolCode").asText("").trim();
            JsonNode argumentsNode = root.path("arguments");
            if (toolCode.isBlank() || !argumentsNode.isObject()) {
                throw new IllegalArgumentException("tool_call 必须包含 toolCode 和 arguments object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = objectMapper.convertValue(argumentsNode, LinkedHashMap.class);
            return new InteractiveLoopAction(type, toolCode,
                    Collections.unmodifiableMap(new LinkedHashMap<>(arguments)), null);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Interactive Loop Action JSON 解析失败", exception);
        }
    }

    private PromptBundle prompts(InteractiveLoopActionRequest request) {
        ExecutionContext context = request.executionContext();
        String systemPrompt = """
                你是受限的 Interactive Loop Action 决策器。每次只能输出一个 JSON object，不要输出 Markdown、解释或思维过程。
                可用格式只有：
                {"action":"tool_call","toolCode":"TOOL_CODE","arguments":{}}
                {"action":"final_answer","content":"给用户的最终回复"}
                只能调用 availableTools 中的 Tool；基于 observations 判断下一步；信息足够时立即 final_answer；禁止重复无进展调用。
                """;
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("userMessage", context.userMessage());
        payload.put("iteration", request.iteration());
        payload.put("availableTools", safeTools(context.availableTools()));
        payload.put("observations", boundedObservations(request.observations()));
        payload.put("conversationMemory", trim(context.conversationMemoryText(), MAX_OBSERVATION_CHARS));
        payload.put("conversationState", context.conversationStatePayload());
        try {
            return new PromptBundle(systemPrompt, objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("Interactive Loop Prompt 序列化失败", exception);
        }
    }

    private List<Map<String, Object>> safeTools(List<RegistryAgentTool> tools) {
        if (tools == null) return List.of();
        return tools.stream().map(tool -> Map.<String, Object>of(
                "toolCode", safe(tool.toolCode()),
                "toolName", safe(tool.toolName()),
                "description", trim(tool.description(), 500),
                "inputSchema", tool.inputSchema() == null ? Map.of() : tool.inputSchema()
        )).toList();
    }

    private List<InteractiveLoopObservation> boundedObservations(List<InteractiveLoopObservation> observations) {
        if (observations == null || observations.isEmpty()) return List.of();
        int fromIndex = Math.max(0, observations.size() - MAX_OBSERVATIONS);
        ArrayList<InteractiveLoopObservation> bounded = new ArrayList<>();
        for (InteractiveLoopObservation item : observations.subList(fromIndex, observations.size())) {
            bounded.add(new InteractiveLoopObservation(item.iteration(), item.toolCode(), item.status(),
                    trim(item.summary(), MAX_OBSERVATION_CHARS), trim(item.responseText(), MAX_OBSERVATION_CHARS)));
        }
        return List.copyOf(bounded);
    }

    private ExecutionContext.ModelContext resolveModel(ExecutionContext context) {
        ExecutionContext.ModelContext model = context.modelForStage("interactive_loop");
        if (model == null) {
            throw new IllegalStateException("Interactive Loop 缺少可用模型配置");
        }
        return model;
    }

    private String trim(String value, int maxChars) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record PromptBundle(String systemPrompt, String userPrompt) {
    }
}

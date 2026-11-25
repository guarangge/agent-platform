package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.dto.TraceReplayRequest;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceAnalyticsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.TaskTraceAnalyticsResponse;
import com.zc.agent.core.modules.conversation.vo.TraceInvestigationResponse;
import com.zc.agent.core.modules.conversation.vo.TraceReplayResponse;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayInstance;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.service.ReplayFeaturePolicy;
import com.zc.agent.core.modules.runtime.replay.service.ReplayInstanceService;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prompt、Tool 与 Trace 联动排查及快照回放服务。
 */
@Service
public class TraceInvestigationService {

    private final ConversationRepository conversationRepository;
    private final RegistryAgentClient registryAgentClient;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final TaskTraceService taskTraceService;
    private final ReplayInstanceService replayInstanceService;
    private final ReplayFeaturePolicy replayFeaturePolicy;
    private final ObjectMapper objectMapper;

    public TraceInvestigationService(ConversationRepository conversationRepository,
                                     RegistryAgentClient registryAgentClient,
                                     RuntimeLlmGatewayService runtimeLlmGatewayService,
                                     TaskTraceService taskTraceService,
                                     ReplayInstanceService replayInstanceService,
                                     ReplayFeaturePolicy replayFeaturePolicy,
                                     ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.registryAgentClient = registryAgentClient;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.taskTraceService = taskTraceService;
        this.replayInstanceService = replayInstanceService;
        this.replayFeaturePolicy = replayFeaturePolicy;
        this.objectMapper = objectMapper;
    }

    /**
     * 提取 Trace 中可操作的 Prompt、Tool 关联信息。
     */
    public TraceInvestigationResponse investigate(Long taskTraceId, SecurityUser securityUser) {
        TaskTraceAnalyticsRecord record = requireOwnedTrace(taskTraceId, securityUser.userId());
        TraceArtifacts artifacts = extractArtifacts(record);
        boolean replayable = hasText(artifacts.promptSnapshot().get("systemPrompt"))
                && hasText(artifacts.promptSnapshot().get("userPrompt"));
        return new TraceInvestigationResponse(
                toResponse(record),
                artifacts.promptSnapshot(),
                artifacts.toolInputObservation(),
                artifacts.promptReferences(),
                artifacts.toolReferences(),
                replayable,
                replayable ? null : "当前 Trace 未保存完整 systemPrompt/userPrompt 快照"
        );
    }

    /**
     * 使用历史 Prompt 快照重新调用模型，不重复执行 Tool 或 Workflow 副作用。
     */
    public TraceReplayResponse replay(Long taskTraceId,
                                      TraceReplayRequest request,
                                      String authorization,
                                      SecurityUser securityUser) {
        requireAdministrator(securityUser);
        TaskTraceAnalyticsRecord record = requireOwnedTrace(taskTraceId, securityUser.userId());
        ReplayFeaturePolicy.Decision featureDecision = replayFeaturePolicy.requireEnabled(
                ReplayMode.PROMPT_REPLAY, record.agentId(), securityUser);
        TraceArtifacts artifacts = extractArtifacts(record);
        String systemPrompt = firstNonBlank(request.systemPrompt(), textValue(artifacts.promptSnapshot().get("systemPrompt")));
        String userPrompt = firstNonBlank(request.userPrompt(), textValue(artifacts.promptSnapshot().get("userPrompt")));
        if (systemPrompt.isBlank() || userPrompt.isBlank()) {
            throw new BusinessException("当前 Trace 缺少完整 Prompt 快照，无法回放");
        }
        AgentSnapshot agentSnapshot = registryAgentClient.getEnabledAgentSnapshot(record.agentId(), authorization);
        String modelCode = firstNonBlank(request.modelCode(), agentSnapshot.defaultModel());
        if (modelCode.isBlank()) {
            throw new BusinessException("原 Agent 未配置默认模型，请指定 modelCode");
        }
        ExecutionContext.ModelContext modelContext = registryAgentClient.getModelSnapshot(modelCode, authorization);
        TaskRecord sourceTask = conversationRepository.findTaskById(record.taskId())
                .orElseThrow(() -> new BusinessException("原 Task 不存在，无法创建 Replay Manifest"));
        String originalOutput = extractOriginalOutput(record.outputSnapshot());
        ReplayInstance replayInstance = replayInstanceService.startPromptReplay(
                new ReplayInstanceService.StartPromptReplayCommand(
                        record.taskId(), taskTraceId, record.traceId(), record.agentId(), record.agentName(),
                        sourceTask.executionType(), modelContext, systemPrompt, userPrompt, originalOutput,
                        artifacts.promptReferences(), artifacts.toolReferences(), featureDecision,
                        securityUser.userId()
                ));
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            LlmCompletionResult completion = runtimeLlmGatewayService.chatCompletionResult(systemPrompt, userPrompt, modelContext);
            Map<String, Object> diff = buildDiff(originalOutput, completion.content());
            LinkedHashMap<String, Object> replayMetadata = new LinkedHashMap<>();
            replayMetadata.put("sourceTaskTraceId", taskTraceId);
            replayMetadata.put("replayCode", replayInstance.replayCode());
            replayMetadata.put("replayMode", replayInstance.replayMode().code());
            replayMetadata.put("modelIdentifier", completion.modelIdentifier() == null ? "" : completion.modelIdentifier());
            taskTraceService.recordSpan(
                    record.taskId(),
                    replayInstance.replayTraceId(),
                    record.taskStepId(),
                    "prompt_replay",
                    "Prompt 快照回放",
                    "success",
                    Map.of("sourceTaskTraceId", taskTraceId, "modelCode", modelCode,
                            "replayCode", replayInstance.replayCode()),
                    Map.of("changed", diff.get("changed"), "replayOutputChars", completion.content().length()),
                    null,
                    startedAt,
                    LocalDateTime.now(),
                    replayMetadata,
                    securityUser.userId()
            );
            replayInstanceService.complete(replayInstance, replayResultSnapshot(completion, diff), securityUser.userId());
            return new TraceReplayResponse(
                    taskTraceId,
                    record.traceId(),
                    modelCode,
                    completion.modelIdentifier(),
                    originalOutput,
                    completion.content(),
                    completion.promptTokens(),
                    completion.completionTokens(),
                    completion.totalTokens(),
                    completion.durationMs(),
                    diff,
                    replayInstance.replayCode(),
                    replayInstance.replayMode().code(),
                    replayInstance.replayTraceId(),
                    "succeeded",
                    replayInstance.manifest()
            );
        } catch (Exception exception) {
            replayInstanceService.fail(replayInstance, exception.getMessage(), securityUser.userId());
            taskTraceService.recordSpan(
                    record.taskId(), replayInstance.replayTraceId(), record.taskStepId(), "prompt_replay", "Prompt 快照回放", "failed",
                    Map.of("sourceTaskTraceId", taskTraceId, "modelCode", modelCode,
                            "replayCode", replayInstance.replayCode()),
                    Map.of(), exception.getMessage(), startedAt, LocalDateTime.now(),
                    Map.of("sourceTaskTraceId", taskTraceId, "replayCode", replayInstance.replayCode(),
                            "replayMode", replayInstance.replayMode().code()), securityUser.userId()
            );
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException("Trace 回放失败: " + exception.getMessage());
        }
    }

    private Map<String, Object> replayResultSnapshot(LlmCompletionResult completion, Map<String, Object> diff) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("replayOutput", completion.content());
        result.put("modelIdentifier", completion.modelIdentifier());
        result.put("promptTokens", completion.promptTokens());
        result.put("completionTokens", completion.completionTokens());
        result.put("totalTokens", completion.totalTokens());
        result.put("durationMs", completion.durationMs());
        result.put("diff", diff);
        return result;
    }

    private void requireAdministrator(SecurityUser securityUser) {
        boolean administrator = securityUser.roleCodes() != null && securityUser.roleCodes().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role));
        if (!administrator) {
            throw new BusinessException("Replay 当前仅允许平台管理员执行");
        }
    }

    private TaskTraceAnalyticsRecord requireOwnedTrace(Long taskTraceId, Long userId) {
        return conversationRepository.findTaskTraceAnalyticsById(userId, taskTraceId)
                .orElseThrow(() -> new BusinessException("Trace 不存在或无权访问"));
    }

    private TraceArtifacts extractArtifacts(TaskTraceAnalyticsRecord record) {
        JsonNode input = readTree(record.inputSnapshot());
        JsonNode output = readTree(record.outputSnapshot());
        JsonNode metadata = readTree(record.metadata());
        JsonNode promptSnapshot = firstObject(
                metadata.path("contextCompaction").path("promptSnapshot"),
                metadata.path("promptTrace").path("promptSnapshot"),
                output.path("promptTrace").path("promptSnapshot"),
                output.path("metadata").path("promptTrace").path("promptSnapshot")
        );
        JsonNode toolObservation = firstObject(
                input.path("inputObservation"),
                input.path("toolInputObservation"),
                metadata.path("toolInputObservation"),
                output.path("toolInputObservation")
        );
        List<Map<String, Object>> promptReferences = buildPromptReferences(record, metadata, output);
        List<Map<String, Object>> toolReferences = buildToolReferences(metadata, input, output);
        return new TraceArtifacts(toMap(promptSnapshot), toMap(toolObservation), promptReferences, toolReferences);
    }

    private List<Map<String, Object>> buildPromptReferences(TaskTraceAnalyticsRecord record, JsonNode metadata, JsonNode output) {
        List<Map<String, Object>> references = new ArrayList<>();
        String stage = firstNonBlank(
                metadata.path("contextCompaction").path("stage").asText(""),
                output.path("promptTrace").path("stage").asText(""),
                output.path("metadata").path("promptTrace").path("stage").asText("")
        );
        LinkedHashMap<String, Object> reference = new LinkedHashMap<>();
        reference.put("agentId", record.agentId());
        reference.put("agentName", record.agentName());
        reference.put("stage", stage);
        reference.put("eventType", record.eventType());
        references.add(reference);
        return references;
    }

    private List<Map<String, Object>> buildToolReferences(JsonNode metadata, JsonNode input, JsonNode output) {
        Set<String> toolCodes = new LinkedHashSet<>();
        collectTextValues(metadata, "targetToolCode", toolCodes);
        collectTextValues(metadata, "toolCode", toolCodes);
        collectTextValues(input, "targetToolCode", toolCodes);
        collectTextValues(input, "toolCode", toolCodes);
        collectTextValues(output, "toolCode", toolCodes);
        return toolCodes.stream().map(code -> Map.<String, Object>of("toolCode", code)).toList();
    }

    private void collectTextValues(JsonNode node, String fieldName, Set<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (fieldName.equals(entry.getKey()) && entry.getValue().isValueNode()) {
                    String value = entry.getValue().asText("").trim();
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
                collectTextValues(entry.getValue(), fieldName, values);
            });
        } else if (node.isArray()) {
            node.forEach(item -> collectTextValues(item, fieldName, values));
        }
    }

    private String extractOriginalOutput(String outputSnapshot) {
        JsonNode output = readTree(outputSnapshot);
        for (String field : List.of("assistantMessage", "responseText", "delegateResponse", "content")) {
            String value = output.path(field).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return outputSnapshot == null ? "" : outputSnapshot;
    }

    private Map<String, Object> buildDiff(String original, String replay) {
        String left = original == null ? "" : original;
        String right = replay == null ? "" : replay;
        int commonPrefix = 0;
        int max = Math.min(left.length(), right.length());
        while (commonPrefix < max && left.charAt(commonPrefix) == right.charAt(commonPrefix)) {
            commonPrefix++;
        }
        LinkedHashMap<String, Object> diff = new LinkedHashMap<>();
        diff.put("changed", !left.equals(right));
        diff.put("originalChars", left.length());
        diff.put("replayChars", right.length());
        diff.put("charDelta", right.length() - left.length());
        diff.put("commonPrefixChars", commonPrefix);
        diff.put("originalDifferencePreview", preview(left, commonPrefix));
        diff.put("replayDifferencePreview", preview(right, commonPrefix));
        return diff;
    }

    private String preview(String value, int start) {
        if (value.isBlank() || start >= value.length()) {
            return "";
        }
        return value.substring(start, Math.min(value.length(), start + 500));
    }

    private JsonNode readTree(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode firstObject(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isObject() && !node.isEmpty()) {
                return node;
            }
        }
        return objectMapper.createObjectNode();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, LinkedHashMap.class);
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private TaskTraceAnalyticsResponse toResponse(TaskTraceAnalyticsRecord record) {
        return new TaskTraceAnalyticsResponse(
                record.taskTraceId(), record.conversationId(), record.conversationCode(), record.taskId(), record.taskCode(),
                record.agentId(), record.agentName(), record.traceId(), record.taskStepId(), record.stepNo(), record.stepName(),
                record.eventType(), record.spanName(), record.traceStatus(), record.durationMs(), record.errorMessage(),
                record.inputSnapshot(), record.outputSnapshot(), record.metadata(), record.startedAt(), record.finishedAt(), record.createdAt()
        );
    }

    private record TraceArtifacts(
            Map<String, Object> promptSnapshot,
            Map<String, Object> toolInputObservation,
            List<Map<String, Object>> promptReferences,
            List<Map<String, Object>> toolReferences
    ) {
    }
}

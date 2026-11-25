package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.workflow.model.WorkflowExecutionResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一步骤结果契约归一化服务。
 */
@Service
public class StepOutputContractService {

    private final ObjectMapper objectMapper;

    public StepOutputContractService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildToolSnapshot(String stepId,
                                    ToolExecutionResult toolExecutionResult,
                                    Map<String, Object> extraMetadata) {
        Object payload = parseJson(toolExecutionResult.outputSnapshot());
        Map<String, Object> structuredData = Map.of(
                "toolCode", safeText(toolExecutionResult.toolCode()),
                "toolName", safeText(toolExecutionResult.toolName()),
                "summary", safeText(toolExecutionResult.summary()),
                "responseText", safeText(toolExecutionResult.responseText())
        );
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("toolCode", safeText(toolExecutionResult.toolCode()));
        metadata.put("toolName", safeText(toolExecutionResult.toolName()));
        if (extraMetadata != null && !extraMetadata.isEmpty()) {
            metadata.putAll(extraMetadata);
        }
        return toJsonText(new StandardStepOutputSnapshot(
                "tool",
                toolExecutionResult.responseText(),
                toolExecutionResult.responseText(),
                toolExecutionResult.summary(),
                toolExecutionResult.summary(),
                0.85d,
                false,
                false,
                "继续执行后续步骤",
                List.of(Map.of(
                        "sourceStepId", safeText(stepId),
                        "point", safeText(toolExecutionResult.summary())
                )),
                structuredData,
                payload,
                metadata
        ));
    }

    public String buildWorkflowSnapshot(WorkflowExecutionResult workflowExecutionResult) {
        Object payload = parseJson(workflowExecutionResult.outputSnapshot());
        Map<String, Object> structuredData = Map.of(
                "workflowCode", safeText(workflowExecutionResult.workflowCode()),
                "workflowName", safeText(workflowExecutionResult.workflowName()),
                "summary", safeText(workflowExecutionResult.summary()),
                "responseText", safeText(workflowExecutionResult.responseText())
        );
        return toJsonText(new StandardStepOutputSnapshot(
                "workflow",
                workflowExecutionResult.responseText(),
                workflowExecutionResult.responseText(),
                workflowExecutionResult.summary(),
                workflowExecutionResult.summary(),
                0.85d,
                false,
                false,
                "继续执行后续步骤",
                List.of(Map.of(
                        "sourceStepId", safeText(workflowExecutionResult.workflowCode()),
                        "point", safeText(workflowExecutionResult.summary())
                )),
                structuredData,
                payload,
                Map.of(
                        "workflowCode", safeText(workflowExecutionResult.workflowCode()),
                        "workflowName", safeText(workflowExecutionResult.workflowName())
                )
        ));
    }

    public String buildGenericSnapshot(String resultType,
                                       String assistantMessage,
                                       String summary,
                                       String observation,
                                       Double confidence,
                                       Boolean needReplan,
                                       Boolean needMoreInfo,
                                       String nextActionHint,
                                       List<Map<String, Object>> evidence,
                                       Object structuredData,
                                       Object payload,
                                       Map<String, Object> metadata) {
        return toJsonText(new StandardStepOutputSnapshot(
                resultType,
                assistantMessage,
                assistantMessage,
                summary,
                observation,
                confidence,
                needReplan,
                needMoreInfo,
                nextActionHint,
                evidence == null ? List.of() : evidence,
                structuredData == null ? Map.of() : structuredData,
                payload == null ? Map.of() : payload,
                metadata == null ? Map.of() : metadata
        ));
    }

    public StandardStepOutputSnapshot parseSnapshot(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(outputSnapshot);
            if (!rootNode.isObject() || rootNode.path("resultType").asText("").isBlank()) {
                return null;
            }
            return objectMapper.treeToValue(rootNode, StandardStepOutputSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private Object parseJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (Exception exception) {
            LinkedHashMap<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", jsonText);
            return fallback;
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("统一步骤结果契约序列化失败");
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}

package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import com.zc.agent.core.modules.workflow.model.WorkflowExecutionResult;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一组装 Workflow 对外回复、标准输出快照和 Recovery 快照。 */
@Service
public class WorkflowResultAssemblerService {
    private final WorkflowRecoveryService workflowRecoveryService;
    private final ObjectMapper objectMapper;

    public WorkflowResultAssemblerService(WorkflowRecoveryService workflowRecoveryService, ObjectMapper objectMapper) {
        this.workflowRecoveryService = workflowRecoveryService;
        this.objectMapper = objectMapper;
    }

    /** 将节点执行现场转换为稳定的 Workflow 输出契约。 */
    public WorkflowExecutionResult assemble(RegistryWorkflowSnapshot workflow, List<Map<String, Object>> executedNodes,
                                            String summary, String responseText, WorkflowResumeContext resumeContext) {
        String finalResponseText = responseText(workflow, executedNodes, responseText);
        Map<String, Object> output = new HashMap<>();
        output.put("resultType", "workflow");
        output.put("assistantMessage", finalResponseText);
        output.put("responseText", finalResponseText);
        output.put("workflowCode", workflow.workflowCode());
        output.put("workflowName", workflow.workflowName());
        output.put("summary", summary);
        output.put("observation", summary);
        output.put("confidence", 0.85D);
        output.put("needReplan", false);
        output.put("needMoreInfo", false);
        output.put("executedNodes", executedNodes);
        output.put("runtimeVariables", finalRuntimeVariables(executedNodes));
        Map<String, Object> recovery = workflowRecoveryService.buildRecoverySnapshot(
                workflow, resumeContext, resumeContext == null ? null : resumeContext.resumeMode());
        if (recovery != null) output.put("recovery", recovery);
        return new WorkflowExecutionResult(workflow.workflowCode(), workflow.workflowName(), summary,
                finalResponseText, json(output));
    }

    private String responseText(RegistryWorkflowSnapshot workflow, List<Map<String, Object>> nodes, String lastResponse) {
        if (isUserReplyOutput(workflow) && hasText(lastResponse)) {
            return lastResponse.trim();
        }
        if (nodes == null || nodes.isEmpty()) return hasText(lastResponse) ? lastResponse
                : "已进入 Workflow「" + workflow.workflowName() + "」，但当前节点执行未产出最终回复。";
        StringBuilder builder = new StringBuilder("已执行 Workflow「").append(workflow.workflowName()).append("」。");
        int index = 0;
        for (Map<String, Object> node : nodes) {
            String type = String.valueOf(node.get("nodeType"));
            if ("start".equals(type) || "end".equals(type) || "condition".equals(type)) continue;
            index++;
            builder.append("\n").append(index).append(". ").append(node.getOrDefault("nodeName", "节点"))
                    .append("：").append(trim(String.valueOf(node.getOrDefault("summary", "")), 80));
            String nodeResponse = String.valueOf(node.getOrDefault("responseText", ""));
            if (hasText(nodeResponse)) builder.append("\n   ").append(trim(nodeResponse.replace('\n', ' '), 240));
        }
        return index == 0 && hasText(lastResponse) ? lastResponse : builder.toString();
    }

    private boolean isUserReplyOutput(RegistryWorkflowSnapshot workflow) {
        return workflow.metadata() != null
                && "user_reply".equalsIgnoreCase(
                workflow.metadata().path("routing").path("outputMode").asText(""));
    }

    private Map<String, Object> finalRuntimeVariables(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) return Map.of();
        Object value = nodes.get(nodes.size() - 1).get("runtimeVariables");
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value, int maxLength) {
        if (!hasText(value)) return "";
        String normalized = value.trim();
        if (normalized.length() <= maxLength) return normalized;
        return maxLength <= 3 ? normalized.substring(0, maxLength) : normalized.substring(0, maxLength - 3) + "...";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow 输出快照序列化失败", exception);
        }
    }
}

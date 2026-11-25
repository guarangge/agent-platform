package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import org.springframework.stereotype.Service;

import java.util.*;

/** 将持久化步骤快照转换为恢复上下文，使会话协调服务不再解析 Workflow 内部结构。 */
@Service
public class ConversationRecoveryContextFactory {
    private final ObjectMapper objectMapper;

    public ConversationRecoveryContextFactory(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public List<RuntimeStepResultContext> previousResults(List<TaskStepRecord> steps, Integer failedStepNo) {
        LinkedHashMap<Integer, RuntimeStepResultContext> results = new LinkedHashMap<>();
        for (TaskStepRecord step : steps) {
            if (step.stepNo() >= failedStepNo || !"success".equals(step.status())) continue;
            String summary = summarize(step.outputSnapshot());
            results.put(step.stepNo(), new RuntimeStepResultContext(step.stepNo(), resolveStepId(step),
                    step.stepType(), step.stepName(), summary, summary, step.outputSnapshot()));
        }
        return new ArrayList<>(results.values());
    }

    public WorkflowResumeContext workflowResume(TaskStepRecord failedStep) {
        JsonNode root = parse(failedStep.outputSnapshot());
        JsonNode recovery = root == null ? null : root.path("recoveryMetadata");
        if (recovery == null || recovery.isMissingNode() || recovery.isNull()) return null;
        String failedNodeId = recovery.path("failedNodeId").asText(null);
        if (failedNodeId == null || failedNodeId.isBlank()) return null;
        List<Map<String,Object>> executed = new ArrayList<>();
        recovery.path("executedNodes").forEach(item -> executed.add(objectMapper.convertValue(item, new TypeReference<>() {})));
        List<WorkflowResumeContext.WorkflowResumeNode> previous = new ArrayList<>();
        recovery.path("previousNodeResults").forEach(item -> previous.add(new WorkflowResumeContext.WorkflowResumeNode(
                item.path("nodeId").asText(null), item.path("nodeType").asText(null),
                item.path("summary").asText(null), item.path("responseText").asText(null))));
        return new WorkflowResumeContext(failedNodeId, "failed_node_resume", executed, previous);
    }

    private String resolveStepId(TaskStepRecord step) {
        JsonNode root = parse(step.inputSnapshot());
        String id = root == null ? null : root.path("currentStep").path("stepId").asText(null);
        return id == null || id.isBlank() ? "recovered_step_" + step.stepNo() : id;
    }

    private String summarize(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return "前序步骤已成功执行";
        JsonNode root = parse(snapshot);
        if (root != null) {
            String summary = root.path("summary").asText(null);
            if (summary != null && !summary.isBlank()) return summary;
            String response = root.path("responseText").asText(null);
            if (response != null && !response.isBlank()) return truncate(response);
            if (root.has("currentDate") || root.has("zoneId")) return "已获取时间信息";
        }
        return truncate(snapshot);
    }

    private JsonNode parse(String json) {
        try { return json == null || json.isBlank() ? null : objectMapper.readTree(json); }
        catch (Exception ignored) { return null; }
    }

    private String truncate(String text) { return text.length() <= 120 ? text : text.substring(0, 120) + "..."; }
}

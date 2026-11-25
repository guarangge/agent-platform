package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/** 只负责 Workflow 图节点和边的确定性导航，不执行任何节点业务。 */
@Service
public class WorkflowGraphNavigator {
    /** 返回定义中的唯一 start 节点 ID。 */
    public String startNodeId(JsonNode definition) {
        JsonNode nodes = definition.path("nodes");
        if (!nodes.isArray()) return null;
        String startId = null;
        for (JsonNode node : nodes) {
            if (!"start".equals(node.path("type").asText())) continue;
            if (startId != null) return null;
            startId = node.path("id").asText(null);
        }
        return startId;
    }

    public String next(JsonNode definition, String sourceNodeId) {
        JsonNode edges = definition.path("edges");
        if (!edges.isArray()) return null;
        for (JsonNode edge : edges) {
            if (sourceNodeId.equals(edge.path("source").asText())) return edge.path("target").asText(null);
        }
        return null;
    }

    public String conditionNext(JsonNode definition, String sourceNodeId, boolean matched) {
        JsonNode edges = definition.path("edges");
        if (!edges.isArray()) return null;
        String expected = matched ? "true" : "false";
        String unlabeledFallback = null;
        for (JsonNode edge : edges) {
            if (!sourceNodeId.equals(edge.path("source").asText())) continue;
            String branch = edge.path("branch").asText(null);
            if (branch == null || branch.isBlank()) branch = edge.path("label").asText(null);
            if ((branch == null || branch.isBlank()) && unlabeledFallback == null) {
                unlabeledFallback = edge.path("target").asText(null);
            }
            if (expected.equalsIgnoreCase(branch)) return edge.path("target").asText(null);
        }
        return unlabeledFallback;
    }

    public JsonNode node(JsonNode definition, String nodeId) {
        JsonNode nodes = definition.path("nodes");
        if (!nodes.isArray()) return null;
        for (JsonNode node : nodes) if (nodeId.equals(node.path("id").asText())) return node;
        return null;
    }
}

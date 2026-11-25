package com.zc.agent.registry.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Workflow canonical graph 的发布前静态校验器。 */
@Service
public class WorkflowDefinitionValidator {
    private static final Set<String> TYPES = Set.of("start", "end", "tool", "condition", "approval",
            "reply", "clarify", "judge", "wait", "memory_write", "parallel", "join", "reflection", "handoff");

    /** 校验节点、边、分支、可达性和循环。 */
    public ValidationReport validate(JsonNode definition) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (definition == null || !definition.isObject()) {
            return new ValidationReport(false, List.of(new ValidationIssue("definition.invalid", null, "Workflow 定义必须为 JSON 对象")));
        }
        JsonNode nodesNode = definition.path("nodes");
        JsonNode edgesNode = definition.path("edges");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) issues.add(issue("nodes.required", null, "nodes 必须是非空数组"));
        if (!edgesNode.isArray()) issues.add(issue("edges.required", null, "edges 必须是数组"));
        if (!issues.isEmpty()) return new ValidationReport(false, issues);

        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        List<String> starts = new ArrayList<>();
        List<String> ends = new ArrayList<>();
        for (JsonNode node : nodesNode) {
            String id = text(node, "id");
            String type = text(node, "type");
            if (id == null) {
                issues.add(issue("node.id.required", null, "节点缺少 id；不再支持 nodeId 旧格式"));
                continue;
            }
            if (nodes.putIfAbsent(id, node) != null) issues.add(issue("node.id.duplicate", id, "节点 id 重复"));
            if (!TYPES.contains(type)) issues.add(issue("node.type.invalid", id, "节点 type 不受支持: " + type));
            if ("start".equals(type)) starts.add(id);
            if ("end".equals(type)) ends.add(id);
            if ("tool".equals(type) && text(node, "toolCode") == null) issues.add(issue("node.tool.required", id, "Tool 节点必须配置 toolCode"));
            if ("handoff".equals(type) && !node.hasNonNull("delegateAgentId") && !node.hasNonNull("targetAgentId")) {
                issues.add(issue("node.delegate.required", id, "Handoff 节点必须配置 delegateAgentId 或 targetAgentId"));
            }
        }
        if (starts.size() != 1) issues.add(issue("start.count", null, "Workflow 必须且只能有一个 start 节点"));
        if (ends.size() != 1) issues.add(issue("end.count", null, "Workflow 必须且只能有一个 end 节点"));

        Map<String, List<Edge>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Set<String> edgeKeys = new HashSet<>();
        for (JsonNode edgeNode : edgesNode) {
            String source = text(edgeNode, "source");
            String target = text(edgeNode, "target");
            String branch = firstText(edgeNode, "branch", "label");
            if (source == null || target == null) {
                issues.add(issue("edge.endpoint.required", null, "每条边必须配置 source 和 target"));
                continue;
            }
            if (!nodes.containsKey(source)) issues.add(issue("edge.source.missing", source, "边引用的 source 节点不存在"));
            if (!nodes.containsKey(target)) issues.add(issue("edge.target.missing", target, "边引用的 target 节点不存在"));
            String edgeKey = source + "->" + target + ":" + branch;
            if (!edgeKeys.add(edgeKey)) issues.add(issue("edge.duplicate", source, "存在重复边: " + edgeKey));
            outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(new Edge(target, branch));
            incoming.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source);
        }

        for (Map.Entry<String, JsonNode> entry : nodes.entrySet()) {
            String id = entry.getKey();
            String type = text(entry.getValue(), "type");
            List<Edge> edges = outgoing.getOrDefault(id, List.of());
            if ("end".equals(type) && !edges.isEmpty()) issues.add(issue("end.outgoing", id, "end 节点不能配置出边"));
            if (!"end".equals(type) && edges.isEmpty()) issues.add(issue("node.outgoing.required", id, "非 end 节点必须配置出边"));
            if ("condition".equals(type)) validateCondition(id, edges, issues);
            else if (edges.size() > 1 && !"parallel".equals(type)) issues.add(issue("node.outgoing.multiple", id, "非 condition/parallel 节点只能配置一条出边"));
        }

        if (starts.size() == 1 && nodes.containsKey(starts.get(0))) {
            Set<String> reachable = traverse(starts.get(0), outgoing);
            nodes.keySet().stream().filter(id -> !reachable.contains(id))
                    .forEach(id -> issues.add(issue("node.unreachable", id, "节点无法从 start 到达")));
        }
        Set<String> cycleVisited = new HashSet<>();
        for (String nodeId : nodes.keySet()) {
            detectCycle(nodeId, outgoing, new HashSet<>(), cycleVisited, issues);
        }
        if (ends.size() == 1) {
            Set<String> reachesEnd = reverseTraverse(ends.get(0), incoming);
            nodes.keySet().stream().filter(id -> !reachesEnd.contains(id))
                    .forEach(id -> issues.add(issue("node.no_end_path", id, "节点不存在到 end 的路径")));
        }
        return new ValidationReport(issues.isEmpty(), List.copyOf(new LinkedHashSet<>(issues)));
    }

    private void validateCondition(String id, List<Edge> edges, List<ValidationIssue> issues) {
        Set<String> branches = new HashSet<>();
        for (Edge edge : edges) if (edge.branch() != null) branches.add(edge.branch().toLowerCase());
        if (!branches.contains("true") || !branches.contains("false")) {
            issues.add(issue("condition.branches", id, "Condition 节点必须配置 true 和 false 两个明确分支"));
        }
        if (edges.size() != 2) issues.add(issue("condition.edge_count", id, "Condition 节点必须且只能配置两条出边"));
    }

    private Set<String> traverse(String start, Map<String, List<Edge>> outgoing) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();queue.add(start);
        while (!queue.isEmpty()) {String id=queue.remove();if(!visited.add(id))continue;outgoing.getOrDefault(id,List.of()).forEach(edge->queue.add(edge.target()));}
        return visited;
    }

    private Set<String> reverseTraverse(String end, Map<String, List<String>> incoming) {
        Set<String> visited = new HashSet<>();ArrayDeque<String> queue = new ArrayDeque<>();queue.add(end);
        while (!queue.isEmpty()) {String id=queue.remove();if(!visited.add(id))continue;queue.addAll(incoming.getOrDefault(id,List.of()));}
        return visited;
    }

    private boolean detectCycle(String id, Map<String, List<Edge>> outgoing, Set<String> visiting,
                                Set<String> visited, List<ValidationIssue> issues) {
        if (visiting.contains(id)) {issues.add(issue("graph.cycle", id, "Workflow 存在循环路径"));return true;}
        if (!visited.add(id)) return false;
        visiting.add(id);for (Edge edge:outgoing.getOrDefault(id,List.of())) detectCycle(edge.target(),outgoing,visiting,visited,issues);visiting.remove(id);return false;
    }

    private String text(JsonNode node, String field) {JsonNode value=node.get(field);if(value==null||!value.isTextual()||value.asText().isBlank())return null;return value.asText().trim();}
    private String firstText(JsonNode node,String first,String second){String value=text(node,first);return value==null?text(node,second):value;}
    private ValidationIssue issue(String code,String nodeId,String message){return new ValidationIssue(code,nodeId,message);}
    private record Edge(String target,String branch){}
    public record ValidationIssue(String code,String nodeId,String message){}
    public record ValidationReport(boolean valid,List<ValidationIssue> issues){}
}

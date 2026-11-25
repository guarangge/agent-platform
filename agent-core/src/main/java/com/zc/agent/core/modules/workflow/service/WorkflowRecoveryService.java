package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow 恢复规则服务。
 *
 * 负责恢复前的节点一致性校验，以及恢复执行元数据的统一组装。
 */
@Service
public class WorkflowRecoveryService {

    /**
     * 校验 Workflow 恢复上下文是否仍然可用。
     *
     * @param workflowSnapshot 当前 Workflow 快照
     * @param resumeContext    恢复上下文
     */
    public void validateResumeContext(RegistryWorkflowSnapshot workflowSnapshot,
                                      WorkflowResumeContext resumeContext) {
        if (resumeContext == null) {
            return;
        }
        String resumeNodeId = resumeContext.resumeNodeId();
        if (resumeNodeId == null || resumeNodeId.isBlank()) {
            throw new BusinessException("Workflow 恢复缺少 resumeNodeId");
        }
        JsonNode resumeNode = findNodeById(workflowSnapshot.definitionJson(), resumeNodeId);
        if (resumeNode == null) {
            throw new BusinessException("Workflow 当前已不存在恢复目标节点，无法继续恢复");
        }
        if ("start".equals(resumeNode.path("type").asText())) {
            throw new BusinessException("Workflow 恢复目标节点不能为 start");
        }
        for (WorkflowResumeContext.WorkflowResumeNode previousNode : resumeContext.previousNodes()) {
            if (previousNode.nodeId() == null || previousNode.nodeId().isBlank()) {
                continue;
            }
            if (findNodeById(workflowSnapshot.definitionJson(), previousNode.nodeId()) == null) {
                throw new BusinessException("Workflow 恢复上下文中的前序节点已不存在，无法继续恢复");
            }
            if (resumeNodeId.equals(previousNode.nodeId())) {
                throw new BusinessException("Workflow 恢复上下文与恢复目标节点冲突，无法继续恢复");
            }
        }
    }

    /**
     * 组装 Workflow 恢复元数据。
     *
     * @param workflowSnapshot 当前 Workflow 快照
     * @param resumeContext    恢复上下文
     * @param resumeMode       恢复模式
     * @return 恢复元数据
     */
    public Map<String, Object> buildRecoverySnapshot(RegistryWorkflowSnapshot workflowSnapshot,
                                                     WorkflowResumeContext resumeContext,
                                                     String resumeMode) {
        if (resumeContext == null) {
            return null;
        }
        JsonNode resumeNode = findNodeById(workflowSnapshot.definitionJson(), resumeContext.resumeNodeId());
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resumeMode", resumeMode == null || resumeMode.isBlank() ? resumeContext.resumeMode() : resumeMode);
        snapshot.put("resumeNodeId", resumeContext.resumeNodeId());
        snapshot.put("resumeNodeType", resumeNode == null ? null : resumeNode.path("type").asText(null));
        snapshot.put("replayedNodeCount", resumeContext.previousNodes().size());
        snapshot.put("executedNodeCountBeforeResume", resumeContext.executedNodes().size());
        return snapshot;
    }

    private JsonNode findNodeById(JsonNode definitionJson, String nodeId) {
        JsonNode nodes = definitionJson.path("nodes");
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (nodeId.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }
}

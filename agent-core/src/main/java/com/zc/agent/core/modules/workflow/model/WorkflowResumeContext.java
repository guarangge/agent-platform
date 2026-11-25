package com.zc.agent.core.modules.workflow.model;

import java.util.List;
import java.util.Map;

/**
 * Workflow 恢复执行上下文。
 *
 * @param resumeNodeId   恢复起始节点 ID
 * @param resumeMode     恢复模式
 * @param executedNodes  已执行节点快照
 * @param previousNodes  已成功节点摘要
 */
public record WorkflowResumeContext(
        String resumeNodeId,
        String resumeMode,
        List<Map<String, Object>> executedNodes,
        List<WorkflowResumeNode> previousNodes
) {

    /**
     * Workflow 恢复节点摘要。
     *
     * @param nodeId       节点 ID
     * @param nodeType     节点类型
     * @param summary      节点摘要
     * @param responseText 节点响应文本
     */
    public record WorkflowResumeNode(
            String nodeId,
            String nodeType,
            String summary,
            String responseText
    ) {
    }
}

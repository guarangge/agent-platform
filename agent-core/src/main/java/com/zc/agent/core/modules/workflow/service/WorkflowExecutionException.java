package com.zc.agent.core.modules.workflow.service;

import com.zc.agent.core.common.exception.BusinessException;

import java.util.List;
import java.util.Map;

/**
 * Workflow 执行异常，携带最小恢复所需的节点上下文。
 */
public class WorkflowExecutionException extends BusinessException {

    private final String failedNodeId;
    private final String failedNodeType;
    private final List<Map<String, Object>> executedNodes;
    private final List<Map<String, Object>> previousNodeResults;

    public WorkflowExecutionException(String message,
                                      Throwable cause,
                                      String failedNodeId,
                                      String failedNodeType,
                                      List<Map<String, Object>> executedNodes,
                                      List<Map<String, Object>> previousNodeResults) {
        super(message, cause);
        this.failedNodeId = failedNodeId;
        this.failedNodeType = failedNodeType;
        this.executedNodes = executedNodes;
        this.previousNodeResults = previousNodeResults;
    }

    public String failedNodeId() {
        return failedNodeId;
    }

    public String failedNodeType() {
        return failedNodeType;
    }

    public List<Map<String, Object>> executedNodes() {
        return executedNodes;
    }

    public List<Map<String, Object>> previousNodeResults() {
        return previousNodeResults;
    }
}

package com.zc.agent.core.modules.workflow.service;

import com.zc.agent.core.common.exception.BusinessException;

import java.util.List;
import java.util.Map;

/**
 * Workflow 审批节点挂起异常。
 */
public class ApprovalRequiredException extends BusinessException {

    private final String approvalNodeId;
    private final String approvalNodeName;
    private final String nextNodeId;
    private final List<Map<String, Object>> executedNodes;
    private final List<Map<String, Object>> previousNodeResults;
    private final String approvalSourceType;
    private final Integer nextPlanStepNo;
    private final Map<String, Object> approvalMetadata;

    public ApprovalRequiredException(String message,
                                     String approvalNodeId,
                                     String approvalNodeName,
                                     String nextNodeId,
                                     List<Map<String, Object>> executedNodes,
                                     List<Map<String, Object>> previousNodeResults) {
        this(message, approvalNodeId, approvalNodeName, nextNodeId, executedNodes, previousNodeResults, "workflow", null, Map.of());
    }

    public ApprovalRequiredException(String message,
                                     String approvalNodeId,
                                     String approvalNodeName,
                                     String nextNodeId,
                                     List<Map<String, Object>> executedNodes,
                                     List<Map<String, Object>> previousNodeResults,
                                     String approvalSourceType,
                                     Integer nextPlanStepNo) {
        this(message, approvalNodeId, approvalNodeName, nextNodeId, executedNodes, previousNodeResults, approvalSourceType, nextPlanStepNo, Map.of());
    }

    public ApprovalRequiredException(String message,
                                     String approvalNodeId,
                                     String approvalNodeName,
                                     String nextNodeId,
                                     List<Map<String, Object>> executedNodes,
                                     List<Map<String, Object>> previousNodeResults,
                                     String approvalSourceType,
                                     Integer nextPlanStepNo,
                                     Map<String, Object> approvalMetadata) {
        super(message);
        this.approvalNodeId = approvalNodeId;
        this.approvalNodeName = approvalNodeName;
        this.nextNodeId = nextNodeId;
        this.executedNodes = executedNodes;
        this.previousNodeResults = previousNodeResults;
        this.approvalSourceType = approvalSourceType;
        this.nextPlanStepNo = nextPlanStepNo;
        this.approvalMetadata = approvalMetadata == null ? Map.of() : Map.copyOf(approvalMetadata);
    }

    public String approvalNodeId() {
        return approvalNodeId;
    }

    public String approvalNodeName() {
        return approvalNodeName;
    }

    public String nextNodeId() {
        return nextNodeId;
    }

    public List<Map<String, Object>> executedNodes() {
        return executedNodes;
    }

    public List<Map<String, Object>> previousNodeResults() {
        return previousNodeResults;
    }

    public String approvalSourceType() {
        return approvalSourceType;
    }

    public Integer nextPlanStepNo() {
        return nextPlanStepNo;
    }

    public Map<String, Object> approvalMetadata() {
        return approvalMetadata;
    }
}

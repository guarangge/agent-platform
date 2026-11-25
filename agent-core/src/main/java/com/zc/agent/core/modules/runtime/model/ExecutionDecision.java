package com.zc.agent.core.modules.runtime.model;

import java.util.List;

/**
 * 一次执行的决策结果。
 *
 * @param decisionType      决策类型
 * @param reason            决策原因
 * @param selectedTools     选中的 Tool 列表
 * @param selectedWorkflows 选中的 Workflow 列表
 */
public record ExecutionDecision(
        String decisionType,
        String reason,
        List<SelectedTool> selectedTools,
        List<SelectedWorkflow> selectedWorkflows
) {

    /**
     * 判断是否走 Tool 执行。
     *
     * @return 是否走 Tool
     */
    public boolean isToolDecision() {
        return "tool".equals(decisionType);
    }

    /**
     * 判断是否走 Hybrid 执行。
     *
     * @return 是否走 Hybrid
     */
    public boolean isHybridDecision() {
        return "hybrid".equals(decisionType);
    }

    /**
     * 判断是否走 Workflow 执行。
     *
     * @return 是否走 Workflow
     */
    public boolean isWorkflowDecision() {
        return "workflow".equals(decisionType);
    }

    /**
     * 判断是否包含 Tool。
     *
     * @return 是否包含 Tool
     */
    public boolean hasToolSelection() {
        return selectedTools != null && !selectedTools.isEmpty();
    }

    /**
     * 判断是否包含 Workflow。
     *
     * @return 是否包含 Workflow
     */
    public boolean hasWorkflowSelection() {
        return selectedWorkflows != null && !selectedWorkflows.isEmpty();
    }

    /**
     * 返回首个 Workflow 名称。
     *
     * @return Workflow 名称
     */
    public String firstWorkflowName() {
        if (!hasWorkflowSelection()) {
            return null;
        }
        return selectedWorkflows.get(0).workflowName();
    }

    /**
     * 已选 Tool。
     *
     * @param toolCode Tool 编码
     * @param toolName Tool 名称
     */
    public record SelectedTool(
            String toolCode,
            String toolName
    ) {
    }

    /**
     * 已选 Workflow。
     *
     * @param workflowId   Workflow ID
     * @param workflowName Workflow 名称
     */
    public record SelectedWorkflow(
            Long workflowId,
            String workflowName
    ) {
    }
}

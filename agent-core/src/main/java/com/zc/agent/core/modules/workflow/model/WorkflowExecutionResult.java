package com.zc.agent.core.modules.workflow.model;

/**
 * Workflow 执行结果。
 *
 * @param workflowCode     Workflow 编码
 * @param workflowName     Workflow 名称
 * @param summary          执行摘要
 * @param responseText     返回文本
 * @param outputSnapshot   输出快照 JSON
 */
public record WorkflowExecutionResult(
        String workflowCode,
        String workflowName,
        String summary,
        String responseText,
        String outputSnapshot
) {
}

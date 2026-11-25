package com.zc.agent.core.modules.workflow.model;

import java.util.List;
import java.util.Map;

/**
 * Workflow 节点执行上下文。
 *
 * @param nodeId              节点 ID
 * @param nodeType            节点类型
 * @param nodeName            节点名称
 * @param inputText           当前节点完整输入文本
 * @param executionInputText  当前节点精简执行输入
 * @param memoryStrategy      当前节点记忆策略
 * @param selectedMemoryItems 当前节点选中的记忆项
 * @param previousNodeResults 前序节点摘要
 * @param runtimeVariables    当前 Workflow 运行时变量快照
 */
public record WorkflowNodeExecutionContext(
        String nodeId,
        String nodeType,
        String nodeName,
        String inputText,
        String executionInputText,
        String memoryStrategy,
        List<String> selectedMemoryItems,
        List<Map<String, Object>> previousNodeResults,
        Map<String, Object> runtimeVariables
) {
}

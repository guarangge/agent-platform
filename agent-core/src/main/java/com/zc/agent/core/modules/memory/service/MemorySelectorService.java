package com.zc.agent.core.modules.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory 选择服务。
 */
@Service
public class MemorySelectorService {

    private final MemoryItemCollectorService memoryItemCollectorService;

    public MemorySelectorService(MemoryItemCollectorService memoryItemCollectorService) {
        this.memoryItemCollectorService = memoryItemCollectorService;
    }

    /**
     * 为 direct reply 选择需要注入的记忆。
     *
     * @param executionContext 运行时上下文
     * @return 选中的记忆
     */
    public SelectedMemory selectForDirectReply(ExecutionContext executionContext) {
        MemoryItemCollectorService.CollectedMemoryItems memoryItems = memoryItemCollectorService.collect(executionContext);
        ArrayList<String> selectedItems = new ArrayList<>();
        appendIfPresent(selectedItems, memoryItems.conversationSummary());
        appendIfPresent(selectedItems, memoryItems.userPreference());
        appendIfPresent(selectedItems, memoryItems.userGoal());
        if (containsContinuationIntent(executionContext.userMessage())) {
            appendIfPresent(selectedItems, memoryItems.recentTaskSummary());
            appendIfPresent(selectedItems, memoryItems.recentKeyFindings());
        }
        return new SelectedMemory("direct_reply_context_memory", selectedItems);
    }

    /**
     * 为 Tool 执行选择需要注入的记忆。
     *
     * @param executionContext 运行时上下文
     * @param targetToolCode   目标 Tool 编码
     * @return 选中的记忆
     */
    public SelectedMemory selectForTool(ExecutionContext executionContext, String targetToolCode) {
        MemoryItemCollectorService.CollectedMemoryItems memoryItems = memoryItemCollectorService.collect(executionContext);
        ArrayList<String> selectedItems = new ArrayList<>();
        RegistryAgentTool targetTool = executionContext.availableTools().stream()
                .filter(item -> targetToolCode.equals(item.toolCode()))
                .findFirst()
                .orElse(null);
        if (targetTool == null) {
            appendGenericToolMemory(selectedItems, memoryItems);
            return new SelectedMemory("tool_memory_generic", selectedItems);
        }
        String memoryStrategy = resolveToolMemoryStrategy(targetTool);
        if ("system_minimal".equals(memoryStrategy)) {
            appendIfPresent(selectedItems, memoryItems.userPreference());
            appendIfPresent(selectedItems, memoryItems.conversationSummary());
            return new SelectedMemory("tool_memory_system_minimal", selectedItems);
        }
        if ("search_context".equals(memoryStrategy)) {
            appendIfPresent(selectedItems, memoryItems.conversationSummary());
            appendIfPresent(selectedItems, memoryItems.recentTaskSummary());
            appendIfPresent(selectedItems, memoryItems.recentKeyFindings());
            appendIfPresent(selectedItems, memoryItems.userAttention());
            appendIfPresent(selectedItems, memoryItems.userGoal());
            return new SelectedMemory("tool_memory_search_context", selectedItems);
        }
        if ("analysis_context".equals(memoryStrategy)) {
            appendIfPresent(selectedItems, memoryItems.userFact());
            appendIfPresent(selectedItems, memoryItems.userGoal());
            appendIfPresent(selectedItems, memoryItems.userPreference());
            appendIfPresent(selectedItems, memoryItems.recentTaskSummary());
            appendIfPresent(selectedItems, memoryItems.recentKeyFindings());
            return new SelectedMemory("tool_memory_analysis_context", selectedItems);
        }
        appendGenericToolMemory(selectedItems, memoryItems);
        return new SelectedMemory("tool_memory_generic", selectedItems);
    }

    /**
     * 为 Workflow 节点执行选择需要注入的记忆。
     *
     * @param executionContext 运行时上下文
     * @param workflowId       Workflow ID
     * @return 选中的记忆
     */
    public SelectedMemory selectForWorkflow(ExecutionContext executionContext, Long workflowId) {
        MemoryItemCollectorService.CollectedMemoryItems memoryItems = memoryItemCollectorService.collect(executionContext);
        ArrayList<String> selectedItems = new ArrayList<>();
        RegistryAgentWorkflow targetWorkflow = executionContext.availableWorkflows().stream()
                .filter(item -> workflowId.equals(item.workflowId()))
                .findFirst()
                .orElse(null);
        String memoryStrategy = resolveWorkflowMemoryStrategy(targetWorkflow);
        if ("minimal".equals(memoryStrategy)) {
            appendIfPresent(selectedItems, memoryItems.conversationSummary());
            appendIfPresent(selectedItems, memoryItems.userGoal());
            return new SelectedMemory("workflow_memory_minimal", selectedItems);
        }
        appendIfPresent(selectedItems, memoryItems.conversationSummary());
        appendIfPresent(selectedItems, memoryItems.userGoal());
        appendIfPresent(selectedItems, memoryItems.userAttention());
        appendIfPresent(selectedItems, memoryItems.recentTaskSummary());
        appendIfPresent(selectedItems, memoryItems.recentKeyFindings());
        return new SelectedMemory("workflow_memory_guided", selectedItems);
    }

    private void appendIfPresent(List<String> selectedItems, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        selectedItems.add(value);
    }

    private boolean containsContinuationIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        return userMessage.contains("继续")
                || userMessage.contains("刚才")
                || userMessage.contains("上一轮")
                || userMessage.contains("上一步")
                || userMessage.contains("之前");
    }

    private void appendGenericToolMemory(List<String> selectedItems,
                                         MemoryItemCollectorService.CollectedMemoryItems memoryItems) {
        appendIfPresent(selectedItems, memoryItems.userPreference());
        appendIfPresent(selectedItems, memoryItems.userAttention());
        appendIfPresent(selectedItems, memoryItems.recentTaskSummary());
        appendIfPresent(selectedItems, memoryItems.recentKeyFindings());
        appendIfPresent(selectedItems, memoryItems.conversationSummary());
    }

    private String resolveToolMemoryStrategy(RegistryAgentTool tool) {
        String metadataStrategy = metadataStrategy(tool.metadata());
        if (!metadataStrategy.isBlank()) {
            return metadataStrategy;
        }
        return "generic";
    }

    private String resolveWorkflowMemoryStrategy(RegistryAgentWorkflow workflow) {
        if (workflow == null) {
            return "guided";
        }
        String metadataStrategy = metadataStrategy(workflow.metadata());
        if (!metadataStrategy.isBlank()) {
            return metadataStrategy;
        }
        return "guided";
    }

    private String metadataStrategy(JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return "";
        }
        JsonNode memoryNode = metadata.path("memory");
        String nestedStrategy = memoryNode.path("strategy").asText("");
        if (!nestedStrategy.isBlank()) {
            return nestedStrategy.trim();
        }
        String flatStrategy = metadata.path("memoryStrategy").asText("");
        return flatStrategy.isBlank() ? "" : flatStrategy.trim();
    }

    /**
     * 选中的记忆结果。
     *
     * @param strategy      选择策略标识
     * @param selectedItems 选中的记忆文本项
     */
    public record SelectedMemory(
            String strategy,
            List<String> selectedItems
    ) {
    }
}

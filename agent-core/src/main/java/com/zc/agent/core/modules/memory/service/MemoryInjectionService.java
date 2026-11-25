package com.zc.agent.core.modules.memory.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Memory 注入服务。
 */
@Service
public class MemoryInjectionService {

    private final MemorySelectorService memorySelectorService;
    private final MemoryInjectionPolicyService memoryInjectionPolicyService;

    public MemoryInjectionService(MemorySelectorService memorySelectorService,
                                  MemoryInjectionPolicyService memoryInjectionPolicyService) {
        this.memorySelectorService = memorySelectorService;
        this.memoryInjectionPolicyService = memoryInjectionPolicyService;
    }

    /**
     * 构建 direct reply 需要使用的记忆文本。
     *
     * @param executionContext 运行时上下文
     * @return direct reply 记忆文本
     */
    public String buildDirectReplyMemoryText(ExecutionContext executionContext) {
        return resolveDirectReplyInput(executionContext).resolvedInputText();
    }

    /**
     * 解析 direct reply 的记忆注入结果。
     *
     * @param executionContext 运行时上下文
     * @return 解析后的输入结果
     */
    public ResolvedMemoryInput resolveDirectReplyInput(ExecutionContext executionContext) {
        MemorySelectorService.SelectedMemory selectedMemory = memorySelectorService.selectForDirectReply(executionContext);
        return new ResolvedMemoryInput(
                selectedMemory.strategy(),
                selectedMemory.selectedItems(),
                memoryInjectionPolicyService.buildDirectReplyInput(
                        executionContext.promptContext().promptContent(),
                        selectedMemory.selectedItems(),
                        executionContext.userMessage()
                )
        );
    }

    /**
     * 构建 Tool 执行输入文本。
     *
     * @param executionContext    运行时上下文
     * @param targetToolCode      目标 Tool 编码
     * @param previousResultLines 前序步骤摘要
     * @return Tool 输入文本
     */
    public String buildToolInputText(ExecutionContext executionContext,
                                     String targetToolCode,
                                     List<String> previousResultLines) {
        return resolveToolInput(executionContext, targetToolCode, previousResultLines).resolvedInputText();
    }

    /**
     * 解析 Tool 执行所需的记忆注入结果。
     *
     * @param executionContext    运行时上下文
     * @param targetToolCode      目标 Tool 编码
     * @param previousResultLines 前序步骤摘要
     * @return 解析后的输入结果
     */
    public ResolvedMemoryInput resolveToolInput(ExecutionContext executionContext,
                                                String targetToolCode,
                                                List<String> previousResultLines) {
        MemorySelectorService.SelectedMemory selectedMemory = memorySelectorService.selectForTool(executionContext, targetToolCode);
        return new ResolvedMemoryInput(
                selectedMemory.strategy(),
                selectedMemory.selectedItems(),
                memoryInjectionPolicyService.buildToolInput(
                        executionContext.promptContext().promptContent(),
                        selectedMemory.selectedItems(),
                        executionContext.userMessage(),
                        previousResultLines
                )
        );
    }

    /**
     * 构建 Workflow 节点执行输入文本。
     *
     * @param executionContext    运行时上下文
     * @param previousResultLines 前序节点摘要
     * @return Workflow 节点输入文本
     */
    public String buildWorkflowNodeInputText(ExecutionContext executionContext,
                                             Long workflowId,
                                             List<String> previousResultLines) {
        return resolveWorkflowInput(executionContext, workflowId, previousResultLines).resolvedInputText();
    }

    /**
     * 解析 Workflow 节点执行所需的记忆注入结果。
     *
     * @param executionContext    运行时上下文
     * @param previousResultLines 前序节点摘要
     * @return 解析后的输入结果
     */
    public ResolvedMemoryInput resolveWorkflowInput(ExecutionContext executionContext,
                                                    Long workflowId,
                                                    List<String> previousResultLines) {
        MemorySelectorService.SelectedMemory selectedMemory = memorySelectorService.selectForWorkflow(
                executionContext,
                workflowId
        );
        return new ResolvedMemoryInput(
                selectedMemory.strategy(),
                selectedMemory.selectedItems(),
                memoryInjectionPolicyService.buildWorkflowInput(
                        executionContext.promptContext().promptContent(),
                        selectedMemory.selectedItems(),
                        executionContext.userMessage(),
                        previousResultLines
                )
        );
    }

    /**
     * 解析后的记忆注入输入。
     *
     * @param memoryStrategy    记忆选择策略
     * @param selectedMemoryItems 选中的记忆文本
     * @param resolvedInputText  最终注入后的输入文本
     */
    public record ResolvedMemoryInput(
            String memoryStrategy,
            List<String> selectedMemoryItems,
            String resolvedInputText
    ) {
    }
}

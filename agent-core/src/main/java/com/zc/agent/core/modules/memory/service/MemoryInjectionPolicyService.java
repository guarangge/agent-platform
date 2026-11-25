package com.zc.agent.core.modules.memory.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Memory 注入策略服务。
 * 负责根据执行场景组装 Prompt、记忆和前序结果的注入文本，
 * 不负责决定“选哪些记忆”，只负责“如何注入这些记忆”。
 */
@Service
public class MemoryInjectionPolicyService {

    /**
     * 构建 direct reply 场景的最终输入文本。
     *
     * @param promptContent        Prompt 正文
     * @param selectedMemoryItems  选中的记忆项
     * @param userMessage          用户消息
     * @return 注入后的输入文本
     */
    public String buildDirectReplyInput(String promptContent,
                                        List<String> selectedMemoryItems,
                                        String userMessage) {
        return buildExecutionInputText(
                promptContent,
                selectedMemoryItems,
                userMessage,
                null,
                List.of()
        );
    }

    /**
     * 构建 Tool 场景的最终输入文本。
     *
     * @param promptContent        Prompt 正文
     * @param selectedMemoryItems  选中的记忆项
     * @param userMessage          用户消息
     * @param previousResultLines  前序步骤摘要
     * @return 注入后的输入文本
     */
    public String buildToolInput(String promptContent,
                                 List<String> selectedMemoryItems,
                                 String userMessage,
                                 List<String> previousResultLines) {
        return buildExecutionInputText(
                promptContent,
                selectedMemoryItems,
                userMessage,
                "前序步骤结果摘要：",
                previousResultLines
        );
    }

    /**
     * 构建 Workflow 场景的最终输入文本。
     *
     * @param promptContent        Prompt 正文
     * @param selectedMemoryItems  选中的记忆项
     * @param userMessage          用户消息
     * @param previousResultLines  前序节点摘要
     * @return 注入后的输入文本
     */
    public String buildWorkflowInput(String promptContent,
                                     List<String> selectedMemoryItems,
                                     String userMessage,
                                     List<String> previousResultLines) {
        return buildExecutionInputText(
                promptContent,
                selectedMemoryItems,
                userMessage,
                "Workflow前序节点结果：",
                previousResultLines
        );
    }

    private String buildExecutionInputText(String promptContent,
                                           List<String> selectedMemoryItems,
                                           String userMessage,
                                           String previousResultTitle,
                                           List<String> previousResultLines) {
        StringBuilder builder = new StringBuilder();
        appendPromptInstruction(builder, promptContent);
        appendSelectedMemory(builder, selectedMemoryItems);
        builder.append(userMessage);
        if (previousResultTitle == null || previousResultLines == null || previousResultLines.isEmpty()) {
            return builder.toString();
        }
        builder.append("\n\n").append(previousResultTitle);
        for (String previousResultLine : previousResultLines) {
            builder.append("\n").append(previousResultLine);
        }
        return builder.toString();
    }

    private void appendPromptInstruction(StringBuilder builder, String promptContent) {
        if (promptContent == null || promptContent.isBlank()) {
            return;
        }
        builder.append("Prompt指令：")
                .append(promptContent)
                .append("\n\n");
    }

    private void appendSelectedMemory(StringBuilder builder, List<String> selectedMemoryItems) {
        String selectedMemoryText = joinSelectedMemory(selectedMemoryItems);
        if (selectedMemoryText == null || selectedMemoryText.isBlank()) {
            return;
        }
        builder.append("相关记忆：")
                .append(selectedMemoryText)
                .append("\n\n");
    }

    private String joinSelectedMemory(List<String> selectedMemoryItems) {
        if (selectedMemoryItems == null || selectedMemoryItems.isEmpty()) {
            return null;
        }
        return String.join("\n", selectedMemoryItems);
    }
}

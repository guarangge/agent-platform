package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.protocol.service.ArtifactRuntimeEventService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 最终回复与真实产物的一致性服务。
 *
 * 结构化步骤快照是产物事实源；当模型回复与产物事实冲突时，以产物快照为准，
 * 避免出现“没有生成文件”但末尾又提供下载链接的矛盾回答。
 */
@Service
public class ArtifactResponseConsistencyService {

    private final ArtifactRuntimeEventService artifactRuntimeEventService;

    public ArtifactResponseConsistencyService(ArtifactRuntimeEventService artifactRuntimeEventService) {
        this.artifactRuntimeEventService = artifactRuntimeEventService;
    }

    /**
     * 校正最终回复中的产物事实并追加受保护的下载链接。
     *
     * @param assistantContent 模型生成的最终回复
     * @param conversationId   会话 ID
     * @param taskId           任务 ID
     * @param stepResults      已完成步骤结果
     * @return 与真实产物一致的最终回复
     */
    public String reconcileAndAppend(String assistantContent,
                                     Long conversationId,
                                     Long taskId,
                                     List<RuntimeStepResultContext> stepResults) {
        LinkedHashMap<String, ArtifactRuntimeEventService.ArtifactDescriptor> artifacts = collectArtifacts(stepResults);
        if (artifacts.isEmpty()) {
            return assistantContent;
        }
        String reconciledContent = contradictsAvailableArtifacts(assistantContent)
                ? buildArtifactReadyMessage(artifacts)
                : safeTrim(assistantContent);
        StringBuilder builder = new StringBuilder(reconciledContent);
        builder.append("\n\n### 可下载产物\n");
        artifacts.values().forEach(artifact -> appendArtifactLink(
                builder, conversationId, taskId, artifact));
        artifactRuntimeEventService.publishPublished(
                conversationId, taskId, List.copyOf(artifacts.values()));
        return builder.toString().trim();
    }

    /**
     * 兼容修正历史已落库的矛盾回复，不修改原始审计数据。
     *
     * @param assistantContent 已落库助手消息
     * @return 面向聊天窗口的一致回复
     */
    public String reconcilePersistedMessage(String assistantContent) {
        String content = safeTrim(assistantContent);
        int artifactSectionIndex = content.indexOf("### 可下载产物");
        if (artifactSectionIndex < 0 || !contradictsAvailableArtifacts(content)) {
            return assistantContent;
        }
        return "文件已成功生成，请通过下方链接下载。\n\n" + content.substring(artifactSectionIndex);
    }

    private LinkedHashMap<String, ArtifactRuntimeEventService.ArtifactDescriptor> collectArtifacts(
            List<RuntimeStepResultContext> stepResults) {
        LinkedHashMap<String, ArtifactRuntimeEventService.ArtifactDescriptor> artifacts = new LinkedHashMap<>();
        for (RuntimeStepResultContext stepResult : stepResults) {
            artifactRuntimeEventService.extractArtifacts(stepResult.outputSnapshot())
                    .forEach(artifact -> artifacts.putIfAbsent(artifact.path(), artifact));
        }
        return artifacts;
    }

    private boolean contradictsAvailableArtifacts(String assistantContent) {
        String normalized = safeTrim(assistantContent)
                .replaceAll("\\s+", "")
                .replace("*", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("没能把可下载")
                || normalized.contains("对外可下载的pdf产物|❌未生成")
                || normalized.contains("可提供的下载链接|❌没有")
                || normalized.contains("给不出下载链接")
                || normalized.contains("没有任何一个环节产出过对外可访问的下载url")
                || normalized.contains("没有任何一个环节返回过可访问url")
                || normalized.contains("no download link")
                || normalized.contains("artifact was not generated")
                || (normalized.contains("审批")
                && (normalized.contains("被拒绝") || normalized.contains("未通过"))
                && normalized.contains("任务已结束"));
    }

    private String buildArtifactReadyMessage(
            LinkedHashMap<String, ArtifactRuntimeEventService.ArtifactDescriptor> artifacts) {
        if (artifacts.size() == 1) {
            String label = artifacts.values().iterator().next().fileName();
            return "文件已成功生成：`" + label + "`。请通过下方链接下载。";
        }
        return "已成功生成 " + artifacts.size() + " 个文件，请通过下方链接下载。";
    }

    private void appendArtifactLink(StringBuilder builder,
                                    Long conversationId,
                                    Long taskId,
                                    ArtifactRuntimeEventService.ArtifactDescriptor artifact) {
        builder.append("- [下载 ").append(artifact.fileName()).append("](")
                .append(artifactRuntimeEventService.buildDownloadUrl(
                        conversationId, taskId, artifact.path()))
                .append(")");
        if (artifact.size() > 0) {
            builder.append("（").append(artifact.size()).append(" bytes）");
        }
        builder.append('\n');
    }

    private String safeTrim(String content) {
        return content == null ? "" : content.trim();
    }
}

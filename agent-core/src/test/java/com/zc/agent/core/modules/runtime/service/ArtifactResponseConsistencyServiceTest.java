package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.protocol.service.ArtifactRuntimeEventService;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArtifactResponseConsistencyServiceTest {

    private final ArtifactResponseConsistencyService service =
            new ArtifactResponseConsistencyService(new ArtifactRuntimeEventService(
                    new ObjectMapper(), mock(RuntimeEventPublisher.class)));

    @Test
    void shouldReplaceContradictoryReplyWhenArtifactExists() {
        String contradictoryReply = """
                # 最终回复
                抱歉，这次仍然没能把可下载的 PDF 交付给你。
                | 对外可下载的 PDF 产物 | ❌ **未生成** |
                | 可提供的下载链接 | ❌ **没有** |
                """;

        String response = service.reconcileAndAppend(
                contradictoryReply, 52L, 126L, List.of(artifactResult("ai-learning-plan.pdf", 116813L)));

        assertThat(response)
                .contains("文件已成功生成：`ai-learning-plan.pdf`")
                .contains("/api/core/conversations/52/tasks/126/artifacts?path=ai-learning-plan.pdf")
                .contains("116813 bytes")
                .doesNotContain("未生成")
                .doesNotContain("没有");
    }

    @Test
    void shouldPreserveConsistentBusinessReplyAndAppendArtifactLink() {
        String response = service.reconcileAndAppend(
                "AI 自学路线 PDF 已完成。",
                52L,
                126L,
                List.of(artifactResult("AI 学习计划.pdf", 1024L))
        );

        assertThat(response)
                .startsWith("AI 自学路线 PDF 已完成。")
                .contains("下载 AI 学习计划.pdf")
                .contains("path=AI%20%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92.pdf");
    }

    @Test
    void shouldKeepReplyUnchangedWhenNoArtifactExists() {
        String response = service.reconcileAndAppend("尚未生成文件。", 52L, 126L, List.of());

        assertThat(response).isEqualTo("尚未生成文件。");
    }

    @Test
    void shouldReconcileHistoricalContradictoryMessageForChatView() {
        String historicalMessage = """
                # 最终回复
                我给不出下载链接。

                ### 可下载产物
                - [下载 ai-learning-plan.pdf](/api/core/conversations/52/tasks/126/artifacts?path=ai-learning-plan.pdf)
                """;

        String response = service.reconcilePersistedMessage(historicalMessage);

        assertThat(response)
                .startsWith("文件已成功生成，请通过下方链接下载。")
                .contains("下载 ai-learning-plan.pdf")
                .doesNotContain("给不出下载链接");
    }

    private RuntimeStepResultContext artifactResult(String path, Long size) {
        String outputSnapshot = "{\"payload\":{\"artifacts\":[{\"path\":\""
                + path + "\",\"size\":" + size + ",\"contentBase64\":\"ignored\"}]}}";
        return new RuntimeStepResultContext(
                1, "tool_1", "tool", "生成文件", "执行成功", "文件已生成", outputSnapshot);
    }
}

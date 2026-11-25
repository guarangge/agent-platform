package com.zc.agent.core.modules.runtime.protocol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ArtifactRuntimeEventServiceTest {

    @AfterEach
    void clearRunContext() {
        AgentRunStreamingContext.clear();
    }

    @Test
    void shouldPublishCreatedAndPublishedWithStableArtifactId() {
        RuntimeEventPublisher publisher = mock(RuntimeEventPublisher.class);
        ArtifactRuntimeEventService service = new ArtifactRuntimeEventService(new ObjectMapper(), publisher);
        String outputSnapshot = """
                {"payload":{"artifacts":[{"path":"AI 学习计划.pdf","size":2048,"contentBase64":"JVBERg=="}]}}
                """;
        AgentRunStreamingContext.bind("RUN_1");

        service.publishCreated(126L, 31L, outputSnapshot);
        List<ArtifactRuntimeEventService.ArtifactDescriptor> artifacts = service.extractArtifacts(outputSnapshot);
        service.publishPublished(52L, 126L, artifacts);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> createdPayload = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> publishedPayload = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.ARTIFACT_CREATED),
                createdPayload.capture(), eq(Map.of()));
        verify(publisher).publishPayload(eq("RUN_1"), eq(RuntimeEventType.ARTIFACT_PUBLISHED),
                publishedPayload.capture(), eq(Map.of()));

        assertThat(createdPayload.getValue())
                .containsEntry("taskStepId", 31L)
                .containsEntry("fileName", "AI 学习计划.pdf")
                .containsEntry("mediaType", "application/pdf")
                .containsEntry("size", 2048L)
                .containsEntry("status", "created")
                .doesNotContainKeys("contentBase64", "downloadUrl");
        assertThat(publishedPayload.getValue())
                .containsEntry("artifactId", createdPayload.getValue().get("artifactId"))
                .containsEntry("status", "published")
                .containsEntry("downloadable", true)
                .containsEntry("downloadUrl",
                        "/api/core/conversations/52/tasks/126/artifacts?path=AI%20%E5%AD%A6%E4%B9%A0%E8%AE%A1%E5%88%92.pdf")
                .doesNotContainKey("contentBase64");
    }
}

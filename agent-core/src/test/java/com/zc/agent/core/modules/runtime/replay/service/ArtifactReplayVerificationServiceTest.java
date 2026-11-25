package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactReplayVerificationServiceTest {

    private final ArtifactReplayVerificationService service = new ArtifactReplayVerificationService();

    @Test
    void shouldVerifyContentHashAndArtifactEventMetadata() {
        RecordedStepResult stepResult = stepResult(5L, Base64.getEncoder().encodeToString("hello".getBytes()));
        var snapshotCapture = service.capture(20L, List.of(), List.of(stepResult));
        var artifact = snapshotCapture.recordedArtifacts().get(0);
        Map<String, Object> payload = Map.of(
                "artifactId", artifact.artifactId(),
                "path", artifact.path(),
                "fileName", artifact.fileName(),
                "size", 5L,
                "mediaType", artifact.mediaType()
        );

        var capture = service.capture(20L, List.of(
                event(3L, "artifact.created", payload),
                event(4L, "artifact.published", payload)
        ), List.of(stepResult));

        assertThat(capture.report().consistent()).isTrue();
        assertThat(capture.report().summary().artifactEventCount()).isEqualTo(2);
        assertThat(capture.report().entries()).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo("verified");
            assertThat(entry.actualSize()).isEqualTo(5L);
            assertThat(entry.contentSha256())
                    .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
            assertThat(entry.sourceEventSequences()).containsExactly(3L, 4L);
        });
    }

    @Test
    void shouldReportSnapshotAndEventSizeMismatch() {
        RecordedStepResult stepResult = stepResult(4L, Base64.getEncoder().encodeToString("hello".getBytes()));
        var artifact = service.capture(20L, List.of(), List.of(stepResult)).recordedArtifacts().get(0);
        RuntimeEventEnvelope event = event(3L, "artifact.created", Map.of(
                "artifactId", artifact.artifactId(), "path", artifact.path(),
                "fileName", artifact.fileName(), "size", 4L, "mediaType", artifact.mediaType()
        ));

        var capture = service.capture(20L, List.of(event), List.of(stepResult));

        assertThat(capture.report().consistent()).isFalse();
        assertThat(capture.report().entries()).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo("metadata_mismatch");
            assertThat(entry.issues()).contains("snapshot_size_mismatch", "event_size_mismatch@3");
        });
    }

    @Test
    void shouldFailWhenArtifactContentSnapshotIsMissing() {
        RecordedStepResult result = stepResult(5L, "");

        assertThatThrownBy(() -> service.capture(20L, List.of(), List.of(result)))
                .hasMessage("Simulation Replay Artifact 内容快照缺失: report.txt");
    }

    @Test
    void shouldFailWhenArtifactEventHasNoRecordedContent() {
        RuntimeEventEnvelope event = event(1L, "artifact.created", Map.of("path", "missing.pdf"));

        assertThatThrownBy(() -> service.capture(20L, List.of(event), List.of()))
                .hasMessage("Simulation Replay Artifact Event 缺少内容快照: missing.pdf");
    }

    private RecordedStepResult stepResult(long size, String contentBase64) {
        Map<String, Object> artifact = Map.of(
                "path", "report.txt", "size", size, "contentBase64", contentBase64);
        return new RecordedStepResult(
                100L, 1, "tool", "生成报告", "tool",
                Map.of("resultType", "tool", "payload", Map.of("artifacts", List.of(artifact))),
                "step-hash"
        );
    }

    private RuntimeEventEnvelope event(Long sequenceNo, String eventType, Map<String, Object> payload) {
        return new RuntimeEventEnvelope(
                "EVT_" + sequenceNo, sequenceNo, 10L, null, "RUN_1", 20L, null,
                eventType, LocalDateTime.now(), 1, payload, Map.of()
        );
    }
}

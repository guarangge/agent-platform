package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.protocol.service.ArtifactMetadataSupport;
import com.zc.agent.core.modules.runtime.replay.model.ArtifactVerificationReport;
import com.zc.agent.core.modules.runtime.replay.model.RecordedArtifact;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从冻结步骤快照校验 Artifact 内容哈希和 Runtime Event 元数据。 */
@Service
public class ArtifactReplayVerificationService {

    private static final int VERIFICATION_VERSION = 1;

    /** 提取 Artifact 内容事实并输出结构化校验报告，全程不读取工作区文件。 */
    public Capture capture(Long taskId,
                           List<RuntimeEventEnvelope> events,
                           List<RecordedStepResult> recordedStepResults) {
        LinkedHashMap<String, MutableArtifact> artifacts = extractArtifacts(taskId, recordedStepResults);
        int artifactEventCount = verifyEvents(events, artifacts);
        List<RecordedArtifact> recordedArtifacts = new ArrayList<>();
        List<ArtifactVerificationReport.Entry> entries = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int verifiedCount = 0;
        for (MutableArtifact artifact : artifacts.values()) {
            RecordedArtifact recorded = artifact.toRecordedArtifact();
            recordedArtifacts.add(recorded);
            boolean verified = artifact.issues.isEmpty();
            if (verified) verifiedCount++;
            artifact.issues.forEach(issue -> reasons.add(artifact.path + ": " + issue));
            entries.add(new ArtifactVerificationReport.Entry(
                    artifact.artifactId, artifact.taskStepId, artifact.path, artifact.fileName,
                    artifact.declaredSize, artifact.actualSize, artifact.mediaType, artifact.contentSha256,
                    verified ? artifact.eventSequences.isEmpty() ? "snapshot_verified" : "verified"
                            : "metadata_mismatch",
                    List.copyOf(artifact.eventSequences), List.copyOf(artifact.issues)
            ));
        }
        ArtifactVerificationReport report = new ArtifactVerificationReport(
                VERIFICATION_VERSION,
                reasons.isEmpty(),
                new ArtifactVerificationReport.Summary(
                        artifacts.size(), artifactEventCount, verifiedCount, artifacts.size() - verifiedCount),
                List.copyOf(entries),
                List.copyOf(reasons)
        );
        return new Capture(List.copyOf(recordedArtifacts), report);
    }

    private LinkedHashMap<String, MutableArtifact> extractArtifacts(
            Long taskId,
            List<RecordedStepResult> recordedStepResults) {
        LinkedHashMap<String, MutableArtifact> artifacts = new LinkedHashMap<>();
        for (RecordedStepResult stepResult : safeList(recordedStepResults)) {
            Map<String, Object> payload = mapValue(stepResult.outputSnapshot().get("payload"));
            Object artifactValue = payload.get("artifacts");
            if (!(artifactValue instanceof List<?> artifactItems)) {
                continue;
            }
            for (Object artifactItem : artifactItems) {
                Map<String, Object> snapshot = mapValue(artifactItem);
                String path = requireSafePath(snapshot.get("path"), stepResult.taskStepId());
                String contentBase64 = text(snapshot.get("contentBase64"));
                if (contentBase64.isBlank()) {
                    throw new BusinessException("Simulation Replay Artifact 内容快照缺失: " + path);
                }
                byte[] content = decodeContent(path, contentBase64);
                Long declaredSizeValue = longValue(snapshot.get("size"));
                long declaredSize = declaredSizeValue == null ? -1L : declaredSizeValue;
                MutableArtifact candidate = new MutableArtifact(
                        stepResult.taskStepId(), ArtifactMetadataSupport.artifactId(taskId, path), path,
                        ArtifactMetadataSupport.fileName(path), declaredSize, content.length,
                        ArtifactMetadataSupport.mediaType(path), sha256(content)
                );
                if (declaredSize < 0) {
                    candidate.issues.add("snapshot_size_missing");
                } else if (declaredSize != content.length) {
                    candidate.issues.add("snapshot_size_mismatch");
                }
                MutableArtifact existing = artifacts.putIfAbsent(path, candidate);
                if (existing != null && !existing.contentSha256.equals(candidate.contentSha256)) {
                    existing.issues.add("duplicate_snapshot_content_mismatch");
                }
            }
        }
        return artifacts;
    }

    private int verifyEvents(List<RuntimeEventEnvelope> events,
                             Map<String, MutableArtifact> artifacts) {
        int eventCount = 0;
        for (RuntimeEventEnvelope event : safeList(events)) {
            if (!"artifact.created".equals(event.eventType())
                    && !"artifact.published".equals(event.eventType())) {
                continue;
            }
            eventCount++;
            String path = text(event.payload().get("path"));
            MutableArtifact artifact = artifacts.get(path);
            if (artifact == null) {
                throw new BusinessException("Simulation Replay Artifact Event 缺少内容快照: " + path);
            }
            artifact.eventSequences.add(event.sequenceNo());
            compare(artifact, event, "artifactId", artifact.artifactId);
            compare(artifact, event, "fileName", artifact.fileName);
            compareLong(artifact, event, "size", artifact.actualSize);
            compare(artifact, event, "mediaType", artifact.mediaType);
        }
        return eventCount;
    }

    private void compare(MutableArtifact artifact,
                         RuntimeEventEnvelope event,
                         String field,
                         String expected) {
        if (!expected.equals(text(event.payload().get(field)))) {
            artifact.issues.add("event_" + field + "_mismatch@" + event.sequenceNo());
        }
    }

    private void compareLong(MutableArtifact artifact,
                             RuntimeEventEnvelope event,
                             String field,
                             long expected) {
        Long actual = longValue(event.payload().get(field));
        if (actual == null || actual != expected) {
            artifact.issues.add("event_" + field + "_mismatch@" + event.sequenceNo());
        }
    }

    private String requireSafePath(Object value, Long taskStepId) {
        String path = text(value).trim();
        if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("\\")) {
            throw new BusinessException("Simulation Replay Artifact 路径无效: taskStepId=" + taskStepId);
        }
        try {
            if (Path.of(path).isAbsolute()) {
                throw new BusinessException("Simulation Replay Artifact 路径无效: " + path);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Simulation Replay Artifact 路径无效: " + path);
        }
        return path;
    }

    private byte[] decodeContent(String path, String contentBase64) {
        try {
            return Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("Simulation Replay Artifact 内容快照损坏: " + path);
        }
    }

    private String sha256(byte[] content) {
        return HexFormat.of().formatHex(sha256Bytes(content));
    }

    private byte[] sha256Bytes(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * @param recordedArtifacts 冻结 Artifact 哈希和元数据
     * @param report 结构化校验报告
     */
    public record Capture(
            List<RecordedArtifact> recordedArtifacts,
            ArtifactVerificationReport report
    ) {
    }

    private static final class MutableArtifact {
        private final Long taskStepId;
        private final String artifactId;
        private final String path;
        private final String fileName;
        private final long declaredSize;
        private final long actualSize;
        private final String mediaType;
        private final String contentSha256;
        private final List<Long> eventSequences = new ArrayList<>();
        private final List<String> issues = new ArrayList<>();

        private MutableArtifact(Long taskStepId,
                                String artifactId,
                                String path,
                                String fileName,
                                long declaredSize,
                                long actualSize,
                                String mediaType,
                                String contentSha256) {
            this.taskStepId = taskStepId;
            this.artifactId = artifactId;
            this.path = path;
            this.fileName = fileName;
            this.declaredSize = declaredSize;
            this.actualSize = actualSize;
            this.mediaType = mediaType;
            this.contentSha256 = contentSha256;
        }

        private RecordedArtifact toRecordedArtifact() {
            return new RecordedArtifact(
                    taskStepId, artifactId, path, fileName, declaredSize, actualSize, mediaType, contentSha256);
        }
    }
}

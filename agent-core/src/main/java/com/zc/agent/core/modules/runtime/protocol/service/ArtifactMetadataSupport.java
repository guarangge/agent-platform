package com.zc.agent.core.modules.runtime.protocol.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Artifact 稳定标识和展示元数据纯函数。 */
public final class ArtifactMetadataSupport {

    private ArtifactMetadataSupport() {
    }

    /** 使用 Task 和相对路径生成稳定 Artifact ID。 */
    public static String artifactId(Long taskId, String artifactPath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (taskId + "\n" + artifactPath).getBytes(StandardCharsets.UTF_8));
            return "ARTIFACT_" + HexFormat.of().withUpperCase().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /** 从相对路径提取显示文件名。 */
    public static String fileName(String artifactPath) {
        try {
            return Path.of(artifactPath).getFileName().toString();
        } catch (Exception ignored) {
            return artifactPath;
        }
    }

    /** 按文件扩展名返回平台统一媒体类型。 */
    public static String mediaType(String artifactPath) {
        String normalized = artifactPath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".pdf")) return "application/pdf";
        if (normalized.endsWith(".json")) return "application/json";
        if (normalized.endsWith(".txt") || normalized.endsWith(".md")) return "text/plain";
        if (normalized.endsWith(".csv")) return "text/csv";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (normalized.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/octet-stream";
    }
}

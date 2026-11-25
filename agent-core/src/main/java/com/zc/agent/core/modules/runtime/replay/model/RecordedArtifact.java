package com.zc.agent.core.modules.runtime.replay.model;

/**
 * 从不可变步骤输出中提取的 Artifact 内容事实。
 *
 * @param taskStepId 来源步骤 ID
 * @param artifactId 稳定 Artifact ID
 * @param path 相对产物路径
 * @param fileName 文件名
 * @param declaredSize 快照声明字节数
 * @param actualSize Base64 解码后的实际字节数
 * @param mediaType 文件媒体类型
 * @param contentSha256 实际内容 SHA-256
 */
public record RecordedArtifact(
        Long taskStepId,
        String artifactId,
        String path,
        String fileName,
        long declaredSize,
        long actualSize,
        String mediaType,
        String contentSha256
) {
}

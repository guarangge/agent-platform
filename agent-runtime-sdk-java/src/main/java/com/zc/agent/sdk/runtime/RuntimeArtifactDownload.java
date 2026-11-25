package com.zc.agent.sdk.runtime;

/**
 * Artifact 下载结果。
 *
 * @param data 文件字节
 * @param fileName UTF-8 文件名
 * @param contentType 内容类型
 * @param size 文件大小
 */
public record RuntimeArtifactDownload(
        byte[] data,
        String fileName,
        String contentType,
        long size
) {
}

package com.zc.agent.core.modules.conversation.vo;

/**
 * Task 执行产物下载内容。
 *
 * @param filename    文件名
 * @param contentType MIME 类型
 * @param content     文件内容
 */
public record TaskArtifactDownload(
        String filename,
        String contentType,
        byte[] content
) {
}

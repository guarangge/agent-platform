package com.zc.agent.core.modules.runtime.replay.model;

import java.util.List;

/**
 * Simulation Replay Artifact 内容与事件元数据校验结果。
 *
 * @param verificationVersion 校验契约版本
 * @param consistent 内容和元数据是否一致
 * @param summary 校验计数
 * @param entries 逐 Artifact 校验项
 * @param inconsistencyReasons 不一致原因
 */
public record ArtifactVerificationReport(
        int verificationVersion,
        boolean consistent,
        Summary summary,
        List<Entry> entries,
        List<String> inconsistencyReasons
) {

    /**
     * @param artifactCount 冻结 Artifact 数
     * @param artifactEventCount 来源 Artifact Event 数
     * @param verifiedCount 无不一致项数量
     * @param mismatchCount 存在不一致项数量
     */
    public record Summary(
            int artifactCount,
            int artifactEventCount,
            int verifiedCount,
            int mismatchCount
    ) {
    }

    /**
     * @param artifactId 稳定 Artifact ID
     * @param taskStepId 来源步骤 ID
     * @param path 相对路径
     * @param fileName 文件名
     * @param declaredSize 快照声明字节数
     * @param actualSize 实际解码字节数
     * @param mediaType 媒体类型
     * @param contentSha256 内容 SHA-256
     * @param status `verified / snapshot_verified / metadata_mismatch`
     * @param sourceEventSequences 关联来源事件序号
     * @param issues 不一致项
     */
    public record Entry(
            String artifactId,
            Long taskStepId,
            String path,
            String fileName,
            long declaredSize,
            long actualSize,
            String mediaType,
            String contentSha256,
            String status,
            List<Long> sourceEventSequences,
            List<String> issues
    ) {
    }
}

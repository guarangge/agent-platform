package com.zc.agent.sdk.runtime;

/**
 * Runtime Event 进度投影。
 *
 * @param stage 执行阶段
 * @param message 进度说明
 * @param sequenceNo 事件序号
 * @param stepNo 步骤序号
 * @param stepCount 总步骤数
 * @param stepType 步骤类型
 */
public record RuntimeEventProgress(
        String stage,
        String message,
        long sequenceNo,
        Integer stepNo,
        Integer stepCount,
        String stepType
) {
}

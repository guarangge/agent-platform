package com.zc.agent.core.modules.runtime.replay.model;

import java.util.Map;

/**
 * Simulation Replay 使用的已录制步骤结果。
 *
 * @param taskStepId 原步骤 ID
 * @param stepNo 原步骤序号
 * @param stepType 原步骤类型
 * @param stepName 原步骤名称
 * @param resultType 标准结果类型
 * @param outputSnapshot 已冻结的结构化输出快照
 * @param outputSha256 输出快照 SHA-256
 */
public record RecordedStepResult(
        Long taskStepId,
        Integer stepNo,
        String stepType,
        String stepName,
        String resultType,
        Map<String, Object> outputSnapshot,
        String outputSha256
) {
}

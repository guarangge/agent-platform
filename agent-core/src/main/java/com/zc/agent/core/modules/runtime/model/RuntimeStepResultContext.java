package com.zc.agent.core.modules.runtime.model;

/**
 * 跨 Kernel、执行服务和恢复链传递的标准步骤结果上下文。
 *
 * @param stepNo 步骤序号
 * @param stepId 稳定步骤 ID
 * @param stepType 步骤类型
 * @param stepName 步骤名称
 * @param summary 结果摘要
 * @param responseText 可面向用户的响应文本
 * @param outputSnapshot 标准输出快照 JSON
 */
public record RuntimeStepResultContext(
        Integer stepNo,
        String stepId,
        String stepType,
        String stepName,
        String summary,
        String responseText,
        String outputSnapshot
) {
}

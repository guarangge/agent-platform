package com.zc.agent.core.modules.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * 统一步骤输出快照。
 *
 * @param resultType      结果类型
 * @param assistantMessage 面向用户的输出文本
 * @param responseText    步骤响应文本
 * @param summary         步骤摘要
 * @param observation     当前步骤观察结论
 * @param confidence      当前结论置信度
 * @param needReplan      是否建议重规划
 * @param needMoreInfo    是否需要补充信息
 * @param nextActionHint  下一步建议
 * @param evidence        关键依据
 * @param structuredData  归一化结构化结果
 * @param payload         原始结果载荷
 * @param metadata        扩展元数据
 */
public record StandardStepOutputSnapshot(
        String resultType,
        String assistantMessage,
        String responseText,
        String summary,
        String observation,
        Double confidence,
        Boolean needReplan,
        Boolean needMoreInfo,
        String nextActionHint,
        List<Map<String, Object>> evidence,
        Object structuredData,
        Object payload,
        Map<String, Object> metadata
) {
}

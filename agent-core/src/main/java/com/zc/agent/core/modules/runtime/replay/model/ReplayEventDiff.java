package com.zc.agent.core.modules.runtime.replay.model;

import java.util.List;

/**
 * Simulation Replay 的结构化事实和结果差异。
 *
 * @param diffVersion Diff 契约版本
 * @param consistent 来源事实与 Replay 结果是否一致
 * @param summary 事实对比计数
 * @param resultDiff 来源消息与 Replay 消息差异
 * @param entries 逐事实差异项
 * @param inconsistencyReasons 不一致原因
 */
public record ReplayEventDiff(
        int diffVersion,
        boolean consistent,
        Summary summary,
        ResultDiff resultDiff,
        List<Entry> entries,
        List<String> inconsistencyReasons
) {

    /**
     * @param expectedCount 来源期望事实数
     * @param actualCount Replay 实际注入事实数
     * @param matchedCount 完全一致数
     * @param changedCount 内容变化数
     * @param missingCount 缺失数
     * @param unexpectedCount 非预期新增数
     */
    public record Summary(
            int expectedCount,
            int actualCount,
            int matchedCount,
            int changedCount,
            int missingCount,
            int unexpectedCount
    ) {
    }

    /**
     * @param status `matched` 或 `changed`
     * @param originalResult 来源事件重组结果
     * @param replayResult Replay 重组结果
     * @param originalSha256 来源结果 SHA-256
     * @param replaySha256 Replay 结果 SHA-256
     * @param originalLength 来源结果字符数
     * @param replayLength Replay 结果字符数
     */
    public record ResultDiff(
            String status,
            String originalResult,
            String replayResult,
            String originalSha256,
            String replaySha256,
            int originalLength,
            int replayLength
    ) {
    }

    /**
     * @param factKey 稳定事实键
     * @param category 事实分类
     * @param status `matched / changed / missing / unexpected`
     * @param sourceSequenceNo 来源事件序号
     * @param sourceEventType 来源事件类型
     * @param expectedHash 期望规范事实哈希
     * @param actualHash 实际规范事实哈希
     * @param changedFields 变化字段
     */
    public record Entry(
            String factKey,
            String category,
            String status,
            Long sourceSequenceNo,
            String sourceEventType,
            String expectedHash,
            String actualHash,
            List<String> changedFields
    ) {
    }
}

package com.zc.agent.sdk.runtime;

/**
 * Replay 管理分页条件。
 *
 * @param pageNum 页码
 * @param pageSize 每页条数
 * @param replayMode Replay 模式
 * @param status Replay 状态
 * @param sourceRunCode 来源 Run
 */
public record RuntimeReplayPageQuery(
        int pageNum,
        int pageSize,
        String replayMode,
        String status,
        String sourceRunCode
) {
}

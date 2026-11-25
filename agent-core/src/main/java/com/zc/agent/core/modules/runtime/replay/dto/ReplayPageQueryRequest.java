package com.zc.agent.core.modules.runtime.replay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Replay 管理分页查询条件。
 *
 * @param pageNum 页码
 * @param pageSize 每页条数
 * @param replayMode Replay 模式
 * @param status Replay 状态
 * @param sourceRunCode 来源 Run 编码
 */
public record ReplayPageQueryRequest(
        @Min(value = 1, message = "页码最小为 1") Integer pageNum,
        @Min(value = 1, message = "每页条数最小为 1")
        @Max(value = 100, message = "每页条数最大为 100") Integer pageSize,
        String replayMode,
        String status,
        String sourceRunCode
) {
}

package com.zc.agent.sdk.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Replay 分页响应。
 *
 * @param records Replay 记录
 * @param total 总数
 * @param pageNum 页码
 * @param pageSize 每页条数
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeReplayPage(
        List<RuntimeReplayPageItem> records,
        Long total,
        Integer pageNum,
        Integer pageSize
) {
}

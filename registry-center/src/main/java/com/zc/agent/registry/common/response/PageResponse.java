package com.zc.agent.registry.common.response;

import java.util.List;

/**
 * 分页响应。
 *
 * @param records  当前页数据
 * @param total    总记录数
 * @param pageNum  当前页码
 * @param pageSize 每页条数
 * @param <T>      数据类型
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        int pageNum,
        int pageSize
) {
}

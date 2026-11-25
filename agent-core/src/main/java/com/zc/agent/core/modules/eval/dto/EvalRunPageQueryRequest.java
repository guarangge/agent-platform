package com.zc.agent.core.modules.eval.dto;

/**
 * Eval Run 分页查询请求。
 */
public record EvalRunPageQueryRequest(
        Integer pageNum,
        Integer pageSize,
        Long suiteId,
        Long agentId,
        String runStatus
) {
}

package com.zc.agent.core.modules.eval.dto;

/**
 * Eval Suite 分页查询请求。
 */
public record EvalSuitePageQueryRequest(
        Integer pageNum,
        Integer pageSize,
        String suiteCode,
        String suiteName,
        Long agentId,
        String status
) {
}

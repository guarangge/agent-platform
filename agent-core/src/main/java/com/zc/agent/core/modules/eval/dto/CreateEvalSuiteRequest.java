package com.zc.agent.core.modules.eval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * 新增 Eval Suite 请求。
 */
public record CreateEvalSuiteRequest(
        @NotBlank(message = "Suite 编码不能为空")
        String suiteCode,
        @NotBlank(message = "Suite 名称不能为空")
        String suiteName,
        @NotNull(message = "Agent ID 不能为空")
        @Positive(message = "Agent ID 必须为正整数")
        Long agentId,
        @NotBlank(message = "Agent 名称不能为空")
        String agentName,
        String description,
        @NotBlank(message = "状态不能为空")
        String status,
        Map<String, Object> metadata,
        @Valid
        @NotEmpty(message = "至少配置一个 Eval 用例")
        List<EvalCaseUpsertRequest> evalCases
) {
}

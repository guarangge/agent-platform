package com.zc.agent.core.modules.eval.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Eval 用例新增/编辑请求。
 */
public record EvalCaseUpsertRequest(
        @NotBlank(message = "用例编码不能为空")
        String caseCode,
        @NotBlank(message = "用例名称不能为空")
        String caseName,
        @NotBlank(message = "用户输入不能为空")
        String userInput,
        List<String> expectedReplyKeywords,
        List<String> forbiddenReplyKeywords,
        List<String> expectedToolCodes,
        List<String> expectedStepTypes,
        String expectedFinalStatus,
        @NotNull(message = "通过阈值不能为空")
        @Min(value = 0, message = "通过阈值不能小于 0")
        @Max(value = 100, message = "通过阈值不能大于 100")
        Integer passThreshold,
        @NotNull(message = "排序号不能为空")
        Integer orderNo,
        @NotBlank(message = "状态不能为空")
        String status,
        Map<String, Object> metadata
) {
}

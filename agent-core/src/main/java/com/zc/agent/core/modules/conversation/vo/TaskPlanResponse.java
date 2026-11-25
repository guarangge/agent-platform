package com.zc.agent.core.modules.conversation.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务计划响应。
 *
 * @param taskPlanId         计划主键 ID
 * @param taskId             任务 ID
 * @param planVersion        计划版本
 * @param planId             计划业务 ID
 * @param planType           计划类型
 * @param goal               计划目标
 * @param status             计划状态
 * @param assumptionsJson    假设 JSON
 * @param constraintsJson    约束 JSON
 * @param risksJson          风险 JSON
 * @param plannerPromptId    规划 Prompt ID
 * @param plannerPromptName  规划 Prompt 名称
 * @param source             来源
 * @param metadataJson       元数据 JSON
 * @param createdAt          创建时间
 * @param steps              计划步骤
 */
public record TaskPlanResponse(
        Long taskPlanId,
        Long taskId,
        Integer planVersion,
        String planId,
        String planType,
        String goal,
        String status,
        String assumptionsJson,
        String constraintsJson,
        String risksJson,
        Long plannerPromptId,
        String plannerPromptName,
        String source,
        String metadataJson,
        LocalDateTime createdAt,
        List<TaskPlanStepResponse> steps
) {
}

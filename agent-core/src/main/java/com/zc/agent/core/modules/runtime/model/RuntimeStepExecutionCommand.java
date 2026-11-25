package com.zc.agent.core.modules.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * 单个 Plan 或 Workflow 节点的稳定运行时执行命令。
 *
 * @param executionContext    运行时上下文
 * @param executionDecision   决策结果，Workflow 节点执行时可为空
 * @param executionPlan       执行计划，Workflow 节点执行时可为空
 * @param step                标准化步骤定义
 * @param previousStepResults 前序执行结果
 * @param inputText           当前步骤输入文本
 * @param inputObservation    输入治理观测
 * @param authorization       原始授权头
 * @param taskStepId          任务步骤 ID，Workflow 内部节点可为空
 * @param operatorId          操作人 ID
 */
public record RuntimeStepExecutionCommand(
        ExecutionContext executionContext,
        ExecutionDecision executionDecision,
        ExecutionPlan executionPlan,
        ExecutionPlan.ExecutionPlanStep step,
        List<RuntimeStepResultContext> previousStepResults,
        String inputText,
        Map<String, Object> inputObservation,
        String authorization,
        Long taskStepId,
        Long operatorId
) {
}

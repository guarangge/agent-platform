package com.zc.agent.core.modules.runtime.kernel;

import java.util.Map;

/** 可替换的运行时状态与事件投影出口。 */
public interface RuntimeEventSink {

    /** 返回稳定事件出口编码。 */
    String sinkCode();

    /** 发布可忽略失败的运行进度投影。 */
    void progress(ProgressEvent event);

    /** 原子创建 Task Step 并发布 step.started。 */
    Long stepStarted(StepStartedEvent event);

    /** 原子迁移 Task Step 状态并发布对应 Runtime Event。 */
    void stepTransitioned(StepTransitionedEvent event);

    /**
     * @param stage 进度阶段
     * @param message 安全可展示文案
     * @param metadata 不包含 Prompt / Token / Tool 私密参数的元数据
     */
    record ProgressEvent(String stage, String message, Map<String, Object> metadata) {
    }

    /** Task Step 开始事件。 */
    record StepStartedEvent(
            Long taskId,
            Integer persistedStepNo,
            String stepId,
            Integer stepNo,
            Integer stepCount,
            String stepType,
            String stepName,
            String inputSnapshot,
            Long operatorId
    ) {
    }

    /** Task Step 状态迁移事件。 */
    record StepTransitionedEvent(
            Long taskId,
            Long taskStepId,
            String stepId,
            Integer stepNo,
            Integer stepCount,
            String stepType,
            String stepName,
            String status,
            String outputSnapshot,
            String errorMessage,
            String failureType,
            boolean skipped,
            Long operatorId
    ) {
    }

}

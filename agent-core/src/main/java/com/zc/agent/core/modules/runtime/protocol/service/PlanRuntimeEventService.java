package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 发布 Plan 创建和重规划事件，完整计划内容继续由任务计划版本表保存。
 */
@Service
public class PlanRuntimeEventService {

    private final AgentRunQueueRepository runRepository;
    private final RuntimeEventPublisher eventPublisher;

    public PlanRuntimeEventService(AgentRunQueueRepository runRepository,
                                   RuntimeEventPublisher eventPublisher) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 发布 plan.created。 */
    public void created(PlanEventCommand command) {
        publish(RuntimeEventType.PLAN_CREATED, command, "created");
    }

    /** 发布 plan.replanned。 */
    public void replanned(PlanEventCommand command) {
        publish(RuntimeEventType.PLAN_REPLANNED, command, "replanned");
    }

    private void publish(RuntimeEventType eventType,
                         PlanEventCommand command,
                         String status) {
        resolveRunCode(command.taskId()).ifPresent(runCode -> {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskPlanId", command.taskPlanId());
            payload.put("planId", command.planId());
            payload.put("taskId", command.taskId());
            payload.put("planVersion", command.planVersion());
            payload.put("planType", safe(command.planType()));
            payload.put("planStatus", safe(command.planStatus()));
            payload.put("stepCount", command.stepCount());
            payload.put("source", safe(command.source()));
            payload.put("qualityScore", command.qualityScore());
            payload.put("status", status);
            if (command.previousPlanId() != null && !command.previousPlanId().isBlank()) {
                payload.put("previousPlanId", command.previousPlanId());
                payload.put("previousPlanVersion", command.previousPlanVersion());
                payload.put("changeSummary", command.changeSummary() == null
                        ? Map.of() : command.changeSummary());
            }
            eventPublisher.publishPayload(runCode, eventType, payload, traceMetadata(command.traceId()));
        });
    }

    private Optional<String> resolveRunCode(Long taskId) {
        String currentRunCode = AgentRunStreamingContext.currentRunCode();
        if (currentRunCode != null && !currentRunCode.isBlank()) {
            return Optional.of(currentRunCode);
        }
        return runRepository.findRunCodeByTaskId(taskId);
    }

    private Map<String, Object> traceMetadata(String traceId) {
        return traceId == null || traceId.isBlank() ? Map.of() : Map.of("traceId", traceId);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param taskPlanId 任务计划版本记录 ID
     * @param planId 计划业务 ID
     * @param taskId Task ID
     * @param planVersion 任务内计划版本
     * @param planType 计划类型
     * @param planStatus 计划状态
     * @param stepCount 计划步骤数
     * @param source 计划来源
     * @param qualityScore 质量评分
     * @param previousPlanId 上一计划业务 ID
     * @param previousPlanVersion 上一计划版本
     * @param changeSummary 步骤增删改计数
     * @param traceId 原任务 Trace ID
     */
    public record PlanEventCommand(
            Long taskPlanId,
            String planId,
            Long taskId,
            Integer planVersion,
            String planType,
            String planStatus,
            int stepCount,
            String source,
            Integer qualityScore,
            String previousPlanId,
            Integer previousPlanVersion,
            Map<String, Object> changeSummary,
            String traceId
    ) {
    }
}

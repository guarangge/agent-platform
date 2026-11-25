package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Task Step 状态变更和标准 Runtime Event 保持在同一事务中。
 */
@Service
public class TaskStepLifecycleEventService {

    private final ConversationRepository conversationRepository;
    private final RuntimeEventPublisher eventPublisher;
    private final ArtifactRuntimeEventService artifactRuntimeEventService;

    public TaskStepLifecycleEventService(ConversationRepository conversationRepository,
                                         RuntimeEventPublisher eventPublisher,
                                         ArtifactRuntimeEventService artifactRuntimeEventService) {
        this.conversationRepository = conversationRepository;
        this.eventPublisher = eventPublisher;
        this.artifactRuntimeEventService = artifactRuntimeEventService;
    }

    /** 创建运行步骤并发布 step.started。 */
    @Transactional
    public Long start(StartStepCommand command) {
        Long taskStepId = conversationRepository.insertTaskStep(
                command.taskId(), command.persistedStepNo(), command.stepType(), command.stepName(),
                command.inputSnapshot(), command.operatorId());
        publish(RuntimeEventType.STEP_STARTED, command.payload(taskStepId, "processing"));
        return taskStepId;
    }

    /** 更新步骤状态并发布 completed、failed 或 waiting 事件。 */
    @Transactional
    public void transition(TransitionStepCommand command) {
        conversationRepository.updateTaskStepStatus(
                command.taskStepId(), command.status(), command.outputSnapshot(),
                command.errorMessage(), command.operatorId());
        RuntimeEventType eventType = switch (command.status()) {
            case "success" -> RuntimeEventType.STEP_COMPLETED;
            case "failed" -> RuntimeEventType.STEP_FAILED;
            case "waiting_approval", "waiting_event", "paused" -> RuntimeEventType.STEP_WAITING;
            default -> throw new IllegalArgumentException("不支持的 Task Step Runtime Event 状态: " + command.status());
        };
        publish(eventType, command.payload());
        if (eventType == RuntimeEventType.STEP_COMPLETED && !command.skipped()) {
            artifactRuntimeEventService.publishCreated(
                    command.taskId(), command.taskStepId(), command.outputSnapshot());
        }
    }

    private void publish(RuntimeEventType eventType, Map<String, Object> payload) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (runCode != null && !runCode.isBlank()) {
            eventPublisher.publishPayload(runCode, eventType, payload, Map.of());
        }
    }

    /**
     * @param taskId Task ID
     * @param persistedStepNo 数据库中的步骤序号
     * @param stepId 计划步骤稳定 ID
     * @param stepNo 当前计划内步骤序号
     * @param stepCount 当前计划步骤总数
     * @param stepType 步骤类型
     * @param stepName 步骤名称
     * @param inputSnapshot 输入快照
     * @param operatorId 操作人 ID
     */
    public record StartStepCommand(
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
        private Map<String, Object> payload(Long taskStepId, String status) {
            LinkedHashMap<String, Object> payload = basePayload(
                    taskStepId, stepId, stepNo, stepCount, stepType, stepName, status);
            payload.put("taskId", taskId);
            return payload;
        }
    }

    /**
     * @param taskId Task ID
     * @param taskStepId Task Step ID
     * @param stepId 计划步骤稳定 ID
     * @param stepNo 当前计划内步骤序号
     * @param stepCount 当前计划步骤总数
     * @param stepType 步骤类型
     * @param stepName 步骤名称
     * @param status 目标状态
     * @param outputSnapshot 输出快照
     * @param errorMessage 错误信息
     * @param failureType 失败分类
     * @param skipped 是否跳过
     * @param operatorId 操作人 ID
     */
    public record TransitionStepCommand(
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
        private Map<String, Object> payload() {
            LinkedHashMap<String, Object> payload = basePayload(
                    taskStepId, stepId, stepNo, stepCount, stepType, stepName, status);
            if (errorMessage != null && !errorMessage.isBlank()) {
                payload.put("errorMessage", errorMessage);
            }
            if (failureType != null && !failureType.isBlank()) {
                payload.put("failureType", failureType);
            }
            if (skipped) {
                payload.put("skipped", true);
            }
            return payload;
        }
    }

    private static LinkedHashMap<String, Object> basePayload(Long taskStepId,
                                                              String stepId,
                                                              Integer stepNo,
                                                              Integer stepCount,
                                                              String stepType,
                                                              String stepName,
                                                              String status) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskStepId", taskStepId);
        payload.put("stepId", stepId);
        payload.put("stepNo", stepNo);
        payload.put("stepCount", stepCount);
        payload.put("stepType", stepType);
        payload.put("stepName", stepName);
        payload.put("status", status);
        return payload;
    }
}

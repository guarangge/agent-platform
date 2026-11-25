package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 发布 Checkpoint 创建和恢复事件，事件只保存定位与恢复控制信息，不复制快照正文。
 */
@Service
public class CheckpointRuntimeEventService {

    private final AgentRunQueueRepository runRepository;
    private final RuntimeEventPublisher eventPublisher;

    public CheckpointRuntimeEventService(AgentRunQueueRepository runRepository,
                                         RuntimeEventPublisher eventPublisher) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 发布 checkpoint.created。 */
    public void created(CheckpointEventCommand command) {
        publish(RuntimeEventType.CHECKPOINT_CREATED, command, "created", null);
    }

    /** 发布 checkpoint.restored，并记录本次实际恢复入口。 */
    public void restored(CheckpointEventCommand command, String recoveryMode) {
        publish(RuntimeEventType.CHECKPOINT_RESTORED, command, "restored", recoveryMode);
    }

    private void publish(RuntimeEventType eventType,
                         CheckpointEventCommand command,
                         String status,
                         String recoveryMode) {
        resolveRunCode(command.taskId()).ifPresent(runCode -> {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkpointId", command.checkpointId());
            payload.put("taskId", command.taskId());
            payload.put("taskStepId", command.taskStepId());
            payload.put("checkpointStatus", command.checkpointStatus());
            payload.put("currentStepNo", command.currentStepNo());
            payload.put("nextStepNo", command.nextStepNo());
            payload.put("planId", safe(command.planId()));
            payload.put("status", status);
            if (recoveryMode != null && !recoveryMode.isBlank()) {
                payload.put("recoveryMode", recoveryMode);
            }
            if (command.recoveryDisposition() != null && !command.recoveryDisposition().isBlank()) {
                payload.put("recoveryDisposition", command.recoveryDisposition());
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
     * @param checkpointId Checkpoint ID
     * @param taskId Task ID
     * @param taskStepId Checkpoint 对应的 Task Step ID
     * @param checkpointStatus 快照保存时的执行状态
     * @param currentStepNo 当前步骤序号
     * @param nextStepNo 恢复目标步骤序号
     * @param planId 快照关联的计划 ID
     * @param traceId 原任务 Trace ID
     * @param recoveryDisposition 恢复安全处置
     */
    public record CheckpointEventCommand(
            Long checkpointId,
            Long taskId,
            Long taskStepId,
            String checkpointStatus,
            Integer currentStepNo,
            Integer nextStepNo,
            String planId,
            String traceId,
            String recoveryDisposition
    ) {
    }
}

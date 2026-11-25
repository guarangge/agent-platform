package com.zc.agent.core.modules.runtime.protocol.service;

import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一维护持久化 Agent Run 状态与 Runtime Event，确保终态和终态事件原子提交。
 */
@Service
public class AgentRunLifecycleEventService {

    private final AgentRunQueueRepository runRepository;
    private final RuntimeEventPublisher eventPublisher;

    public AgentRunLifecycleEventService(AgentRunQueueRepository runRepository,
                                         RuntimeEventPublisher eventPublisher) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    /** 在 Run 入队事务中发布排队事件。 */
    public void publishQueued(String runCode, int priority) {
        eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_QUEUED,
                Map.of("status", "queued", "priority", priority), Map.of());
    }

    /** Worker 获得执行权后发布开始事件。 */
    public void publishStarted(AgentRunRecord run, String workerId) {
        eventPublisher.publishPayload(run.runCode(), RuntimeEventType.RUN_STARTED, Map.of(
                "status", "running",
                "attemptCount", run.attemptCount(),
                "maxAttempts", run.maxAttempts()
        ), Map.of("workerId", workerId));
    }

    /** 更新 Run 完成或等待状态，并在同一事务内写入对应事件。 */
    @Transactional
    public boolean markCompleted(String runCode,
                                 String workerId,
                                 Long taskId,
                                 String status) {
        boolean updated = runRepository.markCompleted(runCode, workerId, taskId, status);
        if (!updated) {
            return false;
        }
        RuntimeEventType eventType = resolveCompletionEventType(status);
        eventPublisher.publishPayload(runCode, eventType, lifecyclePayload(status, taskId, null), Map.of());
        return true;
    }

    /** 更新 Run 失败状态，并在同一事务内发布失败事件。 */
    @Transactional
    public boolean markFailed(String runCode,
                              String workerId,
                              Long taskId,
                              String errorMessage) {
        boolean updated = runRepository.markFailed(runCode, workerId, taskId, errorMessage);
        if (updated) {
            eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_FAILED,
                    lifecyclePayload("failed", taskId, errorMessage), Map.of());
        }
        return updated;
    }

    /** 更新 Run 取消状态，并在同一事务内发布取消事件。 */
    @Transactional
    public boolean markCancelled(String runCode, String workerId, Long taskId) {
        boolean updated = runRepository.markCancelled(runCode, workerId, taskId);
        if (updated) {
            eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_CANCELLED,
                    lifecyclePayload("cancelled", taskId, null), Map.of());
        }
        return updated;
    }

    /** 将可重试 Run 重新排队并发布新的排队事件。 */
    @Transactional
    public boolean requeue(String runCode,
                           String workerId,
                           String errorMessage,
                           int backoffSeconds) {
        boolean updated = runRepository.requeue(runCode, workerId, errorMessage, backoffSeconds);
        if (updated) {
            eventPublisher.publishPayload(runCode, RuntimeEventType.RUN_QUEUED, Map.of(
                    "status", "queued",
                    "retry", true,
                    "backoffSeconds", backoffSeconds
            ), Map.of());
        }
        return updated;
    }

    /**
     * 人工恢复入口同步 Task 与 Run 状态，并在同一事务发布恢复后的新终态事件。
     * 这样客户端可在此前的 run.waiting 游标后继续 Replay，而不必轮询数据库状态。
     */
    @Transactional
    public boolean syncTaskStatus(Long taskId, String taskStatus, String errorMessage) {
        var updates = runRepository.syncTaskStatus(taskId, taskStatus, errorMessage);
        updates.stream()
                .filter(update -> !"running".equals(update.status()))
                .forEach(update -> eventPublisher.publishPayload(
                        update.runCode(), resolveCompletionEventType(update.status()),
                        lifecyclePayload(update.status(), update.taskId(), errorMessage), Map.of()));
        return !updates.isEmpty();
    }

    /** 请求取消；排队中的 Run 会立即进入取消终态并发布事件。 */
    @Transactional
    public int requestCancel(AgentRunRecord run, Long userId) {
        int updated = runRepository.requestCancel(run.runCode(), userId);
        if (updated > 0 && "queued".equals(run.status())) {
            eventPublisher.publishPayload(run.runCode(), RuntimeEventType.RUN_CANCELLED,
                    lifecyclePayload("cancelled", run.taskId(), null), Map.of());
        }
        return updated;
    }

    private RuntimeEventType resolveCompletionEventType(String status) {
        if ("failed".equals(status)) {
            return RuntimeEventType.RUN_FAILED;
        }
        if ("waiting_approval".equals(status)
                || "waiting_event".equals(status)
                || "paused".equals(status)) {
            return RuntimeEventType.RUN_WAITING;
        }
        return RuntimeEventType.RUN_COMPLETED;
    }

    private Map<String, Object> lifecyclePayload(String status, Long taskId, String errorMessage) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (taskId != null) {
            payload.put("taskId", taskId);
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            payload.put("errorMessage", errorMessage);
        }
        return payload;
    }
}

package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.config.AgentRunWorkerProperties;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.TaskResponse;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import com.zc.agent.core.security.SecurityUser;
import com.zc.agent.core.security.TenantScopeContext;
import com.zc.agent.core.modules.cost.service.UsageAttributionContext;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.runtime.protocol.service.AgentRunLifecycleEventService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 持久化 Agent 运行 Worker，负责领取、执行、心跳、失败回写和取消中断。
 */
@Component
public class AgentRunWorker {

    private static final Logger log = LoggerFactory.getLogger(AgentRunWorker.class);

    private final AgentRunQueueRepository runRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final AgentRunWorkerProperties properties;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;
    private final AgentRunControlPlaneService controlPlaneService;
    private final AgentRunLifecycleEventService runLifecycleEventService;
    private final Semaphore permits;
    private final Map<String, Thread> activeThreads = new ConcurrentHashMap<>();
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();

    @Autowired
    public AgentRunWorker(AgentRunQueueRepository runRepository,
                          ConversationRepository conversationRepository,
                          ConversationService conversationService,
                          AgentRunWorkerProperties properties,
                          ObjectMapper objectMapper,
                          @Qualifier("agentRunExecutor") Executor executor,
                          ObservationRegistry observationRegistry,
                          Tracer tracer,
                          AgentRunControlPlaneService controlPlaneService,
                          AgentRunLifecycleEventService runLifecycleEventService) {
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.observationRegistry = observationRegistry;
        this.tracer = tracer;
        this.controlPlaneService = controlPlaneService;
        this.runLifecycleEventService = runLifecycleEventService;
        this.permits = new Semaphore(Math.max(1, properties.getConcurrency()));
    }

    /** 保留原构造契约，测试场景不启用控制面。 */
    public AgentRunWorker(AgentRunQueueRepository runRepository,ConversationRepository conversationRepository,
                          ConversationService conversationService,AgentRunWorkerProperties properties,ObjectMapper objectMapper,
                          Executor executor,ObservationRegistry observationRegistry,Tracer tracer){
        this(runRepository,conversationRepository,conversationService,properties,objectMapper,executor,observationRegistry,tracer,null,null);
    }

    @Scheduled(fixedDelayString = "${core.runtime.worker.poll-delay-ms:1000}")
    public void poll() {
        if (!accepting.get()) {
            return;
        }
        if (controlPlaneService!=null) {
            controlPlaneService.heartbeat(workerId,activeThreads.size());
            if (!controlPlaneService.accepting(workerId)) return;
        }
        int requested = Math.min(
                permits.availablePermits(),
                Math.max(1, properties.getClaimBatchSize())
        );
        if (requested <= 0 || !permits.tryAcquire(requested)) {
            return;
        }
        List<AgentRunRecord> claimed;
        try {
            claimed = controlPlaneService==null?runRepository.claimBatch(
                    workerId,
                    Math.max(30, properties.getLeaseSeconds()),
                    requested,
                    Math.max(1, properties.getGlobalConcurrency()),
                    Math.max(1, properties.getAgingSeconds())
            ):runRepository.claimBatch(workerId,Math.max(30,properties.getLeaseSeconds()),requested,
                    Math.max(1,properties.getGlobalConcurrency()),Math.max(1,properties.getAgingSeconds()),
                    properties.getGroupCode(),capabilitiesJson());
        } catch (RuntimeException exception) {
            permits.release(requested);
            throw exception;
        }
        permits.release(requested - claimed.size());
        for (AgentRunRecord run : claimed) {
            submit(run);
        }
    }

    @Scheduled(fixedDelayString = "${core.runtime.worker.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        runRepository.extendWorkerLeases(workerId, Math.max(30, properties.getLeaseSeconds()));
        if(controlPlaneService!=null)controlPlaneService.heartbeat(workerId,activeThreads.size());
    }

    @Scheduled(fixedDelayString = "${core.runtime.worker.recovery-interval-ms:30000}")
    public void recoverExpiredLeases() {
        int recovered = runRepository.recoverExpiredLeases();
        if(controlPlaneService!=null)controlPlaneService.markStaleNodes();
        if (recovered > 0) {
            log.warn("已回收失效 Agent Run 租约, count={}", recovered);
        }
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public void interrupt(String runCode) {
        Thread thread = activeThreads.get(runCode);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void submit(AgentRunRecord run) {
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException exception) {
            runRepository.releaseClaim(run.runCode(), workerId);
            permits.release();
            log.error("Agent Run 提交本地线程池失败，已归还队列, runCode={}", run.runCode(), exception);
        }
    }

    private void execute(AgentRunRecord run) {
        Observation observation = Observation.start("agent.run", observationRegistry)
                .lowCardinalityKeyValue("agent.run.mode", "async")
                .highCardinalityKeyValue("agent.run.code", run.runCode())
                .highCardinalityKeyValue("agent.conversation.id", String.valueOf(run.conversationId()));
        activeThreads.put(run.runCode(), Thread.currentThread());
        AgentRunStreamingContext.bind(run.runCode());
        try (Observation.Scope ignored = observation.openScope()) {
            if (tracer.currentSpan() != null) {
                log.debug("Agent Run OpenTelemetry span 已建立, runCode={}, traceId={}",
                        run.runCode(), tracer.currentSpan().context().traceId());
            }
            SecurityUser securityUser = objectMapper.readValue(run.userContextJson(), SecurityUser.class);
            // Worker 节点属于调度基础设施，不能随当前业务 Run 写入租户/项目作用域。
            if (controlPlaneService != null) {
                controlPlaneService.heartbeat(workerId, activeThreads.size());
            }
            TenantScopeContext.set(securityUser);
            if (runRepository.isCancellationRequested(run.runCode())) {
                markCancelled(run.runCode(), run.taskId());
                return;
            }
            if (runLifecycleEventService != null) {
                runLifecycleEventService.publishStarted(run, workerId);
            }
            TaskResponse task = conversationService.executeMessageFromRun(
                    run.conversationId(),
                    new SendMessageRequest(run.inputText()),
                    securityUser,
                    run.runCode()
            );
            Long taskId = task == null ? null : task.taskId();
            if (runRepository.isCancellationRequested(run.runCode())) {
                markCancelled(run.runCode(), taskId);
                return;
            }
            String finalStatus = task != null && List.of("waiting_approval", "waiting_event", "paused", "failed").contains(task.status())
                    ? task.status() : "completed";
            if (!markCompleted(run.runCode(), taskId, finalStatus)) {
                log.warn("忽略失去租约后的 Agent Run 完成回写, runCode={}, workerId={}", run.runCode(), workerId);
            }
        } catch (Exception exception) {
            observation.error(exception);
            if (Thread.currentThread().isInterrupted()) {
                markCancelledOrRetry(run, "运行已被中断");
            } else {
                markFailedOrRetry(run, exception);
            }
        } finally {
            AgentRunStreamingContext.clear();
            UsageAttributionContext.clear();
            TenantScopeContext.clear();
            observation.stop();
            activeThreads.remove(run.runCode());
            permits.release();
        }
    }

    private void markCancelledOrRetry(AgentRunRecord run, String message) {
        Long taskId = resolveTaskId(run.runCode());
        if (runRepository.isCancellationRequested(run.runCode())) {
            markCancelled(run.runCode(), taskId);
        } else if (taskId == null && run.attemptCount() < run.maxAttempts()) {
            requeue(run.runCode(), message, calculateBackoffSeconds(run));
        } else {
            markFailed(run.runCode(), taskId, message);
        }
    }

    private void markFailedOrRetry(AgentRunRecord run, Exception exception) {
        Long taskId = resolveTaskId(run.runCode());
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (runRepository.isCancellationRequested(run.runCode())) {
            markCancelled(run.runCode(), taskId);
            return;
        }
        if (run.attemptCount() < run.maxAttempts()
                && (taskId == null || (conversationService.hasRecoverableCheckpoint(taskId) && isTransient(exception)))) {
            int backoffSeconds = calculateBackoffSeconds(run);
            requeue(run.runCode(), message, backoffSeconds);
            log.warn("Agent Run 执行前失败，等待重试, runCode={}, attempt={}, message={}",
                    run.runCode(), run.attemptCount(), message);
            return;
        }
        markFailed(run.runCode(), taskId, message);
        log.error("Agent Run 执行失败, runCode={}, taskId={}, message={}", run.runCode(), taskId, message, exception);
    }

    private boolean isTransient(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ToolExecutionException toolExecutionException && toolExecutionException.retryable()) {
                return true;
            }
            String type = current.getClass().getSimpleName();
            if (type.contains("Timeout") || type.contains("Connect") || type.contains("IOException")
                    || type.contains("Transient") || type.contains("DataAccessResourceFailure")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Long resolveTaskId(String runCode) {
        return conversationRepository.findTaskByRunCode(runCode)
                .map(TaskRecord::taskId)
                .orElse(null);
    }

    private boolean markCompleted(String runCode, Long taskId, String status) {
        return runLifecycleEventService == null
                ? runRepository.markCompleted(runCode, workerId, taskId, status)
                : runLifecycleEventService.markCompleted(runCode, workerId, taskId, status);
    }

    private boolean markFailed(String runCode, Long taskId, String message) {
        return runLifecycleEventService == null
                ? runRepository.markFailed(runCode, workerId, taskId, message)
                : runLifecycleEventService.markFailed(runCode, workerId, taskId, message);
    }

    private boolean markCancelled(String runCode, Long taskId) {
        return runLifecycleEventService == null
                ? runRepository.markCancelled(runCode, workerId, taskId)
                : runLifecycleEventService.markCancelled(runCode, workerId, taskId);
    }

    private boolean requeue(String runCode, String message, int backoffSeconds) {
        return runLifecycleEventService == null
                ? runRepository.requeue(runCode, workerId, message, backoffSeconds)
                : runLifecycleEventService.requeue(runCode, workerId, message, backoffSeconds);
    }

    int calculateBackoffSeconds(AgentRunRecord run) {
        long initial = Math.max(1, properties.getRetryInitialSeconds());
        int exponent = Math.max(0, Math.min(run.attemptCount() - 1, 20));
        long exponential = initial * (1L << exponent);
        long capped = Math.min(Math.max(initial, properties.getRetryMaxSeconds()), exponential);
        long jitterBound = Math.max(1, capped / 4);
        long jitter = Math.floorMod(run.runId(), jitterBound + 1);
        return (int) Math.min(Integer.MAX_VALUE, capped + jitter);
    }

    private String capabilitiesJson(){try{return objectMapper.writeValueAsString(properties.getCapabilities());}catch(Exception e){return "[]";}}

    /**
     * 停机时先停止领取并等待在途执行；等待期间续租，避免其他实例重复接管。
     * 超时后不主动改写状态，由数据库租约到期后安全回收。
     */
    @PreDestroy
    public void shutdown() {
        accepting.set(false);
        if(controlPlaneService!=null)controlPlaneService.offline(workerId);
        long deadline = System.currentTimeMillis() + Math.max(1, properties.getShutdownTimeoutSeconds()) * 1000L;
        while (!activeThreads.isEmpty() && System.currentTimeMillis() < deadline) {
            runRepository.extendWorkerLeases(workerId, Math.max(30, properties.getLeaseSeconds()));
            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!activeThreads.isEmpty()) {
            log.warn("Agent Run Worker 优雅停机等待超时，剩余任务将由租约失效后接管, workerId={}, active={}",
                    workerId, activeThreads.size());
        }
    }
}

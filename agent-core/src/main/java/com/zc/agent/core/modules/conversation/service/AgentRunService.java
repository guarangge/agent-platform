package com.zc.agent.core.modules.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.attachment.service.ConversationAttachmentService;
import com.zc.agent.core.config.AgentRunWorkerProperties;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunBatchRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunEnqueueItem;
import com.zc.agent.core.modules.conversation.model.AgentRunRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.conversation.vo.AgentRunQueueMetricsResponse;
import com.zc.agent.core.modules.runtime.protocol.service.AgentRunLifecycleEventService;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

/**
 * Agent 异步运行入队、查询与取消服务。
 */
@Service
public class AgentRunService {

    private final AgentRunQueueRepository runRepository;
    private final ConversationRepository conversationRepository;
    private final AgentRunWorker runWorker;
    private final AgentRunWorkerProperties workerProperties;
    private final ObjectMapper objectMapper;
    private final AgentRunControlPlaneService controlPlaneService;
    private final AgentRunLifecycleEventService runLifecycleEventService;
    private final com.zc.agent.core.modules.runtime.service.DelegationGovernanceService delegationGovernanceService;
    private final ConversationAttachmentService attachmentService;

    @Autowired
    public AgentRunService(AgentRunQueueRepository runRepository,
                           ConversationRepository conversationRepository,
                           AgentRunWorker runWorker,
                           AgentRunWorkerProperties workerProperties,
                           ObjectMapper objectMapper,
                           AgentRunControlPlaneService controlPlaneService,
                           AgentRunLifecycleEventService runLifecycleEventService,
                           com.zc.agent.core.modules.runtime.service.DelegationGovernanceService delegationGovernanceService,
                           ConversationAttachmentService attachmentService) {
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.runWorker = runWorker;
        this.workerProperties = workerProperties;
        this.objectMapper = objectMapper;
        this.controlPlaneService = controlPlaneService;
        this.runLifecycleEventService = runLifecycleEventService;
        this.delegationGovernanceService = delegationGovernanceService;
        this.attachmentService = attachmentService;
    }

    /** 保留原构造契约，独立单元测试默认使用旧 default 队列。 */
    public AgentRunService(AgentRunQueueRepository runRepository,ConversationRepository conversationRepository,
                           AgentRunWorker runWorker,AgentRunWorkerProperties workerProperties,ObjectMapper objectMapper){
        this(runRepository,conversationRepository,runWorker,workerProperties,objectMapper,null,null,null,null);
    }

    @Transactional
    public AgentRunResponse enqueue(Long conversationId,
                                    SendMessageRequest request,
                                    String idempotencyKey,
                                    SecurityUser securityUser) {
        return enqueue(conversationId, request, idempotencyKey, null, securityUser);
    }

    /**
     * 将单条消息持久化入队；容量检查和插入在同一数据库事务中完成。
     */
    @Transactional
    public AgentRunResponse enqueue(Long conversationId,
                                    SendMessageRequest request,
                                    String idempotencyKey,
                                    Integer priority,
                                    SecurityUser securityUser) {
        var conversation = conversationRepository.findConversationById(conversationId, securityUser.userId())
                .orElseThrow(() -> new BusinessException("会话不存在"));
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        var existing = runRepository.findByIdempotencyKey(conversationId, securityUser.userId(), normalizedKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        String runCode = "RUN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        try {
            OptionalInsertResult insertResult = insertRun(
                    runCode,
                    conversationId,
                    securityUser.userId(),
                    conversation.agentId(),
                    request.normalizedContent(),
                    normalizedKey,
                    priority,
                    securityUser
            );
            if (!insertResult.inserted()) {
                throw new BusinessException("Agent 运行队列已达到容量上限，请稍后重试");
            }
            if (attachmentService != null) {
                attachmentService.reserveForRun(
                        conversationId, request.normalizedAttachmentIds(), runCode, securityUser);
            }
        } catch (DuplicateKeyException exception) {
            return runRepository.findByIdempotencyKey(conversationId, securityUser.userId(), normalizedKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        }
        return get(runCode, securityUser);
    }

    /**
     * 批量入队采用单事务全成全败，任一项触发背压时不会留下部分数据。
     */
    @Transactional
    public List<AgentRunResponse> enqueueBatch(Long conversationId,
                                               AgentRunBatchRequest request,
                                               SecurityUser securityUser) {
        var conversation = conversationRepository.findConversationById(conversationId, securityUser.userId())
                .orElseThrow(() -> new BusinessException("会话不存在"));
        return request.items().stream()
                .map(item -> enqueueBatchItem(conversationId, conversation.agentId(), item, securityUser))
                .toList();
    }

    public AgentRunResponse get(String runCode, SecurityUser securityUser) {
        return runRepository.findByRunCode(runCode, securityUser.userId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException("异步运行不存在"));
    }

    public AgentRunResponse cancel(String runCode, SecurityUser securityUser) {
        AgentRunRecord record = runRepository.findByRunCode(runCode, securityUser.userId())
                .orElseThrow(() -> new BusinessException("异步运行不存在"));
        if (isClosedStatus(record.status())) {
            return toResponse(record);
        }
        if (runLifecycleEventService == null) {
            runRepository.requestCancel(runCode, securityUser.userId());
        } else {
            runLifecycleEventService.requestCancel(record, securityUser.userId());
        }
        Long taskId = record.taskId() != null ? record.taskId()
                : conversationRepository.findTaskByRunCode(runCode).map(com.zc.agent.core.modules.conversation.model.TaskRecord::taskId).orElse(null);
        if (taskId != null && delegationGovernanceService != null) {
            delegationGovernanceService.propagateCancellation(taskId, securityUser.userId());
        }
        runWorker.interrupt(runCode);
        return get(runCode, securityUser);
    }

    public void syncTaskStatus(Long taskId, String taskStatus, String errorMessage) {
        if (runLifecycleEventService == null) {
            runRepository.syncTaskStatus(taskId, taskStatus, errorMessage);
        } else {
            runLifecycleEventService.syncTaskStatus(taskId, taskStatus, errorMessage);
        }
    }

    /** 查询队列指标，仅平台管理员可访问。 */
    public AgentRunQueueMetricsResponse metrics(SecurityUser securityUser) {
        requireAdministrator(securityUser);
        var metrics = runRepository.loadMetrics();
        return new AgentRunQueueMetricsResponse(
                metrics.statusCounts(), metrics.readyCount(), metrics.leasedCount(),
                metrics.expiredLeaseCount(), metrics.oldestQueuedAt(),
                workerProperties.getQueueCapacity(), workerProperties.getGlobalConcurrency(), runWorker.isAccepting()
        );
    }

    /** 主动回收失效租约，仅用于运维加速；正常情况下 Worker 会定时自动回收。 */
    public int recoverExpiredLeases(SecurityUser securityUser) {
        requireAdministrator(securityUser);
        return runRepository.recoverExpiredLeases();
    }

    private AgentRunResponse enqueueBatchItem(Long conversationId,
                                              Long agentId,
                                              AgentRunEnqueueItem item,
                                              SecurityUser securityUser) {
        String normalizedKey = normalizeIdempotencyKey(item.idempotencyKey());
        var existing = runRepository.findByIdempotencyKey(conversationId, securityUser.userId(), normalizedKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        String runCode = newRunCode();
        try {
            OptionalInsertResult result = insertRun(
                    runCode, conversationId, securityUser.userId(), agentId,
                    item.content().trim(), normalizedKey, item.priority(), securityUser
            );
            if (!result.inserted()) {
                throw new BusinessException("Agent 运行队列已达到容量上限，请稍后重试");
            }
        } catch (DuplicateKeyException exception) {
            return runRepository.findByIdempotencyKey(conversationId, securityUser.userId(), normalizedKey)
                    .map(this::toResponse)
                    .orElseThrow(() -> exception);
        }
        return get(runCode, securityUser);
    }

    private OptionalInsertResult insertRun(String runCode,
                                           Long conversationId,
                                           Long userId,
                                           Long agentId,
                                           String input,
                                           String idempotencyKey,
                                           Integer priority,
                                           SecurityUser securityUser) {
        int normalizedPriority=normalizePriority(priority);
        var id=controlPlaneService==null
                ? runRepository.insert(runCode,conversationId,userId,agentId,input,serializeUser(securityUser),idempotencyKey,
                    Math.max(1,workerProperties.getMaxAttempts()),normalizedPriority,Math.max(1,workerProperties.getQueueCapacity()),Math.max(1,workerProperties.getPerUserQueueCapacity()))
                : insertRouted(runCode,conversationId,userId,agentId,input,idempotencyKey,normalizedPriority,securityUser);
        if (id.isPresent() && runLifecycleEventService != null) {
            runLifecycleEventService.publishQueued(runCode, normalizedPriority);
        }
        return new OptionalInsertResult(id.isPresent());
    }

    private java.util.Optional<Long> insertRouted(String runCode,Long conversationId,Long userId,Long agentId,String input,
                                                   String idempotencyKey,int priority,SecurityUser securityUser){
        var route=controlPlaneService.route(agentId,priority);
        try{return runRepository.insert(runCode,conversationId,userId,agentId,input,serializeUser(securityUser),idempotencyKey,
                Math.max(1,workerProperties.getMaxAttempts()),priority,route.queueCapacity(),Math.max(1,workerProperties.getPerUserQueueCapacity()),
                route.groupCode(),objectMapper.writeValueAsString(route.requiredCapabilities()));}
        catch(JsonProcessingException exception){throw new BusinessException("Worker 能力路由序列化失败");}
    }

    private String newRunCode() {
        return "RUN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private int normalizePriority(Integer priority) {
        int max = Math.min(100, Math.max(0, workerProperties.getMaxPriority()));
        int value = priority == null ? workerProperties.getDefaultPriority() : priority;
        if (value < 0 || value > max) {
            throw new BusinessException("运行优先级必须在0到" + max + "之间");
        }
        return value;
    }

    private void requireAdministrator(SecurityUser securityUser) {
        boolean administrator = securityUser.roleCodes() != null && securityUser.roleCodes().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role));
        if (!administrator) {
            throw new BusinessException("无权管理 Agent 运行队列");
        }
    }

    private String serializeUser(SecurityUser securityUser) {
        try {
            return objectMapper.writeValueAsString(securityUser);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("用户运行上下文序列化失败");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new BusinessException("Idempotency-Key 长度不能超过128个字符");
        }
        return normalized;
    }

    private boolean isClosedStatus(String status) {
        return "completed".equals(status) || "waiting_approval".equals(status)
                || "waiting_event".equals(status) || "paused".equals(status)
                || "failed".equals(status) || "cancelled".equals(status);
    }

    private AgentRunResponse toResponse(AgentRunRecord record) {
        return new AgentRunResponse(
                record.runCode(), record.conversationId(), record.agentId(), record.status(), record.attemptCount(),
                record.maxAttempts(), record.priority(), record.taskId(), record.errorMessage(), record.cancelRequested(),
                record.availableAt(),
                record.createdAt(), record.startedAt(), record.finishedAt(),record.workerGroupCode(),record.requiredCapabilitiesJson()
        );
    }

    private record OptionalInsertResult(boolean inserted) {
    }
}

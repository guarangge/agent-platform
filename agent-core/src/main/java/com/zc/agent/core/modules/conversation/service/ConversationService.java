package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.attachment.service.ConversationAttachmentService;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.conversation.dto.ConversationPageQueryRequest;
import com.zc.agent.core.modules.conversation.dto.CreateConversationRequest;
import com.zc.agent.core.modules.conversation.dto.ManualTaskPlanContinueRequest;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.conversation.dto.TaskPlanControlRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationMemoryPolicyRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationTitleRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationInteractionPreferenceRequest;
import com.zc.agent.core.modules.conversation.model.AgentSnapshot;
import com.zc.agent.core.modules.conversation.model.ApprovalRecord;
import com.zc.agent.core.modules.conversation.model.ConversationExecutionOptions;
import com.zc.agent.core.modules.conversation.model.ConversationMemoryRecord;
import com.zc.agent.core.modules.conversation.model.ConversationRecord;
import com.zc.agent.core.modules.conversation.model.MessageRecord;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskDelegationRecord;
import com.zc.agent.core.modules.conversation.model.TaskMemorySnapshotRecord;
import com.zc.agent.core.modules.conversation.model.TaskPlanRecord;
import com.zc.agent.core.modules.conversation.model.TaskPlanStepRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.conversation.model.TaskTraceRecord;
import com.zc.agent.core.modules.conversation.model.UserMemoryRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.memory.service.ConversationMemoryPolicyResolver;
import com.zc.agent.core.modules.memory.service.MemoryResolverService;
import com.zc.agent.core.modules.memory.service.MemoryUpdaterService;
import com.zc.agent.core.modules.conversation.vo.ConversationDetailResponse;
import com.zc.agent.core.modules.conversation.vo.ConversationExecutionResponse;
import com.zc.agent.core.modules.conversation.vo.ConversationPageItemResponse;
import com.zc.agent.core.modules.conversation.vo.MessageResponse;
import com.zc.agent.core.modules.conversation.vo.SendMessageResponse;
import com.zc.agent.core.modules.conversation.vo.ApprovalResponse;
import com.zc.agent.core.modules.conversation.vo.ApprovalMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.TaskResponse;
import com.zc.agent.core.modules.conversation.vo.TaskArtifactDownload;
import com.zc.agent.core.modules.conversation.vo.TaskPlanResponse;
import com.zc.agent.core.modules.conversation.vo.TaskPlanDiffResponse;
import com.zc.agent.core.modules.conversation.vo.TaskPlanStepResponse;
import com.zc.agent.core.modules.conversation.vo.TaskStepResponse;
import com.zc.agent.core.modules.conversation.vo.TaskDelegationTreeResponse;
import com.zc.agent.core.modules.conversation.vo.TaskTraceResponse;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionAggregate;
import com.zc.agent.core.modules.runtime.model.RuntimeExecutionRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpointForkResult;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.cost.service.UsageAttributionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.service.ExecutionContextBuilder;
import com.zc.agent.core.modules.runtime.service.AgentExecutionPolicyResolver;
import com.zc.agent.core.modules.runtime.service.ExecutionDecisionService;
import com.zc.agent.core.modules.runtime.kernel.AgentLoopExecutionGateway;
import com.zc.agent.core.modules.runtime.kernel.ApprovalCoordinator;
import com.zc.agent.core.modules.runtime.kernel.CheckpointStore;
import com.zc.agent.core.modules.runtime.service.ExecutionPlannerService;
import com.zc.agent.core.modules.runtime.service.ObservationReplanRequiredException;
import com.zc.agent.core.modules.runtime.service.OutputGuardrailService;
import com.zc.agent.core.modules.runtime.service.PlanQualityService;
import com.zc.agent.core.modules.runtime.service.PlanValidatorService;
import com.zc.agent.core.modules.runtime.service.ExecutionStatusFlowService;
import com.zc.agent.core.modules.runtime.service.PlanStepExecutionException;
import com.zc.agent.core.modules.runtime.service.ContextCompactionService;
import com.zc.agent.core.modules.runtime.service.PromptContextBudgetService;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.modules.runtime.service.TaskPlanControlService;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.runtime.service.ArtifactResponseConsistencyService;
import com.zc.agent.core.security.SecurityUser;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.service.ToolSideEffectService;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * 会话服务。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_WAITING_APPROVAL = "waiting_approval";
    private static final String STATUS_TIMED_OUT = "timed_out";
    private static final String STATUS_REVOKED = "revoked";
    private static final String MESSAGE_TYPE_USER = "user";
    private static final String MESSAGE_TYPE_ASSISTANT = "assistant";
    private static final String EXECUTION_FAILURE_MESSAGE =
            "抱歉，本次任务未能完成。请重试；如持续失败，请前往任务运行中心查看错误详情。";
    private static final int DEFAULT_APPROVAL_TIMEOUT_MINUTES = 30;
    private final ConversationRepository conversationRepository;
    private final RegistryAgentClient registryAgentClient;
    private final ObjectMapper objectMapper;
    private final ExecutionContextBuilder executionContextBuilder;
    private final ExecutionDecisionService executionDecisionService;
    private final ExecutionPlannerService executionPlannerService;
    private final AgentLoopExecutionGateway agentLoopExecutionGateway;
    private final ExecutionStatusFlowService executionStatusFlowService;
    private final TaskTraceService taskTraceService;
    private final PlanQualityService planQualityService;
    private final PlanValidatorService planValidatorService;
    private final ContextCompactionService contextCompactionService;
    private final MemoryResolverService memoryResolverService;
    private final MemoryUpdaterService memoryUpdaterService;
    private final RecoveryValidationService recoveryValidationService;
    private final ApprovalRecoveryStateMachineService approvalRecoveryStateMachineService;
    private final OutputGuardrailService outputGuardrailService;
    private final ConversationRecoveryContextFactory recoveryContextFactory;
    private final ConversationPolicyService conversationPolicyService;
    private final ToolSideEffectService toolSideEffectService;
    private final TaskPlanControlService taskPlanControlService;
    private final TaskPlanLifecycleService taskPlanLifecycleService;
    private final CheckpointStore checkpointStore;
    private final AgentExecutionPolicyResolver agentExecutionPolicyResolver;
    private final AgentRunStreamEventService agentRunStreamEventService;
    private final ArtifactResponseConsistencyService artifactResponseConsistencyService;
    private final ApprovalCoordinator approvalCoordinator;
    private final ConversationAttachmentService attachmentService;

    public ConversationService(ConversationRepository conversationRepository,
                               RegistryAgentClient registryAgentClient,
                               ObjectMapper objectMapper,
                               ExecutionContextBuilder executionContextBuilder,
                               ExecutionDecisionService executionDecisionService,
                               ExecutionPlannerService executionPlannerService,
                               AgentLoopExecutionGateway agentLoopExecutionGateway,
                               ExecutionStatusFlowService executionStatusFlowService,
                               TaskTraceService taskTraceService,
                               PlanQualityService planQualityService,
                               PlanValidatorService planValidatorService,
                               ContextCompactionService contextCompactionService,
                               MemoryResolverService memoryResolverService,
                               MemoryUpdaterService memoryUpdaterService,
                               RecoveryValidationService recoveryValidationService,
                               ApprovalRecoveryStateMachineService approvalRecoveryStateMachineService,
                               OutputGuardrailService outputGuardrailService,
                               ConversationRecoveryContextFactory recoveryContextFactory,
                               ConversationPolicyService conversationPolicyService,
                               ToolSideEffectService toolSideEffectService,
                               TaskPlanControlService taskPlanControlService,
                               TaskPlanLifecycleService taskPlanLifecycleService,
                               CheckpointStore checkpointStore,
                               AgentExecutionPolicyResolver agentExecutionPolicyResolver,
                               AgentRunStreamEventService agentRunStreamEventService,
                               ArtifactResponseConsistencyService artifactResponseConsistencyService,
                               ApprovalCoordinator approvalCoordinator,
                               ConversationAttachmentService attachmentService) {
        this.conversationRepository = conversationRepository;
        this.registryAgentClient = registryAgentClient;
        this.objectMapper = objectMapper;
        this.executionContextBuilder = executionContextBuilder;
        this.executionDecisionService = executionDecisionService;
        this.executionPlannerService = executionPlannerService;
        this.agentLoopExecutionGateway = agentLoopExecutionGateway;
        this.executionStatusFlowService = executionStatusFlowService;
        this.taskTraceService = taskTraceService;
        this.planQualityService = planQualityService;
        this.planValidatorService = planValidatorService;
        this.contextCompactionService = contextCompactionService;
        this.memoryResolverService = memoryResolverService;
        this.memoryUpdaterService = memoryUpdaterService;
        this.recoveryValidationService = recoveryValidationService;
        this.approvalRecoveryStateMachineService = approvalRecoveryStateMachineService;
        this.outputGuardrailService = outputGuardrailService;
        this.recoveryContextFactory = recoveryContextFactory;
        this.conversationPolicyService = conversationPolicyService;
        this.toolSideEffectService = toolSideEffectService;
        this.taskPlanControlService = taskPlanControlService;
        this.taskPlanLifecycleService = taskPlanLifecycleService;
        this.checkpointStore = checkpointStore;
        this.agentExecutionPolicyResolver = agentExecutionPolicyResolver;
        this.agentRunStreamEventService = agentRunStreamEventService;
        this.artifactResponseConsistencyService = artifactResponseConsistencyService;
        this.approvalCoordinator = approvalCoordinator;
        this.attachmentService = attachmentService;
    }

    /**
     * 分页查询当前用户会话。
     *
     * @param request      查询条件
     * @param securityUser 当前用户
     * @return 分页结果
     */
    public PageResponse<ConversationPageItemResponse> pageConversations(ConversationPageQueryRequest request,
                                                                        SecurityUser securityUser) {
        ConversationPageQueryRequest normalizedRequest = normalizePageQuery(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        long total = conversationRepository.countConversations(securityUser.userId(), normalizedRequest.agentId());
        List<ConversationPageItemResponse> records = conversationRepository.pageConversations(
                        securityUser.userId(),
                        normalizedRequest.agentId(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toConversationPageItemResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 创建会话。
     *
     * @param request        创建请求
     * @param authorization  请求头 Authorization
     * @param securityUser   当前用户
     * @return 会话详情
     */
    @Transactional
    public ConversationDetailResponse createConversation(CreateConversationRequest request,
                                                         String authorization,
                                                         SecurityUser securityUser) {
        AgentSnapshot agentSnapshot = registryAgentClient.getEnabledAgentSnapshot(request.agentId(), authorization);
        String conversationCode = "CONV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Long conversationId = conversationRepository.insertConversation(
                conversationCode,
                securityUser.userId(),
                agentSnapshot,
                normalizeNullableText(request.title()),
                conversationPolicyService.buildMetadata(agentSnapshot, request),
                securityUser.userId()
        );
        return getConversationDetail(conversationId, securityUser);
    }

    /**
     * 查询会话详情。
     *
     * @param conversationId 会话 ID
     * @param securityUser   当前用户
     * @return 会话详情
     */
    public ConversationDetailResponse getConversationDetail(Long conversationId, SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        ConversationMemoryRecord conversationMemoryRecord = memoryResolverService.resolveConversationMemory(conversationId)
                .orElse(null);
        UserMemoryRecord userMemoryRecord = memoryResolverService.resolveUserMemory(securityUser.userId(), conversationRecord.agentId())
                .orElse(null);
        return toConversationDetailResponse(conversationRecord, conversationMemoryRecord, userMemoryRecord);
    }

    /**
     * 更新当前用户的会话名称。
     *
     * @param conversationId 会话 ID
     * @param request        标题更新请求
     * @param securityUser   当前用户
     * @return 更新后的会话详情
     */
    @Transactional
    public ConversationDetailResponse updateConversationTitle(Long conversationId,
                                                              UpdateConversationTitleRequest request,
                                                              SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        conversationRepository.updateConversationTitle(
                conversationId,
                securityUser.userId(),
                request.title().trim(),
                securityUser.userId()
        );
        return getConversationDetail(conversationId, securityUser);
    }

    /** 更新当前会话的交互偏好，不影响 Agent 或其他会话。 */
    @Transactional
    public ConversationDetailResponse updateConversationInteractionPreference(
            Long conversationId,
            UpdateConversationInteractionPreferenceRequest request,
            SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        conversationRepository.updateConversationStreamingPreference(
                conversationId,
                securityUser.userId(),
                request.responseStreamingEnabled(),
                securityUser.userId()
        );
        return getConversationDetail(conversationId, securityUser);
    }

    @Transactional
    public ConversationDetailResponse updateConversationMemoryPolicy(Long conversationId,
                                                                     UpdateConversationMemoryPolicyRequest request,
                                                                     String authorization,
                                                                     SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        AgentSnapshot agentSnapshot = resolveMemoryPolicyAgentSnapshot(conversationRecord, authorization);
        String metadataJson = conversationPolicyService.mergeMetadata(conversationRecord.metadata(), agentSnapshot, request);
        conversationRepository.updateConversationMetadata(conversationId, metadataJson, securityUser.userId());
        return getConversationDetail(conversationId, securityUser);
    }

    /**
     * 查询会话消息。
     *
     * @param conversationId 会话 ID
     * @param securityUser   当前用户
     * @return 消息列表
     */
    public List<MessageResponse> listMessages(Long conversationId, SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        return conversationRepository.findMessagesByConversationId(conversationId).stream()
                .filter(message -> !isRuntimeControlAssistantMessage(message))
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * 查询会话执行记录。
     *
     * @param conversationId 会话 ID
     * @param securityUser   当前用户
     * @return 执行记录
     */
    public ConversationExecutionResponse getConversationExecutions(Long conversationId, SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        refreshApprovalTimeouts(conversationId, securityUser.userId());
        List<TaskResponse> tasks = conversationRepository.findTasksByConversationId(conversationId).stream()
                .map(this::toTaskResponse)
                .toList();
        return new ConversationExecutionResponse(conversationId, tasks);
    }

    public ApprovalMetricsResponse getApprovalMetrics(Long conversationId, SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        refreshApprovalTimeouts(conversationId, securityUser.userId());
        List<TaskRecord> tasks = conversationRepository.findTasksByConversationId(conversationId);
        List<ApprovalRecord> approvals = tasks.stream()
                .flatMap(task -> conversationRepository.findApprovalsByTaskId(task.taskId()).stream())
                .toList();
        int totalCount = approvals.size();
        int pendingCount = 0;
        int approvedCount = 0;
        int rejectedCount = 0;
        int timedOutCount = 0;
        int revokedCount = 0;
        int overduePendingCount = 0;
        long totalWaitMs = 0L;
        long maxWaitMs = 0L;
        int finishedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalRecord approval : approvals) {
            switch (approval.status()) {
                case STATUS_PENDING -> {
                    pendingCount++;
                    if (isApprovalTimedOut(approval, now)) {
                        overduePendingCount++;
                    }
                }
                case "approved" -> {
                    approvedCount++;
                    if (approval.approvedAt() != null && approval.createdAt() != null) {
                        long waitMs = java.time.Duration.between(approval.createdAt(), approval.approvedAt()).toMillis();
                        totalWaitMs += waitMs;
                        maxWaitMs = Math.max(maxWaitMs, waitMs);
                        finishedCount++;
                    }
                }
                case "rejected" -> rejectedCount++;
                case STATUS_TIMED_OUT -> timedOutCount++;
                case STATUS_REVOKED -> revokedCount++;
                default -> {
                }
            }
        }
        return new ApprovalMetricsResponse(
                conversationId,
                totalCount,
                pendingCount,
                approvedCount,
                rejectedCount,
                timedOutCount,
                revokedCount,
                overduePendingCount,
                finishedCount == 0 ? 0L : totalWaitMs / finishedCount,
                maxWaitMs
        );
    }

    public TaskDelegationTreeResponse getDelegationTree(Long conversationId, SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        List<TaskRecord> tasks = conversationRepository.findTasksByConversationId(conversationId);
        List<TaskDelegationRecord> delegations = conversationRepository.findTaskDelegationsByConversationId(conversationId);
        Map<Long, TaskRecord> taskMap = tasks.stream().collect(java.util.stream.Collectors.toMap(TaskRecord::taskId, item -> item));
        Map<Long, TaskDelegationRecord> delegationByChildTaskId = delegations.stream()
                .filter(item -> item.childTaskId() != null)
                .collect(java.util.stream.Collectors.toMap(TaskDelegationRecord::childTaskId, item -> item, (left, right) -> left));
        Map<Long, Integer> stepCountMap = new LinkedHashMap<>();
        Map<Long, Integer> traceCountMap = new LinkedHashMap<>();
        Map<Long, Integer> approvalCountMap = new LinkedHashMap<>();
        for (TaskRecord task : tasks) {
            stepCountMap.put(task.taskId(), conversationRepository.findTaskStepsByTaskId(task.taskId()).size());
            traceCountMap.put(task.taskId(), conversationRepository.findTaskTracesByTaskId(task.taskId()).size());
            approvalCountMap.put(task.taskId(), conversationRepository.findApprovalsByTaskId(task.taskId()).size());
        }
        Map<Long, List<TaskRecord>> childrenMap = tasks.stream()
                .filter(item -> item.parentTaskId() != null)
                .collect(java.util.stream.Collectors.groupingBy(TaskRecord::parentTaskId));
        List<TaskDelegationTreeResponse.DelegationTreeNodeResponse> roots = tasks.stream()
                .filter(item -> item.parentTaskId() == null)
                .sorted(java.util.Comparator.comparing(TaskRecord::taskId).reversed())
                .map(item -> buildDelegationNode(
                        item,
                        childrenMap,
                        stepCountMap,
                        traceCountMap,
                        approvalCountMap,
                        delegationByChildTaskId,
                        conversationRecord.agentName(),
                        0
                ))
                .toList();
        int maxDepth = roots.stream().mapToInt(TaskDelegationTreeResponse.DelegationTreeNodeResponse::depth).max().orElse(0);
        List<TaskDelegationTreeResponse.DelegationAuditResponse> audits = delegations.stream()
                .map(item -> {
                    TaskRecord parentTask = taskMap.get(item.parentTaskId());
                    TaskRecord childTask = taskMap.get(item.childTaskId());
                    return new TaskDelegationTreeResponse.DelegationAuditResponse(
                            item.delegationId(),
                            item.parentTaskId(),
                            parentTask == null ? null : parentTask.taskCode(),
                            item.parentTaskStepId(),
                            item.childTaskId(),
                            childTask == null ? null : childTask.taskCode(),
                            childTask == null ? null : childTask.status(),
                            item.delegateAgentId(),
                            item.delegateAgentCode(),
                            item.delegateAgentName(),
                            item.delegateInstruction(),
                            item.traceId(),
                            item.status(),
                            childTask == null ? null : buildDelegationResultSummary(childTask),
                            item.outputSnapshot(),
                            item.errorMessage(),
                            buildAuditChain(item, taskMap),
                            item.createdAt() == null ? null : item.createdAt().toString(),
                            item.updatedAt() == null ? null : item.updatedAt().toString()
                    );
                })
                .toList();
        long delegatedTaskCount = tasks.stream().filter(item -> item.parentTaskId() != null).count();
        long successCount = tasks.stream().filter(item -> STATUS_SUCCESS.equals(item.status())).count();
        long failedCount = tasks.stream().filter(item -> STATUS_FAILED.equals(item.status())).count();
        long waitingApprovalCount = tasks.stream().filter(item -> STATUS_WAITING_APPROVAL.equals(item.status())).count();
        return new TaskDelegationTreeResponse(
                conversationId,
                new TaskDelegationTreeResponse.DelegationTreeSummaryResponse(
                        tasks.size(),
                        (int) delegatedTaskCount,
                        (int) successCount,
                        (int) failedCount,
                        (int) waitingApprovalCount,
                        maxDepth
                ),
                roots,
                audits
        );
    }

    /**
     * 发送消息并生成最小助手响应。
     *
     * @param conversationId 会话 ID
     * @param request        消息请求
     * @param securityUser   当前用户
     * @return 响应结果
     */
    public SendMessageResponse sendMessage(Long conversationId,
                                           SendMessageRequest request,
                                           String authorization,
                                           SecurityUser securityUser) {
        return sendMessageInternal(
                conversationId,
                request,
                authorization,
                securityUser,
                ConversationExecutionOptions.defaults(),
                null
        );
    }

    /**
     * Eval Harness 隔离执行入口。
     *
     * @param conversationId 会话 ID
     * @param request        消息请求
     * @param authorization  原始 Authorization 请求头
     * @param securityUser   当前用户
     * @return 执行结果
     */
    public SendMessageResponse sendMessageForEvaluation(Long conversationId,
                                                        SendMessageRequest request,
                                                        String authorization,
                                                        SecurityUser securityUser) {
        return sendMessageInternal(
                conversationId,
                request,
                authorization,
                securityUser,
                ConversationExecutionOptions.evalIsolation(),
                null
        );
    }

    /**
     * 持久化 Worker 执行入口，不依赖用户访问 Token 读取注册中心配置。
     *
     * @param conversationId 会话 ID
     * @param request 消息请求
     * @param securityUser 入队时固化的用户上下文
     * @param runCode 异步运行编码
     * @return 消息执行结果
     */
    public SendMessageResponse sendMessageFromRun(Long conversationId,
                                                  SendMessageRequest request,
                                                  SecurityUser securityUser,
                                                  String runCode) {
        return sendMessageInternal(
                conversationId,
                request,
                "Service agent-core",
                securityUser,
                ConversationExecutionOptions.defaults(),
                runCode
        );
    }

    /** Worker 重试入口：已有 Task 时从持久化 Checkpoint 续跑，禁止重复插入用户消息。 */
    public TaskResponse executeMessageFromRun(Long conversationId,
                                              SendMessageRequest request,
                                              SecurityUser securityUser,
                                              String runCode) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        AgentRunStreamingContext.configureResponseStreaming(
                conversationPolicyService.resolveResponseStreamingEnabled(conversationRecord.metadata()));
        return conversationRepository.findTaskByRunCode(runCode)
                .map(task -> {
                    if (STATUS_SUCCESS.equals(task.status()) || STATUS_WAITING_APPROVAL.equals(task.status())) {
                        return toTaskResponse(task);
                    }
                    agentRunStreamEventService.publishProgress("recovery", "正在恢复上次中断的执行现场", Map.of(
                            "taskId", task.taskId()
                    ));
                    checkpointStore.prepareAutomaticRecovery(task.taskId(), securityUser.userId());
                    return resumeTask(conversationId, task.taskId(), "Service agent-core", securityUser);
                })
                .orElseGet(() -> sendMessageFromRun(conversationId, request, securityUser, runCode).task());
    }

    /** 判断异常退出的异步任务是否已经形成可持久恢复现场。 */
    public boolean hasRecoverableCheckpoint(Long taskId) {
        return taskId != null && checkpointStore.latest(taskId)
                .filter(checkpoint -> !"waiting_approval".equals(checkpoint.status()))
                .isPresent();
    }

    /** 处理 Checkpoint 副作用不确定状态，操作前校验任务归属。 */
    public RuntimeCheckpoint resolveCheckpointAmbiguity(Long conversationId,
                                                                             Long taskId,
                                                                             ResolveCheckpointAmbiguityRequest request,
                                                                             SecurityUser securityUser) {
        ConversationRecord conversation = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord task = requireOwnedTask(conversationId, taskId, securityUser);
        MessageRecord message = conversationRepository.findMessageById(task.messageId())
                .orElseThrow(() -> new BusinessException("任务原始用户消息不存在"));
        ExecutionContext context = executionContextBuilder.build(
                securityUser, conversation, message.messageId(), message.content(), message.sequenceNo(), taskId,
                task.taskCode(), ensureTraceId(task, securityUser.userId()), "Service agent-core");
        return checkpointStore.resolveAmbiguity(
                context, taskId, request, securityUser.userId());
    }

    /** 从指定 Checkpoint 创建独立任务分支，并立即进入标准恢复执行链。 */
    public TaskResponse forkFromCheckpoint(Long conversationId,
                                           Long taskId,
                                           Long checkpointId,
                                           SecurityUser securityUser) {
        requireOwnedTask(conversationId, taskId, securityUser);
        RuntimeCheckpointForkResult fork = checkpointStore.fork(
                taskId, checkpointId, securityUser.userId());
        taskTraceService.recordSpan(
                fork.taskId(), fork.traceId(), null, "checkpoint_fork", "从 Checkpoint 创建执行分支", "success",
                Map.of("sourceTaskId", taskId, "sourceCheckpointId", checkpointId),
                Map.of("forkTaskId", fork.taskId(), "resumeStepNo", fork.resumeStepNo()),
                null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "checkpoint_fork"), securityUser.userId()
        );
        return resumeTask(conversationId, fork.taskId(), "Service agent-core", securityUser);
    }

    public Map<String, Object> controlTaskPlan(Long conversationId,
                                               Long taskId,
                                               TaskPlanControlRequest request,
                                               SecurityUser securityUser) {
        requireOwnedTask(conversationId, taskId, securityUser);
        Map<String, Object> control = taskPlanControlService.request(taskId, request.action(), request.targetStepNo(),
                request.comment(), securityUser.userId());
        if (!"resume".equals(request.action()) || Boolean.TRUE.equals(control.get("async"))) {
            return control;
        }
        TaskResponse resumed = resumeTask(conversationId, taskId, "Service agent-core", securityUser);
        Map<String, Object> result = new LinkedHashMap<>(control);
        result.put("status", resumed.status());
        result.put("task", resumed);
        return result;
    }

    public Map<String, Object> previewTaskPlanControl(Long conversationId,
                                                      Long taskId,
                                                      String action,
                                                      Integer targetStepNo,
                                                      SecurityUser securityUser) {
        requireOwnedTask(conversationId, taskId, securityUser);
        TaskPlanRecord planRecord = conversationRepository.findTaskPlansByTaskId(taskId).stream()
                .findFirst().orElseThrow(() -> new BusinessException("任务计划不存在"));
        ExecutionPlan plan = toExecutionPlan(planRecord);
        return taskPlanControlService.preview(plan, action, targetStepNo);
    }

    private TaskRecord requireOwnedTask(Long conversationId, Long taskId, SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(task -> task.taskId().equals(taskId))
                .findFirst().orElseThrow(() -> new BusinessException("任务不属于当前会话"));
    }

    private SendMessageResponse sendMessageInternal(Long conversationId,
                                                    SendMessageRequest request,
                                                    String authorization,
                                                    SecurityUser securityUser,
                                                    ConversationExecutionOptions executionOptions,
                                                    String runCode) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        AgentRunStreamingContext.configureResponseStreaming(
                conversationPolicyService.resolveResponseStreamingEnabled(conversationRecord.metadata()));
        agentRunStreamEventService.publishProgress("accepted", "已接收消息，正在创建执行任务", Map.of());
        String normalizedContent = request.normalizedContent();
        if (normalizedContent.isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }

        ConversationRepository.InsertedMessage insertedUserMessage = conversationRepository.insertMessageWithAllocatedSequence(
                conversationId,
                MESSAGE_TYPE_USER,
                normalizedContent,
                STATUS_SUCCESS,
                null,
                securityUser.userId()
        );
        int userSequenceNo = insertedUserMessage.sequenceNo();
        Long userMessageId = insertedUserMessage.messageId();
        attachmentService.bindToMessage(
                conversationId, request.normalizedAttachmentIds(), runCode, userMessageId, securityUser);
        String title = conversationRecord.title();
        if (title == null || title.isBlank()) {
            title = buildConversationTitle(normalizedContent);
        }
        executionStatusFlowService.markProcessing(
                conversationId,
                truncatePreview(normalizedContent),
                title,
                securityUser.userId()
        );

        String taskCode = "TASK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String traceId = "TRACE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        AgentSnapshot agentSnapshot = new AgentSnapshot(
                conversationRecord.agentId(),
                conversationRecord.agentCode(),
                conversationRecord.agentName(),
                conversationRecord.defaultModel(),
                conversationRecord.defaultPromptId(),
                conversationRecord.defaultPromptName(),
                conversationRecord.decisionPromptId(),
                conversationRecord.decisionPromptName(),
                conversationRecord.plannerPromptId(),
                conversationRecord.plannerPromptName(),
                conversationRecord.status(),
                null
        );
        Long taskId = conversationRepository.insertTask(
                taskCode,
                traceId,
                conversationId,
                userMessageId,
                agentSnapshot,
                securityUser.userId()
        );
        if (runCode != null && !runCode.isBlank()) {
            conversationRepository.bindTaskRunCode(taskId, runCode, securityUser.userId());
        }

        agentRunStreamEventService.publishProgress("context", "正在组装会话上下文", Map.of(
                "taskId", taskId,
                "traceId", traceId
        ));
        LocalDateTime contextStartedAt = LocalDateTime.now();
        ExecutionContext executionContext = executionContextBuilder.build(
                securityUser,
                conversationRecord,
                userMessageId,
                normalizedContent,
                userSequenceNo,
                taskId,
                taskCode,
                traceId,
                authorization,
                executionOptions.contextBuildOptions()
        );
        UsageAttributionContext.bind(executionContext);
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "context_build", "构建执行上下文", "success",
                Map.of("conversationId", conversationId, "messageId", userMessageId),
                Map.of("agentCode", executionContext.agentContext().agentCode(), "toolCount", executionContext.availableTools().size(), "workflowCount", executionContext.availableWorkflows().size()),
                null, contextStartedAt, LocalDateTime.now(), Map.of("entry", executionOptions.traceEntry()), securityUser.userId()
        );
        agentRunStreamEventService.publishProgress("decision", "正在判断任务处理方式", Map.of(
                "taskId", taskId
        ));
        LocalDateTime decisionStartedAt = LocalDateTime.now();
        ExecutionDecision executionDecision = executionDecisionService.decide(executionContext);
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "decision", "执行决策", "success",
                Map.of("userMessage", normalizedContent),
                Map.of("decisionType", executionDecision.decisionType(), "reason", executionDecision.reason()),
                null, decisionStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        executionOptions.traceEntry(),
                        executionDecisionService.buildDecisionTraceMetadata(executionContext)
                ), securityUser.userId()
        );
        conversationRepository.updateTaskExecutionType(
                taskId,
                resolveExecutionType(executionDecision),
                securityUser.userId()
        );
        agentRunStreamEventService.publishProgress("planning", "正在生成执行计划", Map.of(
                "taskId", taskId,
                "decisionType", executionDecision.decisionType()
        ));
        LocalDateTime planningStartedAt = LocalDateTime.now();
        List<ExecutionPlannerService.PlanTemplateHint> initialTemplateHints = taskPlanLifecycleService.resolveTemplateHints(securityUser.userId(), executionContext.agentContext().agentId());
        ExecutionPlan executionPlan = executionPlannerService.buildPlan(
                executionContext,
                executionDecision,
                null,
                initialTemplateHints
        );
        executionPlan = ensureUniquePlanId(executionPlan, taskId);
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "planning", "生成执行计划", "success",
                Map.of("decisionType", executionDecision.decisionType()),
                Map.of("planId", executionPlan.planId(), "planType", executionPlan.planType(), "stepCount", executionPlan.steps().size()),
                null, planningStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        executionOptions.traceEntry(),
                        executionPlannerService.buildPlanningTraceMetadata(
                                executionContext,
                                executionDecision,
                                null,
                                initialTemplateHints
                        )
                ), securityUser.userId()
        );
        RuntimeExecutionAggregate planExecutionAggregate;
        try {
            taskPlanLifecycleService.persistSnapshot(taskId, executionContext, executionPlan, "initial", securityUser.userId());
            conversationRepository.updateTaskExecutionChain(
                    taskId,
                    buildExecutionChainJson(executionContext, executionPlan),
                    securityUser.userId()
            );
            String inputSnapshot = toJsonText(new ExecutionInputSnapshot(
                    executionContext.userMessage(),
                    executionContext.promptContext().promptCode(),
                    executionContext.agentContext().agentCode(),
                    executionContext.agentContext().agentName(),
                    executionContext.promptContext().promptName(),
                    executionContext.promptContext().promptType(),
                    executionContext.promptContext().promptCategory(),
                    executionContext.promptContext().promptContent(),
                    executionContext.decisionPromptContext().promptName(),
                    executionContext.decisionPromptSummary(),
                    executionContext.plannerPromptContext() == null ? null : executionContext.plannerPromptContext().promptName(),
                    executionContext.plannerPromptSummary(),
                    executionDecision.firstWorkflowName(),
                    executionContext.effectiveToolLabels(),
                    executionDecision.decisionType(),
                    executionDecision.reason(),
                    executionPlan.planId(),
                    executionPlan.goal(),
                    executionPlan.planType(),
                    executionPlan.steps().stream()
                            .map(step -> step.stepNo() + ":" + step.stepType() + ":" + step.stepName() + ":" + step.objective())
                            .toList()
            ));
            agentRunStreamEventService.publishProgress("execution", "计划已生成，开始执行", Map.of(
                    "taskId", taskId,
                    "stepCount", executionPlan.steps().size()
            ));
            planExecutionAggregate = executeInitialPlan(
                    executionContext,
                    executionDecision,
                    executionPlan,
                    taskId,
                    traceId,
                    inputSnapshot,
                    authorization,
                    securityUser
            );
        } catch (CancellationException exception) {
            executionStatusFlowService.markCancelled(
                    taskId,
                    conversationId,
                    truncatePreview(normalizedContent),
                    title,
                    securityUser.userId()
            );
            taskPlanLifecycleService.updateFeedback(
                    taskId, "cancelled", null, exception.getMessage(), securityUser.userId());
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "任务取消", "cancelled",
                    Map.of("taskCode", taskCode), Map.of("status", "cancelled"),
                    exception.getMessage(), LocalDateTime.now(), LocalDateTime.now(),
                    Map.of("entry", executionOptions.traceEntry()), securityUser.userId()
            );
            throw exception;
        } catch (Exception exception) {
            executionStatusFlowService.markFailed(
                    taskId,
                    conversationId,
                    exception.getMessage(),
                    truncatePreview(normalizedContent),
                    title,
                    securityUser.userId()
            );
            taskPlanLifecycleService.updateFeedback(taskId, STATUS_FAILED, null, exception.getMessage(), securityUser.userId());
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "任务结束", "failed",
                    Map.of("taskCode", taskCode),
                    Map.of(),
                    exception.getMessage(), LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", executionOptions.traceEntry()), securityUser.userId()
            );
            safeRunPostExecution("记录失败助手消息", () ->
                    conversationRepository.insertMessageWithAllocatedSequence(
                            conversationId,
                            MESSAGE_TYPE_ASSISTANT,
                            EXECUTION_FAILURE_MESSAGE,
                            STATUS_FAILED,
                            exception.getMessage(),
                            securityUser.userId()
                    ));
            throw exception;
        }
        String assistantContent = applyOutputGuardrails(executionContext, planExecutionAggregate.assistantContent(), taskId, traceId, securityUser.userId());
        Long assistantMessageId = persistAssistantMessageIfCompleted(
                conversationId, assistantContent, planExecutionAggregate, securityUser.userId());
        if (planExecutionAggregate.paused()) {
            executionStatusFlowService.markPaused(taskId, conversationId, truncatePreview(normalizedContent), title, securityUser.userId());
        } else if (planExecutionAggregate.waitingEvent()) {
            executionStatusFlowService.markWaitingEvent(taskId, conversationId, truncatePreview(normalizedContent), title, securityUser.userId());
        } else if (planExecutionAggregate.waitingApproval()) {
            executionStatusFlowService.markWaitingApproval(
                    taskId,
                    conversationId,
                    truncatePreview(normalizedContent),
                    title,
                    securityUser.userId()
            );
            safeRunPostExecution("更新等待审批执行反馈", () ->
                    taskPlanLifecycleService.updateFeedback(taskId, STATUS_WAITING_APPROVAL, assistantContent, null, securityUser.userId()));
            safeRunPostExecution("记录等待审批任务 Trace", () ->
                    taskTraceService.recordSpan(
                            taskId, traceId, null,
                            "task_result", "任务挂起", "waiting_approval",
                            Map.of("taskCode", taskCode),
                            Map.of("status", "waiting_approval"),
                            null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", executionOptions.traceEntry()), securityUser.userId()
                    ));
        } else {
            executionStatusFlowService.markSuccess(
                    taskId,
                    conversationId,
                    truncatePreview(assistantContent),
                    title,
                    securityUser.userId()
            );
            safeRunPostExecution("更新成功执行反馈", () ->
                    taskPlanLifecycleService.updateFeedback(taskId, STATUS_SUCCESS, assistantContent, null, securityUser.userId()));
            if (!executionOptions.skipMemoryUpdate()) {
                safeRunPostExecution("更新会话记忆", () ->
                        memoryUpdaterService.updateAfterMessage(
                                conversationId,
                                taskId,
                                agentSnapshot.agentName(),
                                normalizedContent,
                                assistantContent,
                                executionContext,
                                planExecutionAggregate.executionPlan(),
                                securityUser.userId()
                        ));
            }
            safeRunPostExecution("记录任务完成 Trace", () ->
                    taskTraceService.recordSpan(
                            taskId, traceId, null,
                            "task_result", "任务完成", "success",
                            Map.of("taskCode", taskCode),
                            Map.of("status", "success"),
                            null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", executionOptions.traceEntry()), securityUser.userId()
                    ));
        }
        return buildSendMessageResponseSafely(
                conversationRecord,
                conversationId,
                userMessageId,
                assistantMessageId,
                taskId,
                normalizedContent,
                assistantContent,
                securityUser
        );
    }

    /**
     * 从当前任务首个失败步骤恢复执行。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param authorization  原始 Authorization 请求头
     * @param securityUser   当前用户
     * @return 恢复后的任务结果
     */
    public TaskResponse resumeTask(Long conversationId,
                                   Long taskId,
                                   String authorization,
                                   SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在"));
        String traceId = ensureTraceId(taskRecord, securityUser.userId());
        approvalRecoveryStateMachineService.validateTaskCanResume(taskRecord);
        MessageRecord userMessageRecord = conversationRepository.findMessageById(taskRecord.messageId())
                .orElseThrow(() -> new BusinessException("任务原始用户消息不存在"));
        List<TaskStepRecord> existingTaskSteps = conversationRepository.findTaskStepsByTaskId(taskId);
        TaskStepRecord failedTaskStep = existingTaskSteps.stream()
                .filter(item -> STATUS_FAILED.equals(item.status()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前任务没有可恢复的失败步骤"));
        approvalRecoveryStateMachineService.validateFailedRecoveryStep(failedTaskStep);
        recoveryValidationService.validateRecoverableApprovalState(
                taskRecord,
                failedTaskStep,
                conversationRepository.findApprovalsByTaskId(taskRecord.taskId())
        );

        AgentSnapshot agentSnapshot = new AgentSnapshot(
                conversationRecord.agentId(),
                conversationRecord.agentCode(),
                conversationRecord.agentName(),
                conversationRecord.defaultModel(),
                conversationRecord.defaultPromptId(),
                conversationRecord.defaultPromptName(),
                conversationRecord.decisionPromptId(),
                conversationRecord.decisionPromptName(),
                conversationRecord.plannerPromptId(),
                conversationRecord.plannerPromptName(),
                conversationRecord.status(),
                null
        );
        LocalDateTime contextStartedAt = LocalDateTime.now();
        ExecutionContext executionContext = executionContextBuilder.build(
                securityUser,
                conversationRecord,
                userMessageRecord.messageId(),
                userMessageRecord.content(),
                userMessageRecord.sequenceNo(),
                taskId,
                taskRecord.taskCode(),
                traceId,
                authorization
        );
        UsageAttributionContext.bind(executionContext);
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "context_build", "恢复时构建执行上下文", "success",
                Map.of("conversationId", conversationId, "messageId", userMessageRecord.messageId()),
                Map.of("agentCode", executionContext.agentContext().agentCode(), "toolCount", executionContext.availableTools().size(), "workflowCount", executionContext.availableWorkflows().size()),
                null, contextStartedAt, LocalDateTime.now(), Map.of("entry", "resume_task"), securityUser.userId()
        );
        RuntimeCheckpoint durableCheckpoint = checkpointStore.latest(taskId).orElse(null);
        LocalDateTime decisionStartedAt = LocalDateTime.now();
        ExecutionDecision executionDecision = durableCheckpoint == null
                ? executionDecisionService.decide(executionContext)
                : durableCheckpoint.decision();
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "decision", "恢复时执行决策", "success",
                Map.of("userMessage", userMessageRecord.content()),
                Map.of("decisionType", executionDecision.decisionType(), "reason", executionDecision.reason()),
                null, decisionStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        "resume_task",
                        executionDecisionService.buildDecisionTraceMetadata(executionContext)
                ), securityUser.userId()
        );
        conversationRepository.updateTaskExecutionType(
                taskId,
                resolveExecutionType(executionDecision),
                securityUser.userId()
        );
        LocalDateTime planningStartedAt = LocalDateTime.now();
        List<ExecutionPlannerService.PlanTemplateHint> resumeTemplateHints = taskPlanLifecycleService.resolveTemplateHints(securityUser.userId(), executionContext.agentContext().agentId());
        ExecutionPlan executionPlan;
        if (durableCheckpoint == null) {
            executionPlan = ensureUniquePlanId(
                    executionPlannerService.buildPlan(executionContext, executionDecision, null, resumeTemplateHints),
                    taskId
            );
        } else {
            executionPlan = durableCheckpoint.plan();
        }
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "planning", "恢复时生成执行计划", "success",
                Map.of("decisionType", executionDecision.decisionType()),
                Map.of("planId", executionPlan.planId(), "planType", executionPlan.planType(), "stepCount", executionPlan.steps().size()),
                null, planningStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        "resume_task",
                        executionPlannerService.buildPlanningTraceMetadata(
                                executionContext,
                                executionDecision,
                                null,
                                resumeTemplateHints
                        )
                ), securityUser.userId()
        );
        RuntimeExecutionAggregate planExecutionAggregate;
        try {
            taskPlanLifecycleService.persistSnapshot(taskId, executionContext, executionPlan, "resume", securityUser.userId());
            conversationRepository.updateTaskExecutionChain(
                    taskId,
                    buildExecutionChainJson(executionContext, executionPlan),
                    securityUser.userId()
            );
            ExecutionPlan.ExecutionPlanStep failedPlanStep = recoveryValidationService.resolveRecoverablePlanStep(
                    failedTaskStep, executionPlan);
            recoveryValidationService.validateRecoverablePlan(failedTaskStep, failedPlanStep);
            if (durableCheckpoint != null) {
                checkpointStore.markRestored(durableCheckpoint, "task_resume");
            }
            List<RuntimeStepResultContext> previousStepResults = durableCheckpoint == null
                    ? recoveryContextFactory.previousResults(existingTaskSteps, failedPlanStep.stepNo())
                    : durableCheckpoint.results();
            WorkflowResumeContext workflowResumeContext = "workflow".equals(failedPlanStep.stepType())
                    ? recoveryContextFactory.workflowResume(failedTaskStep)
                    : null;
            String inputSnapshot = toJsonText(new ExecutionInputSnapshot(
                    executionContext.userMessage(),
                    executionContext.promptContext().promptCode(),
                    executionContext.agentContext().agentCode(),
                    executionContext.agentContext().agentName(),
                    executionContext.promptContext().promptName(),
                    executionContext.promptContext().promptType(),
                    executionContext.promptContext().promptCategory(),
                    executionContext.promptContext().promptContent(),
                    executionContext.decisionPromptContext().promptName(),
                    executionContext.decisionPromptSummary(),
                    executionContext.plannerPromptContext() == null ? null : executionContext.plannerPromptContext().promptName(),
                    executionContext.plannerPromptSummary(),
                    executionDecision.firstWorkflowName(),
                    executionContext.effectiveToolLabels(),
                    executionDecision.decisionType(),
                    executionDecision.reason(),
                    executionPlan.planId(),
                    executionPlan.goal(),
                    executionPlan.planType(),
                    executionPlan.steps().stream()
                            .map(step -> step.stepNo() + ":" + step.stepType() + ":" + step.stepName() + ":" + step.objective())
                            .toList()
            ));
            conversationRepository.softDeleteTaskStepsFromStepNo(taskId, failedPlanStep.stepNo(), securityUser.userId());
            executionStatusFlowService.markProcessing(
                    conversationId,
                    truncatePreview(userMessageRecord.content()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            planExecutionAggregate = executePlanWithAutoReplan(
                    executionContext,
                    executionDecision,
                    executionPlan,
                    taskId,
                    traceId,
                    inputSnapshot,
                    authorization,
                    securityUser.userId(),
                    failedPlanStep.stepNo(),
                    0,
                    previousStepResults,
                    new RuntimeExecutionRequest.RecoveryReplaySnapshot(
                            true,
                            failedTaskStep.taskStepId(),
                            failedTaskStep.stepNo(),
                            failedTaskStep.stepName(),
                            previousStepResults.stream()
                                    .map(item -> item.stepNo() + ":" + item.stepName() + ":" + item.summary())
                                    .toList()
                    ),
                    null,
                    workflowResumeContext
            );
        } catch (Exception exception) {
            executionStatusFlowService.markFailed(
                    taskId,
                    conversationId,
                    exception.getMessage(),
                    truncatePreview(userMessageRecord.content()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "恢复执行结束", "failed",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "failed"),
                    exception.getMessage(), LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "resume_task"), securityUser.userId()
            );
            throw exception;
        }
        String assistantContent = applyOutputGuardrails(executionContext, planExecutionAggregate.assistantContent(), taskId, traceId, securityUser.userId());
        persistAssistantMessageIfCompleted(conversationId, assistantContent, planExecutionAggregate, securityUser.userId());
        if (planExecutionAggregate.paused()) {
            executionStatusFlowService.markPaused(taskId, conversationId, truncatePreview(userMessageRecord.content()), conversationRecord.title(), securityUser.userId());
        } else if (planExecutionAggregate.waitingEvent()) {
            executionStatusFlowService.markWaitingEvent(taskId, conversationId, truncatePreview(userMessageRecord.content()), conversationRecord.title(), securityUser.userId());
        } else if (planExecutionAggregate.waitingApproval()) {
            executionStatusFlowService.markWaitingApproval(
                    taskId,
                    conversationId,
                    truncatePreview(userMessageRecord.content()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "恢复执行挂起", "waiting_approval",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "waiting_approval"),
                    null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "resume_task"), securityUser.userId()
            );
        } else {
            executionStatusFlowService.markSuccess(
                    taskId,
                    conversationId,
                    truncatePreview(assistantContent),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            memoryUpdaterService.updateAfterMessage(
                    conversationId,
                    taskId,
                    agentSnapshot.agentName(),
                    userMessageRecord.content(),
                    assistantContent,
                    executionContext,
                    planExecutionAggregate.executionPlan(),
                    securityUser.userId()
            );
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "恢复执行完成", "success",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "success"),
                    null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "resume_task"), securityUser.userId()
            );
        }
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException("恢复后的任务查询失败"));
    }

    public TaskPlanDiffResponse getTaskPlanDiff(Long conversationId,
                                                Long taskId,
                                                Integer fromVersion,
                                                Integer toVersion,
                                                SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        List<TaskPlanRecord> plans = conversationRepository.findTaskPlansByTaskId(taskId);
        if (plans.isEmpty()) {
            throw new BusinessException("任务计划不存在");
        }
        TaskPlanRecord toPlan = resolveTaskPlanRecord(plans, toVersion);
        TaskPlanRecord fromPlan = resolveTaskPlanRecord(plans, fromVersion == null ? toPlan.planVersion() - 1 : fromVersion);
        if (fromPlan == null) {
            throw new BusinessException("未找到对比版本");
        }
        return buildTaskPlanDiffResponse(taskId, fromPlan, toPlan);
    }

    public TaskResponse continueTaskWithManualPlan(Long conversationId,
                                                   Long taskId,
                                                   ManualTaskPlanContinueRequest request,
                                                   String authorization,
                                                   SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在"));
        if (!STATUS_FAILED.equals(taskRecord.status())) {
            throw new BusinessException("当前仅支持对失败任务进行人工改计划续跑");
        }
        String traceId = ensureTraceId(taskRecord, securityUser.userId());
        MessageRecord userMessageRecord = conversationRepository.findMessageById(taskRecord.messageId())
                .orElseThrow(() -> new BusinessException("任务原始用户消息不存在"));
        List<TaskStepRecord> existingTaskSteps = conversationRepository.findTaskStepsByTaskId(taskId);
        TaskStepRecord failedTaskStep = existingTaskSteps.stream()
                .filter(item -> STATUS_FAILED.equals(item.status()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前任务没有失败步骤"));

        LocalDateTime contextStartedAt = LocalDateTime.now();
        ExecutionContext executionContext = executionContextBuilder.build(
                securityUser,
                conversationRecord,
                userMessageRecord.messageId(),
                userMessageRecord.content(),
                userMessageRecord.sequenceNo(),
                taskId,
                taskRecord.taskCode(),
                traceId,
                authorization
        );
        UsageAttributionContext.bind(executionContext);
        taskTraceService.recordSpan(
                taskId, traceId, failedTaskStep.taskStepId(),
                "planning", "人工改计划续跑构建上下文", "success",
                Map.of("conversationId", conversationId, "taskId", taskId),
                Map.of("taskCode", taskRecord.taskCode()),
                null, contextStartedAt, LocalDateTime.now(), Map.of("entry", "manual_plan_continue"), securityUser.userId()
        );
        ExecutionDecision executionDecision = executionDecisionService.decide(executionContext);
        ExecutionPlan manualPlan = buildManualExecutionPlan(request, executionContext);
        manualPlan = ensureUniquePlanId(manualPlan, taskId);
        try {
            taskPlanLifecycleService.persistSnapshot(taskId, executionContext, manualPlan, "manual_adjust", securityUser.userId());
            conversationRepository.updateTaskExecutionChain(taskId, buildExecutionChainJson(executionContext, manualPlan), securityUser.userId());
            List<RuntimeStepResultContext> previousStepResults = recoveryContextFactory.previousResults(existingTaskSteps, failedTaskStep.stepNo());
            WorkflowResumeContext workflowResumeContext = "workflow".equals(failedTaskStep.stepType())
                    ? recoveryContextFactory.workflowResume(failedTaskStep)
                    : null;
            conversationRepository.softDeleteTaskStepsFromStepNo(taskId, failedTaskStep.stepNo(), securityUser.userId());
            executionStatusFlowService.markProcessing(
                    conversationId,
                    truncatePreview(userMessageRecord.content()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            String inputSnapshot = toJsonText(new ExecutionInputSnapshot(
                    executionContext.userMessage(),
                    executionContext.promptContext().promptCode(),
                    executionContext.agentContext().agentCode(),
                    executionContext.agentContext().agentName(),
                    executionContext.promptContext().promptName(),
                    executionContext.promptContext().promptType(),
                    executionContext.promptContext().promptCategory(),
                    executionContext.promptContext().promptContent(),
                    executionContext.decisionPromptContext().promptName(),
                    executionContext.decisionPromptSummary(),
                    executionContext.plannerPromptContext() == null ? null : executionContext.plannerPromptContext().promptName(),
                    executionContext.plannerPromptSummary(),
                    executionDecision.firstWorkflowName(),
                    executionContext.effectiveToolLabels(),
                    executionDecision.decisionType(),
                    "manual_plan_continue",
                    manualPlan.planId(),
                    manualPlan.goal(),
                    manualPlan.planType(),
                    manualPlan.steps().stream()
                            .map(step -> step.stepNo() + ":" + step.stepType() + ":" + step.stepName() + ":" + step.objective())
                            .toList()
            ));
            RuntimeExecutionAggregate planExecutionAggregate = executePlanWithAutoReplan(
                    executionContext,
                    executionDecision,
                    manualPlan,
                    taskId,
                    traceId,
                    inputSnapshot,
                    authorization,
                    securityUser.userId(),
                    1,
                    previousStepResults.size(),
                    previousStepResults,
                    new RuntimeExecutionRequest.RecoveryReplaySnapshot(
                            true,
                            failedTaskStep.taskStepId(),
                            failedTaskStep.stepNo(),
                            failedTaskStep.stepName(),
                            previousStepResults.stream()
                                    .map(item -> item.stepNo() + ":" + item.stepName() + ":" + item.summary())
                                    .toList()
                    ),
                    null,
                    workflowResumeContext
            );
            String assistantContent = applyOutputGuardrails(executionContext, planExecutionAggregate.assistantContent(), taskId, traceId, securityUser.userId());
            persistAssistantMessageIfCompleted(conversationId, assistantContent, planExecutionAggregate, securityUser.userId());
            if (planExecutionAggregate.paused()) {
                executionStatusFlowService.markPaused(taskId, conversationId, truncatePreview(userMessageRecord.content()), conversationRecord.title(), securityUser.userId());
            } else if (planExecutionAggregate.waitingEvent()) {
                executionStatusFlowService.markWaitingEvent(taskId, conversationId, truncatePreview(userMessageRecord.content()), conversationRecord.title(), securityUser.userId());
            } else if (planExecutionAggregate.waitingApproval()) {
                executionStatusFlowService.markWaitingApproval(
                        taskId,
                        conversationId,
                        truncatePreview(userMessageRecord.content()),
                        conversationRecord.title(),
                        securityUser.userId()
                );
            } else {
                executionStatusFlowService.markSuccess(
                        taskId,
                        conversationId,
                        truncatePreview(assistantContent),
                        conversationRecord.title(),
                        securityUser.userId()
                );
                memoryUpdaterService.updateAfterMessage(
                        conversationId,
                        taskId,
                        conversationRecord.agentName(),
                        userMessageRecord.content(),
                        assistantContent,
                        executionContext,
                        planExecutionAggregate.executionPlan(),
                        securityUser.userId()
                );
            }
        } catch (Exception exception) {
            executionStatusFlowService.markFailed(
                    taskId,
                    conversationId,
                    exception.getMessage(),
                    truncatePreview(userMessageRecord.content()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            throw exception;
        }
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException("人工改计划续跑后的任务查询失败"));
    }

    /**
     * 审批通过后继续执行。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param approvalId     审批 ID
     * @param authorization  原始 Authorization 头
     * @param securityUser   当前用户
     * @return 最新任务结果
     */
    public TaskResponse approveTask(Long conversationId,
                                    Long taskId,
                                    Long approvalId,
                                    String approvalComment,
                                    String authorization,
                                    SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在"));
        String traceId = ensureTraceId(taskRecord, securityUser.userId());
        approvalRecoveryStateMachineService.validateTaskCanApprove(taskRecord);
        ApprovalRecord approvalRecord = conversationRepository.findApprovalById(approvalId, taskId)
                .orElseThrow(() -> new BusinessException("审批记录不存在"));
        TaskStepRecord approvalTaskStep = conversationRepository.findTaskStepById(approvalRecord.taskStepId())
                .orElseThrow(() -> new BusinessException("审批步骤不存在"));
        MessageRecord approvalMessageRecord = conversationRepository.findMessageById(taskRecord.messageId())
                .orElseThrow(() -> new BusinessException("任务原始用户消息不存在"));
        approvalRecord = expireApprovalIfNeeded(taskRecord, conversationRecord, approvalRecord, approvalTaskStep, traceId, securityUser.userId());
        approvalRecoveryStateMachineService.validateApprovalCanApprove(approvalRecord, approvalTaskStep);
        validateApprovalOperator(approvalRecord, securityUser);
        LocalDateTime contextStartedAt = LocalDateTime.now();
        ExecutionContext executionContext = executionContextBuilder.build(
                securityUser,
                conversationRecord,
                taskRecord.messageId(),
                approvalMessageRecord.content(),
                approvalMessageRecord.sequenceNo(),
                taskId,
                taskRecord.taskCode(),
                traceId,
                authorization
        );
        UsageAttributionContext.bind(executionContext);
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "context_build", "审批后构建执行上下文", "success",
                Map.of("conversationId", conversationId, "messageId", taskRecord.messageId()),
                Map.of("agentCode", executionContext.agentContext().agentCode(), "toolCount", executionContext.availableTools().size(), "workflowCount", executionContext.availableWorkflows().size()),
                null, contextStartedAt, LocalDateTime.now(), Map.of("entry", "approve_task"), securityUser.userId()
        );
        RuntimeCheckpoint durableCheckpoint = checkpointStore.latest(taskId).orElse(null);
        LocalDateTime decisionStartedAt = LocalDateTime.now();
        ExecutionDecision executionDecision = durableCheckpoint == null
                ? executionDecisionService.decide(executionContext)
                : durableCheckpoint.decision();
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "decision", "审批后执行决策", "success",
                Map.of("userMessage", executionContext.userMessage()),
                Map.of("decisionType", executionDecision.decisionType(), "reason", executionDecision.reason()),
                null, decisionStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        "approve_task",
                        durableCheckpoint == null
                                ? executionDecisionService.buildDecisionTraceMetadata(executionContext)
                                : Map.of(
                                        "checkpointRestored", true,
                                        "checkpointId", durableCheckpoint.id(),
                                        "checkpointStatus", durableCheckpoint.status()
                                )
                ), securityUser.userId()
        );
        conversationRepository.updateTaskExecutionType(
                taskId,
                resolveExecutionType(executionDecision),
                securityUser.userId()
        );
        LocalDateTime planningStartedAt = LocalDateTime.now();
        List<ExecutionPlannerService.PlanTemplateHint> approvalTemplateHints = taskPlanLifecycleService.resolveTemplateHints(securityUser.userId(), executionContext.agentContext().agentId());
        ExecutionPlan executionPlan = durableCheckpoint == null
                ? ensureUniquePlanId(executionPlannerService.buildPlan(
                        executionContext,
                        executionDecision,
                        null,
                        approvalTemplateHints
                ), taskId)
                : durableCheckpoint.plan();
        taskTraceService.recordSpan(
                taskId, traceId, null,
                "planning", "审批后生成执行计划", "success",
                Map.of("decisionType", executionDecision.decisionType()),
                Map.of("planId", executionPlan.planId(), "planType", executionPlan.planType(), "stepCount", executionPlan.steps().size()),
                null, planningStartedAt, LocalDateTime.now(), mergeTraceEntryMetadata(
                        "approve_task",
                        durableCheckpoint == null ? executionPlannerService.buildPlanningTraceMetadata(
                                executionContext,
                                executionDecision,
                                null,
                                approvalTemplateHints
                        ) : Map.of(
                                "checkpointRestored", true,
                                "checkpointId", durableCheckpoint.id(),
                                "checkpointStatus", durableCheckpoint.status()
                        )
                ), securityUser.userId()
        );
        RuntimeExecutionAggregate planExecutionAggregate;
        boolean approvalCommitted = false;
        try {
            if (durableCheckpoint == null) {
                taskPlanLifecycleService.persistSnapshot(
                        taskId, executionContext, executionPlan, "approval_resume", securityUser.userId());
            }
            conversationRepository.updateTaskExecutionChain(
                    taskId,
                    buildExecutionChainJson(executionContext, executionPlan),
                    securityUser.userId()
            );
            ExecutionPlan.ExecutionPlanStep approvalPlanStep = recoveryValidationService.resolveRecoverablePlanStep(
                    approvalTaskStep, executionPlan);
            recoveryValidationService.validateRecoverablePlan(approvalTaskStep, approvalPlanStep);
            if (durableCheckpoint != null) {
                checkpointStore.markRestored(durableCheckpoint, "approval_resume");
            }
            String approvalSourceType = resolveApprovalSourceType(approvalTaskStep);
            Integer nextPlanStepNo = resolveNextPlanStepNo(approvalTaskStep, approvalPlanStep.stepNo() + 1);
            List<RuntimeStepResultContext> previousStepResults = new ArrayList<>(
                    durableCheckpoint == null
                            ? recoveryContextFactory.previousResults(
                                    conversationRepository.findTaskStepsByTaskId(taskId), approvalPlanStep.stepNo())
                            : durableCheckpoint.results()
            );
            if ("plan_step".equals(approvalSourceType)) {
                previousStepResults.add(new RuntimeStepResultContext(
                        approvalPlanStep.stepNo(),
                        approvalPlanStep.stepId(),
                        approvalPlanStep.stepType(),
                        approvalPlanStep.stepName(),
                        "审批已通过",
                        "审批节点「" + approvalRecord.nodeName() + "」已通过",
                        approvalTaskStep.outputSnapshot()
                ));
            }
            String inputSnapshot = toJsonText(new ExecutionInputSnapshot(
                    executionContext.userMessage(),
                    executionContext.promptContext().promptCode(),
                    executionContext.agentContext().agentCode(),
                    executionContext.agentContext().agentName(),
                    executionContext.promptContext().promptName(),
                    executionContext.promptContext().promptType(),
                    executionContext.promptContext().promptCategory(),
                    executionContext.promptContext().promptContent(),
                    executionContext.decisionPromptContext().promptName(),
                    executionContext.decisionPromptSummary(),
                    executionContext.plannerPromptContext() == null ? null : executionContext.plannerPromptContext().promptName(),
                    executionContext.plannerPromptSummary(),
                    executionDecision.firstWorkflowName(),
                    executionContext.effectiveToolLabels(),
                    executionDecision.decisionType(),
                    executionDecision.reason(),
                    executionPlan.planId(),
                    executionPlan.goal(),
                    executionPlan.planType(),
                    executionPlan.steps().stream()
                            .map(step -> step.stepNo() + ":" + step.stepType() + ":" + step.stepName() + ":" + step.objective())
                            .toList()
            ));
            String normalizedApprovalComment = normalizeNullableText(approvalComment);
            JsonNode approvalMetadata = objectMapper.valueToTree(safeJson(approvalRecord.metadata()));
            recoveryValidationService.validateApprovableState(approvalRecord, approvalTaskStep);
            approvalCoordinator.transition(new ApprovalCoordinator.ApprovalTransition(
                    approvalRecord,
                    "approved",
                    normalizedApprovalComment == null ? "审批通过并继续执行" : normalizedApprovalComment,
                    approvalSourceType,
                    traceId,
                    securityUser.userId()
            ));
            approvalCommitted = true;
            conversationRepository.softDeleteTaskStepsFromStepNo(taskId, approvalPlanStep.stepNo(), securityUser.userId());
            executionStatusFlowService.markProcessing(
                    conversationId,
                    truncatePreview(executionContext.userMessage()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            WorkflowApprovalResumeContext approvalResumeContext = null;
            int startStepNo = approvalPlanStep.stepNo();
            if ("plan_step".equals(approvalSourceType)) {
                startStepNo = nextPlanStepNo;
            } else if ("step_guardrail".equals(approvalSourceType)
                    || "capability_policy".equals(approvalSourceType)) {
                startStepNo = approvalPlanStep.stepNo();
            } else {
                approvalResumeContext = buildWorkflowApprovalResumeContext(approvalTaskStep);
                if (approvalResumeContext == null) {
                    throw new BusinessException("当前审批步骤缺少恢复元数据，无法继续执行");
                }
            }
            planExecutionAggregate = executePlanWithAutoReplan(
                    executionContext,
                    executionDecision,
                    executionPlan,
                    taskId,
                    traceId,
                    inputSnapshot,
                    authorization,
                    securityUser.userId(),
                    startStepNo,
                    0,
                    previousStepResults,
                    new RuntimeExecutionRequest.RecoveryReplaySnapshot(
                            true,
                            approvalTaskStep.taskStepId(),
                            approvalTaskStep.stepNo(),
                            approvalTaskStep.stepName(),
                            previousStepResults.stream()
                                    .map(item -> item.stepNo() + ":" + item.stepName() + ":" + item.summary())
                                    .toList()
                    ),
                    new RuntimeExecutionRequest.ApprovalReplaySnapshot(
                            approvalId,
                            approvalRecord.nodeId(),
                            approvalRecord.nodeName(),
                            normalizedApprovalComment == null ? "审批通过并继续执行" : normalizedApprovalComment,
                            "approved",
                            normalizeNullableText(approvalMetadata.path("toolCallId").asText(null)),
                            normalizeNullableText(approvalMetadata.path("parameterHash").asText(null))
                    ),
                    approvalResumeContext == null ? null : approvalResumeContext.resumeContext()
            );
        } catch (Exception exception) {
            log.error("审批恢复执行失败, taskId={}, approvalId={}, approvalCommitted={}, message={}",
                    taskId, approvalId, approvalCommitted, exception.getMessage(), exception);
            if (approvalCommitted) {
                executionStatusFlowService.markFailed(
                        taskId,
                        conversationId,
                        exception.getMessage(),
                        truncatePreview(executionContext.userMessage()),
                        conversationRecord.title(),
                        securityUser.userId()
                );
            } else {
                executionStatusFlowService.markWaitingApproval(
                        taskId,
                        conversationId,
                        truncatePreview("审批恢复前置校验失败，可修复后重试：" + exception.getMessage()),
                        conversationRecord.title(),
                        securityUser.userId()
                );
            }
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "审批后继续执行结束", "failed",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "failed"),
                    exception.getMessage(), LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "approve_task"), securityUser.userId()
            );
            throw exception;
        }
        String assistantContent = applyOutputGuardrails(executionContext, planExecutionAggregate.assistantContent(), taskId, traceId, securityUser.userId());
        persistAssistantMessageIfCompleted(conversationId, assistantContent, planExecutionAggregate, securityUser.userId());
        if (planExecutionAggregate.paused()) {
            executionStatusFlowService.markPaused(taskId, conversationId, truncatePreview(executionContext.userMessage()), conversationRecord.title(), securityUser.userId());
        } else if (planExecutionAggregate.waitingEvent()) {
            executionStatusFlowService.markWaitingEvent(taskId, conversationId, truncatePreview(executionContext.userMessage()), conversationRecord.title(), securityUser.userId());
        } else if (planExecutionAggregate.waitingApproval()) {
            executionStatusFlowService.markWaitingApproval(
                    taskId,
                    conversationId,
                    truncatePreview(executionContext.userMessage()),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "审批后再次挂起", "waiting_approval",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "waiting_approval"),
                    null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "approve_task"), securityUser.userId()
            );
        } else {
            executionStatusFlowService.markSuccess(
                    taskId,
                    conversationId,
                    truncatePreview(assistantContent),
                    conversationRecord.title(),
                    securityUser.userId()
            );
            memoryUpdaterService.updateAfterMessage(
                    conversationId,
                    taskId,
                    conversationRecord.agentName(),
                    executionContext.userMessage(),
                    assistantContent,
                    executionContext,
                    executionPlan,
                    securityUser.userId()
            );
            taskTraceService.recordSpan(
                    taskId, traceId, null,
                    "task_result", "审批后继续执行完成", "success",
                    Map.of("taskCode", taskRecord.taskCode()),
                    Map.of("status", "success"),
                    null, LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "approve_task"), securityUser.userId()
            );
        }
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException("审批后的任务查询失败"));
    }

    private void buildRejectReplan(ConversationRecord conversationRecord,
                                   TaskRecord taskRecord,
                                   TaskStepRecord approvalTaskStep,
                                   ApprovalRecord approvalRecord,
                                   String traceId,
                                   String authorization,
                                   SecurityUser securityUser) {
        MessageRecord userMessageRecord = conversationRepository.findMessageById(taskRecord.messageId())
                .orElseThrow(() -> new BusinessException("任务原始用户消息不存在"));
        String userMessage = userMessageRecord.content();
        ExecutionContext executionContext = executionContextBuilder.build(
                securityUser,
                conversationRecord,
                taskRecord.messageId(),
                userMessage,
                userMessageRecord.sequenceNo(),
                taskRecord.taskId(),
                taskRecord.taskCode(),
                traceId,
                authorization
        );
        UsageAttributionContext.bind(executionContext);
        ExecutionDecision executionDecision = executionDecisionService.decide(executionContext);
        TaskPlanRecord latestPlan = resolveTaskPlanRecord(conversationRepository.findTaskPlansByTaskId(taskRecord.taskId()), null);
        if (latestPlan == null) {
            return;
        }
        ExecutionPlan currentPlan = toExecutionPlan(latestPlan);
        ExecutionPlan.ExecutionPlanStep failedStep;
        try {
            failedStep = recoveryValidationService.resolveRecoverablePlanStep(approvalTaskStep, currentPlan);
        } catch (BusinessException exception) {
            return;
        }
        List<ExecutionPlannerService.PlanTemplateHint> rejectTemplateHints = taskPlanLifecycleService.resolveTemplateHints(securityUser.userId(), executionContext.agentContext().agentId());
        ExecutionPlannerService.ReplanContext replanContext = new ExecutionPlannerService.ReplanContext(
                currentPlan,
                failedStep,
                recoveryContextFactory.previousResults(conversationRepository.findTaskStepsByTaskId(taskRecord.taskId()), approvalTaskStep.stepNo()),
                "approval_reject",
                "审批被拒绝，需要重新规划后再决定是否继续",
                1,
                List.of("approval_rejected|" + approvalRecord.approvalId())
        );
        ExecutionPlan replan = executionPlannerService.buildPlan(
                executionContext,
                executionDecision,
                replanContext,
                rejectTemplateHints
        );
        replan = ensureUniquePlanId(replan, taskRecord.taskId());
        taskPlanLifecycleService.persistSnapshot(taskRecord.taskId(), executionContext, replan, "reject_replan", securityUser.userId());
        taskTraceService.recordSpan(
                taskRecord.taskId(), traceId, approvalTaskStep.taskStepId(),
                "planning", "审批拒绝后二次规划", "success",
                Map.of("approvalId", approvalRecord.approvalId(), "nodeName", approvalRecord.nodeName()),
                Map.of("planId", replan.planId(), "stepCount", replan.steps().size()),
                null, LocalDateTime.now(), LocalDateTime.now(), mergeTraceEntryMetadata(
                        "reject_replan",
                        executionPlannerService.buildPlanningTraceMetadata(
                                executionContext,
                                executionDecision,
                                replanContext,
                                rejectTemplateHints
                        )
                ), securityUser.userId()
        );
    }

    private void refreshApprovalTimeouts(Long conversationId, Long operatorId) {
        ConversationRecord conversationRecord = conversationRepository.findConversationById(conversationId, operatorId).orElse(null);
        if (conversationRecord == null) {
            return;
        }
        List<TaskRecord> tasks = conversationRepository.findTasksByConversationId(conversationId);
        for (TaskRecord task : tasks) {
            for (ApprovalRecord approval : conversationRepository.findApprovalsByTaskId(task.taskId())) {
                if (!STATUS_PENDING.equals(approval.status())) {
                    continue;
                }
                TaskStepRecord approvalTaskStep = conversationRepository.findTaskStepById(approval.taskStepId()).orElse(null);
                if (approvalTaskStep == null) {
                    continue;
                }
                expireApprovalIfNeeded(task, conversationRecord, approval, approvalTaskStep, task.traceId(), operatorId);
            }
        }
    }

    private ApprovalRecord expireApprovalIfNeeded(TaskRecord taskRecord,
                                                  ConversationRecord conversationRecord,
                                                  ApprovalRecord approvalRecord,
                                                  TaskStepRecord approvalTaskStep,
                                                  String traceId,
                                                  Long operatorId) {
        if (!STATUS_PENDING.equals(approvalRecord.status())) {
            return approvalRecord;
        }
        if (!isApprovalTimedOut(approvalRecord, LocalDateTime.now())) {
            return approvalRecord;
        }
        approvalCoordinator.transition(new ApprovalCoordinator.ApprovalTransition(
                approvalRecord,
                STATUS_TIMED_OUT,
                "审批超时，任务结束",
                resolveApprovalSourceType(approvalTaskStep),
                traceId,
                operatorId
        ));
        conversationRepository.updateTaskStepStatus(
                approvalTaskStep.taskStepId(),
                STATUS_FAILED,
                toJsonText(new ApprovalPendingSnapshot(
                        "审批超时，任务结束",
                        approvalRecord.nodeId(),
                        approvalRecord.nodeName(),
                        null,
                        List.of(),
                        List.of(),
                        STATUS_TIMED_OUT,
                        resolveApprovalSourceType(approvalTaskStep),
                        resolveNextPlanStepNo(approvalTaskStep, null)
                )),
                "审批超时",
                operatorId
        );
        executionStatusFlowService.markFailed(
                taskRecord.taskId(),
                taskRecord.conversationId(),
                "审批超时",
                "审批超时，任务结束",
                conversationRecord.title(),
                operatorId
        );
        taskTraceService.recordSpan(
                taskRecord.taskId(),
                traceId == null ? ensureTraceId(taskRecord, operatorId) : traceId,
                approvalTaskStep.taskStepId(),
                "approval_timeout",
                "审批超时",
                "failed",
                Map.of("approvalId", approvalRecord.approvalId(), "nodeName", approvalRecord.nodeName()),
                Map.of("timeoutMinutes", resolveApprovalTimeoutMinutes(approvalRecord)),
                "审批超时", LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "approval_timeout"), operatorId
        );
        return conversationRepository.findApprovalById(approvalRecord.approvalId(), taskRecord.taskId()).orElse(approvalRecord);
    }

    private boolean isApprovalTimedOut(ApprovalRecord approvalRecord, LocalDateTime now) {
        if (approvalRecord.createdAt() == null) {
            return false;
        }
        return approvalRecord.createdAt().plusMinutes(resolveApprovalTimeoutMinutes(approvalRecord)).isBefore(now);
    }

    private int resolveApprovalTimeoutMinutes(ApprovalRecord approvalRecord) {
        Object metadata = safeJson(approvalRecord.metadata());
        if (metadata instanceof JsonNode jsonNode) {
            int rootTimeout = jsonNode.path("timeoutMinutes").asInt(0);
            if (rootTimeout > 0) {
                return rootTimeout;
            }
            int primaryPolicyTimeout = jsonNode.path("primaryPolicy").path("timeoutMinutes").asInt(0);
            if (primaryPolicyTimeout > 0) {
                return primaryPolicyTimeout;
            }
        }
        return DEFAULT_APPROVAL_TIMEOUT_MINUTES;
    }

    /**
     * 审批拒绝并结束任务。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param approvalId     审批 ID
     * @param securityUser   当前用户
     * @return 最新任务结果
     */
    public TaskResponse rejectTask(Long conversationId,
                                   Long taskId,
                                   Long approvalId,
                                   String approvalComment,
                                   boolean replanAfterReject,
                                   String authorization,
                                   SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在"));
        String traceId = ensureTraceId(taskRecord, securityUser.userId());
        approvalRecoveryStateMachineService.validateTaskCanReject(taskRecord);
        ApprovalRecord approvalRecord = conversationRepository.findApprovalById(approvalId, taskId)
                .orElseThrow(() -> new BusinessException("审批记录不存在"));
        TaskStepRecord approvalTaskStep = conversationRepository.findTaskStepById(approvalRecord.taskStepId())
                .orElseThrow(() -> new BusinessException("审批步骤不存在"));
        approvalRecord = expireApprovalIfNeeded(taskRecord, conversationRecord, approvalRecord, approvalTaskStep, traceId, securityUser.userId());
        approvalRecoveryStateMachineService.validateApprovalCanReject(approvalRecord, approvalTaskStep);
        validateApprovalOperator(approvalRecord, securityUser);
        String normalizedApprovalComment = normalizeNullableText(approvalComment);
        approvalCoordinator.transition(new ApprovalCoordinator.ApprovalTransition(
                approvalRecord,
                "rejected",
                normalizedApprovalComment == null ? "审批拒绝，任务终止" : normalizedApprovalComment,
                resolveApprovalSourceType(approvalTaskStep),
                traceId,
                securityUser.userId()
        ));
        conversationRepository.updateTaskStepStatus(
                approvalRecord.taskStepId(),
                STATUS_FAILED,
                toJsonText(new ApprovalPendingSnapshot(
                        "审批已拒绝，任务结束",
                        approvalRecord.nodeId(),
                        approvalRecord.nodeName(),
                        null,
                        List.of(),
                        List.of(),
                        "rejected",
                        resolveApprovalSourceType(approvalTaskStep),
                        resolveNextPlanStepNo(approvalTaskStep, null)
                )),
                "审批已拒绝",
                securityUser.userId()
        );
        executionStatusFlowService.markFailed(
                taskId,
                conversationId,
                "审批已拒绝",
                "审批已拒绝，任务结束",
                conversationRecord.title(),
                securityUser.userId()
        );
        taskTraceService.recordSpan(
                taskId, traceId, approvalRecord.taskStepId(),
                "task_result", "审批拒绝结束任务", "failed",
                Map.of("taskCode", taskRecord.taskCode(), "approvalId", approvalId),
                Map.of("status", "rejected", "nodeName", approvalRecord.nodeName()),
                "审批已拒绝", LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "reject_task"), securityUser.userId()
        );
        if (replanAfterReject) {
            buildRejectReplan(conversationRecord, taskRecord, approvalTaskStep, approvalRecord, traceId, authorization, securityUser);
        }
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException("审批后的任务查询失败"));
    }

    public TaskResponse revokeApproval(Long conversationId,
                                       Long taskId,
                                       Long approvalId,
                                       String approvalComment,
                                       SecurityUser securityUser) {
        ConversationRecord conversationRecord = getConversationRecord(conversationId, securityUser.userId());
        TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("任务不存在"));
        String traceId = ensureTraceId(taskRecord, securityUser.userId());
        approvalRecoveryStateMachineService.validateTaskCanReject(taskRecord);
        ApprovalRecord approvalRecord = conversationRepository.findApprovalById(approvalId, taskId)
                .orElseThrow(() -> new BusinessException("审批记录不存在"));
        TaskStepRecord approvalTaskStep = conversationRepository.findTaskStepById(approvalRecord.taskStepId())
                .orElseThrow(() -> new BusinessException("审批步骤不存在"));
        approvalRecord = expireApprovalIfNeeded(taskRecord, conversationRecord, approvalRecord, approvalTaskStep, traceId, securityUser.userId());
        approvalRecoveryStateMachineService.validateApprovalCanReject(approvalRecord, approvalTaskStep);
        String comment = normalizeNullableText(approvalComment);
        approvalCoordinator.transition(new ApprovalCoordinator.ApprovalTransition(
                approvalRecord,
                STATUS_REVOKED,
                comment == null ? "发起人撤回审批" : comment,
                resolveApprovalSourceType(approvalTaskStep),
                traceId,
                securityUser.userId()
        ));
        conversationRepository.updateTaskStepStatus(
                approvalTaskStep.taskStepId(),
                STATUS_FAILED,
                toJsonText(new ApprovalPendingSnapshot(
                        "审批已撤回，任务结束",
                        approvalRecord.nodeId(),
                        approvalRecord.nodeName(),
                        null,
                        List.of(),
                        List.of(),
                        STATUS_REVOKED,
                        resolveApprovalSourceType(approvalTaskStep),
                        resolveNextPlanStepNo(approvalTaskStep, null)
                )),
                "审批已撤回",
                securityUser.userId()
        );
        executionStatusFlowService.markFailed(
                taskId,
                conversationId,
                "审批已撤回",
                "审批已撤回，任务结束",
                conversationRecord.title(),
                securityUser.userId()
        );
        taskTraceService.recordSpan(
                taskId, traceId, approvalRecord.taskStepId(),
                "approval_revoke", "审批撤回", "failed",
                Map.of("taskCode", taskRecord.taskCode(), "approvalId", approvalId),
                Map.of("status", STATUS_REVOKED, "nodeName", approvalRecord.nodeName()),
                "审批已撤回", LocalDateTime.now(), LocalDateTime.now(), Map.of("entry", "revoke_approval"), securityUser.userId()
        );
        return conversationRepository.findTasksByConversationId(conversationId).stream()
                .filter(item -> item.taskId().equals(taskId))
                .findFirst()
                .map(this::toTaskResponse)
                .orElseThrow(() -> new BusinessException("撤回后的任务查询失败"));
    }

    private ConversationPageQueryRequest normalizePageQuery(ConversationPageQueryRequest request) {
        return new ConversationPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                request.agentId()
        );
    }

    /**
     * 下载当前用户会话中 Task 成功生成的执行产物。
     */
    public TaskArtifactDownload downloadTaskArtifact(Long conversationId,
                                                     Long taskId,
                                                     String artifactPath,
                                                     SecurityUser securityUser) {
        getConversationRecord(conversationId, securityUser.userId());
        TaskRecord task = conversationRepository.findTaskById(taskId)
                .filter(record -> conversationId.equals(record.conversationId()))
                .orElseThrow(() -> new BusinessException("Task 不存在或不属于当前会话"));
        String normalizedPath = normalizeNullableText(artifactPath);
        if (normalizedPath == null || normalizedPath.startsWith("/") || normalizedPath.contains("..")) {
            throw new BusinessException("产物路径无效");
        }
        List<TaskStepRecord> steps = conversationRepository.findTaskStepsByTaskId(task.taskId());
        for (int stepIndex = steps.size() - 1; stepIndex >= 0; stepIndex--) {
            TaskStepRecord step = steps.get(stepIndex);
            try {
                JsonNode artifacts = objectMapper.readTree(step.outputSnapshot()).path("payload").path("artifacts");
                if (!artifacts.isArray()) {
                    continue;
                }
                for (JsonNode artifact : artifacts) {
                    if (!normalizedPath.equals(artifact.path("path").asText())) {
                        continue;
                    }
                    byte[] content = java.util.Base64.getDecoder().decode(artifact.path("contentBase64").asText());
                    String filename = java.nio.file.Path.of(normalizedPath).getFileName().toString();
                    String contentType = filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                            ? "application/pdf" : "application/octet-stream";
                    return new TaskArtifactDownload(filename, contentType, content);
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("产物内容损坏，无法下载");
            } catch (Exception ignored) {
                // 跳过不包含合法 artifacts 的普通步骤快照。
            }
        }
        throw new BusinessException("Task 产物不存在：" + normalizedPath);
    }

    private ConversationRecord getConversationRecord(Long conversationId, Long userId) {
        return conversationRepository.findConversationById(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildConversationTitle(String content) {
        if (content.length() <= 24) {
            return content;
        }
        return content.substring(0, 24) + "...";
    }

    private RuntimeExecutionAggregate executeInitialPlan(ExecutionContext executionContext,
                                                                                   ExecutionDecision executionDecision,
                                                                                   ExecutionPlan executionPlan,
                                                                                   Long taskId,
                                                                                   String traceId,
                                                                                   String inputSnapshot,
                                                                                   String authorization,
                                                                                   SecurityUser securityUser) {
        return executePlanWithAutoReplan(
                executionContext,
                executionDecision,
                executionPlan,
                taskId,
                traceId,
                inputSnapshot,
                authorization,
                securityUser.userId(),
                1,
                0,
                List.of(),
                null,
                null,
                null
        );
    }

    private RuntimeExecutionAggregate executePlanWithAutoReplan(ExecutionContext executionContext,
                                                                                           ExecutionDecision executionDecision,
                                                                                           ExecutionPlan executionPlan,
                                                                                           Long taskId,
                                                                                           String traceId,
                                                                                           String inputSnapshot,
                                                                                           String authorization,
                                                                                           Long operatorId,
                                                                                           Integer startStepNo,
                                                                                           Integer stepNoOffset,
                                                                                           List<RuntimeStepResultContext> recoveredStepResults,
                                                                                           RuntimeExecutionRequest.RecoveryReplaySnapshot recoveryReplaySnapshot,
                                                                                           RuntimeExecutionRequest.ApprovalReplaySnapshot approvalReplaySnapshot,
                                                                                           WorkflowResumeContext workflowResumeContext) {
        ExecutionPlan currentPlan = executionPlan;
        List<RuntimeStepResultContext> currentRecoveredResults = recoveredStepResults;
        int currentStepOffset = stepNoOffset == null ? 0 : stepNoOffset;
        int autoReplanCount = 0;
        AgentExecutionPolicyResolver.Policy executionPolicy = agentExecutionPolicyResolver.resolve(executionContext);
        java.util.Set<String> failedStepFingerprints = new LinkedHashSet<>();
        while (true) {
            try {
                return agentLoopExecutionGateway.execute(
                        new RuntimeExecutionRequest(
                                executionContext,
                                executionDecision,
                                currentPlan,
                                taskId,
                                traceId,
                                inputSnapshot,
                                authorization,
                                operatorId,
                                startStepNo,
                                currentStepOffset,
                                currentRecoveredResults,
                                recoveryReplaySnapshot,
                                approvalReplaySnapshot,
                                workflowResumeContext
                        )
                );
            } catch (PlanStepExecutionException exception) {
                String failedFingerprint = buildFailedStepFingerprint(exception.failedStep(), exception.getMessage());
                if (!exception.failureSemantics().replannable()
                        || !shouldAutoReplan(exception.failedStep(), autoReplanCount, failedStepFingerprints,
                        failedFingerprint, executionPolicy)) {
                    toolSideEffectService.compensateTask(executionContext);
                    throw exception;
                }
                failedStepFingerprints.add(failedFingerprint);
                autoReplanCount++;
                LocalDateTime replanStartedAt = LocalDateTime.now();
                currentPlan = executionPlannerService.buildPlan(
                        executionContext,
                        executionDecision,
                        new ExecutionPlannerService.ReplanContext(
                                currentPlan,
                                exception.failedStep(),
                                exception.previousStepResults(),
                                "step_failure",
                                exception.getMessage(),
                                autoReplanCount,
                                new ArrayList<>(failedStepFingerprints)
                        ),
                        taskPlanLifecycleService.resolveTemplateHints(operatorId, executionContext.agentContext().agentId())
                );
                currentPlan = ensureUniquePlanId(currentPlan, taskId);
                Integer replanScore = planQualityService.evaluate(currentPlan, executionContext).score();
                if (replanScore != null && replanScore < executionPolicy.minimumReplanQualityScore()) {
                    toolSideEffectService.compensateTask(executionContext);
                    throw new BusinessException("自动重规划已熔断：生成计划质量过低，当前评分 " + replanScore);
                }
                taskTraceService.recordSpan(
                        taskId,
                        traceId,
                        exception.failedTaskStepId(),
                        "planning",
                        "自动重规划",
                        "success",
                        Map.of(
                                "failedStepNo", exception.failedStep().stepNo(),
                                "failedStepName", exception.failedStep().stepName(),
                                "failedStepType", exception.failedStep().stepType(),
                                "autoReplanCount", autoReplanCount,
                                "failedFingerprint", failedFingerprint,
                                "executionPolicy", executionPolicy.traceMetadata()
                        ),
                        Map.of(
                                "planId", currentPlan.planId(),
                                "planType", currentPlan.planType(),
                                "stepCount", currentPlan.steps().size(),
                                "qualityScore", replanScore
                        ),
                        null,
                        replanStartedAt,
                        LocalDateTime.now(),
                        Map.of("entry", "auto_replan"),
                        operatorId
                );
                taskPlanLifecycleService.persistSnapshot(taskId, executionContext, currentPlan, "auto_replan", operatorId);
                conversationRepository.updateTaskExecutionChain(
                        taskId,
                        buildExecutionChainJson(executionContext, currentPlan),
                        operatorId
                );
                currentStepOffset = conversationRepository.findTaskStepsByTaskId(taskId).size();
                currentRecoveredResults = exception.previousStepResults();
                recoveryReplaySnapshot = null;
                approvalReplaySnapshot = null;
                workflowResumeContext = null;
                startStepNo = 1;
            } catch (ObservationReplanRequiredException exception) {
                String replanFingerprint = buildFailedStepFingerprint(exception.triggerStep(), exception.getMessage());
                if (!shouldAutoReplan(exception.triggerStep(), autoReplanCount, failedStepFingerprints,
                        replanFingerprint, executionPolicy)) {
                    toolSideEffectService.compensateTask(executionContext);
                    throw exception;
                }
                failedStepFingerprints.add(replanFingerprint);
                autoReplanCount++;
                LocalDateTime replanStartedAt = LocalDateTime.now();
                currentPlan = executionPlannerService.buildPlan(
                        executionContext,
                        executionDecision,
                        new ExecutionPlannerService.ReplanContext(
                                currentPlan,
                                exception.triggerStep(),
                                exception.previousStepResults(),
                                "observation_replan",
                                exception.getMessage(),
                                autoReplanCount,
                                new ArrayList<>(failedStepFingerprints)
                        ),
                        taskPlanLifecycleService.resolveTemplateHints(operatorId, executionContext.agentContext().agentId())
                );
                currentPlan = ensureUniquePlanId(currentPlan, taskId);
                Integer replanScore = planQualityService.evaluate(currentPlan, executionContext).score();
                if (replanScore != null && replanScore < executionPolicy.minimumReplanQualityScore()) {
                    toolSideEffectService.compensateTask(executionContext);
                    throw new BusinessException("自动重规划已熔断：生成计划质量过低，当前评分 " + replanScore);
                }
                taskTraceService.recordSpan(
                        taskId,
                        traceId,
                        exception.triggerTaskStepId(),
                        "planning",
                        "Observation 后自动重规划",
                        "success",
                        Map.of(
                                "failedStepNo", exception.triggerStep().stepNo(),
                                "failedStepName", exception.triggerStep().stepName(),
                                "failedStepType", exception.triggerStep().stepType(),
                                "autoReplanCount", autoReplanCount,
                                "failedFingerprint", replanFingerprint,
                                "triggerType", "observation_replan",
                                "executionPolicy", executionPolicy.traceMetadata()
                        ),
                        Map.of(
                                "planId", currentPlan.planId(),
                                "planType", currentPlan.planType(),
                                "stepCount", currentPlan.steps().size(),
                                "qualityScore", replanScore
                        ),
                        null,
                        replanStartedAt,
                        LocalDateTime.now(),
                        Map.of("entry", "observation_replan"),
                        operatorId
                );
                taskPlanLifecycleService.persistSnapshot(taskId, executionContext, currentPlan, "observation_replan", operatorId);
                conversationRepository.updateTaskExecutionChain(
                        taskId,
                        buildExecutionChainJson(executionContext, currentPlan),
                        operatorId
                );
                currentStepOffset = conversationRepository.findTaskStepsByTaskId(taskId).size();
                currentRecoveredResults = exception.previousStepResults();
                recoveryReplaySnapshot = null;
                approvalReplaySnapshot = null;
                workflowResumeContext = null;
                startStepNo = 1;
            }
        }
    }

    private boolean shouldAutoReplan(ExecutionPlan.ExecutionPlanStep failedStep,
                                     int autoReplanCount,
                                     java.util.Set<String> failedStepFingerprints,
                                     String failedFingerprint,
                                     AgentExecutionPolicyResolver.Policy executionPolicy) {
        if (failedStep == null || !executionPolicy.autoReplanEnabled()
                || autoReplanCount >= executionPolicy.maxReplanTimes()
                || failedStepFingerprints.size() >= executionPolicy.replanCircuitBreakerThreshold()) {
            return false;
        }
        if (failedFingerprint != null && failedStepFingerprints.contains(failedFingerprint)) {
            return false;
        }
        return "replan".equalsIgnoreCase(failedStep.failurePolicy()) && Boolean.TRUE.equals(failedStep.replannable());
    }

    private String buildFailedStepFingerprint(ExecutionPlan.ExecutionPlanStep failedStep, String errorMessage) {
        if (failedStep == null) {
            return null;
        }
        return failedStep.stepId() + "|" + failedStep.stepType() + "|" + normalizeNullableText(errorMessage);
    }

    private String truncatePreview(String content) {
        if (content.length() <= 120) {
            return content;
        }
        return content.substring(0, 120) + "...";
    }

    private WorkflowApprovalResumeContext buildWorkflowApprovalResumeContext(TaskStepRecord approvalTaskStep) {
        Object snapshot = safeJson(approvalTaskStep.outputSnapshot());
        if (!(snapshot instanceof com.fasterxml.jackson.databind.JsonNode jsonNode)) {
            return null;
        }
        String nextNodeId = jsonNode.path("nextNodeId").asText(null);
        if (nextNodeId == null || nextNodeId.isBlank()) {
            return null;
        }
        List<Map<String, Object>> executedNodes = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode executedNodeArray = jsonNode.path("executedNodes");
        if (executedNodeArray.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : executedNodeArray) {
                executedNodes.add(objectMapper.convertValue(
                        item,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        }
                ));
            }
        }
        List<WorkflowResumeContext.WorkflowResumeNode> previousNodes = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode previousNodeArray = jsonNode.path("previousNodeResults");
        if (previousNodeArray.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : previousNodeArray) {
                previousNodes.add(new WorkflowResumeContext.WorkflowResumeNode(
                        item.path("nodeId").asText(null),
                        item.path("nodeType").asText(null),
                        item.path("summary").asText(null),
                        item.path("responseText").asText(null)
                ));
            }
        }
        previousNodes.add(new WorkflowResumeContext.WorkflowResumeNode(
                jsonNode.path("approvalNodeId").asText(null),
                "approval",
                "审批节点已通过",
                "审批节点已通过"
        ));
        return new WorkflowApprovalResumeContext(
                new WorkflowResumeContext(nextNodeId, "approval_continue", executedNodes, previousNodes)
        );
    }

    private String resolveApprovalSourceType(TaskStepRecord approvalTaskStep) {
        Object snapshot = safeJson(approvalTaskStep.outputSnapshot());
        if (snapshot instanceof com.fasterxml.jackson.databind.JsonNode jsonNode) {
            String approvalSourceType = jsonNode.path("approvalSourceType").asText(null);
            if (approvalSourceType != null && !approvalSourceType.isBlank()) {
                return approvalSourceType;
            }
        }
        return "workflow";
    }

    private Integer resolveNextPlanStepNo(TaskStepRecord approvalTaskStep, Integer defaultNextPlanStepNo) {
        Object snapshot = safeJson(approvalTaskStep.outputSnapshot());
        if (snapshot instanceof com.fasterxml.jackson.databind.JsonNode jsonNode
                && jsonNode.hasNonNull("nextPlanStepNo")) {
            return jsonNode.path("nextPlanStepNo").asInt();
        }
        return defaultNextPlanStepNo;
    }

    private Object safeJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return java.util.Map.of();
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            return java.util.Map.of("raw", jsonText);
        }
    }

    private AgentSnapshot resolveMemoryPolicyAgentSnapshot(ConversationRecord conversationRecord, String authorization) {
        try {
            return registryAgentClient.getEnabledAgentSnapshot(conversationRecord.agentId(), authorization);
        } catch (Exception exception) {
            log.warn("读取 Agent 默认记忆策略失败，回退使用会话元数据: conversationId={}, agentId={}, reason={}",
                    conversationRecord.conversationId(),
                    conversationRecord.agentId(),
                    exception.getMessage());
            return null;
        }
    }

    private ConversationPageItemResponse toConversationPageItemResponse(ConversationRecord record) {
        return new ConversationPageItemResponse(
                record.conversationId(),
                record.conversationCode(),
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.title(),
                record.status(),
                record.lastMessageAt(),
                record.lastMessagePreview(),
                record.createdAt()
        );
    }

    private ConversationDetailResponse toConversationDetailResponse(ConversationRecord record,
                                                                    ConversationMemoryRecord memoryRecord,
                                                                    UserMemoryRecord userMemoryRecord) {
        ConversationMemoryPolicyResolver.ConversationUserMemoryPolicy policy = ConversationMemoryPolicyResolver.resolveConversationPolicy(
                record.metadata(),
                null
        );
        ConversationPolicyService.PolicySnapshot effectiveCompactionPolicy = conversationPolicyService.resolveEffective(record.metadata());
        ConversationPolicyService.PolicySnapshot agentDefaultCompactionPolicy = conversationPolicyService.resolveAgentDefault(record.metadata());
        return new ConversationDetailResponse(
                record.conversationId(),
                record.conversationCode(),
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.defaultModel(),
                record.defaultPromptId(),
                record.defaultPromptName(),
                record.decisionPromptId(),
                record.decisionPromptName(),
                record.plannerPromptId(),
                record.plannerPromptName(),
                record.title(),
                record.status(),
                conversationPolicyService.resolveResponseStreamingEnabled(record.metadata()),
                record.lastMessageAt(),
                record.lastMessagePreview(),
                memoryRecord == null ? null : memoryRecord.memorySummary(),
                memoryRecord == null ? null : memoryRecord.preferenceSummary(),
                memoryRecord == null ? null : memoryRecord.lastUserMessage(),
                memoryRecord == null ? null : memoryRecord.lastAssistantSummary(),
                memoryRecord == null ? null : memoryRecord.updatedAt(),
                userMemoryRecord == null ? null : userMemoryRecord.preferenceSummary(),
                userMemoryRecord == null ? null : userMemoryRecord.attentionSummary(),
                userMemoryRecord == null ? null : userMemoryRecord.factSummary(),
                userMemoryRecord == null ? null : userMemoryRecord.goalSummary(),
                userMemoryRecord == null ? null : userMemoryRecord.updatedAt(),
                policy.userMemoryEnabled(),
                policy.userMemoryMode(),
                policy.userMemoryWritebackEnabled(),
                policy.agentDefaultUserMemoryEnabled(),
                policy.agentDefaultUserMemoryMode(),
                policy.agentDefaultUserMemoryWritebackEnabled(),
                effectiveCompactionPolicy.preferSummaryOverRawHistory(),
                effectiveCompactionPolicy.maxRecentMessages(),
                effectiveCompactionPolicy.maxRecentUserMessages(),
                effectiveCompactionPolicy.preferStateFirst(),
                effectiveCompactionPolicy.historySummaryThresholdMessages(),
                effectiveCompactionPolicy.minRetentionRatio(),
                effectiveCompactionPolicy.qualityWarningThreshold(),
                agentDefaultCompactionPolicy.preferSummaryOverRawHistory(),
                agentDefaultCompactionPolicy.maxRecentMessages(),
                agentDefaultCompactionPolicy.maxRecentUserMessages(),
                agentDefaultCompactionPolicy.preferStateFirst(),
                agentDefaultCompactionPolicy.historySummaryThresholdMessages(),
                agentDefaultCompactionPolicy.minRetentionRatio(),
                agentDefaultCompactionPolicy.qualityWarningThreshold(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private MessageResponse toMessageResponse(MessageRecord record) {
        return new MessageResponse(
                record.messageId(),
                record.conversationId(),
                record.messageType(),
                record.sequenceNo(),
                MESSAGE_TYPE_ASSISTANT.equals(record.messageType())
                        ? artifactResponseConsistencyService.reconcilePersistedMessage(record.content())
                        : record.content(),
                record.responseStatus(),
                record.errorMessage(),
                record.createdAt(),
                attachmentService.listMessageAttachments(record.conversationId(), record.messageId())
        );
    }

    private TaskResponse toTaskResponse(TaskRecord record) {
        List<TaskStepResponse> steps = conversationRepository.findTaskStepsByTaskId(record.taskId()).stream()
                .map(this::toTaskStepResponse)
                .toList();
        List<TaskPlanResponse> plans = conversationRepository.findTaskPlansByTaskId(record.taskId()).stream()
                .map(this::toTaskPlanResponse)
                .toList();
        List<ApprovalResponse> approvals = conversationRepository.findApprovalsByTaskId(record.taskId()).stream()
                .map(this::toApprovalResponse)
                .toList();
        TaskMemorySnapshotRecord taskMemorySnapshotRecord = memoryResolverService.resolveTaskMemorySnapshot(record.taskId())
                .orElse(null);
        return new TaskResponse(
                record.taskId(),
                record.taskCode(),
                record.conversationId(),
                record.messageId(),
                record.agentId(),
                record.parentTaskId(),
                record.rootTaskId(),
                record.triggerTaskStepId(),
                record.taskKind(),
                record.executionType(),
                record.currentPlanVersion(),
                record.executionChainJson(),
                record.traceId(),
                record.promptId(),
                record.promptName(),
                record.status(),
                record.startedAt(),
                record.finishedAt(),
                record.errorMessage(),
                taskMemorySnapshotRecord == null ? null : taskMemorySnapshotRecord.memorySummary(),
                taskMemorySnapshotRecord == null ? null : taskMemorySnapshotRecord.keyFindings(),
                taskMemorySnapshotRecord == null ? null : taskMemorySnapshotRecord.finalResponseSummary(),
                record.createdAt(),
                approvals,
                plans,
                conversationRepository.findTaskTracesByTaskId(record.taskId()).stream()
                        .map(this::toTaskTraceResponse)
                        .toList(),
                steps
        );
    }

    private TaskTraceResponse toTaskTraceResponse(TaskTraceRecord record) {
        return new TaskTraceResponse(
                record.taskTraceId(),
                record.taskId(),
                record.traceId(),
                record.taskStepId(),
                record.eventType(),
                record.spanName(),
                record.traceStatus(),
                record.inputSnapshot(),
                record.outputSnapshot(),
                record.errorMessage(),
                record.startedAt(),
                record.finishedAt(),
                record.durationMs(),
                record.metadata(),
                record.createdAt()
        );
    }

    private TaskDelegationTreeResponse.DelegationTreeNodeResponse buildDelegationNode(
            TaskRecord task,
            Map<Long, List<TaskRecord>> childrenMap,
            Map<Long, Integer> stepCountMap,
            Map<Long, Integer> traceCountMap,
            Map<Long, Integer> approvalCountMap,
            Map<Long, TaskDelegationRecord> delegationByChildTaskId,
            String rootAgentName,
            int depth) {
        List<TaskDelegationTreeResponse.DelegationTreeNodeResponse> children = childrenMap.getOrDefault(task.taskId(), List.of()).stream()
                .sorted(java.util.Comparator.comparing(TaskRecord::taskId))
                .map(item -> buildDelegationNode(
                        item,
                        childrenMap,
                        stepCountMap,
                        traceCountMap,
                        approvalCountMap,
                        delegationByChildTaskId,
                        rootAgentName,
                        depth + 1
                ))
                .toList();
        int maxChildDepth = children.stream().mapToInt(TaskDelegationTreeResponse.DelegationTreeNodeResponse::depth).max().orElse(depth);
        return new TaskDelegationTreeResponse.DelegationTreeNodeResponse(
                task.taskId(),
                task.taskCode(),
                task.agentId(),
                resolveTaskAgentName(task, delegationByChildTaskId, rootAgentName),
                task.taskKind(),
                task.executionType(),
                task.traceId(),
                task.status(),
                task.parentTaskId(),
                task.rootTaskId(),
                task.triggerTaskStepId(),
                task.currentPlanVersion(),
                buildDelegationResultSummary(task),
                task.errorMessage(),
                stepCountMap.getOrDefault(task.taskId(), 0),
                traceCountMap.getOrDefault(task.taskId(), 0),
                approvalCountMap.getOrDefault(task.taskId(), 0),
                children.size(),
                countDescendants(children),
                countDescendantsByStatus(children, STATUS_SUCCESS),
                countDescendantsByStatus(children, STATUS_FAILED),
                countDescendantsByStatus(children, STATUS_WAITING_APPROVAL),
                maxChildDepth,
                task.startedAt(),
                task.finishedAt(),
                children
        );
    }

    private String resolveTaskAgentName(TaskRecord task,
                                        Map<Long, TaskDelegationRecord> delegationByChildTaskId,
                                        String rootAgentName) {
        if (task.parentTaskId() == null) {
            return rootAgentName;
        }
        TaskDelegationRecord delegationRecord = delegationByChildTaskId.get(task.taskId());
        if (delegationRecord != null && delegationRecord.delegateAgentName() != null && !delegationRecord.delegateAgentName().isBlank()) {
            return delegationRecord.delegateAgentName();
        }
        return task.agentId() == null ? null : "Agent#" + task.agentId();
    }

    private String buildAuditChain(TaskDelegationRecord delegationRecord, Map<Long, TaskRecord> taskMap) {
        ArrayList<String> chain = new ArrayList<>();
        TaskRecord current = taskMap.get(delegationRecord.childTaskId());
        while (current != null) {
            chain.add(current.taskCode());
            if (current.parentTaskId() == null) {
                break;
            }
            current = taskMap.get(current.parentTaskId());
        }
        java.util.Collections.reverse(chain);
        return String.join(" -> ", chain);
    }

    private int countDescendants(List<TaskDelegationTreeResponse.DelegationTreeNodeResponse> children) {
        return children.stream()
                .mapToInt(item -> 1 + item.descendantCount())
                .sum();
    }

    private int countDescendantsByStatus(List<TaskDelegationTreeResponse.DelegationTreeNodeResponse> children, String status) {
        return children.stream()
                .mapToInt(item -> (status.equals(item.status()) ? 1 : 0) + countDescendantsByStatus(item.children(), status))
                .sum();
    }

    private String buildDelegationResultSummary(TaskRecord task) {
        if (STATUS_SUCCESS.equals(task.status())) {
            return Optional.ofNullable(memoryResolverService.resolveTaskMemorySnapshot(task.taskId()).orElse(null))
                    .map(item -> {
                        if (item.finalResponseSummary() != null && !item.finalResponseSummary().isBlank()) {
                            return item.finalResponseSummary();
                        }
                        if (item.keyFindings() != null && !item.keyFindings().isBlank()) {
                            return item.keyFindings();
                        }
                        if (item.memorySummary() != null && !item.memorySummary().isBlank()) {
                            return item.memorySummary();
                        }
                        return "任务已成功完成";
                    })
                    .orElse("任务已成功完成");
        }
        if (STATUS_FAILED.equals(task.status())) {
            return task.errorMessage() == null || task.errorMessage().isBlank() ? "任务执行失败" : task.errorMessage();
        }
        if (STATUS_WAITING_APPROVAL.equals(task.status())) {
            return "任务等待审批后继续执行";
        }
        if (STATUS_PENDING.equals(task.status())) {
            return "任务待执行";
        }
        return "任务处理中";
    }

    private String ensureTraceId(TaskRecord taskRecord, Long operatorId) {
        if (taskRecord.traceId() != null && !taskRecord.traceId().isBlank()) {
            return taskRecord.traceId();
        }
        String traceId = "TRACE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        conversationRepository.updateTaskTraceId(taskRecord.taskId(), traceId, operatorId);
        return traceId;
    }

    private void validateApprovalOperator(ApprovalRecord approvalRecord, SecurityUser securityUser) {
        JsonNode metadataNode = objectMapper.valueToTree(safeJson(approvalRecord.metadata()));
        JsonNode primaryPolicy = metadataNode.path("primaryPolicy");
        List<Long> approvalUserIds = jsonLongList(primaryPolicy.path("approvalUserIds"));
        List<String> approvalRoleCodes = jsonTextList(primaryPolicy.path("approvalRoleCodes"));
        if (!approvalUserIds.isEmpty() && !approvalUserIds.contains(securityUser.userId())) {
            throw new BusinessException("当前审批仅允许指定用户处理");
        }
        if (!approvalRoleCodes.isEmpty()) {
            List<String> currentRoleCodes = securityUser.roleCodes() == null ? List.of() : securityUser.roleCodes().stream()
                    .map(item -> item == null ? null : item.trim().toLowerCase())
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
            boolean matched = approvalRoleCodes.stream()
                    .map(item -> item == null ? null : item.trim().toLowerCase())
                    .filter(item -> item != null && !item.isBlank())
                    .anyMatch(currentRoleCodes::contains);
            if (!matched) {
                throw new BusinessException("当前审批仅允许指定角色处理");
            }
        }
    }

    private List<Long> jsonLongList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<Long> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.canConvertToLong()) {
                values.add(item.asLong());
            }
        }
        return values;
    }

    private List<String> jsonTextList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private TaskPlanResponse toTaskPlanResponse(TaskPlanRecord record) {
        List<TaskPlanStepResponse> steps = conversationRepository.findTaskPlanStepsByTaskPlanId(record.taskPlanId()).stream()
                .map(this::toTaskPlanStepResponse)
                .toList();
        return new TaskPlanResponse(
                record.taskPlanId(),
                record.taskId(),
                record.planVersion(),
                record.planId(),
                record.planType(),
                record.goal(),
                record.status(),
                record.assumptionsJson(),
                record.constraintsJson(),
                record.risksJson(),
                record.plannerPromptId(),
                record.plannerPromptName(),
                record.source(),
                record.metadataJson(),
                record.createdAt(),
                steps
        );
    }

    private TaskPlanRecord resolveTaskPlanRecord(List<TaskPlanRecord> plans, Integer planVersion) {
        if (planVersion == null) {
            return plans.stream()
                    .max(java.util.Comparator.comparing(TaskPlanRecord::planVersion))
                    .orElse(null);
        }
        return plans.stream()
                .filter(item -> planVersion.equals(item.planVersion()))
                .findFirst()
                .orElse(null);
    }

    private TaskPlanDiffResponse buildTaskPlanDiffResponse(Long taskId, TaskPlanRecord fromPlan, TaskPlanRecord toPlan) {
        Map<String, TaskPlanStepRecord> fromSteps = conversationRepository.findTaskPlanStepsByTaskPlanId(fromPlan.taskPlanId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.stepId() == null || item.stepId().isBlank() ? "stepNo#" + item.stepNo() : item.stepId(),
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, TaskPlanStepRecord> toSteps = conversationRepository.findTaskPlanStepsByTaskPlanId(toPlan.taskPlanId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.stepId() == null || item.stepId().isBlank() ? "stepNo#" + item.stepNo() : item.stepId(),
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        LinkedHashSet<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(fromSteps.keySet());
        allKeys.addAll(toSteps.keySet());
        List<TaskPlanDiffResponse.PlanStepDiffResponse> changedSteps = new ArrayList<>();
        int addedCount = 0;
        int removedCount = 0;
        int changedCount = 0;
        for (String key : allKeys) {
            TaskPlanStepRecord before = fromSteps.get(key);
            TaskPlanStepRecord after = toSteps.get(key);
            if (before == null && after != null) {
                addedCount++;
                changedSteps.add(new TaskPlanDiffResponse.PlanStepDiffResponse("added", key, null, formatPlanStepText(after)));
                continue;
            }
            if (before != null && after == null) {
                removedCount++;
                changedSteps.add(new TaskPlanDiffResponse.PlanStepDiffResponse("removed", key, formatPlanStepText(before), null));
                continue;
            }
            if (before != null && after != null && !formatPlanStepText(before).equals(formatPlanStepText(after))) {
                changedCount++;
                changedSteps.add(new TaskPlanDiffResponse.PlanStepDiffResponse("changed", key, formatPlanStepText(before), formatPlanStepText(after)));
            }
        }
        return new TaskPlanDiffResponse(
                taskId,
                fromPlan.planVersion(),
                toPlan.planVersion(),
                new TaskPlanDiffResponse.DiffSummaryResponse(addedCount, removedCount, changedCount),
                changedSteps
        );
    }

    private String formatPlanStepText(TaskPlanStepRecord record) {
        return "stepNo=" + record.stepNo()
                + ", type=" + record.stepType()
                + ", name=" + record.stepName()
                + ", objective=" + normalizeNullableText(record.objective())
                + ", tool=" + normalizeNullableText(record.targetToolCode())
                + ", workflow=" + (record.targetWorkflowId() == null ? "" : record.targetWorkflowId())
                + ", expectedOutput=" + normalizeNullableText(record.expectedOutput())
                + ", failurePolicy=" + normalizeNullableText(record.failurePolicy());
    }

    private String formatPlanStepText(ExecutionPlan.ExecutionPlanStep record) {
        return "stepNo=" + record.stepNo()
                + ", type=" + record.stepType()
                + ", name=" + record.stepName()
                + ", objective=" + normalizeNullableText(record.objective())
                + ", tool=" + normalizeNullableText(record.targetToolCode())
                + ", workflow=" + (record.targetWorkflowId() == null ? "" : record.targetWorkflowId())
                + ", expectedOutput=" + normalizeNullableText(record.expectedOutput())
                + ", failurePolicy=" + normalizeNullableText(record.failurePolicy());
    }

    private ExecutionPlan buildManualExecutionPlan(ManualTaskPlanContinueRequest request, ExecutionContext executionContext) {
        ExecutionPlan rawPlan = new ExecutionPlan(
                "PLAN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(),
                request.planType(),
                request.goal(),
                "draft",
                request.assumptions() == null ? List.of() : request.assumptions(),
                request.constraints() == null ? List.of() : request.constraints(),
                request.risks() == null ? List.of() : request.risks(),
                request.steps().stream()
                        .map(item -> new ExecutionPlan.ExecutionPlanStep(
                                item.stepNo(),
                                item.stepId(),
                                item.stepType(),
                                item.stepName(),
                                item.objective(),
                                item.dependsOnStepIds() == null ? List.of() : item.dependsOnStepIds(),
                                item.targetToolCode(),
                                item.targetWorkflowId(),
                                item.inputs() == null ? Map.of() : item.inputs(),
                                item.expectedOutput(),
                                item.failurePolicy(),
                                item.replannable(),
                                item.status(),
                                item.description()
                        ))
                        .toList()
        );
        return planValidatorService.validateAndNormalize(rawPlan, executionContext);
    }

    private ExecutionPlan toExecutionPlan(TaskPlanRecord taskPlanRecord) {
        List<ExecutionPlan.ExecutionPlanStep> steps = conversationRepository.findTaskPlanStepsByTaskPlanId(taskPlanRecord.taskPlanId()).stream()
                .map(item -> new ExecutionPlan.ExecutionPlanStep(
                        item.stepNo(),
                        item.stepId(),
                        item.stepType(),
                        item.stepName(),
                        item.objective(),
                        parseJsonArrayToStringList(item.dependsOnJson()),
                        item.targetToolCode(),
                        item.targetWorkflowId(),
                        parseJsonObjectMap(item.inputsJson()),
                        item.expectedOutput(),
                        item.failurePolicy(),
                        item.replannable(),
                        item.status(),
                        item.description()
                ))
                .toList();
        return new ExecutionPlan(
                taskPlanRecord.planId(),
                taskPlanRecord.planType(),
                taskPlanRecord.goal(),
                taskPlanRecord.status(),
                parseJsonArrayToStringList(taskPlanRecord.assumptionsJson()),
                parseJsonArrayToStringList(taskPlanRecord.constraintsJson()),
                parseJsonArrayToStringList(taskPlanRecord.risksJson()),
                steps
        );
    }

    private List<String> parseJsonArrayToStringList(String jsonText) {
        Object parsed = safeJson(jsonText);
        if (!(parsed instanceof JsonNode jsonNode) || !jsonNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : jsonNode) {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private Map<String, Object> parseJsonObjectMap(String jsonText) {
        Object parsed = safeJson(jsonText);
        if (parsed instanceof JsonNode jsonNode && jsonNode.isObject()) {
            return objectMapper.convertValue(jsonNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        }
        return Map.of();
    }

    private TaskPlanStepResponse toTaskPlanStepResponse(TaskPlanStepRecord record) {
        return new TaskPlanStepResponse(
                record.taskPlanStepId(),
                record.stepId(),
                record.stepNo(),
                record.stepType(),
                record.stepName(),
                record.objective(),
                record.dependsOnJson(),
                record.targetToolCode(),
                record.targetWorkflowId(),
                record.inputsJson(),
                record.expectedOutput(),
                record.failurePolicy(),
                record.replannable(),
                record.status(),
                record.description(),
                record.createdAt()
        );
    }

    private String resolveExecutionType(ExecutionDecision executionDecision) {
        boolean hasWorkflow = executionDecision.hasWorkflowSelection();
        boolean hasTools = executionDecision.hasToolSelection();
        if (hasWorkflow && hasTools) {
            return "hybrid";
        }
        if (hasWorkflow) {
            return "workflow_only";
        }
        if (hasTools) {
            return "tool_only";
        }
        return "direct_reply";
    }

    private String buildExecutionChainJson(ExecutionContext executionContext, ExecutionPlan executionPlan) {
        List<Map<String, Object>> chain = executionPlan.steps().stream()
                .map(step -> buildExecutionChainItem(executionContext, step))
                .toList();
        return toJsonText(chain);
    }

    private ExecutionPlan ensureUniquePlanId(ExecutionPlan executionPlan, Long taskId) {
        if (executionPlan == null || executionPlan.planId() == null || executionPlan.planId().isBlank()) {
            return executionPlan;
        }
        if (!conversationRepository.existsTaskPlanId(executionPlan.planId())) {
            return executionPlan;
        }
        String normalizedPlanId = executionPlan.planId()
                + "_T" + taskId
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return new ExecutionPlan(
                normalizedPlanId,
                executionPlan.planType(),
                executionPlan.goal(),
                executionPlan.status(),
                executionPlan.assumptions(),
                executionPlan.constraints(),
                executionPlan.risks(),
                executionPlan.steps()
        );
    }

    private SendMessageResponse buildSendMessageResponseSafely(ConversationRecord conversationRecord,
                                                               Long conversationId,
                                                               Long userMessageId,
                                                               Long assistantMessageId,
                                                               Long taskId,
                                                               String userContent,
                                                               String assistantContent,
                                                               SecurityUser securityUser) {
        try {
            ConversationDetailResponse conversation = getConversationDetail(conversationId, securityUser);
            MessageResponse userMessage = conversationRepository.findMessageById(userMessageId)
                    .map(this::toMessageResponse)
                    .orElseThrow(() -> new BusinessException("用户消息写入失败"));
            MessageResponse assistantMessage = buildAssistantResponse(
                    assistantMessageId, conversationId, assistantContent);
            TaskResponse task = conversationRepository.findTasksByConversationId(conversationId).stream()
                    .filter(item -> item.taskId().equals(taskId))
                    .findFirst()
                    .map(this::toTaskResponse)
                    .orElseThrow(() -> new BusinessException("任务写入失败"));
            return new SendMessageResponse(conversation, userMessage, assistantMessage, task);
        } catch (Exception exception) {
            log.warn("发送消息响应组装失败，降级返回最小响应, conversationId={}, taskId={}, message={}",
                    conversationId, taskId, exception.getMessage(), exception);
            MessageResponse userMessage = conversationRepository.findMessageById(userMessageId)
                    .map(this::toMessageResponse)
                    .orElse(new MessageResponse(userMessageId, conversationId, MESSAGE_TYPE_USER, 0, userContent, STATUS_SUCCESS, null, LocalDateTime.now()));
            MessageResponse assistantMessage = buildAssistantResponse(
                    assistantMessageId, conversationId, assistantContent);
            TaskRecord taskRecord = conversationRepository.findTasksByConversationId(conversationId).stream()
                    .filter(item -> item.taskId().equals(taskId))
                    .findFirst()
                    .orElse(null);
            TaskResponse task = taskRecord == null
                    ? buildMinimalTaskResponse(conversationRecord, taskId, userMessageId)
                    : buildMinimalTaskResponse(taskRecord);
            return new SendMessageResponse(
                    buildMinimalConversationResponse(conversationRecord, assistantContent),
                    userMessage,
                    assistantMessage,
                    task
            );
        }
    }

    /**
     * 仅在任务产生最终业务结果时写入助手消息。
     *
     * 审批、外部事件等待和人工暂停属于运行状态，由 Task、Run 与审批弹窗承载，
     * 不应伪装成 Agent 回答进入聊天历史。
     */
    private Long persistAssistantMessageIfCompleted(
            Long conversationId,
            String assistantContent,
            RuntimeExecutionAggregate aggregate,
            Long operatorId) {
        if (aggregate.waitingApproval() || aggregate.waitingEvent() || aggregate.paused()) {
            return null;
        }
        return conversationRepository.insertMessageWithAllocatedSequence(
                conversationId,
                MESSAGE_TYPE_ASSISTANT,
                assistantContent,
                STATUS_SUCCESS,
                null,
                operatorId
        ).messageId();
    }

    /** 历史运行控制提示不再作为对话消息返回，原始 Task/Trace/Approval 审计数据仍完整保留。 */
    private boolean isRuntimeControlAssistantMessage(MessageRecord message) {
        if (!MESSAGE_TYPE_ASSISTANT.equals(message.messageType()) || message.content() == null) {
            return false;
        }
        String content = message.content().trim();
        return (content.contains("审批节点") && content.contains("等待人工审批"))
                || "当前任务等待审批".equals(content)
                || (content.startsWith("审批节点「")
                && (content.contains("当前任务结束") || content.contains("二次规划")))
                || (content.startsWith("# 当前进度汇报")
                && content.contains("高风险Tool人工审批")
                && (content.contains("等待中") || content.contains("暂停")))
                || content.contains("任务已在安全步骤边界暂停")
                || content.contains("任务等待外部事件");
    }

    /**
     * 同步接口在执行挂起时仍返回兼容的响应对象，但该运行状态不会持久化到会话消息表。
     */
    private MessageResponse buildAssistantResponse(Long assistantMessageId,
                                                   Long conversationId,
                                                   String assistantContent) {
        if (assistantMessageId == null) {
            return new MessageResponse(
                    null, conversationId, MESSAGE_TYPE_ASSISTANT, 0,
                    assistantContent, STATUS_SUCCESS, null, LocalDateTime.now());
        }
        return conversationRepository.findMessageById(assistantMessageId)
                .map(this::toMessageResponse)
                .orElse(new MessageResponse(
                        assistantMessageId, conversationId, MESSAGE_TYPE_ASSISTANT, 0,
                        assistantContent, STATUS_SUCCESS, null, LocalDateTime.now()));
    }

    private ConversationDetailResponse buildMinimalConversationResponse(ConversationRecord record,
                                                                        String assistantContent) {
        ConversationMemoryPolicyResolver.ConversationUserMemoryPolicy memoryPolicy =
                ConversationMemoryPolicyResolver.resolveConversationPolicy(record.metadata(), null);
        ConversationPolicyService.PolicySnapshot effectiveCompactionPolicy = conversationPolicyService.resolveEffective(record.metadata());
        ConversationPolicyService.PolicySnapshot agentDefaultCompactionPolicy = conversationPolicyService.resolveAgentDefault(record.metadata());
        return new ConversationDetailResponse(
                record.conversationId(),
                record.conversationCode(),
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.defaultModel(),
                record.defaultPromptId(),
                record.defaultPromptName(),
                record.decisionPromptId(),
                record.decisionPromptName(),
                record.plannerPromptId(),
                record.plannerPromptName(),
                record.title(),
                STATUS_SUCCESS,
                conversationPolicyService.resolveResponseStreamingEnabled(record.metadata()),
                LocalDateTime.now(),
                truncatePreview(assistantContent),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                memoryPolicy.userMemoryEnabled(),
                memoryPolicy.userMemoryMode(),
                memoryPolicy.userMemoryWritebackEnabled(),
                memoryPolicy.agentDefaultUserMemoryEnabled(),
                memoryPolicy.agentDefaultUserMemoryMode(),
                memoryPolicy.agentDefaultUserMemoryWritebackEnabled(),
                effectiveCompactionPolicy.preferSummaryOverRawHistory(),
                effectiveCompactionPolicy.maxRecentMessages(),
                effectiveCompactionPolicy.maxRecentUserMessages(),
                effectiveCompactionPolicy.preferStateFirst(),
                effectiveCompactionPolicy.historySummaryThresholdMessages(),
                effectiveCompactionPolicy.minRetentionRatio(),
                effectiveCompactionPolicy.qualityWarningThreshold(),
                agentDefaultCompactionPolicy.preferSummaryOverRawHistory(),
                agentDefaultCompactionPolicy.maxRecentMessages(),
                agentDefaultCompactionPolicy.maxRecentUserMessages(),
                agentDefaultCompactionPolicy.preferStateFirst(),
                agentDefaultCompactionPolicy.historySummaryThresholdMessages(),
                agentDefaultCompactionPolicy.minRetentionRatio(),
                agentDefaultCompactionPolicy.qualityWarningThreshold(),
                record.createdAt(),
                LocalDateTime.now()
        );
    }

    private TaskResponse buildMinimalTaskResponse(TaskRecord record) {
        return new TaskResponse(
                record.taskId(),
                record.taskCode(),
                record.conversationId(),
                record.messageId(),
                record.agentId(),
                record.parentTaskId(),
                record.rootTaskId(),
                record.triggerTaskStepId(),
                record.taskKind(),
                record.executionType(),
                record.currentPlanVersion(),
                record.executionChainJson(),
                record.traceId(),
                record.promptId(),
                record.promptName(),
                record.status(),
                record.startedAt(),
                record.finishedAt(),
                record.errorMessage(),
                null,
                null,
                null,
                record.createdAt(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private TaskResponse buildMinimalTaskResponse(ConversationRecord conversationRecord,
                                                  Long taskId,
                                                  Long messageId) {
        return new TaskResponse(
                taskId,
                "",
                conversationRecord.conversationId(),
                messageId,
                conversationRecord.agentId(),
                null,
                null,
                null,
                "root",
                null,
                null,
                null,
                null,
                conversationRecord.defaultPromptId(),
                conversationRecord.defaultPromptName(),
                STATUS_SUCCESS,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private void safeRunPostExecution(String actionName, CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            log.warn("消息执行后置处理失败, action={}, message={}", actionName, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run();
    }

    private Map<String, Object> buildExecutionChainItem(ExecutionContext executionContext,
                                                        ExecutionPlan.ExecutionPlanStep step) {
        if ("workflow".equals(step.stepType()) && step.targetWorkflowId() != null) {
            RegistryAgentWorkflow workflow = executionContext.availableWorkflows().stream()
                    .filter(item -> step.targetWorkflowId().equals(item.workflowId()))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepNo", step.stepNo());
            item.put("resourceType", "workflow");
            item.put("resourceId", step.targetWorkflowId());
            item.put("resourceCode", workflow == null ? null : workflow.workflowCode());
            item.put("resourceName", workflow == null ? step.stepName() : workflow.workflowName());
            item.put("stepId", step.stepId());
            item.put("objective", step.objective());
            item.put("dependsOnStepIds", step.dependsOnStepIds());
            item.put("expectedOutput", step.expectedOutput());
            item.put("failurePolicy", step.failurePolicy());
            return item;
        }
        if ("tool".equals(step.stepType()) && step.targetToolCode() != null) {
            RegistryAgentTool tool = executionContext.availableTools().stream()
                    .filter(item -> step.targetToolCode().equals(item.toolCode()))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepNo", step.stepNo());
            item.put("resourceType", "tool");
            item.put("resourceId", tool == null ? null : tool.toolId());
            item.put("resourceCode", step.targetToolCode());
            item.put("resourceName", tool == null ? step.stepName() : tool.toolName());
            item.put("stepId", step.stepId());
            item.put("objective", step.objective());
            item.put("dependsOnStepIds", step.dependsOnStepIds());
            item.put("expectedOutput", step.expectedOutput());
            item.put("failurePolicy", step.failurePolicy());
            return item;
        }
        if ("handoff".equals(step.stepType())) {
            Long delegateAgentId = resolveDelegateAgentId(step);
            ExecutionContext.DelegateAgent delegateAgent = executionContext.availableDelegates().stream()
                    .filter(item -> delegateAgentId != null && delegateAgentId.equals(item.agentId()))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepNo", step.stepNo());
            item.put("resourceType", "handoff");
            item.put("resourceId", delegateAgentId);
            item.put("resourceCode", delegateAgent == null ? null : delegateAgent.agentCode());
            item.put("resourceName", delegateAgent == null ? step.stepName() : delegateAgent.agentName());
            item.put("stepId", step.stepId());
            item.put("objective", step.objective());
            item.put("dependsOnStepIds", step.dependsOnStepIds());
            item.put("expectedOutput", step.expectedOutput());
            item.put("failurePolicy", step.failurePolicy());
            return item;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("stepNo", step.stepNo());
        item.put("resourceType", step.stepType());
        item.put("resourceId", null);
        item.put("resourceCode", null);
        item.put("resourceName", step.stepName());
        item.put("stepId", step.stepId());
        item.put("objective", step.objective());
        item.put("dependsOnStepIds", step.dependsOnStepIds());
        item.put("expectedOutput", step.expectedOutput());
        item.put("failurePolicy", step.failurePolicy());
        return item;
    }

    private Long resolveDelegateAgentId(ExecutionPlan.ExecutionPlanStep step) {
        if (step.inputs() == null) {
            return null;
        }
        Object delegateAgentIdValue = step.inputs().get("delegateAgentId");
        if (delegateAgentIdValue instanceof Number number) {
            return number.longValue();
        }
        if (delegateAgentIdValue instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private ApprovalResponse toApprovalResponse(ApprovalRecord record) {
        return new ApprovalResponse(
                record.approvalId(),
                record.conversationId(),
                record.taskId(),
                record.taskStepId(),
                record.nodeId(),
                record.nodeName(),
                record.status(),
                record.approvalComment(),
                record.approvedBy(),
                record.approvedAt(),
                record.metadata(),
                record.createdAt()
        );
    }

    private TaskStepResponse toTaskStepResponse(TaskStepRecord record) {
        return new TaskStepResponse(
                record.taskStepId(),
                record.stepNo(),
                record.stepType(),
                record.stepName(),
                record.status(),
                record.retryCount(),
                record.inputSnapshot(),
                record.outputSnapshot(),
                record.errorMessage(),
                record.startedAt(),
                record.finishedAt(),
                record.createdAt()
        );
    }

    private String applyOutputGuardrails(ExecutionContext executionContext,
                                         String assistantContent,
                                         Long taskId,
                                         String traceId,
                                         Long operatorId) {
        return outputGuardrailService.apply(executionContext, assistantContent, taskId, traceId, operatorId);
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("执行快照序列化失败");
        }
    }

    private Map<String, Object> mergeTraceEntryMetadata(String entry, Map<String, Object> metadata) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        merged.put("entry", entry);
        if (metadata != null && !metadata.isEmpty()) {
            merged.putAll(metadata);
        }
        return merged;
    }

    private String truncatePromptContent(String promptContent) {
        if (promptContent.length() <= 120) {
            return promptContent;
        }
        return promptContent.substring(0, 120) + "...";
    }

    /**
     * 执行输入快照。
     *
     * @param userMessage       用户消息
     * @param promptCode        Prompt 编码
     * @param agentCode         Agent 编码
     * @param agentName         Agent 名称
     * @param defaultPromptName Prompt 名称
     * @param promptType        Prompt 类型
     * @param promptCategory    Prompt 分类
     * @param promptContent     Prompt 正文
     * @param decisionPromptName 决策 Prompt 名称
     * @param decisionPromptSummary 决策 Prompt 摘要
     * @param plannerPromptName 规划 Prompt 名称
     * @param plannerPromptSummary 规划 Prompt 摘要
     * @param workflowName      Workflow 名称
     * @param decisionType      决策类型
     * @param decisionReason    决策原因
     * @param planId            计划 ID
     * @param planGoal          计划目标
     * @param planType          计划类型
     * @param plannedSteps      计划步骤摘要
     */
    private record ExecutionInputSnapshot(
            String userMessage,
            String promptCode,
            String agentCode,
            String agentName,
            String defaultPromptName,
            String promptType,
            String promptCategory,
            String promptContent,
            String decisionPromptName,
            String decisionPromptSummary,
            String plannerPromptName,
            String plannerPromptSummary,
            String workflowName,
            List<String> effectiveTools,
            String decisionType,
            String decisionReason,
            String planId,
            String planGoal,
            String planType,
            List<String> plannedSteps
    ) {
    }

    private record ApprovalPendingSnapshot(
            String assistantMessage,
            String approvalNodeId,
            String approvalNodeName,
            String nextNodeId,
            List<Map<String, Object>> executedNodes,
            List<Map<String, Object>> previousNodeResults,
            String approvalStatus,
            String approvalSourceType,
            Integer nextPlanStepNo
    ) {
    }

    private record WorkflowApprovalResumeContext(
            WorkflowResumeContext resumeContext
    ) {
    }
}

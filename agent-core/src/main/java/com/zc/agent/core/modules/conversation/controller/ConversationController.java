package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.ConversationPageQueryRequest;
import com.zc.agent.core.modules.conversation.dto.CreateConversationRequest;
import com.zc.agent.core.modules.conversation.dto.ManualTaskPlanContinueRequest;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.modules.conversation.dto.ResolveCheckpointAmbiguityRequest;
import com.zc.agent.core.modules.conversation.dto.TaskPlanControlRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationTitleRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationInteractionPreferenceRequest;
import com.zc.agent.core.modules.runtime.model.RuntimeCheckpoint;
import com.zc.agent.core.modules.runtime.service.DurableWaitService;
import com.zc.agent.core.modules.conversation.dto.AgentRunBatchRequest;
import com.zc.agent.core.modules.conversation.dto.ApprovalActionRequest;
import com.zc.agent.core.modules.conversation.dto.UpdateConversationMemoryPolicyRequest;
import com.zc.agent.core.modules.conversation.service.ConversationService;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.service.AgentRunStreamService;
import com.zc.agent.core.modules.runtime.kernel.ApprovalCoordinator;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.conversation.vo.ConversationDetailResponse;
import com.zc.agent.core.modules.conversation.vo.ConversationExecutionResponse;
import com.zc.agent.core.modules.conversation.vo.ConversationPageItemResponse;
import com.zc.agent.core.modules.conversation.vo.MessageResponse;
import com.zc.agent.core.modules.conversation.vo.ApprovalMetricsResponse;
import com.zc.agent.core.modules.conversation.vo.SendMessageResponse;
import com.zc.agent.core.modules.conversation.vo.TaskPlanDiffResponse;
import com.zc.agent.core.modules.conversation.vo.TaskResponse;
import com.zc.agent.core.modules.conversation.vo.TaskDelegationTreeResponse;
import com.zc.agent.core.modules.conversation.vo.TaskArtifactDownload;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * 会话接口。
 */
@Validated
@RestController
@RequestMapping("/api/core/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final AgentRunService agentRunService;
    private final AgentRunStreamService agentRunStreamService;
    private final DurableWaitService durableWaitService;
    private final ApprovalCoordinator approvalCoordinator;

    public ConversationController(ConversationService conversationService,
                                  AgentRunService agentRunService,
                                  AgentRunStreamService agentRunStreamService,
                                  DurableWaitService durableWaitService,
                                  ApprovalCoordinator approvalCoordinator) {
        this.conversationService = conversationService;
        this.agentRunService = agentRunService;
        this.agentRunStreamService = agentRunStreamService;
        this.durableWaitService = durableWaitService;
        this.approvalCoordinator = approvalCoordinator;
    }

    /**
     * 分页查询当前用户会话。
     *
     * @param request        查询条件
     * @param authentication 当前认证
     * @return 会话分页
     */
    @GetMapping
    public ApiResponse<PageResponse<ConversationPageItemResponse>> pageConversations(@Valid ConversationPageQueryRequest request,
                                                                                     Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.pageConversations(request, securityUser));
    }

    /**
     * 创建会话。
     *
     * @param request        创建请求
     * @param authorization  原始 Authorization 头
     * @param authentication 当前认证
     * @return 会话详情
     */
    @PostMapping
    public ApiResponse<ConversationDetailResponse> createConversation(@Valid @RequestBody CreateConversationRequest request,
                                                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                      Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.createConversation(request, authorization, securityUser));
    }

    /**
     * 获取会话详情。
     *
     * @param conversationId 会话 ID
     * @param authentication 当前认证
     * @return 会话详情
     */
    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationDetailResponse> getConversationDetail(@PathVariable Long conversationId,
                                                                         Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.getConversationDetail(conversationId, securityUser));
    }

    /**
     * 下载 Task 执行生成的文件产物。
     */
    @GetMapping("/{conversationId}/tasks/{taskId}/artifacts")
    public ResponseEntity<byte[]> downloadTaskArtifact(@PathVariable Long conversationId,
                                                       @PathVariable Long taskId,
                                                       @RequestParam String path,
                                                       Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskArtifactDownload artifact = conversationService.downloadTaskArtifact(
                conversationId, taskId, path, securityUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(artifact.filename(), StandardCharsets.UTF_8)
                                .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(artifact.content());
    }

    /**
     * 修改当前用户的会话名称。
     *
     * @param conversationId 会话 ID
     * @param request        标题更新请求
     * @param authentication 当前认证
     * @return 更新后的会话详情
     */
    @PutMapping("/{conversationId}/title")
    public ApiResponse<ConversationDetailResponse> updateConversationTitle(@PathVariable Long conversationId,
                                                                           @Valid @RequestBody UpdateConversationTitleRequest request,
                                                                           Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.updateConversationTitle(conversationId, request, securityUser));
    }

    /** 更新当前会话的交互偏好。 */
    @PutMapping("/{conversationId}/interaction-preferences")
    public ApiResponse<ConversationDetailResponse> updateConversationInteractionPreference(
            @PathVariable Long conversationId,
            @Valid @RequestBody UpdateConversationInteractionPreferenceRequest request,
            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.updateConversationInteractionPreference(
                conversationId, request, securityUser));
    }

    @PostMapping("/{conversationId}/memory-policy")
    public ApiResponse<ConversationDetailResponse> updateConversationMemoryPolicy(@PathVariable Long conversationId,
                                                                                  @Valid @RequestBody UpdateConversationMemoryPolicyRequest request,
                                                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                                  Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.updateConversationMemoryPolicy(
                conversationId,
                request,
                authorization,
                securityUser
        ));
    }

    /**
     * 查询会话消息。
     *
     * @param conversationId 会话 ID
     * @param authentication 当前认证
     * @return 消息列表
     */
    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> listMessages(@PathVariable Long conversationId,
                                                           Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.listMessages(conversationId, securityUser));
    }

    /**
     * 查询会话执行记录。
     *
     * @param conversationId 会话 ID
     * @param authentication 当前认证
     * @return 执行记录
     */
    @GetMapping("/{conversationId}/executions")
    public ApiResponse<ConversationExecutionResponse> getConversationExecutions(@PathVariable Long conversationId,
                                                                                Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.getConversationExecutions(conversationId, securityUser));
    }

    @GetMapping("/{conversationId}/approval-metrics")
    public ApiResponse<ApprovalMetricsResponse> getApprovalMetrics(@PathVariable Long conversationId,
                                                                   Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.getApprovalMetrics(conversationId, securityUser));
    }

    /**
     * 查询会话委派任务树。
     *
     * @param conversationId 会话 ID
     * @param authentication 当前认证
     * @return 委派任务树
     */
    @GetMapping("/{conversationId}/delegation-tree")
    public ApiResponse<TaskDelegationTreeResponse> getDelegationTree(@PathVariable Long conversationId,
                                                                     Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.getDelegationTree(conversationId, securityUser));
    }

    /**
     * 发送消息。
     *
     * @param conversationId 会话 ID
     * @param request        消息请求
     * @param authentication 当前认证
     * @return 发送结果
     */
    @PostMapping("/{conversationId}/messages")
    public ApiResponse<SendMessageResponse> sendMessage(@PathVariable Long conversationId,
                                                        @Valid @RequestBody SendMessageRequest request,
                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                        Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.sendMessage(conversationId, request, authorization, securityUser));
    }

    /**
     * 将消息提交到持久化运行队列并立即返回。
     */
    @PostMapping("/{conversationId}/messages/async")
    public ApiResponse<AgentRunResponse> enqueueMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Agent-Run-Priority", required = false) Integer priority,
            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(agentRunService.enqueue(conversationId, request, idempotencyKey, priority, securityUser));
    }

    /**
     * 批量提交同一会话下的异步消息。
     */
    @PostMapping("/{conversationId}/messages/async/batch")
    public ApiResponse<List<AgentRunResponse>> enqueueMessages(
            @PathVariable Long conversationId,
            @Valid @RequestBody AgentRunBatchRequest request,
            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(agentRunService.enqueueBatch(conversationId, request, securityUser));
    }

    /**
     * 查询异步运行状态。
     */
    @GetMapping("/{conversationId}/runs/{runCode}")
    public ApiResponse<AgentRunResponse> getRun(@PathVariable Long conversationId,
                                                @PathVariable String runCode,
                                                Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        AgentRunResponse response = agentRunService.get(runCode, securityUser);
        if (!conversationId.equals(response.conversationId())) {
            throw new com.zc.agent.core.common.exception.BusinessException("异步运行不属于当前会话");
        }
        return ApiResponse.success(response);
    }

    /**
     * 订阅 Run 状态和安全校验后的最终回复分片。
     * 使用 fetch ReadableStream 连接，支持 Authorization 请求头。
     */
    @GetMapping(value = "/{conversationId}/runs/{runCode}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamRun(@PathVariable Long conversationId,
                                            @PathVariable String runCode,
                                            Authentication authentication,
                                            HttpServletResponse response) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return agentRunStreamService.stream(conversationId, runCode, securityUser);
    }

    /**
     * 取消排队中或执行中的异步运行。
     */
    @PostMapping("/{conversationId}/runs/{runCode}/cancel")
    public ApiResponse<AgentRunResponse> cancelRun(@PathVariable Long conversationId,
                                                   @PathVariable String runCode,
                                                   Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        AgentRunResponse response = agentRunService.get(runCode, securityUser);
        if (!conversationId.equals(response.conversationId())) {
            throw new com.zc.agent.core.common.exception.BusinessException("异步运行不属于当前会话");
        }
        return ApiResponse.success(agentRunService.cancel(runCode, securityUser));
    }

    /**
     * 从失败步骤恢复执行。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param authorization  原始 Authorization 头
     * @param authentication 当前认证
     * @return 恢复后的任务结果
     */
    @PostMapping("/{conversationId}/tasks/{taskId}/resume")
    public ApiResponse<TaskResponse> resumeTask(@PathVariable Long conversationId,
                                                @PathVariable Long taskId,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskResponse response = conversationService.resumeTask(conversationId, taskId, authorization, securityUser);
        agentRunService.syncTaskStatus(taskId, response.status(), response.errorMessage());
        return ApiResponse.success(response);
    }

    /** 人工核验副作用结果后解除持久化 Checkpoint 恢复阻断。 */
    @PostMapping("/{conversationId}/tasks/{taskId}/checkpoint/resolve")
    public ApiResponse<RuntimeCheckpoint> resolveCheckpointAmbiguity(
            @PathVariable Long conversationId,
            @PathVariable Long taskId,
            @Valid @RequestBody ResolveCheckpointAmbiguityRequest request,
            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.resolveCheckpointAmbiguity(
                conversationId, taskId, request, securityUser));
    }

    /** 从指定 Checkpoint 创建独立任务分支，不修改源任务。 */
    @PostMapping("/{conversationId}/tasks/{taskId}/checkpoints/{checkpointId}/fork")
    public ApiResponse<TaskResponse> forkFromCheckpoint(@PathVariable Long conversationId,
                                                        @PathVariable Long taskId,
                                                        @PathVariable Long checkpointId,
                                                        Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.forkFromCheckpoint(
                conversationId, taskId, checkpointId, securityUser));
    }

    @GetMapping("/{conversationId}/tasks/{taskId}/plan-control/preview")
    public ApiResponse<Map<String, Object>> previewTaskPlanControl(@PathVariable Long conversationId,
                                                                   @PathVariable Long taskId,
                                                                   String action,
                                                                   Integer targetStepNo,
                                                                   Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.previewTaskPlanControl(
                conversationId, taskId, action, targetStepNo, securityUser));
    }

    @PostMapping("/{conversationId}/tasks/{taskId}/plan-control")
    public ApiResponse<Map<String, Object>> controlTaskPlan(@PathVariable Long conversationId,
                                                            @PathVariable Long taskId,
                                                            @Valid @RequestBody TaskPlanControlRequest request,
                                                            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.controlTaskPlan(conversationId, taskId, request, securityUser));
    }

    /** 通过事件或 Webhook 唤醒持久化等待任务。 */
    @PostMapping("/waits/{eventKey}/signal")
    public ApiResponse<Void> signalTaskWait(@PathVariable String eventKey,
                                            @RequestBody(required = false) Map<String, Object> payload,
                                            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        durableWaitService.signal(eventKey, payload == null ? Map.of() : payload, securityUser);
        return ApiResponse.success(null);
    }

    /**
     * 查看两个计划版本差异。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param fromVersion    基准版本
     * @param toVersion      目标版本
     * @param authentication 当前认证
     * @return 计划差异
     */
    @GetMapping("/{conversationId}/tasks/{taskId}/plans/diff")
    public ApiResponse<TaskPlanDiffResponse> getTaskPlanDiff(@PathVariable Long conversationId,
                                                             @PathVariable Long taskId,
                                                             Integer fromVersion,
                                                             Integer toVersion,
                                                             Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(conversationService.getTaskPlanDiff(conversationId, taskId, fromVersion, toVersion, securityUser));
    }

    /**
     * 人工修改计划后继续执行。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param request        手工计划
     * @param authorization  原始 Authorization 头
     * @param authentication 当前认证
     * @return 最新任务结果
     */
    @PostMapping("/{conversationId}/tasks/{taskId}/plans/manual-continue")
    public ApiResponse<TaskResponse> continueTaskWithManualPlan(@PathVariable Long conversationId,
                                                                @PathVariable Long taskId,
                                                                @Valid @RequestBody ManualTaskPlanContinueRequest request,
                                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskResponse response = conversationService.continueTaskWithManualPlan(
                conversationId,
                taskId,
                request,
                authorization,
                securityUser
        );
        agentRunService.syncTaskStatus(taskId, response.status(), response.errorMessage());
        return ApiResponse.success(response);
    }

    /**
     * 审批通过后继续执行。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param approvalId     审批 ID
     * @param authorization  原始 Authorization 头
     * @param authentication 当前认证
     * @return 审批后的任务结果
     */
    @PostMapping("/{conversationId}/tasks/{taskId}/approvals/{approvalId}/approve")
    public ApiResponse<TaskResponse> approveTask(@PathVariable Long conversationId,
                                                 @PathVariable Long taskId,
                                                 @PathVariable Long approvalId,
                                                 @RequestBody(required = false) ApprovalActionRequest request,
                                                 @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                 Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskResponse response;
        try (var ignored = approvalCoordinator.openRunScope(taskId)) {
            response = conversationService.approveTask(
                    conversationId,
                    taskId,
                    approvalId,
                    request == null ? null : request.comment(),
                    authorization,
                    securityUser
            );
        }
        agentRunService.syncTaskStatus(taskId, response.status(), response.errorMessage());
        return ApiResponse.success(response);
    }

    /**
     * 审批拒绝并结束任务。
     *
     * @param conversationId 会话 ID
     * @param taskId         任务 ID
     * @param approvalId     审批 ID
     * @param authentication 当前认证
     * @return 审批后的任务结果
     */
    @PostMapping("/{conversationId}/tasks/{taskId}/approvals/{approvalId}/reject")
    public ApiResponse<TaskResponse> rejectTask(@PathVariable Long conversationId,
                                                @PathVariable Long taskId,
                                                @PathVariable Long approvalId,
                                                @RequestBody(required = false) ApprovalActionRequest request,
                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskResponse response = conversationService.rejectTask(
                conversationId,
                taskId,
                approvalId,
                request == null ? null : request.comment(),
                request != null && Boolean.TRUE.equals(request.replanAfterReject()),
                authorization,
                securityUser
        );
        agentRunService.syncTaskStatus(taskId, response.status(), response.errorMessage());
        return ApiResponse.success(response);
    }

    @PostMapping("/{conversationId}/tasks/{taskId}/approvals/{approvalId}/revoke")
    public ApiResponse<TaskResponse> revokeApproval(@PathVariable Long conversationId,
                                                    @PathVariable Long taskId,
                                                    @PathVariable Long approvalId,
                                                    @RequestBody(required = false) ApprovalActionRequest request,
                                                    Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        TaskResponse response = conversationService.revokeApproval(
                conversationId,
                taskId,
                approvalId,
                request == null ? null : request.comment(),
                securityUser
        );
        agentRunService.syncTaskStatus(taskId, response.status(), response.errorMessage());
        return ApiResponse.success(response);
    }
}

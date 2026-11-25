package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.TaskOperationsPageQueryRequest;
import com.zc.agent.core.modules.conversation.model.TaskOperationsRecord;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.vo.TaskOperationsPageItemResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务运行中心查询服务，只负责跨会话的任务检索与摘要，不承载执行逻辑。
 */
@Service
public class TaskOperationsService {

    private final ConversationRepository conversationRepository;

    public TaskOperationsService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /** 分页查询当前用户拥有会话中的任务。 */
    public PageResponse<TaskOperationsPageItemResponse> page(TaskOperationsPageQueryRequest request,
                                                             SecurityUser securityUser) {
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        String status = normalize(request.status());
        String keyword = normalize(request.keyword());
        long total = conversationRepository.countTaskOperations(
                securityUser.userId(), status, request.agentId(), request.conversationId(), request.taskId(), keyword
        );
        List<TaskOperationsPageItemResponse> records = conversationRepository.pageTaskOperations(
                        securityUser.userId(),
                        status,
                        request.agentId(),
                        request.conversationId(),
                        request.taskId(),
                        keyword,
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    private TaskOperationsPageItemResponse toResponse(TaskOperationsRecord record) {
        LocalDateTime durationEnd = record.finishedAt() == null ? LocalDateTime.now() : record.finishedAt();
        Long durationMs = record.startedAt() == null
                ? null
                : Math.max(0L, Duration.between(record.startedAt(), durationEnd).toMillis());
        return new TaskOperationsPageItemResponse(
                record.taskId(), record.taskCode(), record.runCode(), record.conversationId(),
                record.conversationCode(), record.conversationTitle(), record.agentId(), record.agentName(),
                record.parentTaskId(), record.rootTaskId(), record.taskKind(), record.executionType(),
                record.traceId(), record.currentPlanVersion(), record.status(), record.errorMessage(),
                record.stepCount(), record.successStepCount(), record.failedStepCount(), record.approvalCount(),
                record.pendingApprovalCount(), record.delegationCount(), durationMs, record.startedAt(),
                record.finishedAt(), record.createdAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行状态流转服务。
 * 负责统一维护 conversation / task 的主流程状态回写。
 */
@Service
public class ExecutionStatusFlowService {

    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_WAITING_APPROVAL = "waiting_approval";
    private static final String STATUS_WAITING_EVENT = "waiting_event";
    private static final String STATUS_PAUSED = "paused";
    private static final String STATUS_CANCELLED = "cancelled";

    private final ConversationRepository conversationRepository;

    public ExecutionStatusFlowService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /**
     * 标记收到用户消息后进入处理中。
     *
     * @param conversationId 会话 ID
     * @param userPreview    用户消息预览
     * @param title          会话标题
     * @param operatorId     操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long conversationId,
                               String userPreview,
                               String title,
                               Long operatorId) {
        conversationRepository.updateConversationRuntime(
                conversationId,
                STATUS_PROCESSING,
                userPreview,
                title,
                operatorId
        );
    }

    /**
     * 标记任务和会话执行成功。
     *
     * @param taskId            任务 ID
     * @param conversationId    会话 ID
     * @param assistantPreview  助手消息预览
     * @param title             会话标题
     * @param operatorId        操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long taskId,
                            Long conversationId,
                            String assistantPreview,
                            String title,
                            Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_SUCCESS, null, operatorId);
        conversationRepository.updateConversationRuntime(
                conversationId,
                STATUS_SUCCESS,
                assistantPreview,
                title,
                operatorId
        );
    }

    /**
     * 标记任务和会话执行失败。
     *
     * @param taskId          任务 ID
     * @param conversationId  会话 ID
     * @param errorMessage    错误信息
     * @param previewText     预览文本
     * @param title           会话标题
     * @param operatorId      操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long taskId,
                           Long conversationId,
                           String errorMessage,
                           String previewText,
                           String title,
                           Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_FAILED, errorMessage, operatorId);
        conversationRepository.updateConversationRuntime(
                conversationId,
                STATUS_FAILED,
                previewText,
                title,
                operatorId
        );
    }

    /**
     * 标记任务等待审批。
     *
     * @param taskId            任务 ID
     * @param conversationId    会话 ID
     * @param assistantPreview  助手消息预览
     * @param title             会话标题
     * @param operatorId        操作人
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWaitingApproval(Long taskId,
                                    Long conversationId,
                                    String assistantPreview,
                                    String title,
                                    Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_WAITING_APPROVAL, null, operatorId);
        conversationRepository.updateConversationRuntime(
                conversationId,
                STATUS_WAITING_APPROVAL,
                assistantPreview,
                title,
                operatorId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markWaitingEvent(Long taskId, Long conversationId, String preview, String title, Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_WAITING_EVENT, null, operatorId);
        conversationRepository.updateConversationRuntime(conversationId, STATUS_WAITING_EVENT, preview, title, operatorId);
    }

    /** 将任务和会话正式标记为人工暂停，而不是伪装成执行失败。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaused(Long taskId, Long conversationId, String preview, String title, Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_PAUSED, null, operatorId);
        conversationRepository.updateConversationRuntime(conversationId, STATUS_PAUSED, preview, title, operatorId);
    }

    /** 将被用户取消的任务和会话写入独立取消终态。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(Long taskId, Long conversationId, String preview, String title, Long operatorId) {
        conversationRepository.updateTaskStatus(taskId, STATUS_CANCELLED, "运行已取消", operatorId);
        conversationRepository.updateConversationRuntime(
                conversationId, STATUS_CANCELLED, preview, title, operatorId);
    }
}

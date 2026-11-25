package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.protocol.service.ApprovalRuntimeEventService;
import org.springframework.stereotype.Service;

/** 将现有 Approval 状态 / Runtime Event 服务适配为 ApprovalCoordinator。 */
@Service
public class LegacyApprovalCoordinatorAdapter implements ApprovalCoordinator {

    public static final String COORDINATOR_CODE = "legacy_approval_coordinator_v1";

    private final ApprovalRuntimeEventService approvalRuntimeEventService;

    public LegacyApprovalCoordinatorAdapter(ApprovalRuntimeEventService approvalRuntimeEventService) {
        this.approvalRuntimeEventService = approvalRuntimeEventService;
    }

    @Override
    public String coordinatorCode() {
        return COORDINATOR_CODE;
    }

    @Override
    public Long request(ApprovalRequest command) {
        return approvalRuntimeEventService.request(new ApprovalRuntimeEventService.RequestApprovalCommand(
                command.conversationId(),
                command.taskId(),
                command.taskStepId(),
                command.nodeId(),
                command.nodeName(),
                command.approvalSourceType(),
                command.metadataJson(),
                command.traceId(),
                command.operatorId()
        ));
    }

    @Override
    public void transition(ApprovalTransition command) {
        approvalRuntimeEventService.transition(new ApprovalRuntimeEventService.TransitionApprovalCommand(
                command.approval(),
                command.status(),
                command.approvalComment(),
                command.approvalSourceType(),
                command.traceId(),
                command.operatorId()
        ));
    }

    @Override
    public Scope openRunScope(Long taskId) {
        var legacyScope = approvalRuntimeEventService.openRunScope(taskId);
        return legacyScope::close;
    }
}

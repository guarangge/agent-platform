package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.ApprovalWorkbenchQueryRequest;
import com.zc.agent.core.modules.conversation.service.ApprovalWorkbenchService;
import com.zc.agent.core.modules.conversation.vo.ApprovalWorkbenchResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批工作台接口。
 */
@RestController
@RequestMapping("/api/core/approvals")
public class ApprovalWorkbenchController {

    private final ApprovalWorkbenchService approvalWorkbenchService;

    public ApprovalWorkbenchController(ApprovalWorkbenchService approvalWorkbenchService) {
        this.approvalWorkbenchService = approvalWorkbenchService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ApprovalWorkbenchResponse>> page(@Valid ApprovalWorkbenchQueryRequest request,
                                                                     Authentication authentication) {
        return ApiResponse.success(approvalWorkbenchService.page(request, (SecurityUser) authentication.getPrincipal()));
    }
}

package com.zc.agent.core.modules.runtime.capability.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.runtime.capability.dto.CapabilityPolicyAuditQueryRequest;
import com.zc.agent.core.modules.runtime.capability.service.CapabilityPolicyAuditService;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditDetailResponse;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditItemResponse;
import com.zc.agent.core.modules.runtime.capability.vo.CapabilityPolicyAuditMetricsResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Capability Policy 全量决策运营查询接口。 */
@RestController
@RequestMapping("/api/core/capability-policy/audits")
public class CapabilityPolicyAuditController {

    private final CapabilityPolicyAuditService auditService;

    public CapabilityPolicyAuditController(CapabilityPolicyAuditService auditService) {
        this.auditService = auditService;
    }

    /** 分页查询当前用户可见的 Capability Policy 决策。 */
    @GetMapping
    public ApiResponse<PageResponse<CapabilityPolicyAuditItemResponse>> page(
            @Valid CapabilityPolicyAuditQueryRequest request,
            Authentication authentication) {
        return ApiResponse.success(auditService.page(request, (SecurityUser) authentication.getPrincipal()));
    }

    /** 查询当前筛选范围内的决策运营指标。 */
    @GetMapping("/metrics")
    public ApiResponse<CapabilityPolicyAuditMetricsResponse> metrics(
            @Valid CapabilityPolicyAuditQueryRequest request,
            Authentication authentication) {
        return ApiResponse.success(auditService.metrics(request, (SecurityUser) authentication.getPrincipal()));
    }

    /** 查询单次决策和同一 Tool Call 的完整安全事件链。 */
    @GetMapping("/{eventId}")
    public ApiResponse<CapabilityPolicyAuditDetailResponse> detail(
            @PathVariable Long eventId,
            Authentication authentication) {
        return ApiResponse.success(auditService.detail(eventId, (SecurityUser) authentication.getPrincipal()));
    }
}

package com.zc.agent.registry.modules.policy.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.policy.dto.CreateGuardrailPolicyRequest;
import com.zc.agent.registry.modules.policy.dto.GuardrailPolicyDebugRequest;
import com.zc.agent.registry.modules.policy.dto.GuardrailPolicyPageQueryRequest;
import com.zc.agent.registry.modules.policy.dto.UpdateGuardrailPolicyRequest;
import com.zc.agent.registry.modules.policy.service.GuardrailPolicyService;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyDebugResponse;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyDetailResponse;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyPageItemResponse;
import com.zc.agent.registry.modules.policy.vo.RuntimeGuardrailPolicyResponse;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Guardrail 策略接口。
 */
@RestController
@RequestMapping("/api/registry/guardrail-policies")
public class GuardrailPolicyController {

    private final GuardrailPolicyService guardrailPolicyService;

    public GuardrailPolicyController(GuardrailPolicyService guardrailPolicyService) {
        this.guardrailPolicyService = guardrailPolicyService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GuardrailPolicyPageItemResponse>> pagePolicies(@Valid GuardrailPolicyPageQueryRequest request) {
        return ApiResponse.success(guardrailPolicyService.pagePolicies(request));
    }

    @GetMapping("/{policyId}")
    public ApiResponse<GuardrailPolicyDetailResponse> getPolicyDetail(@PathVariable Long policyId) {
        return ApiResponse.success(guardrailPolicyService.getPolicyDetail(policyId));
    }

    @PostMapping
    public ApiResponse<GuardrailPolicyDetailResponse> createPolicy(@Valid @RequestBody CreateGuardrailPolicyRequest request,
                                                                   Authentication authentication) {
        return ApiResponse.success(guardrailPolicyService.createPolicy(request, securityUser(authentication)));
    }

    @PutMapping("/{policyId}")
    public ApiResponse<GuardrailPolicyDetailResponse> updatePolicy(@PathVariable Long policyId,
                                                                   @Valid @RequestBody UpdateGuardrailPolicyRequest request,
                                                                   Authentication authentication) {
        return ApiResponse.success(guardrailPolicyService.updatePolicy(policyId, request, securityUser(authentication)));
    }

    @PutMapping("/{policyId}/enable")
    public ApiResponse<Void> enablePolicy(@PathVariable Long policyId, Authentication authentication) {
        guardrailPolicyService.updatePolicyStatus(policyId, "enabled", securityUser(authentication));
        return ApiResponse.success(null);
    }

    @PutMapping("/{policyId}/disable")
    public ApiResponse<Void> disablePolicy(@PathVariable Long policyId, Authentication authentication) {
        guardrailPolicyService.updatePolicyStatus(policyId, "disabled", securityUser(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/debug")
    public ApiResponse<GuardrailPolicyDebugResponse> debugPolicies(@Valid @RequestBody GuardrailPolicyDebugRequest request) {
        return ApiResponse.success(guardrailPolicyService.debugPolicies(request));
    }

    @GetMapping("/runtime/effective")
    public ApiResponse<List<RuntimeGuardrailPolicyResponse>> listRuntimePolicies() {
        return ApiResponse.success(guardrailPolicyService.listRuntimePolicies());
    }

    private SecurityUser securityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

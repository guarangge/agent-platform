package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.GuardrailPolicyHitPageQueryRequest;
import com.zc.agent.core.modules.conversation.service.GuardrailPolicyHitService;
import com.zc.agent.core.modules.conversation.vo.GuardrailPolicyHitResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guardrail 运行时接口。
 */
@RestController
@RequestMapping("/api/core/guardrails")
public class GuardrailController {

    private final GuardrailPolicyHitService guardrailPolicyHitService;

    public GuardrailController(GuardrailPolicyHitService guardrailPolicyHitService) {
        this.guardrailPolicyHitService = guardrailPolicyHitService;
    }

    @GetMapping("/hits")
    public ApiResponse<PageResponse<GuardrailPolicyHitResponse>> pageHits(@Valid GuardrailPolicyHitPageQueryRequest request,
                                                                          Authentication authentication) {
        return ApiResponse.success(guardrailPolicyHitService.pageHits(request, (SecurityUser) authentication.getPrincipal()));
    }
}

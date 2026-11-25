package com.zc.agent.core.modules.sandbox.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.sandbox.dto.ExecuteSandboxRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxExecutionQueryRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxProfileQueryRequest;
import com.zc.agent.core.modules.sandbox.dto.SandboxProfileRequest;
import com.zc.agent.core.modules.sandbox.model.SandboxExecutionRecord;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import com.zc.agent.core.modules.sandbox.service.SandboxService;
import com.zc.agent.core.modules.sandbox.vo.SandboxExecutionResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sandbox 配置、容器执行与审计接口。
 */
@Validated
@RestController
@RequestMapping("/api/core/sandboxes")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @GetMapping("/profiles")
    public ApiResponse<PageResponse<SandboxProfileRecord>> pageProfiles(@Valid SandboxProfileQueryRequest request) {
        return ApiResponse.success(sandboxService.pageProfiles(request));
    }

    @GetMapping("/profiles/{profileId}")
    public ApiResponse<SandboxProfileRecord> getProfile(@PathVariable Long profileId) {
        return ApiResponse.success(sandboxService.getProfile(profileId));
    }

    @PostMapping("/profiles")
    public ApiResponse<SandboxProfileRecord> createProfile(@Valid @RequestBody SandboxProfileRequest request,
                                                           Authentication authentication) {
        return ApiResponse.success(sandboxService.createProfile(request, user(authentication)));
    }

    @PutMapping("/profiles/{profileId}")
    public ApiResponse<SandboxProfileRecord> updateProfile(@PathVariable Long profileId,
                                                           @Valid @RequestBody SandboxProfileRequest request,
                                                           Authentication authentication) {
        return ApiResponse.success(sandboxService.updateProfile(profileId, request, user(authentication)));
    }

    @PostMapping("/executions")
    public ApiResponse<SandboxExecutionResponse> execute(@Valid @RequestBody ExecuteSandboxRequest request,
                                                         Authentication authentication) {
        return ApiResponse.success(sandboxService.execute(request, user(authentication)));
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<SandboxExecutionRecord> cancel(@PathVariable Long executionId) {
        return ApiResponse.success(sandboxService.cancel(executionId));
    }

    @GetMapping("/executions")
    public ApiResponse<PageResponse<SandboxExecutionRecord>> pageExecutions(@Valid SandboxExecutionQueryRequest request) {
        return ApiResponse.success(sandboxService.pageExecutions(request));
    }

    @GetMapping("/executions/{executionId}")
    public ApiResponse<SandboxExecutionRecord> getExecution(@PathVariable Long executionId) {
        return ApiResponse.success(sandboxService.getExecution(executionId));
    }

    private SecurityUser user(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

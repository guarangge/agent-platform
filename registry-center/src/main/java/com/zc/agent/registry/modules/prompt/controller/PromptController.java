package com.zc.agent.registry.modules.prompt.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.prompt.dto.CreatePromptRequest;
import com.zc.agent.registry.modules.prompt.dto.PromptPageQueryRequest;
import com.zc.agent.registry.modules.prompt.dto.UpdatePromptRequest;
import com.zc.agent.registry.modules.prompt.service.PromptService;
import com.zc.agent.registry.modules.prompt.vo.PromptDetailResponse;
import com.zc.agent.registry.modules.prompt.vo.PromptPageItemResponse;
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

/**
 * Prompt 管理接口。
 */
@RestController
@RequestMapping("/api/registry/prompts")
public class PromptController {

    private final PromptService promptService;

    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    /**
     * 分页查询 Prompt。
     */
    @GetMapping
    public ApiResponse<PageResponse<PromptPageItemResponse>> pagePrompts(@Valid PromptPageQueryRequest request) {
        return ApiResponse.success(promptService.pagePrompts(request));
    }

    /**
     * 查询 Prompt 详情。
     */
    @GetMapping("/{promptId}")
    public ApiResponse<PromptDetailResponse> getPromptDetail(@PathVariable Long promptId) {
        return ApiResponse.success(promptService.getPromptDetail(promptId));
    }

    /**
     * 新增 Prompt。
     */
    @PostMapping
    public ApiResponse<PromptDetailResponse> createPrompt(@Valid @RequestBody CreatePromptRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(promptService.createPrompt(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑 Prompt。
     */
    @PutMapping("/{promptId}")
    public ApiResponse<PromptDetailResponse> updatePrompt(@PathVariable Long promptId,
                                                          @Valid @RequestBody UpdatePromptRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(promptService.updatePrompt(promptId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用 Prompt。
     */
    @PutMapping("/{promptId}/enable")
    public ApiResponse<Void> enablePrompt(@PathVariable Long promptId, Authentication authentication) {
        promptService.enablePrompt(promptId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用 Prompt。
     */
    @PutMapping("/{promptId}/disable")
    public ApiResponse<Void> disablePrompt(@PathVariable Long promptId, Authentication authentication) {
        promptService.disablePrompt(promptId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

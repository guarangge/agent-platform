package com.zc.agent.registry.modules.tool.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.tool.dto.CreateToolRequest;
import com.zc.agent.registry.modules.tool.dto.ToolPageQueryRequest;
import com.zc.agent.registry.modules.tool.dto.UpdateToolRequest;
import com.zc.agent.registry.modules.tool.service.ToolService;
import com.zc.agent.registry.modules.tool.vo.ToolDetailResponse;
import com.zc.agent.registry.modules.tool.vo.ToolPageItemResponse;
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
 * Tool 管理接口。
 */
@RestController
@RequestMapping("/api/registry/tools")
public class ToolController {

    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * 分页查询 Tool。
     *
     * @param request 查询条件
     * @return Tool 分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ToolPageItemResponse>> pageTools(@Valid ToolPageQueryRequest request) {
        return ApiResponse.success(toolService.pageTools(request));
    }

    /**
     * 查询 Tool 详情。
     *
     * @param toolId Tool ID
     * @return Tool 详情
     */
    @GetMapping("/{toolId}")
    public ApiResponse<ToolDetailResponse> getToolDetail(@PathVariable Long toolId) {
        return ApiResponse.success(toolService.getToolDetail(toolId));
    }

    /**
     * 查询 Tool 运行时详情。
     *
     * @param toolId Tool ID
     * @return Tool 运行时详情
     */
    @GetMapping("/{toolId}/runtime")
    public ApiResponse<ToolDetailResponse> getToolRuntimeDetail(@PathVariable Long toolId) {
        return ApiResponse.success(toolService.getToolRuntimeDetail(toolId));
    }

    /**
     * 新增 Tool。
     *
     * @param request        新增请求
     * @param authentication 当前认证信息
     * @return Tool 详情
     */
    @PostMapping
    public ApiResponse<ToolDetailResponse> createTool(@Valid @RequestBody CreateToolRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(toolService.createTool(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑 Tool。
     *
     * @param toolId         Tool ID
     * @param request        编辑请求
     * @param authentication 当前认证信息
     * @return Tool 详情
     */
    @PutMapping("/{toolId}")
    public ApiResponse<ToolDetailResponse> updateTool(@PathVariable Long toolId,
                                                      @Valid @RequestBody UpdateToolRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(toolService.updateTool(toolId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用 Tool。
     *
     * @param toolId         Tool ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{toolId}/enable")
    public ApiResponse<Void> enableTool(@PathVariable Long toolId, Authentication authentication) {
        toolService.updateToolStatus(toolId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用 Tool。
     *
     * @param toolId         Tool ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{toolId}/disable")
    public ApiResponse<Void> disableTool(@PathVariable Long toolId, Authentication authentication) {
        toolService.updateToolStatus(toolId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

package com.zc.agent.registry.modules.agent.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.agent.dto.AgentPageQueryRequest;
import com.zc.agent.registry.modules.agent.dto.CreateAgentRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentToolsRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentWorkflowsRequest;
import com.zc.agent.registry.modules.agent.dto.UpdateAgentRequest;
import com.zc.agent.registry.modules.agent.service.AgentService;
import com.zc.agent.registry.modules.agent.vo.AgentDetailResponse;
import com.zc.agent.registry.modules.agent.vo.AgentPageItemResponse;
import com.zc.agent.registry.modules.agent.vo.AgentToolsResponse;
import com.zc.agent.registry.modules.agent.vo.AgentWorkflowsResponse;
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
 * Agent 管理接口。
 */
@RestController
@RequestMapping("/api/registry/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 分页查询 Agent。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<AgentPageItemResponse>> pageAgents(@Valid AgentPageQueryRequest request) {
        return ApiResponse.success(agentService.pageAgents(request));
    }

    /**
     * 查询 Agent 详情。
     *
     * @param agentId Agent ID
     * @return Agent 详情
     */
    @GetMapping("/{agentId}")
    public ApiResponse<AgentDetailResponse> getAgentDetail(@PathVariable Long agentId) {
        return ApiResponse.success(agentService.getAgentDetail(agentId));
    }

    /**
     * 查询 Agent 可用 Tool。
     *
     * @param agentId Agent ID
     * @return Tool 范围结果
     */
    @GetMapping("/{agentId}/tools")
    public ApiResponse<AgentToolsResponse> getAgentTools(@PathVariable Long agentId) {
        return ApiResponse.success(agentService.getAgentTools(agentId));
    }

    /**
     * 查询 Agent 运行时 Tool。
     *
     * @param agentId Agent ID
     * @return Tool 范围结果
     */
    @GetMapping("/{agentId}/tools/runtime")
    public ApiResponse<AgentToolsResponse> getAgentRuntimeTools(@PathVariable Long agentId) {
        return ApiResponse.success(agentService.getAgentRuntimeTools(agentId));
    }

    /**
     * 查询 Agent 可用 Workflow。
     *
     * @param agentId Agent ID
     * @return Workflow 范围结果
     */
    @GetMapping("/{agentId}/workflows")
    public ApiResponse<AgentWorkflowsResponse> getAgentWorkflows(@PathVariable Long agentId) {
        return ApiResponse.success(agentService.getAgentWorkflows(agentId));
    }

    /**
     * 新增 Agent。
     *
     * @param request        新增请求
     * @param authentication 当前认证信息
     * @return Agent 详情
     */
    @PostMapping
    public ApiResponse<AgentDetailResponse> createAgent(@Valid @RequestBody CreateAgentRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(agentService.createAgent(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑 Agent。
     *
     * @param agentId        Agent ID
     * @param request        编辑请求
     * @param authentication 当前认证信息
     * @return Agent 详情
     */
    @PutMapping("/{agentId}")
    public ApiResponse<AgentDetailResponse> updateAgent(@PathVariable Long agentId,
                                                        @Valid @RequestBody UpdateAgentRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(agentService.updateAgent(agentId, request, getSecurityUser(authentication)));
    }

    /**
     * 覆盖更新 Agent Tool 范围。
     *
     * @param agentId        Agent ID
     * @param request        更新请求
     * @param authentication 当前认证信息
     * @return 最新 Tool 范围结果
     */
    @PutMapping("/{agentId}/tools")
    public ApiResponse<AgentToolsResponse> updateAgentTools(@PathVariable Long agentId,
                                                            @Valid @RequestBody UpdateAgentToolsRequest request,
                                                            Authentication authentication) {
        return ApiResponse.success(agentService.updateAgentTools(agentId, request, getSecurityUser(authentication)));
    }

    /**
     * 覆盖更新 Agent Workflow 范围。
     *
     * @param agentId        Agent ID
     * @param request        更新请求
     * @param authentication 当前认证信息
     * @return 最新 Workflow 范围结果
     */
    @PutMapping("/{agentId}/workflows")
    public ApiResponse<AgentWorkflowsResponse> updateAgentWorkflows(@PathVariable Long agentId,
                                                                    @Valid @RequestBody UpdateAgentWorkflowsRequest request,
                                                                    Authentication authentication) {
        return ApiResponse.success(agentService.updateAgentWorkflows(agentId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用 Agent。
     *
     * @param agentId        Agent ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{agentId}/enable")
    public ApiResponse<Void> enableAgent(@PathVariable Long agentId, Authentication authentication) {
        agentService.updateAgentStatus(agentId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用 Agent。
     *
     * @param agentId        Agent ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{agentId}/disable")
    public ApiResponse<Void> disableAgent(@PathVariable Long agentId, Authentication authentication) {
        agentService.updateAgentStatus(agentId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

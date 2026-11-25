package com.zc.agent.registry.modules.runtimeplugin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.runtimeplugin.dto.UpdateAgentRuntimePluginsRequest;
import com.zc.agent.registry.modules.runtimeplugin.service.RuntimePluginService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Agent Runtime Plugin 绑定接口。 */
@RestController
@RequestMapping("/api/registry/agents/{agentId}/runtime-plugins")
public class AgentRuntimePluginController {

    private final RuntimePluginService runtimePluginService;

    public AgentRuntimePluginController(RuntimePluginService runtimePluginService) {
        this.runtimePluginService = runtimePluginService;
    }

    /** 查询 Agent 当前绑定及可绑定安装。 */
    @GetMapping
    public ApiResponse<JsonNode> agentBindings(@PathVariable Long agentId) {
        return ApiResponse.success(runtimePluginService.agentBindings(agentId));
    }

    /** 覆盖更新 Agent Runtime Plugin 绑定。 */
    @PutMapping
    public ApiResponse<JsonNode> updateAgentBindings(
            @PathVariable Long agentId,
            @Valid @RequestBody UpdateAgentRuntimePluginsRequest request,
            Authentication authentication) {
        return ApiResponse.success(runtimePluginService.updateAgentBindings(
                agentId, request, operatorId(authentication)));
    }

    private Long operatorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.userId();
        }
        return null;
    }
}

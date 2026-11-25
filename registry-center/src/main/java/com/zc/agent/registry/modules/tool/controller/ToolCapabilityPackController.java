package com.zc.agent.registry.modules.tool.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.tool.dto.AgentToolCapabilityPackRequest;
import com.zc.agent.registry.modules.tool.dto.ToolCapabilityPackRequest;
import com.zc.agent.registry.modules.tool.service.ToolCapabilityPackService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool Capability Pack 管理及 Agent 关联接口。
 */
@RestController
@RequestMapping("/api/registry")
public class ToolCapabilityPackController {

    private final ToolCapabilityPackService service;

    public ToolCapabilityPackController(ToolCapabilityPackService service) {
        this.service = service;
    }

    @GetMapping("/tool-capability-packs")
    public ApiResponse<JsonNode> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/tool-capability-packs/{packId}")
    public ApiResponse<JsonNode> detail(@PathVariable Long packId) {
        return ApiResponse.success(service.detail(packId));
    }

    @PostMapping("/tool-capability-packs")
    public ApiResponse<JsonNode> create(@Valid @RequestBody ToolCapabilityPackRequest request,
                                        Authentication authentication) {
        return ApiResponse.success(service.create(request, user(authentication)));
    }

    @PutMapping("/tool-capability-packs/{packId}")
    public ApiResponse<JsonNode> update(@PathVariable Long packId,
                                        @Valid @RequestBody ToolCapabilityPackRequest request,
                                        Authentication authentication) {
        return ApiResponse.success(service.update(packId, request, user(authentication)));
    }

    @DeleteMapping("/tool-capability-packs/{packId}")
    public ApiResponse<Void> delete(@PathVariable Long packId, Authentication authentication) {
        service.delete(packId, user(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/agents/{agentId}/tool-capability-packs")
    public ApiResponse<JsonNode> agentPacks(@PathVariable Long agentId) {
        return ApiResponse.success(service.agentPacks(agentId));
    }

    @PutMapping("/agents/{agentId}/tool-capability-packs")
    public ApiResponse<JsonNode> updateAgentPacks(@PathVariable Long agentId,
                                                  @Valid @RequestBody AgentToolCapabilityPackRequest request,
                                                  Authentication authentication) {
        return ApiResponse.success(service.updateAgentPacks(agentId, request, user(authentication)));
    }

    private SecurityUser user(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

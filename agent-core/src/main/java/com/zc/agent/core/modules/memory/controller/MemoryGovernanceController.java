package com.zc.agent.core.modules.memory.controller;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.memory.service.MemoryGovernanceService;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前用户长期记忆查询与撤销入口。 */
@RestController
@RequestMapping("/api/core/memories")
public class MemoryGovernanceController {
    private final MemoryGovernanceService memoryGovernanceService;

    public MemoryGovernanceController(MemoryGovernanceService memoryGovernanceService) {
        this.memoryGovernanceService = memoryGovernanceService;
    }

    @GetMapping
    public ApiResponse<List<MemoryGovernanceService.MemoryItem>> list(@RequestParam Long agentId,
                                                                      Authentication authentication) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(memoryGovernanceService.findActive(user.userId(), agentId));
    }

    @PostMapping("/{memoryId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable Long memoryId,
                                    @RequestBody(required = false) RevokeMemoryRequest request,
                                    Authentication authentication) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        if (!memoryGovernanceService.revoke(memoryId, user.userId(), request == null ? null : request.reason())) {
            throw new BusinessException("记忆项不存在或已撤销");
        }
        return ApiResponse.success(null);
    }

    public record RevokeMemoryRequest(String reason) {
    }
}

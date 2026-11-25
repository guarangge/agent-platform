package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.vo.AgentRunQueueMetricsResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 持久化运行队列运维接口。
 */
@RestController
@RequestMapping("/api/core/agent-runs/queue")
public class AgentRunQueueController {

    private final AgentRunService agentRunService;

    public AgentRunQueueController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    /** 查询队列容量、积压和租约指标。 */
    @GetMapping("/metrics")
    public ApiResponse<AgentRunQueueMetricsResponse> metrics(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(agentRunService.metrics(securityUser));
    }

    /** 立即回收已过期的 Worker 租约。 */
    @PostMapping("/recover-expired")
    public ApiResponse<Integer> recoverExpired(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(agentRunService.recoverExpiredLeases(securityUser));
    }
}

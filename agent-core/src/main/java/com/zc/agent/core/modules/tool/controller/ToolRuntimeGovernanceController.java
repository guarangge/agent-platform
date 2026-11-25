package com.zc.agent.core.modules.tool.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.tool.repository.ToolRuntimeGovernanceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tool 实例熔断状态与故障切换审计查询 API。 */
@RestController
@RequestMapping("/api/core/tools")
public class ToolRuntimeGovernanceController {

    private final ToolRuntimeGovernanceRepository repository;

    public ToolRuntimeGovernanceController(ToolRuntimeGovernanceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{toolId}/runtime-governance")
    public ApiResponse<Map<String, Object>> runtimeGovernance(@PathVariable Long toolId) {
        return ApiResponse.success(Map.of(
                "states", repository.findStates(toolId),
                "audits", repository.findAudits(toolId)
        ));
    }
}

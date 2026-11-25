package com.zc.agent.core.modules.tool.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.tool.dto.ToolDebugRequest;
import com.zc.agent.core.modules.tool.dto.ToolHealthCheckHistoryQueryRequest;
import com.zc.agent.core.modules.tool.service.ToolDebugService;
import com.zc.agent.core.modules.tool.service.ToolHealthCheckHistoryService;
import com.zc.agent.core.modules.tool.service.ToolProtocolCompatibilityService;
import com.zc.agent.core.modules.tool.vo.ToolDebugResponse;
import com.zc.agent.core.modules.tool.vo.ToolHealthCheckHistoryResponse;
import com.zc.agent.core.modules.tool.vo.ToolHealthCheckMetricsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool 调试接口。
 */
@RestController
@RequestMapping("/api/core/tools")
public class ToolDebugController {

    private final ToolDebugService toolDebugService;
    private final ToolHealthCheckHistoryService toolHealthCheckHistoryService;
    private final ToolProtocolCompatibilityService compatibilityService;

    public ToolDebugController(ToolDebugService toolDebugService,
                               ToolHealthCheckHistoryService toolHealthCheckHistoryService,
                               ToolProtocolCompatibilityService compatibilityService) {
        this.toolDebugService = toolDebugService;
        this.toolHealthCheckHistoryService = toolHealthCheckHistoryService;
        this.compatibilityService = compatibilityService;
    }

    @PostMapping("/{toolId}/compatibility-runs")
    public ApiResponse<java.util.Map<String,Object>> runCompatibility(@PathVariable Long toolId,
            @RequestBody(required=false) com.zc.agent.core.modules.tool.dto.ToolCompatibilityRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization){return ApiResponse.success(compatibilityService.run(toolId,request,authorization));}

    @GetMapping("/{toolId}/compatibility-runs")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> compatibilityHistory(@PathVariable Long toolId){return ApiResponse.success(compatibilityService.history(toolId));}

    @GetMapping("/compatibility-matrix")
    public ApiResponse<java.util.List<java.util.Map<String,Object>>> compatibilityMatrix(){return ApiResponse.success(compatibilityService.matrix());}

    @PostMapping("/{toolId}/debug")
    public ApiResponse<ToolDebugResponse> debugTool(@PathVariable Long toolId,
                                                    @Valid @RequestBody(required = false) ToolDebugRequest request,
                                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ApiResponse.success(toolDebugService.debugTool(toolId, request, authorization));
    }

    @GetMapping("/{toolId}/health")
    public ApiResponse<ToolDebugResponse> healthCheck(@PathVariable Long toolId,
                                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ApiResponse.success(toolDebugService.healthCheck(toolId, authorization));
    }

    @GetMapping("/{toolId}/health-history")
    public ApiResponse<PageResponse<ToolHealthCheckHistoryResponse>> healthHistory(@PathVariable Long toolId,
                                                                                   @Valid ToolHealthCheckHistoryQueryRequest request) {
        return ApiResponse.success(toolHealthCheckHistoryService.page(toolId, request));
    }

    @GetMapping("/{toolId}/health-history/metrics")
    public ApiResponse<ToolHealthCheckMetricsResponse> healthHistoryMetrics(@PathVariable Long toolId,
                                                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return ApiResponse.success(toolHealthCheckHistoryService.metrics(toolId, authorization));
    }
}

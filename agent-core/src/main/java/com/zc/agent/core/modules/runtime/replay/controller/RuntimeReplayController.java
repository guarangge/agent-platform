package com.zc.agent.core.modules.runtime.replay.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.runtime.replay.dto.ReplayPageQueryRequest;
import com.zc.agent.core.modules.runtime.replay.dto.SimulationReplayRequest;
import com.zc.agent.core.modules.runtime.replay.service.ReplayQueryService;
import com.zc.agent.core.modules.runtime.replay.service.SimulationReplayService;
import com.zc.agent.core.modules.runtime.replay.vo.ReplayDetailResponse;
import com.zc.agent.core.modules.runtime.replay.vo.ReplayPageItemResponse;
import com.zc.agent.core.modules.runtime.replay.vo.SimulationReplayResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime Replay 管理接口。 */
@RestController
@RequestMapping("/api/core/runtime/replays")
public class RuntimeReplayController {

    private final SimulationReplayService simulationReplayService;
    private final ReplayQueryService replayQueryService;

    public RuntimeReplayController(SimulationReplayService simulationReplayService,
                                   ReplayQueryService replayQueryService) {
        this.simulationReplayService = simulationReplayService;
        this.replayQueryService = replayQueryService;
    }

    /** 分页查询 Replay 独立审计实例。 */
    @GetMapping
    public ApiResponse<PageResponse<ReplayPageItemResponse>> page(
            @Valid ReplayPageQueryRequest request,
            Authentication authentication) {
        return ApiResponse.success(replayQueryService.page(
                request, (SecurityUser) authentication.getPrincipal()));
    }

    /** 查询 Replay Manifest、Input 和独立执行结果。 */
    @GetMapping("/{replayCode}")
    public ApiResponse<ReplayDetailResponse> detail(@PathVariable String replayCode,
                                                     Authentication authentication) {
        return ApiResponse.success(replayQueryService.detail(
                replayCode, (SecurityUser) authentication.getPrincipal()));
    }

    /** 使用冻结 Runtime Event 和步骤结果执行无副作用 Simulation Replay。 */
    @PostMapping("/runs/{runCode}/simulation")
    public ApiResponse<SimulationReplayResponse> simulate(@PathVariable String runCode,
                                                           @Valid @RequestBody SimulationReplayRequest request,
                                                           Authentication authentication) {
        return ApiResponse.success(simulationReplayService.replay(
                runCode, request, (SecurityUser) authentication.getPrincipal()));
    }
}

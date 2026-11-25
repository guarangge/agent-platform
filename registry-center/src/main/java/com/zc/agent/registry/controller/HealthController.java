package com.zc.agent.registry.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 基础健康检查接口，便于本地初始化阶段快速验证服务可用性。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 返回当前服务基础状态。
     *
     * @return 健康状态信息
     */
    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "service", "registry-center",
                "status", "UP"
        );
    }
}

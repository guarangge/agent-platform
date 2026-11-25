package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** 将插件加载与 Registry 对账摘要暴露到 Actuator health details。 */
@Component("runtimePlugins")
public class RuntimePluginHealthIndicator implements HealthIndicator {

    private final RuntimePluginObservationService observationService;

    public RuntimePluginHealthIndicator(RuntimePluginObservationService observationService) {
        this.observationService = observationService;
    }

    /** 插件治理异常使用 UP + DEGRADED 明细，避免 Registry 短暂不可用拉低 Core 存活状态。 */
    @Override
    public Health health() {
        RuntimePluginHealthSnapshot snapshot = observationService.snapshot();
        return Health.up()
                .withDetail("status", snapshot.status())
                .withDetail("pluginCount", snapshot.pluginCount())
                .withDetail("optionalMissingCount", snapshot.optionalMissingCount())
                .withDetail("registrySync", snapshot.registrySync())
                .build();
    }
}

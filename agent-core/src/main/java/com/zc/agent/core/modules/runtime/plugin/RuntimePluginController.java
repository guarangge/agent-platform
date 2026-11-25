package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.PluginObservation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime Plugin 本地健康观测和手动对账 API。 */
@RestController
@RequestMapping("/api/core/runtime/plugins")
public class RuntimePluginController {

    private final RuntimePluginObservationService observationService;
    private final RuntimePluginRegistryReconciler reconciler;

    public RuntimePluginController(RuntimePluginObservationService observationService,
                                   RuntimePluginRegistryReconciler reconciler) {
        this.observationService = observationService;
        this.reconciler = reconciler;
    }

    /** 查询当前进程的插件观测快照。 */
    @GetMapping
    public ApiResponse<RuntimePluginHealthSnapshot> snapshot() {
        return ApiResponse.success(observationService.snapshot());
    }

    /** 查询单个实际加载插件。 */
    @GetMapping("/{pluginCode}")
    public ApiResponse<PluginObservation> detail(@PathVariable String pluginCode) {
        PluginObservation plugin = observationService.plugins().stream()
                .filter(item -> item.pluginCode().equals(pluginCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("当前进程未加载 Runtime Plugin: " + pluginCode));
        return ApiResponse.success(plugin);
    }

    /** 手动重试 Registry 对账并返回最新快照。 */
    @PostMapping("/reconcile")
    public ApiResponse<RuntimePluginHealthSnapshot> reconcile() {
        reconciler.reconcile();
        return ApiResponse.success(observationService.snapshot());
    }
}

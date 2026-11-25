package com.zc.agent.registry.modules.runtimeplugin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.runtimeplugin.dto.CreateRuntimePluginRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.CreateRuntimePluginVersionRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallationStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginInstallRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginLoadResultRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.dto.RuntimePluginVersionStatusRequest;
import com.zc.agent.registry.modules.runtimeplugin.service.RuntimePluginService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Runtime Plugin 注册、版本和安装治理接口。 */
@RestController
@RequestMapping("/api/registry/runtime-plugins")
public class RuntimePluginController {

    private final RuntimePluginService runtimePluginService;

    public RuntimePluginController(RuntimePluginService runtimePluginService) {
        this.runtimePluginService = runtimePluginService;
    }

    /** 查询插件定义列表。 */
    @GetMapping
    public ApiResponse<JsonNode> listPlugins() {
        return ApiResponse.success(runtimePluginService.listPlugins());
    }

    /** 创建插件定义。 */
    @PostMapping
    public ApiResponse<JsonNode> createPlugin(@Valid @RequestBody CreateRuntimePluginRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(runtimePluginService.createPlugin(request, operatorId(authentication)));
    }

    /** 查询插件定义、版本、安装和审计详情。 */
    @GetMapping("/{pluginId}")
    public ApiResponse<JsonNode> pluginDetail(@PathVariable Long pluginId) {
        return ApiResponse.success(runtimePluginService.pluginDetail(pluginId));
    }

    /** 启用或停用插件定义。 */
    @PutMapping("/{pluginId}/status")
    public ApiResponse<Void> updatePluginStatus(@PathVariable Long pluginId,
                                                @Valid @RequestBody RuntimePluginStatusRequest request,
                                                Authentication authentication) {
        runtimePluginService.updatePluginStatus(pluginId, request, operatorId(authentication));
        return ApiResponse.success(null);
    }

    /** 创建不可变插件版本。 */
    @PostMapping("/{pluginId}/versions")
    public ApiResponse<JsonNode> createVersion(@PathVariable Long pluginId,
                                               @Valid @RequestBody CreateRuntimePluginVersionRequest request,
                                               Authentication authentication) {
        return ApiResponse.success(runtimePluginService.createVersion(
                pluginId, request, operatorId(authentication)));
    }

    /** 更新插件版本发布状态。 */
    @PutMapping("/{pluginId}/versions/{versionId}/status")
    public ApiResponse<Void> updateVersionStatus(@PathVariable Long pluginId,
                                                 @PathVariable Long versionId,
                                                 @Valid @RequestBody RuntimePluginVersionStatusRequest request,
                                                 Authentication authentication) {
        runtimePluginService.updateVersionStatus(pluginId, versionId, request, operatorId(authentication));
        return ApiResponse.success(null);
    }

    /** 登记或替换指定环境的插件安装。 */
    @PostMapping("/{pluginId}/installations")
    public ApiResponse<JsonNode> install(@PathVariable Long pluginId,
                                         @Valid @RequestBody RuntimePluginInstallRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(runtimePluginService.install(
                pluginId, request, operatorId(authentication)));
    }

    /** 启用、停用或卸载插件环境安装。 */
    @PutMapping("/{pluginId}/installations/{installationId}/status")
    public ApiResponse<Void> updateInstallationStatus(
            @PathVariable Long pluginId,
            @PathVariable Long installationId,
            @Valid @RequestBody RuntimePluginInstallationStatusRequest request,
            Authentication authentication) {
        runtimePluginService.updateInstallationStatus(
                pluginId, installationId, request, operatorId(authentication));
        return ApiResponse.success(null);
    }

    /** 查询指定环境期望加载的插件安装，供 agent-core 启动对账。 */
    @GetMapping("/runtime/installations")
    public ApiResponse<JsonNode> runtimeInstallations(@RequestParam String environmentCode) {
        return ApiResponse.success(runtimePluginService.runtimeInstallations(environmentCode));
    }

    /** 接收 agent-core 的实际插件加载结果。 */
    @PutMapping("/runtime/installations/{installationId}/load-result")
    public ApiResponse<Void> reportLoadResult(
            @PathVariable Long installationId,
            @Valid @RequestBody RuntimePluginLoadResultRequest request) {
        runtimePluginService.reportLoadResult(installationId, request);
        return ApiResponse.success(null);
    }

    private Long operatorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.userId();
        }
        return null;
    }
}

package com.zc.agent.registry.modules.tool.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.tool.dto.ToolCatalogRequest;
import com.zc.agent.registry.modules.tool.dto.ToolDeploymentRequest;
import com.zc.agent.registry.modules.tool.dto.ToolDiscoveryRequest;
import com.zc.agent.registry.modules.tool.dto.ToolMigrationRequest;
import com.zc.agent.registry.modules.tool.dto.ToolInstallRequest;
import com.zc.agent.registry.modules.tool.dto.ToolRouteUpdateRequest;
import com.zc.agent.registry.modules.tool.dto.ToolVersionRequest;
import com.zc.agent.registry.modules.tool.service.ToolLifecycleService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tool/MCP 生命周期治理管理 API。 */
@RestController
@RequestMapping("/api/registry/tools")
public class ToolLifecycleController {

    private final ToolLifecycleService lifecycleService;

    public ToolLifecycleController(ToolLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/lifecycle/catalogs")
    public ApiResponse<JsonNode> catalogs() {
        return ApiResponse.success(lifecycleService.listCatalogs());
    }

    @GetMapping("/lifecycle/audits")
    public ApiResponse<JsonNode> globalAudits() {
        return ApiResponse.success(lifecycleService.globalAudits());
    }

    @PostMapping("/lifecycle/catalogs")
    public ApiResponse<JsonNode> createCatalog(@Valid @RequestBody ToolCatalogRequest request,
                                               Authentication authentication) {
        return ApiResponse.success(lifecycleService.createCatalog(request, operatorId(authentication)));
    }

    @PostMapping("/lifecycle/discover")
    public ApiResponse<JsonNode> discover(@Valid @RequestBody ToolDiscoveryRequest request,
                                          Authentication authentication) {
        return ApiResponse.success(lifecycleService.discover(request, operatorId(authentication)));
    }

    @GetMapping("/lifecycle/catalogs/{catalogId}/items")
    public ApiResponse<JsonNode> catalogItems(@PathVariable Long catalogId) {
        return ApiResponse.success(lifecycleService.catalogItems(catalogId));
    }

    @PostMapping("/lifecycle/install")
    public ApiResponse<JsonNode> install(@Valid @RequestBody ToolInstallRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(lifecycleService.install(request, operatorId(authentication)));
    }

    @GetMapping("/{toolId}/lifecycle")
    public ApiResponse<JsonNode> lifecycle(@PathVariable Long toolId) {
        return ApiResponse.success(lifecycleService.lifecycle(toolId));
    }

    @PostMapping("/{toolId}/lifecycle/versions")
    public ApiResponse<JsonNode> createVersion(@PathVariable Long toolId,
                                               @Valid @RequestBody ToolVersionRequest request,
                                               Authentication authentication) {
        return ApiResponse.success(lifecycleService.createVersion(toolId, request, operatorId(authentication)));
    }

    @PostMapping("/{toolId}/lifecycle/deployments")
    public ApiResponse<JsonNode> createDeployment(@PathVariable Long toolId,
                                                  @Valid @RequestBody ToolDeploymentRequest request,
                                                  Authentication authentication) {
        return ApiResponse.success(lifecycleService.createDeployment(toolId, request, operatorId(authentication)));
    }

    @PutMapping("/{toolId}/lifecycle/deployments/{deploymentId}/route")
    public ApiResponse<Void> updateRoute(@PathVariable Long toolId,
                                         @PathVariable Long deploymentId,
                                         @Valid @RequestBody ToolRouteUpdateRequest request,
                                         Authentication authentication) {
        lifecycleService.updateRoute(toolId, deploymentId, request, operatorId(authentication));
        return ApiResponse.success(null);
    }

    @PostMapping("/{toolId}/lifecycle/migrations")
    public ApiResponse<JsonNode> migrate(@PathVariable Long toolId,
                                         @Valid @RequestBody ToolMigrationRequest request,
                                         Authentication authentication) {
        return ApiResponse.success(lifecycleService.migrate(toolId, request, operatorId(authentication)));
    }

    @GetMapping("/{toolId}/lifecycle/impact")
    public ApiResponse<JsonNode> impact(@PathVariable Long toolId) {
        return ApiResponse.success(lifecycleService.impact(toolId));
    }

    @PostMapping("/{toolId}/lifecycle/retire")
    public ApiResponse<Void> retire(@PathVariable Long toolId, Authentication authentication) {
        lifecycleService.retire(toolId, operatorId(authentication));
        return ApiResponse.success(null);
    }

    @GetMapping("/{toolId}/runtime-routing")
    public ApiResponse<JsonNode> runtimeRouting(@PathVariable Long toolId) {
        return ApiResponse.success(lifecycleService.runtimeRouting(toolId));
    }

    private Long operatorId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.userId();
        }
        return null;
    }
}

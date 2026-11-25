package com.zc.agent.registry.modules.model.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.model.dto.CreateModelRequest;
import com.zc.agent.registry.modules.model.dto.ModelPageQueryRequest;
import com.zc.agent.registry.modules.model.dto.UpdateModelRequest;
import com.zc.agent.registry.modules.model.service.ModelService;
import com.zc.agent.registry.modules.model.vo.ModelDetailResponse;
import com.zc.agent.registry.modules.model.vo.ModelPageItemResponse;
import com.zc.agent.registry.modules.model.vo.RuntimeModelDetailResponse;
import com.zc.agent.registry.config.RegistryRuntimeSecurityProperties;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 模型管理接口。
 */
@RestController
@RequestMapping("/api/registry/models")
public class ModelController {

    private final ModelService modelService;
    private final RegistryRuntimeSecurityProperties runtimeSecurityProperties;

    public ModelController(ModelService modelService,
                           RegistryRuntimeSecurityProperties runtimeSecurityProperties) {
        this.modelService = modelService;
        this.runtimeSecurityProperties = runtimeSecurityProperties;
    }

    @GetMapping
    public ApiResponse<PageResponse<ModelPageItemResponse>> pageModels(@Valid ModelPageQueryRequest request) {
        return ApiResponse.success(modelService.pageModels(request));
    }

    @GetMapping("/{modelId}")
    public ApiResponse<ModelDetailResponse> getModelDetail(@PathVariable Long modelId) {
        return ApiResponse.success(modelService.getModelDetail(modelId));
    }

    @GetMapping("/code/{modelCode}")
    public ApiResponse<ModelDetailResponse> getModelDetailByCode(@PathVariable String modelCode) {
        return ApiResponse.success(modelService.getModelDetailByCode(modelCode));
    }

    /**
     * 供 agent-core 获取包含解密凭据的模型运行时配置。
     */
    @GetMapping("/code/{modelCode}/runtime")
    public ApiResponse<RuntimeModelDetailResponse> getRuntimeModelDetailByCode(
            @PathVariable String modelCode,
            @RequestHeader("X-Registry-Service-Token") String serviceToken) {
        validateServiceToken(serviceToken);
        return ApiResponse.success(modelService.getRuntimeModelDetailByCode(modelCode));
    }

    @PostMapping
    public ApiResponse<ModelDetailResponse> createModel(@Valid @RequestBody CreateModelRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(modelService.createModel(request, getSecurityUser(authentication)));
    }

    @PutMapping("/{modelId}")
    public ApiResponse<ModelDetailResponse> updateModel(@PathVariable Long modelId,
                                                        @Valid @RequestBody UpdateModelRequest request,
                                                        Authentication authentication) {
        return ApiResponse.success(modelService.updateModel(modelId, request, getSecurityUser(authentication)));
    }

    @PutMapping("/{modelId}/enable")
    public ApiResponse<Void> enableModel(@PathVariable Long modelId, Authentication authentication) {
        modelService.enableModel(modelId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    @PutMapping("/{modelId}/disable")
    public ApiResponse<Void> disableModel(@PathVariable Long modelId, Authentication authentication) {
        modelService.disableModel(modelId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }

    private void validateServiceToken(String serviceToken) {
        byte[] expected = runtimeSecurityProperties.getServiceToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = (serviceToken == null ? "" : serviceToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException("无权读取模型运行时配置");
        }
    }
}

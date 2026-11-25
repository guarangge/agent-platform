package com.zc.agent.core.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 注册中心访问配置。
 */
@Validated
@ConfigurationProperties(prefix = "core.registry")
public class RegistryProperties {

    private String baseUrl;
    /** 必须由部署环境显式提供，避免共享默认凭据。 */
    @NotBlank
    private String serviceToken;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}

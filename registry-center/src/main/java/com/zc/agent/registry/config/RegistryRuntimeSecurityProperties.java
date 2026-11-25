package com.zc.agent.registry.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 注册中心运行时配置读取安全参数。
 */
@Validated
@ConfigurationProperties(prefix = "registry.runtime.security")
public class RegistryRuntimeSecurityProperties {

    /** 必须由部署环境显式提供，避免共享默认凭据。 */
    @NotBlank
    private String serviceToken;

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}

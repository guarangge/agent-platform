package com.zc.agent.registry.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tool 安全配置。
 */
@Validated
@ConfigurationProperties(prefix = "registry.tools.security")
public class ToolSecurityProperties {

    /** 必须由部署环境显式提供，避免共享默认凭据。 */
    @NotBlank
    private String encryptionKey;

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
}

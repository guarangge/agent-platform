package com.zc.agent.core.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT 配置项。
 */
@Validated
@ConfigurationProperties(prefix = "core.security")
public class JwtProperties {

    /** 必须由部署环境显式提供，避免共享默认凭据。 */
    @NotBlank
    private String jwtSecret;

    private List<String> allowedOrigins = new ArrayList<>();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}

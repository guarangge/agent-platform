package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台 Tool 配置。
 */
@ConfigurationProperties(prefix = "core.tools")
public class ToolProperties {

    private String docSearchRoot;

    public String getDocSearchRoot() {
        return docSearchRoot;
    }

    public void setDocSearchRoot(String docSearchRoot) {
        this.docSearchRoot = docSearchRoot;
    }
}

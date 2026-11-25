package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Eval LLM Judge 配置。
 */
@ConfigurationProperties(prefix = "core.eval.llm")
public class EvalLlmProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey;
    private String model = "gpt-4.1-mini";
    private Double temperature = 0.1D;
    private Integer maxTokens = 1024;
    private Integer timeoutSeconds = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}

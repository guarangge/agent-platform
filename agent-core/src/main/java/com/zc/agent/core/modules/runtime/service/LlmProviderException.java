package com.zc.agent.core.modules.runtime.service;

import java.io.IOException;

/**
 * 标准模型供应商调用异常。
 */
public class LlmProviderException extends IOException {

    private final String errorCode;
    private final Integer providerStatusCode;
    private final boolean retryable;

    public LlmProviderException(String message,
                                String errorCode,
                                Integer providerStatusCode,
                                boolean retryable,
                                Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerStatusCode = providerStatusCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public Integer providerStatusCode() {
        return providerStatusCode;
    }

    public boolean retryable() {
        return retryable;
    }
}

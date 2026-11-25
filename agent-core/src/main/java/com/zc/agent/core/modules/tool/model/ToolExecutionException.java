package com.zc.agent.core.modules.tool.model;

import com.zc.agent.core.common.exception.BusinessException;

import java.util.Map;

/**
 * Tool 标准化执行异常。
 */
public class ToolExecutionException extends BusinessException {

    private final String errorCode;
    private final String errorType;
    private final boolean retryable;
    private final Integer providerStatusCode;
    private final Map<String, Object> metadata;

    public ToolExecutionException(String message,
                                  String errorCode,
                                  String errorType,
                                  boolean retryable,
                                  Integer providerStatusCode,
                                  Map<String, Object> metadata) {
        super(message);
        this.errorCode = errorCode;
        this.errorType = errorType;
        this.retryable = retryable;
        this.providerStatusCode = providerStatusCode;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer providerStatusCode() {
        return providerStatusCode;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}

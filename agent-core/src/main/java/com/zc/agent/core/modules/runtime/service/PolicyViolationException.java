package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;

import java.util.Map;

/**
 * Guardrail 策略拦截异常。
 */
public class PolicyViolationException extends BusinessException {

    private final Map<String, Object> policyMetadata;

    public PolicyViolationException(String message, Map<String, Object> policyMetadata) {
        super(message);
        this.policyMetadata = policyMetadata == null ? Map.of() : Map.copyOf(policyMetadata);
    }

    public Map<String, Object> policyMetadata() {
        return policyMetadata;
    }
}

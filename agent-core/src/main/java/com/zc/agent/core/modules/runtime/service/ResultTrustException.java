package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;

/** 结果可信度策略要求阻断或重规划。 */
public class ResultTrustException extends BusinessException {
    private final boolean replannable;

    public ResultTrustException(String message, boolean replannable) {
        super(message);
        this.replannable = replannable;
    }

    public boolean replannable() {
        return replannable;
    }
}

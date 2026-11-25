package com.zc.agent.registry.common.exception;

/**
 * 业务异常。
 */
public class BusinessException extends RuntimeException {

    /**
     * 创建业务异常。
     *
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
    }
}

package com.zc.agent.core.common.exception;

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

    /**
     * 创建业务异常。
     *
     * @param message 错误消息
     * @param cause   原始异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

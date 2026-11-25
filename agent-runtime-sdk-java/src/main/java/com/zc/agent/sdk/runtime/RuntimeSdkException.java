package com.zc.agent.sdk.runtime;

/** Java Runtime SDK 的稳定异常类型。 */
public class RuntimeSdkException extends RuntimeException {

    private final String code;
    private final Integer httpStatus;

    public RuntimeSdkException(String message, String code) {
        this(message, code, null, null);
    }

    public RuntimeSdkException(String message, String code, Integer httpStatus) {
        this(message, code, httpStatus, null);
    }

    public RuntimeSdkException(String message, String code, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}

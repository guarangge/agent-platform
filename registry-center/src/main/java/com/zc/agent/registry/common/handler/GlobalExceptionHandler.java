package com.zc.agent.registry.common.handler;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return new ApiResponse<>(400, exception.getMessage(), null);
    }

    /**
     * 处理参数校验异常。
     *
     * @param exception 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数校验失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return new ApiResponse<>(400, message, null);
    }

    /**
     * 处理表单或查询参数校验异常。
     *
     * @param exception 校验异常
     * @return 错误响应
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数校验失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return new ApiResponse<>(400, message, null);
    }

    /**
     * 处理兜底异常。
     *
     * @param exception 未知异常
     * @return 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Registry 接口发生未处理异常", exception);
        return new ApiResponse<>(500, "系统异常，请稍后重试", null);
    }
}

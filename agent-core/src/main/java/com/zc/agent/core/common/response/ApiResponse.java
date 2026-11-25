package com.zc.agent.core.common.response;

/**
 * 统一接口返回结构。
 *
 * @param <T> 数据类型
 */
public record ApiResponse<T>(int code, String message, T data) {

    /**
     * 构造成功返回。
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }
}

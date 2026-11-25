package com.zc.agent.registry.modules.auth.vo;

/**
 * 登录响应。
 *
 * @param accessToken 访问令牌
 * @param tokenType   令牌类型
 * @param expiresIn   过期时间，单位秒
 * @param refreshToken 刷新令牌
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {
}

package com.zc.agent.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT Token 服务。
 */
@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 解析访问 Token。
     *
     * @param token JWT Token
     * @return 当前用户
     */
    @SuppressWarnings("unchecked")
    public SecurityUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = claims.get("userId", Number.class).longValue();
        String username = claims.getSubject();
        String displayName = claims.get("displayName", String.class);
        List<String> roleCodes = claims.get("roleCodes", List.class);
        List<String> menuScopes = claims.get("menuScopes", List.class);
        return new SecurityUser(userId, username, displayName, roleCodes, menuScopes,
                longClaim(claims, "tenantId"), longClaim(claims, "organizationId"),
                longClaim(claims, "workspaceId"), longClaim(claims, "projectId"));
    }

    private Long longClaim(Claims claims, String name) {
        Number value = claims.get(name, Number.class);
        return value == null ? 1L : value.longValue();
    }

    private SecretKey getSecretKey() {
        byte[] secretBytes = jwtProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length >= 32) {
            return Keys.hmacShaKeyFor(secretBytes);
        }
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getJwtSecret()));
    }
}

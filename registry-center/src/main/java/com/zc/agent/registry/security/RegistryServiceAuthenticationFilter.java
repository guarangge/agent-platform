package com.zc.agent.registry.security;

import com.zc.agent.registry.config.RegistryRuntimeSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 将持有注册中心服务令牌的调用识别为 agent-core 运行时身份。
 */
@Component
public class RegistryServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-Registry-Service-Token";
    public static final String SERVICE_RUNTIME_AUTHORITY = "SERVICE_RUNTIME";

    private final RegistryRuntimeSecurityProperties securityProperties;

    public RegistryServiceAuthenticationFilter(RegistryRuntimeSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String serviceToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (StringUtils.hasText(serviceToken) && matches(serviceToken, securityProperties.getServiceToken())) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "agent-core",
                    null,
                    List.of(new SimpleGrantedAuthority(SERVICE_RUNTIME_AUTHORITY))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8)
        );
    }
}

package com.zc.agent.registry.modules.auth.service;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.auth.dto.LoginRequest;
import com.zc.agent.registry.modules.auth.dto.SwitchScopeRequest;
import com.zc.agent.registry.modules.auth.model.AuthRoleRecord;
import com.zc.agent.registry.modules.auth.model.AuthUserRecord;
import com.zc.agent.registry.modules.auth.repository.AuthRepository;
import com.zc.agent.registry.modules.auth.vo.CurrentUserResponse;
import com.zc.agent.registry.modules.auth.vo.LoginResponse;
import com.zc.agent.registry.modules.auth.vo.RoleInfoResponse;
import com.zc.agent.registry.modules.auth.vo.ScopeInfoResponse;
import com.zc.agent.registry.security.JwtProperties;
import com.zc.agent.registry.security.JwtTokenService;
import com.zc.agent.registry.security.SecurityUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;

/**
 * 认证服务。
 */
@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public AuthService(AuthRepository authRepository,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder,
                       ObjectMapper objectMapper) {
        this.authRepository = authRepository;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    public LoginResponse login(LoginRequest request) {
        AuthUserRecord userRecord = authRepository.findEnabledUserByUsername(request.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), userRecord.passwordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        List<AuthRoleRecord> roleRecords = authRepository.findEnabledRolesByUserId(userRecord.userId());
        var scope = defaultScope(userRecord.userId());
        authRepository.updateLastLoginAt(userRecord.userId());

        SecurityUser securityUser = new SecurityUser(
                userRecord.userId(),
                userRecord.username(),
                userRecord.displayName(),
                roleRecords.stream().map(AuthRoleRecord::roleCode).toList(),
                collectMenuScopes(roleRecords), scope.tenantId(), scope.organizationId(),
                scope.workspaceId(), scope.projectId()
        );
        String accessToken = jwtTokenService.generateAccessToken(securityUser);
        return new LoginResponse(accessToken, "Bearer", jwtProperties.getAccessTokenExpireSeconds(), null);
    }

    /**
     * 获取当前用户信息。
     *
     * @param securityUser 当前登录用户
     * @return 当前用户响应
     */
    public CurrentUserResponse getCurrentUser(SecurityUser securityUser) {
        AuthUserRecord userRecord = authRepository.findEnabledUserById(securityUser.userId())
                .orElseThrow(() -> new BusinessException("当前用户不存在"));
        List<AuthRoleRecord> roleRecords = authRepository.findEnabledRolesByUserId(userRecord.userId());
        List<ScopeInfoResponse> scopes = authRepository.findEnabledScopesByUserId(userRecord.userId()).stream().map(this::scopeResponse).toList();
        return new CurrentUserResponse(
                userRecord.userId(),
                userRecord.username(),
                userRecord.displayName(),
                userRecord.status(),
                roleRecords.stream()
                        .map(item -> new RoleInfoResponse(item.roleId(), item.roleCode(), item.roleName()))
                        .toList(),
                collectMenuScopes(roleRecords),
                scopes.stream().filter(item -> item.tenantId().equals(securityUser.tenantId())
                        && java.util.Objects.equals(item.projectId(), securityUser.projectId())).findFirst().orElse(scopes.get(0)),
                scopes
        );
    }

    /** 校验成员关系并签发带新作用域的 Token。 */
    public LoginResponse switchScope(SwitchScopeRequest request, SecurityUser currentUser) {
        var scope = authRepository.findEnabledScope(currentUser.userId(), request.membershipId())
                .orElseThrow(() -> new BusinessException("无权切换到目标作用域"));
        SecurityUser switched = new SecurityUser(currentUser.userId(), currentUser.username(), currentUser.displayName(),
                currentUser.roleCodes(), currentUser.menuScopes(), scope.tenantId(), scope.organizationId(),
                scope.workspaceId(), scope.projectId());
        return new LoginResponse(jwtTokenService.generateAccessToken(switched), "Bearer",
                jwtProperties.getAccessTokenExpireSeconds(), null);
    }

    private com.zc.agent.registry.modules.auth.model.SecurityScopeRecord defaultScope(Long userId) {
        return authRepository.findEnabledScopesByUserId(userId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("当前用户未分配租户作用域"));
    }

    private ScopeInfoResponse scopeResponse(com.zc.agent.registry.modules.auth.model.SecurityScopeRecord item) {
        return new ScopeInfoResponse(item.membershipId(), item.tenantId(), item.tenantCode(), item.tenantName(),
                item.organizationId(), item.organizationName(), item.workspaceId(), item.workspaceName(),
                item.projectId(), item.projectName(), item.membershipRole(), item.defaultScope());
    }

    private List<String> collectMenuScopes(List<AuthRoleRecord> roleRecords) {
        LinkedHashSet<String> menuScopes = new LinkedHashSet<>();
        for (AuthRoleRecord roleRecord : roleRecords) {
            menuScopes.addAll(parseMenuScope(roleRecord.menuScopeJson()));
        }
        return new ArrayList<>(menuScopes);
    }

    private List<String> parseMenuScope(String menuScopeJson) {
        try {
            return objectMapper.readValue(menuScopeJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException("角色菜单范围数据格式不正确");
        }
    }
}

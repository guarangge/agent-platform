package com.zc.agent.registry.modules.auth.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.auth.dto.LoginRequest;
import com.zc.agent.registry.modules.auth.dto.SwitchScopeRequest;
import com.zc.agent.registry.modules.auth.service.AuthService;
import com.zc.agent.registry.modules.auth.vo.CurrentUserResponse;
import com.zc.agent.registry.modules.auth.vo.LoginResponse;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/registry/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 获取当前登录用户。
     *
     * @param authentication 当前认证信息
     * @return 当前用户响应
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(authService.getCurrentUser(securityUser));
    }

    /** 切换租户/组织/工作区/项目作用域并重新签发 Token。 */
    @PostMapping("/switch-scope")
    public ApiResponse<LoginResponse> switchScope(@Valid @RequestBody SwitchScopeRequest request,
                                                   Authentication authentication) {
        return ApiResponse.success(authService.switchScope(request, (SecurityUser) authentication.getPrincipal()));
    }
}

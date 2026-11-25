package com.zc.agent.registry.modules.user.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.user.dto.CreateUserRequest;
import com.zc.agent.registry.modules.user.dto.ResetPasswordRequest;
import com.zc.agent.registry.modules.user.dto.UpdateUserRolesRequest;
import com.zc.agent.registry.modules.user.dto.UserPageQueryRequest;
import com.zc.agent.registry.modules.user.dto.UpdateUserRequest;
import com.zc.agent.registry.modules.user.service.UserService;
import com.zc.agent.registry.modules.user.vo.UserDetailResponse;
import com.zc.agent.registry.modules.user.vo.UserPageItemResponse;
import com.zc.agent.registry.modules.user.vo.UserRoleResponse;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口。
 */
@RestController
@RequestMapping("/api/registry/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<UserPageItemResponse>> pageUsers(@Valid UserPageQueryRequest request) {
        return ApiResponse.success(userService.pageUsers(request));
    }

    /**
     * 查询用户详情。
     *
     * @param userId 用户 ID
     * @return 用户详情
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserDetailResponse> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUserDetail(userId));
    }

    /**
     * 查询用户已绑定角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @GetMapping("/{userId}/roles")
    public ApiResponse<List<UserRoleResponse>> getUserRoles(@PathVariable Long userId) {
        return ApiResponse.success(userService.getUserRoles(userId));
    }

    /**
     * 新增用户。
     *
     * @param request        新增请求
     * @param authentication 当前认证信息
     * @return 用户详情
     */
    @PostMapping
    public ApiResponse<UserDetailResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(userService.createUser(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑用户。
     *
     * @param userId         用户 ID
     * @param request        编辑请求
     * @param authentication 当前认证信息
     * @return 用户详情
     */
    @PutMapping("/{userId}")
    public ApiResponse<UserDetailResponse> updateUser(@PathVariable Long userId,
                                                      @Valid @RequestBody UpdateUserRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(userService.updateUser(userId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用用户。
     *
     * @param userId         用户 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{userId}/enable")
    public ApiResponse<Void> enableUser(@PathVariable Long userId, Authentication authentication) {
        userService.updateUserStatus(userId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用用户。
     *
     * @param userId         用户 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{userId}/disable")
    public ApiResponse<Void> disableUser(@PathVariable Long userId, Authentication authentication) {
        userService.updateUserStatus(userId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 重置密码。
     *
     * @param userId         用户 ID
     * @param request        重置密码请求
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{userId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long userId,
                                           @Valid @RequestBody ResetPasswordRequest request,
                                           Authentication authentication) {
        userService.resetPassword(userId, request, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 覆盖更新用户角色。
     *
     * @param userId         用户 ID
     * @param request        更新请求
     * @param authentication 当前认证信息
     * @return 最新角色列表
     */
    @PutMapping("/{userId}/roles")
    public ApiResponse<List<UserRoleResponse>> updateUserRoles(@PathVariable Long userId,
                                                               @Valid @RequestBody UpdateUserRolesRequest request,
                                                               Authentication authentication) {
        return ApiResponse.success(userService.updateUserRoles(userId, request, getSecurityUser(authentication)));
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

package com.zc.agent.registry.modules.role.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.role.dto.CreateRoleRequest;
import com.zc.agent.registry.modules.role.dto.RolePageQueryRequest;
import com.zc.agent.registry.modules.role.dto.UpdateRoleAgentsRequest;
import com.zc.agent.registry.modules.role.dto.UpdateRoleRequest;
import com.zc.agent.registry.modules.role.service.RoleService;
import com.zc.agent.registry.modules.role.vo.RoleAgentResponse;
import com.zc.agent.registry.modules.role.vo.RoleDetailResponse;
import com.zc.agent.registry.modules.role.vo.RolePageItemResponse;
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
 * 角色管理接口。
 */
@RestController
@RequestMapping("/api/registry/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询角色。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<RolePageItemResponse>> pageRoles(@Valid RolePageQueryRequest request) {
        return ApiResponse.success(roleService.pageRoles(request));
    }

    /**
     * 查询角色详情。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    @GetMapping("/{roleId}")
    public ApiResponse<RoleDetailResponse> getRoleDetail(@PathVariable Long roleId) {
        return ApiResponse.success(roleService.getRoleDetail(roleId));
    }

    /**
     * 查询角色已绑定 Agent。
     *
     * @param roleId 角色 ID
     * @return Agent 列表
     */
    @GetMapping("/{roleId}/agents")
    public ApiResponse<List<RoleAgentResponse>> getRoleAgents(@PathVariable Long roleId) {
        return ApiResponse.success(roleService.getRoleAgents(roleId));
    }

    /**
     * 新增角色。
     *
     * @param request        新增请求
     * @param authentication 当前认证信息
     * @return 角色详情
     */
    @PostMapping
    public ApiResponse<RoleDetailResponse> createRole(@Valid @RequestBody CreateRoleRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(roleService.createRole(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑角色。
     *
     * @param roleId         角色 ID
     * @param request        编辑请求
     * @param authentication 当前认证信息
     * @return 角色详情
     */
    @PutMapping("/{roleId}")
    public ApiResponse<RoleDetailResponse> updateRole(@PathVariable Long roleId,
                                                      @Valid @RequestBody UpdateRoleRequest request,
                                                      Authentication authentication) {
        return ApiResponse.success(roleService.updateRole(roleId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用角色。
     *
     * @param roleId         角色 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{roleId}/enable")
    public ApiResponse<Void> enableRole(@PathVariable Long roleId, Authentication authentication) {
        roleService.updateRoleStatus(roleId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用角色。
     *
     * @param roleId         角色 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/{roleId}/disable")
    public ApiResponse<Void> disableRole(@PathVariable Long roleId, Authentication authentication) {
        roleService.updateRoleStatus(roleId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 覆盖更新角色 Agent。
     *
     * @param roleId         角色 ID
     * @param request        更新请求
     * @param authentication 当前认证信息
     * @return 最新 Agent 列表
     */
    @PutMapping("/{roleId}/agents")
    public ApiResponse<List<RoleAgentResponse>> updateRoleAgents(@PathVariable Long roleId,
                                                                 @Valid @RequestBody UpdateRoleAgentsRequest request,
                                                                 Authentication authentication) {
        return ApiResponse.success(roleService.updateRoleAgents(roleId, request, getSecurityUser(authentication)));
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

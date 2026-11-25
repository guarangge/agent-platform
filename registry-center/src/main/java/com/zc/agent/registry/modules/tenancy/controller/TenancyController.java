package com.zc.agent.registry.modules.tenancy.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.modules.tenancy.dto.TenancyContracts;
import com.zc.agent.registry.modules.tenancy.service.TenancyService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 租户和组织作用域管理接口。 */
@RestController
@RequestMapping("/api/registry/tenancy")
public class TenancyController {
    private final TenancyService service;
    public TenancyController(TenancyService service) { this.service=service; }

    @GetMapping("/tree") public ApiResponse<List<TenancyContracts.ScopeNode>> tree(Authentication auth) { return ApiResponse.success(service.tree(user(auth))); }
    @PostMapping("/tenants") public ApiResponse<Long> tenant(@Valid @RequestBody TenancyContracts.SaveTenantRequest r,Authentication a) { return ApiResponse.success(service.createTenant(r,user(a))); }
    @PostMapping("/organizations") public ApiResponse<Long> organization(@Valid @RequestBody TenancyContracts.SaveOrganizationRequest r,Authentication a) { return ApiResponse.success(service.createOrganization(r,user(a))); }
    @PostMapping("/workspaces") public ApiResponse<Long> workspace(@Valid @RequestBody TenancyContracts.SaveWorkspaceRequest r,Authentication a) { return ApiResponse.success(service.createWorkspace(r,user(a))); }
    @PostMapping("/projects") public ApiResponse<Long> project(@Valid @RequestBody TenancyContracts.SaveProjectRequest r,Authentication a) { return ApiResponse.success(service.createProject(r,user(a))); }
    @PostMapping("/memberships") public ApiResponse<Long> grant(@Valid @RequestBody TenancyContracts.SaveMembershipRequest r,Authentication a) { return ApiResponse.success(service.grant(r,user(a))); }
    @GetMapping("/users/{userId}/memberships") public ApiResponse<List<TenancyContracts.MembershipItem>> memberships(@PathVariable Long userId,Authentication a) { return ApiResponse.success(service.memberships(userId,user(a))); }
    private SecurityUser user(Authentication auth) { return (SecurityUser) auth.getPrincipal(); }
}

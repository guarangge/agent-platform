package com.zc.agent.registry.modules.tenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 租户、组织、工作区、项目和成员授权公共契约。 */
public final class TenancyContracts {
    private TenancyContracts() { }

    public record SaveTenantRequest(@NotBlank @Size(max=64) String code,
                                    @NotBlank @Size(max=128) String name) { }
    public record SaveOrganizationRequest(@NotNull @Positive Long tenantId,
                                          @NotBlank @Size(max=64) String code,
                                          @NotBlank @Size(max=128) String name) { }
    public record SaveWorkspaceRequest(@NotNull @Positive Long tenantId,
                                       @NotNull @Positive Long organizationId,
                                       @NotBlank @Size(max=64) String code,
                                       @NotBlank @Size(max=128) String name) { }
    public record SaveProjectRequest(@NotNull @Positive Long tenantId,
                                     @NotNull @Positive Long organizationId,
                                     @NotNull @Positive Long workspaceId,
                                     @NotBlank @Size(max=64) String code,
                                     @NotBlank @Size(max=128) String name) { }
    public record SaveMembershipRequest(@NotNull @Positive Long userId,
                                        @NotNull @Positive Long tenantId,
                                        Long organizationId, Long workspaceId, Long projectId,
                                        @NotBlank @Size(max=32) String membershipRole,
                                        Boolean defaultScope) { }
    public record ScopeNode(Long id, String code, String name, String status, List<ScopeNode> children) { }
    public record MembershipItem(Long id, Long userId, String username, Long tenantId, Long organizationId,
                                 Long workspaceId, Long projectId, String membershipRole, Boolean defaultScope,
                                 String status) { }
}

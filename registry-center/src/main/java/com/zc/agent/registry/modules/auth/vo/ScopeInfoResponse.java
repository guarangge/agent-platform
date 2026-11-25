package com.zc.agent.registry.modules.auth.vo;

/** 当前用户可切换的租户作用域。 */
public record ScopeInfoResponse(
        Long membershipId,
        Long tenantId,
        String tenantCode,
        String tenantName,
        Long organizationId,
        String organizationName,
        Long workspaceId,
        String workspaceName,
        Long projectId,
        String projectName,
        String membershipRole,
        Boolean defaultScope
) {
}

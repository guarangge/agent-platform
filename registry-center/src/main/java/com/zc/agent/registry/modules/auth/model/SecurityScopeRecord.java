package com.zc.agent.registry.modules.auth.model;

/** 当前用户可用的租户作用域。 */
public record SecurityScopeRecord(
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

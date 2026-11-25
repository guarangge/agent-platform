package com.zc.agent.core.modules.runtime.capability.model;

/**
 * Capability Policy 审计访问作用域。
 *
 * @param userId 当前用户 ID
 * @param tenantId 当前租户 ID
 * @param organizationId 当前组织 ID
 * @param workspaceId 当前工作区 ID
 * @param projectId 当前项目 ID
 */
public record CapabilityPolicyAuditScope(
        Long userId,
        Long tenantId,
        Long organizationId,
        Long workspaceId,
        Long projectId
) {
}

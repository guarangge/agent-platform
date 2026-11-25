package com.zc.agent.core.security;

/** 当前线程的租户数据库作用域。 */
public final class TenantScopeContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private TenantScopeContext() { }
    public static void set(SecurityUser user) { CURRENT.set(new Scope(user.tenantId(),user.organizationId(),user.workspaceId(),user.projectId())); }
    public static Scope current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
    public record Scope(Long tenantId,Long organizationId,Long workspaceId,Long projectId) { }
}

package com.zc.agent.registry.config;

import com.zc.agent.registry.security.TenantScopeContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** 在连接借出时注入 PostgreSQL RLS 所需的当前作用域。 */
@Component
public class TenantAwareDataSourcePostProcessor implements BeanPostProcessor {
    @Override public Object postProcessAfterInitialization(Object bean,String beanName) {
        return bean instanceof DataSource dataSource && !(bean instanceof ScopedDataSource)
                ? new ScopedDataSource(dataSource) : bean;
    }
    private static final class ScopedDataSource extends DelegatingDataSource {
        private ScopedDataSource(DataSource target) { super(target); }
        @Override public Connection getConnection() throws SQLException { Connection c=super.getConnection(); apply(c); return c; }
        @Override public Connection getConnection(String u,String p) throws SQLException { Connection c=super.getConnection(u,p); apply(c); return c; }
        private void apply(Connection c) throws SQLException {
            var s=TenantScopeContext.current();
            try(var ps=c.prepareStatement("SELECT set_config('app.tenant_id',?,false),set_config('app.organization_id',?,false),set_config('app.workspace_id',?,false),set_config('app.project_id',?,false)")) {
                ps.setString(1,value(s==null?null:s.tenantId())); ps.setString(2,value(s==null?null:s.organizationId()));
                ps.setString(3,value(s==null?null:s.workspaceId())); ps.setString(4,value(s==null?null:s.projectId())); ps.execute();
            }
        }
        private String value(Long value) { return value==null?"0":value.toString(); }
    }
}

package com.zc.agent.registry.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 仅初始化被安全迁移锁定的历史演示管理员，不重置正常账号或已有凭据。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public BootstrapAdminInitializer(JdbcTemplate jdbcTemplate,
                                     PasswordEncoder passwordEncoder,
                                     @Value("${registry.bootstrap.admin-password:}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    /**
     * Flyway 完成后一次性设置初始密码。缺少配置时保留锁定状态，日志不输出密码。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Long> pendingAdminIds = jdbcTemplate.query("""
                SELECT id FROM users
                WHERE username = 'zc' AND status = 'disabled' AND is_deleted = FALSE
                  AND metadata ->> 'bootstrapPasswordRequired' = 'true'
                """, (rs, rowNum) -> rs.getLong("id"));
        if (pendingAdminIds.isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("初始管理员已锁定，请设置 REGISTRY_BOOTSTRAP_ADMIN_PASSWORD 后重启 Registry 完成初始化");
            return;
        }
        // BCrypt 最多接受 72 字节，按 UTF-8 长度检查，避免中文密码被静默截断。
        if (adminPassword.length() < 12 || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("REGISTRY_BOOTSTRAP_ADMIN_PASSWORD 至少 12 个字符且 UTF-8 编码不超过 72 字节");
        }
        String passwordHash = passwordEncoder.encode(adminPassword);
        int initialized = 0;
        for (Long adminId : pendingAdminIds) {
            // 多实例并发启动时以标记和状态作为原子门禁，避免重复重置已初始化的密码。
            initialized += jdbcTemplate.update("""
                    UPDATE users
                    SET password_hash = ?, status = 'enabled',
                        metadata = metadata - 'bootstrapPasswordRequired', updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND username = 'zc' AND status = 'disabled' AND is_deleted = FALSE
                      AND metadata ->> 'bootstrapPasswordRequired' = 'true'
                    """, passwordHash, adminId);
        }
        log.info("初始管理员密码设置完成, initializedCount={}", initialized);
    }
}

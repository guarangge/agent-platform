package com.zc.agent.registry.modules.model.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时迁移历史明文模型凭据，避免旧数据长期以明文保留。
 */
@Component
public class ModelCredentialMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelCredentialMigrationRunner.class);

    private final ModelService modelService;

    public ModelCredentialMigrationRunner(ModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = modelService.migrateLegacyApiKeys();
        if (migrated > 0) {
            log.info("历史模型 API Key 加密迁移完成, migratedCount={}", migrated);
        }
    }
}

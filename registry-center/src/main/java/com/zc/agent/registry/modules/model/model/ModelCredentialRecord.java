package com.zc.agent.registry.modules.model.model;

/**
 * 模型凭据迁移记录。
 *
 * @param modelId 模型 ID
 * @param apiKey 当前存储的 API Key
 */
public record ModelCredentialRecord(Long modelId, String apiKey) {
}

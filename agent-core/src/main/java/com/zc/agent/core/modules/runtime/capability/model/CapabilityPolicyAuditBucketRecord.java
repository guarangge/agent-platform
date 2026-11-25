package com.zc.agent.core.modules.runtime.capability.model;

/**
 * Capability Policy 运营聚合桶数据库投影。
 *
 * @param key 聚合键
 * @param label 展示名称
 * @param count 决策数量
 */
public record CapabilityPolicyAuditBucketRecord(String key, String label, Long count) {
}

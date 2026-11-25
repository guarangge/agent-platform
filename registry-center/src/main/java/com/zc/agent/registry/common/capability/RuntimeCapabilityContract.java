package com.zc.agent.registry.common.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry 侧 Runtime Capability 与 Tool Side Effect 声明契约。
 *
 * <p>这里仅校验资源声明；声明本身不授予运行权限。</p>
 */
public final class RuntimeCapabilityContract {

    public static final Set<String> CAPABILITIES = Set.of(
            "filesystem.read",
            "filesystem.write",
            "network.read",
            "network.write",
            "process.execute",
            "database.read",
            "database.write",
            "artifact.publish",
            "external.side_effect"
    );
    public static final Set<String> SIDE_EFFECT_LEVELS = Set.of(
            "none", "idempotent", "compensatable", "irreversible");
    private static final Set<String> IDEMPOTENCY_MODES = Set.of("generated", "required_custom");

    private RuntimeCapabilityContract() {
    }

    /**
     * 规范化运行时能力，保持用户声明顺序并拒绝未知或重复项。
     */
    public static List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null) {
            throw new BusinessException("运行时能力声明不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            String code = capability == null ? "" : capability.trim();
            if (!CAPABILITIES.contains(code)) {
                throw new BusinessException("不支持的 Runtime Capability: " + code);
            }
            if (!normalized.add(code)) {
                throw new BusinessException("Runtime Capability 重复: " + code);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 校验 Tool 副作用等级，并保证 metadata.sideEffectPolicy 与顶层声明一致。
     */
    public static String validateSideEffect(String sideEffectLevel, JsonNode metadata) {
        String normalized = sideEffectLevel == null ? "" : sideEffectLevel.trim();
        if (!SIDE_EFFECT_LEVELS.contains(normalized)) {
            throw new BusinessException("Tool Side Effect Level 不合法: " + normalized);
        }
        JsonNode policy = metadata == null ? null : metadata.path("sideEffectPolicy");
        if (policy == null || policy.isMissingNode()) {
            if (!"none".equals(normalized)) {
                throw new BusinessException("有副作用的 Tool 必须配置 metadata.sideEffectPolicy");
            }
            return normalized;
        }
        if (!policy.isObject()) {
            throw new BusinessException("metadata.sideEffectPolicy 必须为 JSON 对象");
        }
        String metadataLevel = policy.path("level").asText("none").trim();
        if (!normalized.equals(metadataLevel)) {
            throw new BusinessException("sideEffectLevel 必须与 metadata.sideEffectPolicy.level 一致");
        }
        String idempotencyMode = policy.path("idempotencyMode").asText("generated").trim();
        if (!IDEMPOTENCY_MODES.contains(idempotencyMode)) {
            throw new BusinessException("Tool idempotencyMode 不合法: " + idempotencyMode);
        }
        String compensationToolCode = policy.path("compensationToolCode").asText("").trim();
        if ("compensatable".equals(normalized) && compensationToolCode.isBlank()) {
            throw new BusinessException("可补偿 Tool 必须配置 compensationToolCode");
        }
        return normalized;
    }
}

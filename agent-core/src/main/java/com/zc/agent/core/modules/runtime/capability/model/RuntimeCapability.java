package com.zc.agent.core.modules.runtime.capability.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tool 与 Runtime Plugin 可声明的运行时安全能力。
 *
 * <p>能力声明只描述组件可能触达的边界，不代表已经获得授权；实际执行仍需由
 * Capability Policy 决策。</p>
 */
public enum RuntimeCapability {

    FILESYSTEM_READ("filesystem.read"),
    FILESYSTEM_WRITE("filesystem.write"),
    NETWORK_READ("network.read"),
    NETWORK_WRITE("network.write"),
    PROCESS_EXECUTE("process.execute"),
    DATABASE_READ("database.read"),
    DATABASE_WRITE("database.write"),
    ARTIFACT_PUBLISH("artifact.publish"),
    EXTERNAL_SIDE_EFFECT("external.side_effect");

    private static final Set<String> SUPPORTED_CODES = Set.of(
            FILESYSTEM_READ.code,
            FILESYSTEM_WRITE.code,
            NETWORK_READ.code,
            NETWORK_WRITE.code,
            PROCESS_EXECUTE.code,
            DATABASE_READ.code,
            DATABASE_WRITE.code,
            ARTIFACT_PUBLISH.code,
            EXTERNAL_SIDE_EFFECT.code
    );

    private final String code;

    RuntimeCapability(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 规范化并校验能力列表，保持声明顺序且拒绝重复项。
     */
    public static List<String> normalize(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : capabilities) {
            if (capability == null || capability.isBlank()) {
                throw new IllegalArgumentException("Runtime Capability 不能为空");
            }
            String code = capability.trim();
            if (!SUPPORTED_CODES.contains(code)) {
                throw new IllegalArgumentException("不支持的 Runtime Capability: " + code);
            }
            if (!normalized.add(code)) {
                throw new IllegalArgumentException("Runtime Capability 存在重复值: " + code);
            }
        }
        return List.copyOf(normalized);
    }
}

package com.zc.agent.core.modules.runtime.capability;

import com.zc.agent.core.modules.tool.model.RegistryAgentTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 对历史 MCP Tool 的明显副作用操作补充最低安全声明。
 *
 * <p>这里只允许提升风险等级，不能覆盖 Registry 已声明的更严格能力。</p>
 */
final class ToolCapabilityInference {

    private static final Set<String> FILESYSTEM_WRITE_OPERATIONS = Set.of(
            "write_file", "edit_file", "move_file", "rename_file",
            "delete_file", "remove_file", "create_directory", "delete_directory", "remove_directory");
    private static final Set<String> MUTATION_TOKENS = Set.of(
            "write", "edit", "create", "update", "upsert", "delete", "remove", "move", "rename",
            "publish", "upload", "send", "execute", "run", "post", "put", "patch");

    private ToolCapabilityInference() {
    }

    /** 合并 Registry 声明与从 MCP operation 推断出的最低能力。 */
    static InferredDeclaration merge(RegistryAgentTool tool, List<String> declaredCapabilities,
                                     String declaredSideEffectLevel) {
        if (!"mcp".equalsIgnoreCase(tool.providerType())) {
            return new InferredDeclaration(declaredCapabilities, declaredSideEffectLevel);
        }
        String operation = normalize(tool.operationName());
        String toolCode = normalize(tool.toolCode());
        String inferredCapability = inferCapability(operation, toolCode);
        if (inferredCapability == null) {
            return new InferredDeclaration(declaredCapabilities, declaredSideEffectLevel);
        }
        ArrayList<String> merged = new ArrayList<>(declaredCapabilities);
        if (!merged.contains(inferredCapability)) {
            merged.add(inferredCapability);
        }
        String sideEffectLevel = "none".equals(declaredSideEffectLevel)
                ? "irreversible" : declaredSideEffectLevel;
        return new InferredDeclaration(List.copyOf(merged), sideEffectLevel);
    }

    private static String inferCapability(String operation, String toolCode) {
        if (FILESYSTEM_WRITE_OPERATIONS.contains(operation)
                || toolCode.startsWith("mcp_fs_") && containsMutationToken(toolCode)) {
            return "filesystem.write";
        }
        if (containsMutationToken(operation)) {
            return "external.side_effect";
        }
        return null;
    }

    private static boolean containsMutationToken(String value) {
        if (value.isBlank()) {
            return false;
        }
        for (String token : value.split("[^a-z0-9]+")) {
            if (MUTATION_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    record InferredDeclaration(List<String> capabilities, String sideEffectLevel) {
    }
}

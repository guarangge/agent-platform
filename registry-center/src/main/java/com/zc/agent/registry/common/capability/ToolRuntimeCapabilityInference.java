package com.zc.agent.registry.common.capability;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 为 Catalog 安装的远程 Tool 推断保守的最低运行时安全声明。 */
public final class ToolRuntimeCapabilityInference {

    private static final Set<String> FILESYSTEM_READ_OPERATIONS = Set.of(
            "read_file", "read_text_file", "read_media_file", "list_directory", "list_directory_with_sizes",
            "read_multiple_files", "directory_tree", "search_files", "get_file_info", "list_allowed_directories");
    private static final Set<String> FILESYSTEM_WRITE_OPERATIONS = Set.of(
            "write_file", "edit_file", "move_file", "rename_file",
            "delete_file", "remove_file", "create_directory", "delete_directory", "remove_directory");
    private static final Set<String> MUTATION_TOKENS = Set.of(
            "write", "edit", "create", "update", "upsert", "delete", "remove", "move", "rename",
            "publish", "upload", "send", "execute", "run", "post", "put", "patch");

    private ToolRuntimeCapabilityInference() {
    }

    /** 根据 Provider 与远程 operation 名称生成最低能力及副作用等级。 */
    public static Declaration infer(String providerType, String operationName) {
        if (!"mcp".equalsIgnoreCase(providerType)) {
            return new Declaration(List.of(), "none");
        }
        String operation = operationName == null ? "" : operationName.trim().toLowerCase(Locale.ROOT);
        if (FILESYSTEM_WRITE_OPERATIONS.contains(operation)) {
            return new Declaration(List.of("filesystem.write"), "irreversible");
        }
        if (FILESYSTEM_READ_OPERATIONS.contains(operation)) {
            return new Declaration(List.of("filesystem.read"), "none");
        }
        for (String token : operation.split("[^a-z0-9]+")) {
            if (MUTATION_TOKENS.contains(token)) {
                return new Declaration(List.of("external.side_effect"), "irreversible");
            }
        }
        return new Declaration(List.of("network.read"), "none");
    }

    /** 推断结果；Catalog 安装时作为可编辑的初始声明。 */
    public record Declaration(List<String> capabilities, String sideEffectLevel) {
    }
}

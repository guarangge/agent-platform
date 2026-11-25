package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * agent-core 回写 Runtime Plugin 实际加载结果请求。
 *
 * @param loadStatus            实际状态：loaded / failed / not_loaded
 * @param observedPluginCode    实际插件编码
 * @param observedVersion       实际插件版本
 * @param observedArtifactSha256 实际 Jar SHA-256，可无法计算时为空
 * @param errorCode             稳定错误编码
 * @param errorMessage          不含堆栈和敏感配置的错误摘要
 */
public record RuntimePluginLoadResultRequest(
        @NotBlank(message = "插件加载状态不能为空") String loadStatus,
        @Size(max = 64, message = "实际插件编码长度不能超过64个字符") String observedPluginCode,
        @Size(max = 64, message = "实际插件版本长度不能超过64个字符") String observedVersion,
        @Pattern(regexp = "^$|^[0-9a-f]{64}$", message = "实际制品 SHA-256 不合法")
        String observedArtifactSha256,
        @Size(max = 64, message = "加载错误编码长度不能超过64个字符") String errorCode,
        @Size(max = 1000, message = "加载错误摘要长度不能超过1000个字符") String errorMessage
) {
}

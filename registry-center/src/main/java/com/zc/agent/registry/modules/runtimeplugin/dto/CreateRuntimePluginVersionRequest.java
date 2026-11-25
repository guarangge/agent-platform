package com.zc.agent.registry.modules.runtimeplugin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 创建不可变 Runtime Plugin 版本请求。
 *
 * @param version            SemVer 版本
 * @param platformApiVersion 目标平台 API 主版本
 * @param entrypoint         SPI Provider 全限定类名
 * @param capabilities       插件声明能力
 * @param dependencies       插件依赖
 * @param components         七类组件编码
 * @param artifactUri        可部署 Jar 的制品地址
 * @param artifactSha256     制品 SHA-256
 * @param releaseNotes       发布说明
 * @param status             版本状态：draft / published / deprecated
 */
public record CreateRuntimePluginVersionRequest(
        @NotBlank(message = "插件版本不能为空")
        @Size(max = 64, message = "插件版本长度不能超过64个字符")
        String version,
        @NotBlank(message = "平台 API 版本不能为空")
        @Size(max = 16, message = "平台 API 版本长度不能超过16个字符")
        String platformApiVersion,
        @NotBlank(message = "插件入口类不能为空")
        @Size(max = 255, message = "插件入口类长度不能超过255个字符")
        String entrypoint,
        @NotNull(message = "插件能力列表不能为空")
        @Size(max = 100, message = "插件能力数量不能超过100个")
        List<String> capabilities,
        @Valid
        @NotNull(message = "插件依赖列表不能为空")
        @Size(max = 100, message = "插件依赖数量不能超过100个")
        List<RuntimePluginDependencyRequest> dependencies,
        @NotNull(message = "插件组件不能为空")
        @Size(max = 7, message = "插件组件类型不能超过7类")
        Map<String, List<String>> components,
        @NotBlank(message = "插件制品地址不能为空")
        @Size(max = 500, message = "插件制品地址长度不能超过500个字符")
        String artifactUri,
        @NotBlank(message = "插件制品 SHA-256 不能为空")
        String artifactSha256,
        @Size(max = 1000, message = "发布说明长度不能超过1000个字符")
        String releaseNotes,
        @NotBlank(message = "插件版本状态不能为空")
        String status
) {
}

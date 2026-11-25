package com.zc.agent.core.modules.runtime.plugin.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 当前 agent-core 进程的 Runtime Plugin 加载与治理观测快照。
 *
 * @param status             总体状态：UP / DEGRADED
 * @param platformApiVersion 当前平台 API 主版本
 * @param processLoadedAt    插件发现完成时间
 * @param observedAt         本次快照生成时间
 * @param pluginCount        实际加载插件数
 * @param optionalMissingCount 缺失可选依赖数
 * @param registrySync       Registry 期望状态对账结果
 * @param plugins            实际加载插件详情
 */
public record RuntimePluginHealthSnapshot(
        String status,
        String platformApiVersion,
        Instant processLoadedAt,
        Instant observedAt,
        int pluginCount,
        int optionalMissingCount,
        RegistrySyncObservation registrySync,
        List<PluginObservation> plugins
) {

    /**
     * 单个插件实际加载结果。
     *
     * @param pluginCode      插件编码
     * @param version         实际版本
     * @param platformApiVersion 插件目标平台 API 主版本
     * @param entrypoint      实际 SPI Provider 类
     * @param loadStatus      本地状态：loaded
     * @param artifactLocation 类或 Jar 来源位置
     * @param artifactSha256  可计算时的 Jar SHA-256
     * @param classLoader     实际 ClassLoader 类型
     * @param capabilities    Manifest 能力声明
     * @param dependencies    依赖观测
     * @param components      组件注册观测
     */
    public record PluginObservation(
            String pluginCode,
            String version,
            String platformApiVersion,
            String entrypoint,
            String loadStatus,
            String artifactLocation,
            String artifactSha256,
            String classLoader,
            List<String> capabilities,
            List<DependencyObservation> dependencies,
            Map<String, List<ComponentObservation>> components
    ) {
    }

    /**
     * 插件依赖解析结果。
     *
     * @param pluginCode        依赖插件编码
     * @param versionConstraint 版本约束
     * @param optional          是否可选
     * @param installedVersion  实际发现版本
     * @param status            satisfied / optional_missing
     */
    public record DependencyObservation(
            String pluginCode,
            String versionConstraint,
            boolean optional,
            String installedVersion,
            String status
    ) {
    }

    /**
     * 插件组件注册结果。
     *
     * @param componentCode 组件编码
     * @param status        registered / declared
     * @param implementations 已注册实现类；非 Tool 组件当前为空
     */
    public record ComponentObservation(
            String componentCode,
            String status,
            List<String> implementations
    ) {
    }

    /**
     * 本进程与 Registry 期望安装的最近对账结果。
     *
     * @param status             not_synced / succeeded / degraded / failed
     * @param environmentCode    对账环境编码
     * @param expectedCount      Registry 已启用安装数
     * @param matchedCount       编码、版本和可用摘要匹配数
     * @param failedCount        缺失或不匹配数
     * @param unmanagedCount     本地已加载但 Registry 未登记数
     * @param reportFailureCount 加载结果回写失败数
     * @param observedAt         最近对账时间
     * @param messages           不含堆栈和敏感配置的观测摘要
     */
    public record RegistrySyncObservation(
            String status,
            String environmentCode,
            int expectedCount,
            int matchedCount,
            int failedCount,
            int unmanagedCount,
            int reportFailureCount,
            Instant observedAt,
            List<String> messages
    ) {

        /** 创建尚未执行 Registry 对账的初始状态。 */
        public static RegistrySyncObservation notSynced() {
            return new RegistrySyncObservation(
                    "not_synced", null, 0, 0, 0, 0, 0, null, List.of());
        }
    }
}

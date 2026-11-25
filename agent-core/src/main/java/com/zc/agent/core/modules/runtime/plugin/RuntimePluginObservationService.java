package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginDependency;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.ComponentObservation;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.DependencyObservation;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.PluginObservation;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.RegistrySyncObservation;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginManifest;
import com.zc.agent.core.modules.tool.service.ToolExecutorRegistry;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 汇总当前进程的 Runtime Plugin 加载、依赖和组件注册观测。 */
@Service
public class RuntimePluginObservationService {

    private final RuntimePluginDiscovery discovery;
    private final ToolExecutorRegistry toolExecutorRegistry;
    private final Instant processLoadedAt = Instant.now();
    private final AtomicReference<RegistrySyncObservation> registrySync =
            new AtomicReference<>(RegistrySyncObservation.notSynced());

    public RuntimePluginObservationService(RuntimePluginDiscovery discovery,
                                           ToolExecutorRegistry toolExecutorRegistry) {
        this.discovery = discovery;
        this.toolExecutorRegistry = toolExecutorRegistry;
    }

    /** 返回当前插件观测快照。 */
    public RuntimePluginHealthSnapshot snapshot() {
        List<PluginObservation> plugins = plugins();
        int optionalMissingCount = plugins.stream()
                .flatMap(plugin -> plugin.dependencies().stream())
                .mapToInt(dependency -> "optional_missing".equals(dependency.status()) ? 1 : 0)
                .sum();
        RegistrySyncObservation sync = registrySync.get();
        String status = List.of("failed", "degraded").contains(sync.status()) ? "DEGRADED" : "UP";
        return new RuntimePluginHealthSnapshot(
                status,
                AgentRuntimePlugin.PLATFORM_API_VERSION,
                processLoadedAt,
                Instant.now(),
                plugins.size(),
                optionalMissingCount,
                sync,
                plugins
        );
    }

    /** 返回按插件编码稳定排序的本地实际加载详情。 */
    public List<PluginObservation> plugins() {
        List<AgentRuntimePlugin> discovered = discovery.plugins();
        Map<String, String> installedVersions = new LinkedHashMap<>();
        discovered.forEach(plugin -> installedVersions.put(
                plugin.manifest().pluginCode(), plugin.manifest().version()));
        return discovered.stream()
                .map(plugin -> observePlugin(plugin, installedVersions))
                .sorted(Comparator.comparing(PluginObservation::pluginCode))
                .toList();
    }

    /** 更新最近一次 Registry 对账结果。 */
    public void updateRegistrySync(RegistrySyncObservation observation) {
        registrySync.set(observation);
    }

    private PluginObservation observePlugin(AgentRuntimePlugin plugin,
                                            Map<String, String> installedVersions) {
        RuntimePluginManifest manifest = plugin.manifest();
        ArtifactObservation artifact = observeArtifact(plugin.getClass());
        return new PluginObservation(
                manifest.pluginCode(),
                manifest.version(),
                manifest.platformApiVersion(),
                plugin.getClass().getName(),
                "loaded",
                artifact.location(),
                artifact.sha256(),
                plugin.getClass().getClassLoader() == null
                        ? "bootstrap" : plugin.getClass().getClassLoader().getClass().getName(),
                manifest.capabilities(),
                observeDependencies(manifest.dependencies(), installedVersions),
                observeComponents(manifest.components())
        );
    }

    private List<DependencyObservation> observeDependencies(
            List<RuntimePluginDependency> dependencies,
            Map<String, String> installedVersions) {
        return dependencies.stream()
                .map(dependency -> new DependencyObservation(
                        dependency.pluginCode(),
                        dependency.versionConstraint(),
                        dependency.optional(),
                        installedVersions.get(dependency.pluginCode()),
                        installedVersions.containsKey(dependency.pluginCode())
                                ? "satisfied" : "optional_missing"))
                .toList();
    }

    private Map<String, List<ComponentObservation>> observeComponents(
            Map<String, List<String>> components) {
        LinkedHashMap<String, List<ComponentObservation>> result = new LinkedHashMap<>();
        components.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    List<ComponentObservation> observations = new ArrayList<>();
                    entry.getValue().stream().sorted().forEach(componentCode -> {
                        List<String> implementations = "tools".equals(entry.getKey())
                                ? toolExecutorRegistry.supportingExecutorClasses(componentCode)
                                : List.of();
                        observations.add(new ComponentObservation(
                                componentCode,
                                implementations.isEmpty() ? "declared" : "registered",
                                implementations));
                    });
                    result.put(entry.getKey(), List.copyOf(observations));
                });
        return java.util.Collections.unmodifiableMap(result);
    }

    private ArtifactObservation observeArtifact(Class<?> providerClass) {
        try {
            URI location = providerClass.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location);
            return new ArtifactObservation(location.toString(), Files.isRegularFile(path) ? sha256(path) : null);
        } catch (Exception exception) {
            return new ArtifactObservation(null, null);
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record ArtifactObservation(String location, String sha256) {
    }
}

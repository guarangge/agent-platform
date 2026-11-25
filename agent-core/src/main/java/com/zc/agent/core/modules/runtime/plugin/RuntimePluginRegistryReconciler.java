package com.zc.agent.core.modules.runtime.plugin;

import com.zc.agent.core.modules.runtime.plugin.RegistryRuntimePluginClient.ExpectedInstallation;
import com.zc.agent.core.modules.runtime.plugin.RegistryRuntimePluginClient.LoadResultRequest;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.PluginObservation;
import com.zc.agent.core.modules.runtime.plugin.model.RuntimePluginHealthSnapshot.RegistrySyncObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将当前进程实际加载插件与 Registry 期望安装进行对账并回写。 */
@Component
public class RuntimePluginRegistryReconciler {

    private static final Logger log = LoggerFactory.getLogger(RuntimePluginRegistryReconciler.class);

    private final RegistryRuntimePluginClient client;
    private final RuntimePluginObservationService observationService;
    private final String environmentCode;

    public RuntimePluginRegistryReconciler(
            RegistryRuntimePluginClient client,
            RuntimePluginObservationService observationService,
            @Value("${core.runtime.plugin.environment-code:default}") String environmentCode) {
        this.client = client;
        this.observationService = observationService;
        this.environmentCode = environmentCode;
    }

    /** 应用就绪后执行一次对账；Registry 故障只进入降级状态，不阻断启动。 */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        reconcile();
    }

    /** 执行插件期望状态与本地加载状态对账。 */
    public RegistrySyncObservation reconcile() {
        Instant observedAt = Instant.now();
        List<String> messages = new ArrayList<>();
        try {
            List<ExpectedInstallation> expected = client.expectedInstallations(environmentCode);
            Map<String, PluginObservation> actualByCode = new LinkedHashMap<>();
            observationService.plugins().forEach(plugin -> actualByCode.put(plugin.pluginCode(), plugin));
            Set<String> expectedCodes = new LinkedHashSet<>();
            int matched = 0;
            int failed = 0;
            int reportFailures = 0;
            for (ExpectedInstallation installation : expected) {
                expectedCodes.add(installation.pluginCode());
                PluginObservation actual = actualByCode.get(installation.pluginCode());
                LoadResultRequest result = compare(installation, actual, messages);
                if ("loaded".equals(result.loadStatus())) {
                    matched++;
                } else {
                    failed++;
                }
                try {
                    client.reportLoadResult(installation.installationId(), result);
                } catch (RuntimeException exception) {
                    reportFailures++;
                    messages.add("加载结果回写失败: " + installation.pluginCode());
                    log.warn("Runtime Plugin 加载结果回写失败, pluginCode={}, error={}",
                            installation.pluginCode(), safeError(exception));
                }
            }
            List<String> unmanaged = actualByCode.keySet().stream()
                    .filter(pluginCode -> !expectedCodes.contains(pluginCode))
                    .sorted()
                    .toList();
            unmanaged.forEach(pluginCode -> messages.add("本地插件未在 Registry 登记: " + pluginCode));
            String status = failed == 0 && reportFailures == 0 && unmanaged.isEmpty()
                    ? "succeeded" : "degraded";
            RegistrySyncObservation result = new RegistrySyncObservation(
                    status, environmentCode, expected.size(), matched, failed, unmanaged.size(),
                    reportFailures, observedAt, List.copyOf(messages));
            observationService.updateRegistrySync(result);
            log.info("Runtime Plugin 对账完成, environmentCode={}, status={}, expected={}, matched={}, failed={}, unmanaged={}, reportFailures={}",
                    environmentCode, status, expected.size(), matched, failed, unmanaged.size(), reportFailures);
            return result;
        } catch (RuntimeException exception) {
            messages.add("Registry 对账不可用: " + safeError(exception));
            RegistrySyncObservation result = new RegistrySyncObservation(
                    "failed", environmentCode, 0, 0, 0, 0, 0, observedAt, List.copyOf(messages));
            observationService.updateRegistrySync(result);
            log.warn("Runtime Plugin Registry 对账失败，Core 将继续运行, environmentCode={}, error={}",
                    environmentCode, safeError(exception));
            return result;
        }
    }

    private LoadResultRequest compare(ExpectedInstallation expected,
                                      PluginObservation actual,
                                      List<String> messages) {
        if (actual == null) {
            messages.add("期望插件未加载: " + expected.pluginCode());
            return failed(null, "PLUGIN_NOT_LOADED", "期望插件未在当前进程加载");
        }
        if (!expected.version().equals(actual.version())) {
            messages.add("插件版本不匹配: " + expected.pluginCode());
            return failed(actual, "PLUGIN_VERSION_MISMATCH",
                    "期望版本 " + expected.version() + "，实际版本 " + actual.version());
        }
        if (actual.artifactSha256() != null
                && !expected.artifactSha256().equalsIgnoreCase(actual.artifactSha256())) {
            messages.add("插件制品摘要不匹配: " + expected.pluginCode());
            return failed(actual, "PLUGIN_ARTIFACT_SHA256_MISMATCH", "实际 Jar SHA-256 与登记值不一致");
        }
        if (actual.artifactSha256() == null) {
            messages.add("插件制品摘要不可计算: " + expected.pluginCode());
        }
        return new LoadResultRequest("loaded", actual.pluginCode(), actual.version(),
                actual.artifactSha256(), null, null);
    }

    private LoadResultRequest failed(PluginObservation actual, String errorCode, String errorMessage) {
        return new LoadResultRequest(
                "failed",
                actual == null ? null : actual.pluginCode(),
                actual == null ? null : actual.version(),
                actual == null ? null : actual.artifactSha256(),
                errorCode,
                errorMessage
        );
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }
}

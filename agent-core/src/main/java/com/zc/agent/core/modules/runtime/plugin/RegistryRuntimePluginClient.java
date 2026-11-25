package com.zc.agent.core.modules.runtime.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.RegistryProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/** 调用 Registry Runtime Plugin 运行时对账接口。 */
@Component
public class RegistryRuntimePluginClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Registry-Service-Token";

    private final RestClient registryRestClient;
    private final RegistryProperties properties;

    public RegistryRuntimePluginClient(RestClient registryRestClient,
                                       RegistryProperties properties) {
        this.registryRestClient = registryRestClient;
        this.properties = properties;
    }

    /** 查询指定环境期望加载的插件安装。 */
    public List<ExpectedInstallation> expectedInstallations(String environmentCode) {
        Envelope<List<ExpectedInstallation>> envelope = registryRestClient.get()
                .uri(builder -> builder.path("/runtime-plugins/runtime/installations")
                        .queryParam("environmentCode", environmentCode)
                        .build())
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (envelope == null || envelope.code() != 200 || envelope.data() == null) {
            throw new BusinessException("读取 Runtime Plugin 期望安装失败");
        }
        return envelope.data();
    }

    /** 回写单个安装的实际加载结果。 */
    public void reportLoadResult(Long installationId, LoadResultRequest request) {
        registryRestClient.put()
                .uri("/runtime-plugins/runtime/installations/{installationId}/load-result", installationId)
                .header(SERVICE_TOKEN_HEADER, properties.getServiceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    /** Registry 期望安装快照。 */
    public record ExpectedInstallation(
            @JsonProperty("installation_id") Long installationId,
            @JsonProperty("plugin_id") Long pluginId,
            @JsonProperty("plugin_code") String pluginCode,
            @JsonProperty("version_id") Long versionId,
            String version,
            @JsonProperty("platform_api_version") String platformApiVersion,
            @JsonProperty("artifact_uri") String artifactUri,
            @JsonProperty("artifact_sha256") String artifactSha256,
            @JsonProperty("environment_code") String environmentCode,
            @JsonProperty("load_status") String loadStatus
    ) {
    }

    /** Core 实际加载结果。 */
    public record LoadResultRequest(
            String loadStatus,
            String observedPluginCode,
            String observedVersion,
            String observedArtifactSha256,
            String errorCode,
            String errorMessage
    ) {
    }

    private record Envelope<T>(int code, String message, T data) {
    }
}

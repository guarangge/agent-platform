package com.zc.agent.registry.modules.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Registry 调用 agent-core Eval 发布门禁的服务客户端。 */
@Service
public class AgentReleaseGateClient {
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceToken;

    public AgentReleaseGateClient(ObjectMapper objectMapper,
                                  @Value("${registry.agent-core.base-url}") String baseUrl,
                                  @Value("${registry.agent-core.service-token}") String serviceToken) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    /** 配置开启时调用 Core 门禁，任何不可判定状态均按失败处理。 */
    public void verify(Long agentId, JsonNode agentMetadata) {
        JsonNode gate = agentMetadata == null ? null : agentMetadata.path("releaseGate");
        if (gate == null || !gate.path("enabled").asBoolean(false)) {
            return;
        }
        String query = "minimumAverageScore=" + encode(gate.path("minimumAverageScore").asText("80"))
                + "&minimumPassRate=" + encode(gate.path("minimumPassRate").asText("90"))
                + "&minimumCases=" + encode(gate.path("minimumCases").asText("1"))
                + "&maximumAgeHours=" + encode(gate.path("maximumAgeHours").asText("168"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/evals/release-gate/agents/" + agentId + "?" + query))
                .timeout(Duration.ofSeconds(10))
                .header("X-Service-Token", serviceToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("Agent Eval 发布门禁调用失败，HTTP " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.path("passed").asBoolean(false)) {
                throw new BusinessException("Agent Eval 发布门禁未通过：" + data.path("reasons"));
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Agent Eval 发布门禁不可用：" + exception.getMessage());
        } catch (Exception exception) {
            throw new BusinessException("Agent Eval 发布门禁不可用：" + exception.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

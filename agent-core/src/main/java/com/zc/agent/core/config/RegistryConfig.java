package com.zc.agent.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 注册中心客户端配置。
 */
@Configuration
@EnableConfigurationProperties({
        RegistryProperties.class,
        ToolProperties.class,
        MemoryLlmProperties.class,
        EvalLlmProperties.class,
        ConversationContextProperties.class,
        AgentRunWorkerProperties.class,
        ReplayFeatureProperties.class,
        AgentLoopKernelProperties.class,
        InteractiveLoopProperties.class
})
public class RegistryConfig {

    /**
     * 注册中心客户端。
     *
     * @param registryProperties 配置
     * @return RestClient
     */
    @Bean
    public RestClient registryRestClient(RegistryProperties registryProperties,
                                         RestClient.Builder restClientBuilder) {
        return restClientBuilder
                .baseUrl(registryProperties.getBaseUrl())
                .defaultHeader("X-Registry-Service-Token", registryProperties.getServiceToken())
                .build();
    }
}

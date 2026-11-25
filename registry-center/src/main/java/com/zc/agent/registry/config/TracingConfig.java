package com.zc.agent.registry.config;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 本地基线配置，生产环境可由 OTLP exporter Bean 替换默认空 exporter。
 */
@Configuration
public class TracingConfig {

    @Bean
    @ConditionalOnMissingBean(SpanExporter.class)
    public SpanExporter noOpSpanExporter() {
        return SpanExporter.composite();
    }
}

package com.zc.agent.core.config;

import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 本地基线配置。
 * 默认 exporter 仅用于启用 SDK 与 W3C context，部署环境可用 OTLP exporter Bean 替换。
 */
@Configuration
public class TracingConfig {

    @Bean
    @ConditionalOnMissingBean(SpanExporter.class)
    public SpanExporter noOpSpanExporter() {
        return SpanExporter.composite();
    }
}

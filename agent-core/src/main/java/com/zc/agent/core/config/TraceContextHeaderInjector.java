package com.zc.agent.core.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

/**
 * 为未受 Spring 自动观测的 JDK HttpClient 请求注入 W3C trace context。
 */
@Component
public class TraceContextHeaderInjector {

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContextHeaderInjector(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void inject(HttpRequest.Builder requestBuilder) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }
        propagator.inject(currentSpan.context(), requestBuilder, HttpRequest.Builder::header);
    }
}

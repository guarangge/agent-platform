package com.zc.agent.sdk.runtime;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** 基于 Java 17 HttpClient 的默认同步 Transport。 */
public final class JdkRuntimeHttpTransport implements RuntimeHttpTransport {

    private final HttpClient httpClient;

    public JdkRuntimeHttpTransport() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    public JdkRuntimeHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public RuntimeHttpResponse execute(RuntimeHttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(buildRequest(request), HttpResponse.BodyHandlers.ofString());
        return new RuntimeHttpResponse(response.statusCode(), response.headers().map(), response.body());
    }

    @Override
    public RuntimeHttpStreamResponse openStream(RuntimeHttpRequest request) throws IOException, InterruptedException {
        HttpResponse<java.io.InputStream> response = httpClient.send(
                buildRequest(request), HttpResponse.BodyHandlers.ofInputStream());
        return new RuntimeHttpStreamResponse(response.statusCode(), response.headers().map(), response.body());
    }

    private HttpRequest buildRequest(RuntimeHttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeout());
        request.headers().forEach(builder::header);
        if (request.body() == null) {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(request.method(), HttpRequest.BodyPublishers.ofString(request.body()));
        }
        return builder.build();
    }
}

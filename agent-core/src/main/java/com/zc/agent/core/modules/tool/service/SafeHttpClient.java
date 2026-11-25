package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用 Tool 出站 HTTP 客户端，统一执行 SSRF、超时和响应体积防护。
 */
@Component
public class SafeHttpClient {

    private static final int DEFAULT_MAX_BYTES = 1024 * 1024;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FetchResponse get(RegistryAgentTool tool, String rawUrl, Map<String, String> requestHeaders, int maxBytes) {
        URI uri = validateTarget(tool, rawUrl);
        int timeoutSeconds = tool.providerTimeoutSeconds() == null
                ? tool.timeoutSeconds() == null ? 30 : tool.timeoutSeconds()
                : tool.providerTimeoutSeconds();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, Math.min(120, timeoutSeconds))))
                .GET();
        if (requestHeaders != null) {
            requestHeaders.forEach((name, value) -> {
                if (isSafeHeader(name) && value != null && !value.isBlank()) {
                    builder.header(name, value);
                }
            });
        }
        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int limit = Math.max(1024, Math.min(DEFAULT_MAX_BYTES * 5, maxBytes));
            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = body.readNBytes(limit + 1);
            }
            if (bytes.length > limit) {
                throw failure("HTTP 响应超过大小限制", "HTTP_RESPONSE_TOO_LARGE", Map.of("maxBytes", limit));
            }
            String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
            Charset charset = charset(contentType);
            return new FetchResponse(response.statusCode(), uri.toString(), contentType,
                    new String(bytes, charset), bytes.length, response.headers().map());
        } catch (ToolExecutionException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("HTTP 请求被中断", "HTTP_REQUEST_INTERRUPTED", Map.of("url", uri.toString()));
        } catch (Exception exception) {
            throw failure("HTTP 请求失败：" + safeMessage(exception), "HTTP_REQUEST_FAILED",
                    Map.of("url", uri.toString()));
        }
    }

    private URI validateTarget(RegistryAgentTool tool, String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw failure("URL 格式无效", "HTTP_URL_INVALID", Map.of());
        }
        if (!Set.of("http", "https").contains(lower(uri.getScheme())) || uri.getHost() == null) {
            throw failure("仅允许访问 http/https URL", "HTTP_SCHEME_NOT_ALLOWED", Map.of());
        }
        String host = lower(uri.getHost());
        Set<String> allowedHosts = allowedHosts(tool);
        if (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(allowed -> host.equals(allowed) || host.endsWith('.' + allowed))) {
            throw failure("目标域名不在 Tool 白名单中", "HTTP_HOST_NOT_ALLOWED", Map.of("host", host));
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivate(address)) {
                    throw failure("禁止访问本地或私有网络地址", "HTTP_PRIVATE_ADDRESS_BLOCKED", Map.of("host", host));
                }
            }
        } catch (ToolExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("目标域名解析失败", "HTTP_DNS_FAILED", Map.of("host", host));
        }
        return uri;
    }

    private Set<String> allowedHosts(RegistryAgentTool tool) {
        Set<String> result = new HashSet<>();
        if (tool.metadata() != null) {
            tool.metadata().path("allowedHosts").forEach(node -> {
                if (node.isTextual() && !node.asText().isBlank()) {
                    result.add(lower(node.asText().trim()));
                }
            });
        }
        return result;
    }

    private boolean isPrivate(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress();
    }

    private boolean isSafeHeader(String name) {
        return Set.of("accept", "accept-language", "user-agent").contains(lower(name));
    }

    private Charset charset(String contentType) {
        for (String item : contentType.split(";")) {
            String value = item.trim();
            if (lower(value).startsWith("charset=")) {
                try {
                    return Charset.forName(value.substring("charset=".length()).trim());
                } catch (Exception ignored) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private ToolExecutionException failure(String message, String code, Map<String, Object> metadata) {
        return new ToolExecutionException(message, code, "transport", true, null, metadata);
    }

    public record FetchResponse(int statusCode, String url, String contentType, String body, int bodyBytes,
                                Map<String, java.util.List<String>> headers) {
    }
}

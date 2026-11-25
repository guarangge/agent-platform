package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import jakarta.annotation.PreDestroy;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Apache Tika 的受限文档解析 Tool，支持 PDF、Office、HTML 与纯文本等常用格式。
 */
@Component
public class DocumentParseToolExecutor extends StructuredToolExecutor {

    private static final String TOOL_CODE = "DOCUMENT_PARSE";
    private static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();
    private final ExecutorService parserExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "document-parse-tool");
        thread.setDaemon(true);
        return thread;
    });

    public DocumentParseToolExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String toolCode) {
        return TOOL_CODE.equals(toolCode);
    }

    @Override
    public ToolExecutionResult execute(ExecutionContext context, RegistryAgentTool tool, ToolInvocationRequest request) {
        String fileName = BuiltinToolSupport.text(request, "fileName", "document");
        String contentBase64 = BuiltinToolSupport.requiredText(request, "contentBase64");
        int maxChars = BuiltinToolSupport.integer(request, "maxChars", 100000, 1000, 500000);
        byte[] content;
        try {
            content = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException exception) {
            throw BuiltinToolSupport.failure("contentBase64 不是有效 Base64", "DOCUMENT_CONTENT_INVALID", Map.of());
        }
        if (content.length == 0 || content.length > MAX_INPUT_BYTES) {
            throw BuiltinToolSupport.failure("文档大小必须在 1 字节到 10MB 之间", "DOCUMENT_SIZE_INVALID",
                    Map.of("bytes", content.length));
        }
        int timeoutSeconds = Math.max(1, Math.min(60, tool.timeoutSeconds() == null ? 30 : tool.timeoutSeconds()));
        Future<ParsedDocument> future = parserExecutor.submit(() -> parse(content, fileName, maxChars));
        try {
            ParsedDocument parsed = future.get(timeoutSeconds, TimeUnit.SECONDS);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("fileName", fileName);
            snapshot.put("contentType", parsed.contentType());
            snapshot.put("title", parsed.title());
            snapshot.put("author", parsed.author());
            snapshot.put("inputBytes", content.length);
            snapshot.put("characterCount", parsed.text().length());
            snapshot.put("truncated", parsed.text().length() >= maxChars);
            snapshot.put("text", parsed.text());
            return new ToolExecutionResult(tool.toolCode(), tool.toolName(),
                    "文档解析完成，共 " + parsed.text().length() + " 个字符", parsed.text(),
                    BuiltinToolSupport.json(objectMapper, snapshot));
        } catch (java.util.concurrent.TimeoutException exception) {
            future.cancel(true);
            throw BuiltinToolSupport.failure("文档解析超时", "DOCUMENT_PARSE_TIMEOUT",
                    Map.of("timeoutSeconds", timeoutSeconds));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BuiltinToolSupport.failure("文档解析被中断", "DOCUMENT_PARSE_INTERRUPTED", Map.of());
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw BuiltinToolSupport.failure("文档解析失败：" + safeMessage(cause),
                    "DOCUMENT_PARSE_FAILED", Map.of("fileName", fileName));
        }
    }

    private ParsedDocument parse(byte[] content, String fileName, int maxChars) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        String text;
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            text = tika.parseToString(input, metadata, maxChars);
        }
        return new ParsedDocument(text == null ? "" : text.trim(), metadata.get(Metadata.CONTENT_TYPE),
                metadata.get(TikaCoreProperties.TITLE), metadata.get(TikaCoreProperties.CREATOR));
    }

    @PreDestroy
    public void shutdown() {
        parserExecutor.shutdownNow();
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private record ParsedDocument(String text, String contentType, String title, String author) {
    }
}

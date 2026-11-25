package com.zc.agent.core.modules.runtime.protocol.controller;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventStreamService;
import com.zc.agent.core.security.SecurityUser;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/**
 * Runtime Protocol 对外事件接口。
 */
@RestController
@RequestMapping("/api/core/runtime/runs")
public class RuntimeEventController {

    private final RuntimeEventStreamService runtimeEventStreamService;

    public RuntimeEventController(RuntimeEventStreamService runtimeEventStreamService) {
        this.runtimeEventStreamService = runtimeEventStreamService;
    }

    /**
     * 订阅统一 Runtime Event；优先使用标准 Last-Event-ID，也支持 afterSequence 查询参数。
     */
    @GetMapping(value = "/{runCode}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamEvents(
            @PathVariable String runCode,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            Authentication authentication,
            HttpServletResponse response) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return runtimeEventStreamService.stream(
                runCode,
                resolveCursor(afterSequence, lastEventId),
                securityUser
        );
    }

    private long resolveCursor(long afterSequence, String lastEventId) {
        if (afterSequence < 0) {
            throw new BusinessException("afterSequence 必须是非负整数");
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            return afterSequence;
        }
        try {
            long parsed = Long.parseLong(lastEventId.trim());
            if (parsed < 0) {
                throw new BusinessException("Last-Event-ID 必须是非负整数");
            }
            return Math.max(afterSequence, parsed);
        } catch (NumberFormatException exception) {
            throw new BusinessException("Last-Event-ID 必须是非负整数");
        }
    }
}

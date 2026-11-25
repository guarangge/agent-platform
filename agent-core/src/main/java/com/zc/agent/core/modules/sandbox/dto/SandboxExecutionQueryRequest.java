package com.zc.agent.core.modules.sandbox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Sandbox 审计分页查询。
 */
public record SandboxExecutionQueryRequest(
        @Min(1) Integer pageNum,
        @Min(1) @Max(200) Integer pageSize,
        Long profileId,
        String status,
        String executionCode,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedAfter,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedBefore
) {
}

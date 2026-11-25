package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.runtime.replay.dto.ReplayPageQueryRequest;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.model.RuntimeReplayRecord;
import com.zc.agent.core.modules.runtime.replay.repository.RuntimeReplayRepository;
import com.zc.agent.core.modules.runtime.replay.vo.ReplayDetailResponse;
import com.zc.agent.core.modules.runtime.replay.vo.ReplayPageItemResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replay 管理只读查询服务，不承载 Replay 执行逻辑。 */
@Service
public class ReplayQueryService {

    private static final Set<String> STATUSES = Set.of("pending", "running", "succeeded", "failed");

    private final RuntimeReplayRepository repository;
    private final ObjectMapper objectMapper;

    public ReplayQueryService(RuntimeReplayRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 分页查询 Replay 独立审计实例。 */
    public PageResponse<ReplayPageItemResponse> page(ReplayPageQueryRequest request, SecurityUser securityUser) {
        requireAdministrator(securityUser);
        int pageNum = request.pageNum() == null ? 1 : request.pageNum();
        int pageSize = request.pageSize() == null ? 20 : request.pageSize();
        String replayMode = normalize(request.replayMode());
        String status = normalize(request.status());
        String sourceRunCode = normalize(request.sourceRunCode());
        validateFilters(replayMode, status);
        long total = repository.count(replayMode, status, sourceRunCode);
        List<ReplayPageItemResponse> records = repository.page(
                        replayMode, status, sourceRunCode, (pageNum - 1) * pageSize, pageSize)
                .stream().map(this::toPageItem).toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /** 查询 Replay 不可变输入、Manifest 和独立结果。 */
    public ReplayDetailResponse detail(String replayCode, SecurityUser securityUser) {
        requireAdministrator(securityUser);
        String normalizedCode = normalize(replayCode);
        if (normalizedCode == null) {
            throw new BusinessException("Replay 编码不能为空");
        }
        RuntimeReplayRecord record = repository.findByReplayCode(normalizedCode)
                .orElseThrow(() -> new BusinessException("Replay 不存在: " + normalizedCode));
        return new ReplayDetailResponse(
                record.replayCode(), record.replayTraceId(), record.replayMode(), record.sourceRunCode(),
                record.sourceTaskId(), record.sourceTaskTraceId(), record.userId(), record.status(),
                readJson(record.manifestJson()), readJson(record.inputSnapshotJson()),
                readJson(record.resultSnapshotJson()), record.errorMessage(), record.startedAt(),
                record.finishedAt(), record.createdAt()
        );
    }

    private ReplayPageItemResponse toPageItem(RuntimeReplayRecord record) {
        LocalDateTime durationEnd = record.finishedAt() == null ? LocalDateTime.now() : record.finishedAt();
        Long durationMs = record.startedAt() == null
                ? null
                : Math.max(0L, Duration.between(record.startedAt(), durationEnd).toMillis());
        return new ReplayPageItemResponse(
                record.replayCode(), record.replayTraceId(), record.replayMode(), record.sourceRunCode(),
                record.sourceTaskId(), record.sourceTaskTraceId(), record.userId(), record.status(),
                record.errorMessage(), durationMs, record.startedAt(), record.finishedAt(), record.createdAt()
        );
    }

    private void validateFilters(String replayMode, String status) {
        if (replayMode != null) {
            try {
                ReplayMode.fromCode(replayMode);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("Replay 模式无效: " + replayMode);
            }
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException("Replay 状态无效: " + status);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new BusinessException("Replay 审计快照解析失败");
        }
    }

    private void requireAdministrator(SecurityUser securityUser) {
        boolean administrator = securityUser != null && securityUser.roleCodes() != null
                && securityUser.roleCodes().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role));
        if (!administrator) {
            throw new BusinessException("Replay 管理当前仅允许平台管理员访问");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

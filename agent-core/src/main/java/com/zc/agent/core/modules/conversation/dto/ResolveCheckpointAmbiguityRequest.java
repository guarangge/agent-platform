package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.List;

/**
 * @param action         confirm_success/retry/confirm_compensated
 * @param resultSnapshot 确认成功时用于幂等复用的 Tool 结果快照
 */
public record ResolveCheckpointAmbiguityRequest(
        String action,
        Map<String, Object> resultSnapshot,
        List<@Valid SideEffectResolution> resolutions
) {
    public ResolveCheckpointAmbiguityRequest(String action, Map<String, Object> resultSnapshot) {
        this(action, resultSnapshot, List.of());
    }

    /** @param executionId 副作用执行记录 ID @param action confirm_success/retry/compensate @param resultSnapshot 确认结果 */
    public record SideEffectResolution(Long executionId, @NotBlank String action, Map<String, Object> resultSnapshot) {
    }
}

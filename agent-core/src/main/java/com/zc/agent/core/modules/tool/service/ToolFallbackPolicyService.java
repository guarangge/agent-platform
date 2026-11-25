package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Tool 失败回退策略服务。
 */
@Service
public class ToolFallbackPolicyService {

    public FallbackDecision decide(RegistryAgentTool tool,
                                   ToolExecutionException exception,
                                   int candidateIndex,
                                   int candidateCount) {
        if (candidateIndex < candidateCount - 1 && isSwitchableFailure(exception)) {
            return new FallbackDecision("switch_candidate", "当前 Tool 执行失败，切换到下一个候选实例");
        }
        if (exception.retryable()) {
            return new FallbackDecision("retry_same_tool", "当前 Tool 标记可重试，优先保留原路径");
        }
        if ("timeout".equals(normalize(exception.errorType())) || "network".equals(normalize(exception.errorType()))) {
            return new FallbackDecision("replan", "Tool 网络或超时失败，建议重规划后决定后续路径");
        }
        return new FallbackDecision("fail_fast", "当前 Tool 无可用回退候选，按失败处理");
    }

    private boolean isSwitchableFailure(ToolExecutionException exception) {
        String errorType = normalize(exception.errorType());
        return "network".equals(errorType)
                || "timeout".equals(errorType)
                || "provider_protocol".equals(errorType)
                || "configuration".equals(errorType)
                || "execution".equals(errorType);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record FallbackDecision(
            String action,
            String reason
    ) {
    }
}

package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.config.AgentLoopKernelProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 基于总开关、Agent 白名单和稳定百分比的默认内核路由策略。 */
@Service
public class DefaultExecutionModeStrategy implements ExecutionModeStrategy {

    private final AgentLoopKernelProperties properties;

    public DefaultExecutionModeStrategy(AgentLoopKernelProperties properties) {
        this.properties = properties;
    }

    @Override
    public Selection select(AgentLoopExecutionRequest request) {
        if (!properties.isEnabled()) {
            return new Selection(false, "kernel_disabled", null);
        }
        RuntimeContextProvider.ResolvedRuntimeContext runtimeContext = request.runtimeContext();
        Long projectId = runtimeContext.projectId();
        Long agentId = runtimeContext.agentId();
        if (!properties.getProjectIds().isEmpty()
                && (projectId == null || !properties.getProjectIds().contains(projectId))) {
            return new Selection(false, "project_not_allowlisted", null);
        }
        if (!properties.getAgentIds().isEmpty()) {
            return agentId != null && properties.getAgentIds().contains(agentId)
                    ? new Selection(true, "agent_allowlisted", null)
                    : new Selection(false, "agent_not_allowlisted", null);
        }
        int rolloutPercent = Math.max(0, Math.min(100, properties.getRolloutPercent()));
        if (rolloutPercent >= 100) return new Selection(true, "full_rollout", null);
        if (rolloutPercent <= 0 || agentId == null) {
            return new Selection(false, "rollout_excluded", null);
        }
        int bucket = stableBucket(agentId, projectId);
        return bucket < rolloutPercent
                ? new Selection(true, "percentage_rollout", bucket)
                : new Selection(false, "rollout_excluded", bucket);
    }

    private int stableBucket(Long agentId, Long projectId) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(("agent-loop-kernel\n" + agentId + "\n" + (projectId == null ? 0L : projectId))
                            .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
        int value = ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16)
                | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        return Math.floorMod(value, 100);
    }
}

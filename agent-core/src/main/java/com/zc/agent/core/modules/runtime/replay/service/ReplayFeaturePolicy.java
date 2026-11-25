package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.ReplayFeatureProperties;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 在后端 Replay 执行入口执行模式开关、Agent/Project 白名单和稳定百分比灰度。 */
@Service
public class ReplayFeaturePolicy {

    private final ReplayFeatureProperties properties;

    public ReplayFeaturePolicy(ReplayFeatureProperties properties) {
        this.properties = properties;
    }

    /** 校验当前 Replay 是否命中灰度；查询历史 Replay 不受此策略影响。 */
    public Decision requireEnabled(ReplayMode mode, Long agentId, SecurityUser securityUser) {
        Decision decision = decide(mode, agentId, securityUser == null ? null : securityUser.projectId());
        if (!decision.enabled()) {
            throw new BusinessException("Replay 当前未对该 Agent / Project 开放: " + decision.reason());
        }
        return decision;
    }

    /** 输出可测试的确定性灰度决策。 */
    public Decision decide(ReplayMode mode, Long agentId, Long projectId) {
        if (mode == null) return disabled(null, "mode_missing", null);
        if (!properties.isEnabled()) return disabled(mode, "master_disabled", null);
        if (mode == ReplayMode.EXECUTION_REPLAY) {
            return disabled(mode, "execution_replay_not_available", null);
        }
        if (mode == ReplayMode.PROMPT_REPLAY && !properties.isPromptEnabled()) {
            return disabled(mode, "prompt_replay_disabled", null);
        }
        if (mode == ReplayMode.SIMULATION_REPLAY && !properties.isSimulationEnabled()) {
            return disabled(mode, "simulation_replay_disabled", null);
        }
        if (!properties.getProjectIds().isEmpty()
                && (projectId == null || !properties.getProjectIds().contains(projectId))) {
            return disabled(mode, "project_not_allowlisted", null);
        }
        if (!properties.getAgentIds().isEmpty()) {
            return agentId != null && properties.getAgentIds().contains(agentId)
                    ? new Decision(true, mode.code(), "agent_allowlisted", null)
                    : disabled(mode, "agent_not_allowlisted", null);
        }
        int rolloutPercent = Math.max(0, Math.min(100, properties.getRolloutPercent()));
        if (rolloutPercent >= 100) return new Decision(true, mode.code(), "full_rollout", null);
        if (rolloutPercent <= 0 || agentId == null) {
            return disabled(mode, "rollout_excluded", agentId == null ? null : stableBucket(mode, agentId, projectId));
        }
        int bucket = stableBucket(mode, agentId, projectId);
        return bucket < rolloutPercent
                ? new Decision(true, mode.code(), "percentage_rollout", bucket)
                : disabled(mode, "rollout_excluded", bucket);
    }

    private int stableBucket(ReplayMode mode, Long agentId, Long projectId) {
        String key = mode.code() + "\n" + agentId + "\n" + (projectId == null ? 0L : projectId);
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
        int value = ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16)
                | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        return Math.floorMod(value, 100);
    }

    private Decision disabled(ReplayMode mode, String reason, Integer bucket) {
        return new Decision(false, mode == null ? null : mode.code(), reason, bucket);
    }

    /**
     * @param enabled 是否允许执行
     * @param replayMode Replay 模式
     * @param reason 稳定决策原因
     * @param rolloutBucket 稳定分桶；白名单或前置关闭时为空
     */
    public record Decision(
            boolean enabled,
            String replayMode,
            String reason,
            Integer rolloutBucket
    ) {
    }
}

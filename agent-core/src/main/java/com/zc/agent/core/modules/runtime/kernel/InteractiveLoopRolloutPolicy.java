package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.config.InteractiveLoopProperties;
import com.zc.agent.core.modules.runtime.kernel.interactive.InteractiveLoopMetrics;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Interactive Loop 独立开关、白名单和稳定百分比灰度策略。 */
@Service
public class InteractiveLoopRolloutPolicy {

    private final InteractiveLoopProperties properties;
    private final InteractiveLoopMetrics metrics;

    public InteractiveLoopRolloutPolicy(InteractiveLoopProperties properties,
                                        InteractiveLoopMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    /** 返回当前 Agent/Project 是否进入动态循环。 */
    public Selection select(AgentLoopExecutionRequest request) {
        Selection selection = evaluate(request);
        metrics.recordRoute(selection.enabled(), selection.reason());
        return selection;
    }

    private Selection evaluate(AgentLoopExecutionRequest request) {
        if (properties.isKillSwitch()) {
            return new Selection(false, "interactive_loop_kill_switched", null);
        }
        if (!properties.isEnabled()) {
            return new Selection(false, "interactive_loop_disabled", null);
        }
        Long projectId = request.runtimeContext().projectId();
        Long agentId = request.runtimeContext().agentId();
        if (!properties.getProjectIds().isEmpty()
                && (projectId == null || !properties.getProjectIds().contains(projectId))) {
            return new Selection(false, "interactive_loop_project_not_allowlisted", null);
        }
        if (!properties.getAgentIds().isEmpty()) {
            return agentId != null && properties.getAgentIds().contains(agentId)
                    ? new Selection(true, "interactive_loop_agent_allowlisted", null)
                    : new Selection(false, "interactive_loop_agent_not_allowlisted", null);
        }
        int percent = Math.max(0, Math.min(100, properties.getRolloutPercent()));
        if (percent >= 100) return new Selection(true, "interactive_loop_full_rollout", null);
        if (percent <= 0 || agentId == null) {
            return new Selection(false, "interactive_loop_rollout_excluded", null);
        }
        int bucket = stableBucket(agentId, projectId);
        return bucket < percent
                ? new Selection(true, "interactive_loop_percentage_rollout", bucket)
                : new Selection(false, "interactive_loop_rollout_excluded", bucket);
    }

    private int stableBucket(Long agentId, Long projectId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("interactive-loop\n" + agentId + "\n" + (projectId == null ? 0L : projectId))
                            .getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
            return Math.floorMod(value, 100);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    /** @param enabled 是否允许进入 Interactive Loop @param reason 灰度原因 @param rolloutBucket 稳定分桶 */
    public record Selection(boolean enabled, String reason, Integer rolloutBucket) {
    }
}

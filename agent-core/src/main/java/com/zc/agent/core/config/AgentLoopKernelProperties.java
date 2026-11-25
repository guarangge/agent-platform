package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** AgentLoopKernel Adapter 灰度配置。 */
@ConfigurationProperties(prefix = "core.runtime.kernel")
public class AgentLoopKernelProperties {

    private boolean enabled = false;
    private int rolloutPercent = 0;
    private List<Long> agentIds = new ArrayList<>();
    private List<Long> projectIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRolloutPercent() {
        return rolloutPercent;
    }

    public void setRolloutPercent(int rolloutPercent) {
        this.rolloutPercent = rolloutPercent;
    }

    public List<Long> getAgentIds() {
        return agentIds;
    }

    public void setAgentIds(List<Long> agentIds) {
        this.agentIds = agentIds == null ? new ArrayList<>() : new ArrayList<>(agentIds);
    }

    public List<Long> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<Long> projectIds) {
        this.projectIds = projectIds == null ? new ArrayList<>() : new ArrayList<>(projectIds);
    }
}

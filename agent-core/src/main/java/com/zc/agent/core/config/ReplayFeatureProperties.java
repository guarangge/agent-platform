package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Replay 执行入口 Feature Flag 和灰度配置。 */
@ConfigurationProperties(prefix = "core.runtime.replay")
public class ReplayFeatureProperties {

    private boolean enabled = true;
    private boolean promptEnabled = true;
    private boolean simulationEnabled = true;
    private int rolloutPercent = 100;
    private List<Long> agentIds = new ArrayList<>();
    private List<Long> projectIds = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPromptEnabled() {
        return promptEnabled;
    }

    public void setPromptEnabled(boolean promptEnabled) {
        this.promptEnabled = promptEnabled;
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    public void setSimulationEnabled(boolean simulationEnabled) {
        this.simulationEnabled = simulationEnabled;
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

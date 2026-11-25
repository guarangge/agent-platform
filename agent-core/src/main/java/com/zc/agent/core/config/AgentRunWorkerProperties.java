package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 异步运行 Worker 配置。
 */
@ConfigurationProperties(prefix = "core.runtime.worker")
public class AgentRunWorkerProperties {

    private int concurrency = 4;
    private long pollDelayMs = 1000L;
    private int leaseSeconds = 900;
    private int maxAttempts = 2;
    private int claimBatchSize = 4;
    private int queueCapacity = 1000;
    private int perUserQueueCapacity = 100;
    private int globalConcurrency = 32;
    private int defaultPriority = 50;
    private int maxPriority = 100;
    private int agingSeconds = 60;
    private int retryInitialSeconds = 2;
    private int retryMaxSeconds = 300;
    private int shutdownTimeoutSeconds = 60;
    private String groupCode = "default";
    private String groupName = "默认 Worker 组";
    private String instanceAddress = "local";
    private List<String> capabilities = new ArrayList<>();
    private int nodeOfflineSeconds = 90;

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getClaimBatchSize() {
        return claimBatchSize;
    }

    public void setClaimBatchSize(int claimBatchSize) {
        this.claimBatchSize = claimBatchSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getPerUserQueueCapacity() {
        return perUserQueueCapacity;
    }

    public void setPerUserQueueCapacity(int perUserQueueCapacity) {
        this.perUserQueueCapacity = perUserQueueCapacity;
    }

    public int getGlobalConcurrency() {
        return globalConcurrency;
    }

    public void setGlobalConcurrency(int globalConcurrency) {
        this.globalConcurrency = globalConcurrency;
    }

    public int getDefaultPriority() {
        return defaultPriority;
    }

    public void setDefaultPriority(int defaultPriority) {
        this.defaultPriority = defaultPriority;
    }

    public int getMaxPriority() {
        return maxPriority;
    }

    public void setMaxPriority(int maxPriority) {
        this.maxPriority = maxPriority;
    }

    public int getAgingSeconds() {
        return agingSeconds;
    }

    public void setAgingSeconds(int agingSeconds) {
        this.agingSeconds = agingSeconds;
    }

    public int getRetryInitialSeconds() {
        return retryInitialSeconds;
    }

    public void setRetryInitialSeconds(int retryInitialSeconds) {
        this.retryInitialSeconds = retryInitialSeconds;
    }

    public int getRetryMaxSeconds() {
        return retryMaxSeconds;
    }

    public void setRetryMaxSeconds(int retryMaxSeconds) {
        this.retryMaxSeconds = retryMaxSeconds;
    }

    public int getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }
    public String getGroupCode(){return groupCode;} public void setGroupCode(String value){this.groupCode=value;}
    public String getGroupName(){return groupName;} public void setGroupName(String value){this.groupName=value;}
    public String getInstanceAddress(){return instanceAddress;} public void setInstanceAddress(String value){this.instanceAddress=value;}
    public List<String> getCapabilities(){return capabilities;} public void setCapabilities(List<String> value){this.capabilities=value==null?new ArrayList<>():value;}
    public int getNodeOfflineSeconds(){return nodeOfflineSeconds;} public void setNodeOfflineSeconds(int value){this.nodeOfflineSeconds=value;}
}

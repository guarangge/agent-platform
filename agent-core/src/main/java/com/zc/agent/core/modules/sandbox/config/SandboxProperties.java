package com.zc.agent.core.modules.sandbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Sandbox 运行时基础配置。
 */
@Component
@ConfigurationProperties(prefix = "core.sandbox")
public class SandboxProperties {

    private String dockerHost = "unix:///var/run/docker.sock";
    private String nodeId = "local";
    private Path workspaceRoot = Path.of(System.getProperty("java.io.tmpdir"), "agent-sandbox");
    private String hostWorkspaceRoot;
    private String defaultImage = "agent-sandbox-runtime:1.0.0";
    private long maxInputBytes = 10 * 1024 * 1024L;
    private long maxArtifactBytes = 10 * 1024 * 1024L;
    private boolean cleanupOnStartup = true;

    public String getDockerHost() { return dockerHost; }
    public void setDockerHost(String dockerHost) { this.dockerHost = dockerHost; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public Path getWorkspaceRoot() { return workspaceRoot; }
    public void setWorkspaceRoot(Path workspaceRoot) { this.workspaceRoot = workspaceRoot; }
    public String getHostWorkspaceRoot() { return hostWorkspaceRoot; }
    public void setHostWorkspaceRoot(String hostWorkspaceRoot) { this.hostWorkspaceRoot = hostWorkspaceRoot; }
    public String getDefaultImage() { return defaultImage; }
    public void setDefaultImage(String defaultImage) { this.defaultImage = defaultImage; }
    public long getMaxInputBytes() { return maxInputBytes; }
    public void setMaxInputBytes(long maxInputBytes) { this.maxInputBytes = maxInputBytes; }
    public long getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(long maxArtifactBytes) { this.maxArtifactBytes = maxArtifactBytes; }
    public boolean isCleanupOnStartup() { return cleanupOnStartup; }
    public void setCleanupOnStartup(boolean cleanupOnStartup) { this.cleanupOnStartup = cleanupOnStartup; }
}

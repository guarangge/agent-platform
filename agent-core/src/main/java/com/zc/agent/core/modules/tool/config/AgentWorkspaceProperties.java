package com.zc.agent.core.modules.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Agent 会话持久工作区配置。
 */
@Component
@ConfigurationProperties(prefix = "core.agent-workspace")
public class AgentWorkspaceProperties {

    private Path root = Path.of(System.getProperty("java.io.tmpdir"), "agent-workspace");
    private long maxFileBytes = 1024 * 1024L;
    private long maxWorkspaceBytes = 20 * 1024 * 1024L;

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public long getMaxWorkspaceBytes() {
        return maxWorkspaceBytes;
    }

    public void setMaxWorkspaceBytes(long maxWorkspaceBytes) {
        this.maxWorkspaceBytes = maxWorkspaceBytes;
    }
}

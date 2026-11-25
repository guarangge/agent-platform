package com.zc.agent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话上下文窗口配置。
 */
@ConfigurationProperties(prefix = "core.context")
public class ConversationContextProperties {

    private int recentConversationTurns = 4;

    private int recentConversationMessageMaxLength = 300;

    public int getRecentConversationTurns() {
        return recentConversationTurns;
    }

    public void setRecentConversationTurns(int recentConversationTurns) {
        this.recentConversationTurns = recentConversationTurns;
    }

    public int getRecentConversationMessageMaxLength() {
        return recentConversationMessageMaxLength;
    }

    public void setRecentConversationMessageMaxLength(int recentConversationMessageMaxLength) {
        this.recentConversationMessageMaxLength = recentConversationMessageMaxLength;
    }
}

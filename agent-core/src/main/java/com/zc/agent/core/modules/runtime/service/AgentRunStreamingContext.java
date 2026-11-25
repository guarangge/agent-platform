package com.zc.agent.core.modules.runtime.service;

/**
 * Worker 线程内的 Run 流式执行边界。
 * 只在编排器明确标记的最终用户回复阶段允许模型 delta 外发。
 */
public final class AgentRunStreamingContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final Scope NOOP_SCOPE = () -> { };

    private AgentRunStreamingContext() {
    }

    /** 绑定当前 Worker 正在执行的持久化 Run。 */
    public static void bind(String runCode) {
        CURRENT.set(new State(runCode));
    }

    /** 临时绑定 Run，并在关闭时恢复调用线程原有上下文。 */
    public static Scope openRunScope(String runCode) {
        State previous = CURRENT.get();
        CURRENT.set(new State(runCode));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /** 打开最终回复流式边界。 */
    public static Scope openFinalResponseScope(boolean enabled) {
        State state = CURRENT.get();
        if (!enabled || state == null) return NOOP_SCOPE;
        state.finalResponseDepth++;
        return () -> state.finalResponseDepth = Math.max(0, state.finalResponseDepth - 1);
    }

    /** 记录当前会话是否启用响应流，供进度事件和模型 delta 共用。 */
    public static void configureResponseStreaming(boolean enabled) {
        State state = CURRENT.get();
        if (state != null) state.responseStreamingEnabled = enabled;
    }

    public static boolean isResponseStreamingEnabled() {
        State state = CURRENT.get();
        return state != null && state.responseStreamingEnabled;
    }

    public static boolean isFinalResponseScope() {
        State state = CURRENT.get();
        return state != null && state.finalResponseDepth > 0;
    }

    public static String currentRunCode() {
        State state = CURRENT.get();
        return state == null ? null : state.runCode;
    }

    public static void clear() {
        CURRENT.remove();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final class State {
        private final String runCode;
        private int finalResponseDepth;
        private boolean responseStreamingEnabled;

        private State(String runCode) {
            this.runCode = runCode;
        }
    }
}

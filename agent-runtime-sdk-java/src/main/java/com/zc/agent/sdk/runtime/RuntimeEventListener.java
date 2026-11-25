package com.zc.agent.sdk.runtime;

/** Runtime Event 回调；按需覆盖感兴趣的方法。 */
public interface RuntimeEventListener {

    default void onEvent(RuntimeEventEnvelope event) {
    }

    default void onStatus(String status, Long taskId) {
    }

    default void onProgress(RuntimeEventProgress progress) {
    }

    default void onDelta(String content) {
    }

    default void onReset() {
    }

    default void onApprovalRequested(RuntimeEventEnvelope event) {
    }

    default void onArtifactPublished(RuntimeEventEnvelope event) {
    }
}

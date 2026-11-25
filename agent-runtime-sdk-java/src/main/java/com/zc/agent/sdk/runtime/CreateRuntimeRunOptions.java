package com.zc.agent.sdk.runtime;

/**
 * 创建 Run 的幂等键与优先级选项。
 *
 * @param idempotencyKey 幂等键，为空时由 SDK 自动生成
 * @param priority 运行优先级，范围 0 到 100
 */
public record CreateRuntimeRunOptions(
        String idempotencyKey,
        Integer priority
) {
    public static CreateRuntimeRunOptions defaults() {
        return new CreateRuntimeRunOptions(null, null);
    }
}

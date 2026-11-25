package com.zc.agent.sdk.runtime;

/**
 * Runtime Event 消费至等待态或终态后的结果。
 *
 * @param status Run 状态
 * @param taskId Task ID
 * @param errorMessage 错误信息
 * @param lastSequenceNo 最后消费序号
 */
public record RuntimeEventStreamResult(
        String status,
        Long taskId,
        String errorMessage,
        long lastSequenceNo
) {
}

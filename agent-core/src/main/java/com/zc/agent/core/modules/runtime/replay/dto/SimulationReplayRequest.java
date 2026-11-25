package com.zc.agent.core.modules.runtime.replay.dto;

import jakarta.validation.constraints.Min;

/**
 * @param fromSequence 来源事件起始序号，为空时从第一条事件开始
 * @param toSequence 来源事件结束序号，为空时冻结到请求时最后一条事件
 */
public record SimulationReplayRequest(
        @Min(value = 1, message = "fromSequence 必须大于0") Long fromSequence,
        @Min(value = 1, message = "toSequence 必须大于0") Long toSequence
) {
}

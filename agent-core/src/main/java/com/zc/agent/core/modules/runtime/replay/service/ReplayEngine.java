package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按 Replay Mode 路由只读执行处理器。 */
@Service
public class ReplayEngine {

    private final Map<ReplayMode, ReplayModeHandler> handlers;

    public ReplayEngine(List<ReplayModeHandler> handlers) {
        EnumMap<ReplayMode, ReplayModeHandler> resolved = new EnumMap<>(ReplayMode.class);
        for (ReplayModeHandler handler : handlers) {
            if (resolved.putIfAbsent(handler.mode(), handler) != null) {
                throw new IllegalStateException("Replay Mode 存在重复处理器: " + handler.mode().code());
            }
        }
        this.handlers = Map.copyOf(resolved);
    }

    /** 执行已开放的 Replay Mode。 */
    public ReplayExecutionResult execute(ReplayExecutionInput input) {
        if (input == null || input.replayMode() == null || !input.replayMode().enabled()) {
            throw new BusinessException("Replay Mode 尚未开放");
        }
        ReplayModeHandler handler = handlers.get(input.replayMode());
        if (handler == null) {
            throw new BusinessException("Replay Mode 暂无可用处理器: " + input.replayMode().code());
        }
        return handler.execute(input);
    }
}

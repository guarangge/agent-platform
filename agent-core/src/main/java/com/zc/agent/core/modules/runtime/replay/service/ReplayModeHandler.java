package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;

/** Replay 模式处理器扩展点。 */
public interface ReplayModeHandler {

    /** 返回处理器支持的 Replay 模式。 */
    ReplayMode mode();

    /** 使用冻结输入执行 Replay，不得修改来源执行数据。 */
    ReplayExecutionResult execute(ReplayExecutionInput input);
}

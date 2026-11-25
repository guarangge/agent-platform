package com.zc.agent.core.modules.runtime.kernel.interactive;

import com.zc.agent.core.modules.conversation.repository.AgentRunQueueRepository;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import org.springframework.stereotype.Service;

/** 为 Interactive Loop 提供线程中断与持久化 Run 取消标记的统一检查。 */
@Service
public class InteractiveLoopCancellationSignal {

    private final AgentRunQueueRepository runRepository;

    public InteractiveLoopCancellationSignal(AgentRunQueueRepository runRepository) {
        this.runRepository = runRepository;
    }

    /** 同步执行没有 Run 上下文时只检查线程中断；异步执行同时检查数据库取消标记。 */
    public boolean isCancellationRequested() {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        String runCode = AgentRunStreamingContext.currentRunCode();
        return runCode != null && !runCode.isBlank() && runRepository.isCancellationRequested(runCode);
    }
}

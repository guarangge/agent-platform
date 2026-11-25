package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.modules.conversation.model.AgentRunStreamEventRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.runtime.service.AgentRunStreamingContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventType;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 发布和查询跨 Worker 实例可见的原生模型流事件。 */
@Service
public class AgentRunStreamEventService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunStreamEventService.class);
    private final AgentRunStreamEventRepository repository;
    private final RuntimeEventPublisher runtimeEventPublisher;

    public AgentRunStreamEventService(AgentRunStreamEventRepository repository,
                                      RuntimeEventPublisher runtimeEventPublisher) {
        this.repository = repository;
        this.runtimeEventPublisher = runtimeEventPublisher;
    }

    /** 新一轮最终回复开始时通知客户端清空旧的部分输出。 */
    public void publishReset(String runCode) {
        runtimeEventPublisher.publish(runCode, RuntimeEventType.MESSAGE_RESET, "", Map.of());
    }

    /** 模型正文增量到达后立即持久化。 */
    public void publishDelta(String runCode, String content) {
        if (content == null || content.isEmpty()) return;
        runtimeEventPublisher.publish(runCode, RuntimeEventType.MESSAGE_DELTA, content, Map.of());
    }

    /** 发布不包含模型思维链的安全执行阶段，观测失败不能中断主任务。 */
    public void publishProgress(String stage, String message, Map<String, Object> metadata) {
        String runCode = AgentRunStreamingContext.currentRunCode();
        if (!AgentRunStreamingContext.isResponseStreamingEnabled()
                || runCode == null || runCode.isBlank()
                || message == null || message.isBlank()) {
            return;
        }
        LinkedHashMap<String, Object> eventMetadata = new LinkedHashMap<>();
        eventMetadata.put("stage", stage == null ? "execution" : stage);
        if (metadata != null) eventMetadata.putAll(metadata);
        try {
            runtimeEventPublisher.publish(runCode, RuntimeEventType.RUN_PROGRESS, message, eventMetadata);
        } catch (RuntimeException exception) {
            log.warn("Run 进度事件写入失败, runCode={}, stage={}, message={}", runCode, stage, exception.getMessage());
        }
    }

    /** 查询指定游标后的增量事件。 */
    public List<AgentRunStreamEventRecord> findAfter(String runCode, long sequenceNo) {
        return repository.findAfter(runCode, sequenceNo, 200);
    }
}

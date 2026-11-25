package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.conversation.service.AgentRunStreamEventService;
import com.zc.agent.core.modules.runtime.protocol.service.TaskStepLifecycleEventService;
import org.springframework.stereotype.Service;

/** 将现有进度、Step 生命周期和 Approval 事件服务适配为 RuntimeEventSink。 */
@Service
public class LegacyRuntimeEventSinkAdapter implements RuntimeEventSink {

    public static final String SINK_CODE = "legacy_runtime_event_sink_v1";

    private final AgentRunStreamEventService agentRunStreamEventService;
    private final TaskStepLifecycleEventService taskStepLifecycleEventService;

    public LegacyRuntimeEventSinkAdapter(AgentRunStreamEventService agentRunStreamEventService,
                                         TaskStepLifecycleEventService taskStepLifecycleEventService) {
        this.agentRunStreamEventService = agentRunStreamEventService;
        this.taskStepLifecycleEventService = taskStepLifecycleEventService;
    }

    @Override
    public String sinkCode() {
        return SINK_CODE;
    }

    @Override
    public void progress(ProgressEvent event) {
        agentRunStreamEventService.publishProgress(event.stage(), event.message(), event.metadata());
    }

    @Override
    public Long stepStarted(StepStartedEvent event) {
        return taskStepLifecycleEventService.start(new TaskStepLifecycleEventService.StartStepCommand(
                event.taskId(),
                event.persistedStepNo(),
                event.stepId(),
                event.stepNo(),
                event.stepCount(),
                event.stepType(),
                event.stepName(),
                event.inputSnapshot(),
                event.operatorId()
        ));
    }

    @Override
    public void stepTransitioned(StepTransitionedEvent event) {
        taskStepLifecycleEventService.transition(new TaskStepLifecycleEventService.TransitionStepCommand(
                event.taskId(),
                event.taskStepId(),
                event.stepId(),
                event.stepNo(),
                event.stepCount(),
                event.stepType(),
                event.stepName(),
                event.status(),
                event.outputSnapshot(),
                event.errorMessage(),
                event.failureType(),
                event.skipped(),
                event.operatorId()
        ));
    }

}

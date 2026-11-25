package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用录制事件和步骤快照进行无副作用结果注入。 */
@Component
public class SimulationReplayModeHandler implements ReplayModeHandler {

    @Override
    public ReplayMode mode() {
        return ReplayMode.SIMULATION_REPLAY;
    }

    /** 只执行内存投影，不调用 LLM、Tool Provider、Workflow 或 Sandbox。 */
    @Override
    public ReplayExecutionResult execute(ReplayExecutionInput input) {
        if (input == null || input.replayMode() != ReplayMode.SIMULATION_REPLAY) {
            throw new BusinessException("Simulation Replay 输入模式不匹配");
        }
        if (input.events() == null || input.events().isEmpty()) {
            throw new BusinessException("Simulation Replay 缺少来源 Runtime Event");
        }
        Map<Long, RecordedStepResult> recordedResults = indexResults(input.recordedStepResults());
        List<Map<String, Object>> observations = new ArrayList<>();
        StringBuilder assembledMessage = new StringBuilder();
        String lastControlEventType = "";
        for (RuntimeEventEnvelope event : input.events()) {
            String eventType = event.eventType();
            if ("message.reset".equals(eventType)) {
                assembledMessage.setLength(0);
            } else if ("message.delta".equals(eventType)) {
                assembledMessage.append(text(event.payload().get("content")));
            }
            if ("step.completed".equals(eventType)) {
                Long taskStepId = longValue(event.payload().get("taskStepId"));
                RecordedStepResult recordedResult = recordedResults.remove(taskStepId);
                if (recordedResult == null) {
                    throw new BusinessException("Simulation Replay 缺少步骤结果快照: taskStepId=" + taskStepId);
                }
                observations.add(stepObservation(event, recordedResult));
            } else if (SimulationReplayFactContract.isRecordedFact(eventType)) {
                observations.add(eventObservation(event));
            }
            if (eventType != null && (eventType.startsWith("run.") || eventType.startsWith("approval."))) {
                lastControlEventType = eventType;
            }
        }
        if (!recordedResults.isEmpty()) {
            throw new BusinessException("Simulation Replay 存在未关联来源事件的步骤快照");
        }
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("executionMode", "recorded_result_injection");
        output.put("resultSource", "runtime_events_and_task_step_snapshots");
        output.put("assembledMessage", assembledMessage.toString());
        output.put("lastControlEventType", lastControlEventType);
        output.put("sourceEventCount", input.events().size());
        output.put("injectedObservationCount", observations.size());
        output.put("providerInvocationCount", 0);
        output.put("llmInvocationCount", 0);
        output.put("networkWriteCount", 0);
        output.put("fileWriteCount", 0);
        output.put("databaseBusinessWriteCount", 0);
        return new ReplayExecutionResult(Map.copyOf(output), List.copyOf(observations));
    }

    private Map<Long, RecordedStepResult> indexResults(List<RecordedStepResult> results) {
        Map<Long, RecordedStepResult> indexed = new HashMap<>();
        if (results == null) {
            return indexed;
        }
        for (RecordedStepResult result : results) {
            if (result == null || result.taskStepId() == null || indexed.putIfAbsent(result.taskStepId(), result) != null) {
                throw new BusinessException("Simulation Replay 步骤快照标识无效或重复");
            }
        }
        return indexed;
    }

    private Map<String, Object> stepObservation(RuntimeEventEnvelope event, RecordedStepResult result) {
        LinkedHashMap<String, Object> observation = baseObservation(event);
        observation.put("observationType", result.resultType() + "_result");
        observation.put("source", "recorded_task_step_snapshot");
        observation.put("taskStepId", result.taskStepId());
        observation.put("stepNo", result.stepNo());
        observation.put("stepType", result.stepType());
        observation.put("stepName", result.stepName());
        observation.put("outputSha256", result.outputSha256());
        observation.put("outputSnapshot", result.outputSnapshot());
        return Map.copyOf(observation);
    }

    private Map<String, Object> eventObservation(RuntimeEventEnvelope event) {
        LinkedHashMap<String, Object> observation = baseObservation(event);
        observation.put("observationType", SimulationReplayFactContract.observationType(event.eventType()));
        observation.put("source", "recorded_runtime_event");
        observation.put("payload", event.payload());
        return Map.copyOf(observation);
    }

    private LinkedHashMap<String, Object> baseObservation(RuntimeEventEnvelope event) {
        LinkedHashMap<String, Object> observation = new LinkedHashMap<>();
        observation.put("sourceEventId", event.eventId());
        observation.put("sourceSequenceNo", event.sequenceNo());
        observation.put("sourceEventType", event.eventType());
        return observation;
    }

    private Long longValue(Object value) {
        return SimulationReplayFactContract.longValue(value);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

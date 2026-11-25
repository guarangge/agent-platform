package com.zc.agent.core.modules.runtime.replay.service;

import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;

import java.util.Map;

/** Simulation Replay 来源事实与注入观察的稳定对齐规则。 */
final class SimulationReplayFactContract {

    private SimulationReplayFactContract() {
    }

    static boolean isRecordedFact(String eventType) {
        return "tool.call.completed".equals(eventType)
                || "tool.call.failed".equals(eventType)
                || "approval.requested".equals(eventType)
                || "approval.resolved".equals(eventType)
                || "approval.revoked".equals(eventType)
                || "artifact.created".equals(eventType)
                || "artifact.published".equals(eventType)
                || "input.attachment.resolved".equals(eventType)
                || "input.attachment.analyzed".equals(eventType);
    }

    static String observationType(String eventType) {
        if (eventType != null && eventType.startsWith("tool.call.")) return "tool_lifecycle_fact";
        if (eventType != null && eventType.startsWith("approval.")) return "approval_result";
        if (eventType != null && eventType.startsWith("artifact.")) return "artifact_result";
        if (eventType != null && eventType.startsWith("input.attachment.")) return "attachment_input_fact";
        return "runtime_event";
    }

    static String sourceFactKey(RuntimeEventEnvelope event) {
        if ("step.completed".equals(event.eventType())) {
            return stepFactKey(longValue(event.payload().get("taskStepId")));
        }
        return eventFactKey(event.sequenceNo(), event.eventType());
    }

    static String observationFactKey(Map<String, Object> observation) {
        if ("recorded_task_step_snapshot".equals(text(observation.get("source")))) {
            return stepFactKey(longValue(observation.get("taskStepId")));
        }
        return eventFactKey(longValue(observation.get("sourceSequenceNo")),
                text(observation.get("sourceEventType")));
    }

    static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stepFactKey(Long taskStepId) {
        return "step:" + (taskStepId == null ? "unknown" : taskStepId);
    }

    private static String eventFactKey(Long sequenceNo, String eventType) {
        return "event:" + (sequenceNo == null ? "unknown" : sequenceNo) + ":" + text(eventType);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

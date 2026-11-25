package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.model.TaskRecord;
import com.zc.agent.core.modules.conversation.model.TaskStepRecord;
import com.zc.agent.core.modules.conversation.repository.AgentRunStreamEventRepository;
import com.zc.agent.core.modules.conversation.repository.ConversationRepository;
import com.zc.agent.core.modules.conversation.service.AgentRunService;
import com.zc.agent.core.modules.conversation.vo.AgentRunResponse;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.protocol.service.RuntimeEventQueryService;
import com.zc.agent.core.modules.runtime.replay.dto.SimulationReplayRequest;
import com.zc.agent.core.modules.runtime.replay.model.ArtifactVerificationReport;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayEventDiff;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionInput;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayInstance;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.vo.SimulationReplayResponse;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.security.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 构建冻结输入并执行无副作用 Simulation Replay。 */
@Service
public class SimulationReplayService {

    private static final Logger log = LoggerFactory.getLogger(SimulationReplayService.class);
    private static final int EVENT_BATCH_SIZE = 500;
    private static final int MAX_EVENT_COUNT = 5000;

    private final AgentRunService agentRunService;
    private final AgentRunStreamEventRepository eventRepository;
    private final RuntimeEventQueryService eventQueryService;
    private final ConversationRepository conversationRepository;
    private final ReplayInstanceService replayInstanceService;
    private final ReplayEngine replayEngine;
    private final ReplayEventDiffService replayEventDiffService;
    private final ArtifactReplayVerificationService artifactReplayVerificationService;
    private final ReplayFeaturePolicy replayFeaturePolicy;
    private final TaskTraceService taskTraceService;
    private final ObjectMapper objectMapper;

    public SimulationReplayService(AgentRunService agentRunService,
                                   AgentRunStreamEventRepository eventRepository,
                                   RuntimeEventQueryService eventQueryService,
                                   ConversationRepository conversationRepository,
                                   ReplayInstanceService replayInstanceService,
                                   ReplayEngine replayEngine,
                                   ReplayEventDiffService replayEventDiffService,
                                   ArtifactReplayVerificationService artifactReplayVerificationService,
                                   ReplayFeaturePolicy replayFeaturePolicy,
                                   TaskTraceService taskTraceService,
                                   ObjectMapper objectMapper) {
        this.agentRunService = agentRunService;
        this.eventRepository = eventRepository;
        this.eventQueryService = eventQueryService;
        this.conversationRepository = conversationRepository;
        this.replayInstanceService = replayInstanceService;
        this.replayEngine = replayEngine;
        this.replayEventDiffService = replayEventDiffService;
        this.artifactReplayVerificationService = artifactReplayVerificationService;
        this.replayFeaturePolicy = replayFeaturePolicy;
        this.taskTraceService = taskTraceService;
        this.objectMapper = objectMapper;
    }

    /** 冻结来源事件和步骤结果后进行纯内存 Replay。 */
    public SimulationReplayResponse replay(String runCode,
                                           SimulationReplayRequest request,
                                           SecurityUser securityUser) {
        requireAdministrator(securityUser);
        AgentRunResponse sourceRun = agentRunService.get(runCode, securityUser);
        ReplayFeaturePolicy.Decision featureDecision = replayFeaturePolicy.requireEnabled(
                ReplayMode.SIMULATION_REPLAY, sourceRun.agentId(), securityUser);
        if (sourceRun.taskId() == null) {
            throw new BusinessException("原 Run 尚未关联 Task，无法执行 Simulation Replay");
        }
        TaskRecord sourceTask = conversationRepository.findTaskById(sourceRun.taskId())
                .orElseThrow(() -> new BusinessException("原 Task 不存在，无法构建 Simulation Replay"));
        EventRange range = resolveRange(runCode, request);
        List<RuntimeEventEnvelope> events = loadFrozenEvents(runCode, range, securityUser);
        List<RecordedStepResult> recordedResults = loadRecordedResults(sourceRun.taskId(), events);
        ArtifactReplayVerificationService.Capture artifactCapture = artifactReplayVerificationService.capture(
                sourceRun.taskId(), events, recordedResults);
        ReplayInstance instance = replayInstanceService.startSimulationReplay(
                new ReplayInstanceService.StartSimulationReplayCommand(
                        runCode, sourceRun.taskId(), sourceTask.traceId(), sourceRun.agentId(),
                        sourceTask.executionType(), range.fromSequence(), range.toSequence(),
                        events, recordedResults, artifactCapture.recordedArtifacts(), featureDecision,
                        securityUser.userId()
                ));
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            ReplayExecutionResult executionResult = replayEngine.execute(new ReplayExecutionInput(
                    ReplayMode.SIMULATION_REPLAY, runCode, range.fromSequence(), range.toSequence(),
                    events, recordedResults
            ));
            ReplayEventDiff eventDiff = replayEventDiffService.compare(events, recordedResults, executionResult);
            ArtifactVerificationReport artifactVerification = artifactCapture.report();
            Map<String, Object> resultSnapshot = resultSnapshot(
                    range, executionResult, eventDiff, artifactVerification);
            replayInstanceService.complete(instance, resultSnapshot, securityUser.userId());
            recordSpan(sourceRun.taskId(), instance, "success", range,
                    Map.of("output", executionResult.output(), "eventDiff", eventDiff,
                            "artifactVerification", artifactVerification), null,
                    startedAt, securityUser.userId());
            return new SimulationReplayResponse(
                    instance.replayCode(), instance.replayTraceId(), instance.replayMode().code(),
                    runCode, sourceRun.taskId(), "succeeded", range.toMap(), executionResult.output(),
                    executionResult.injectedObservations(), eventDiff, artifactVerification, instance.manifest()
            );
        } catch (Exception exception) {
            replayInstanceService.fail(instance, exception.getMessage(), securityUser.userId());
            recordSpan(sourceRun.taskId(), instance, "failed", range, Map.of(), exception.getMessage(),
                    startedAt, securityUser.userId());
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("Simulation Replay 执行失败: " + safe(exception.getMessage()));
        }
    }

    private EventRange resolveRange(String runCode, SimulationReplayRequest request) {
        long lastSequence = eventRepository.findLastSequence(runCode);
        if (lastSequence <= 0) {
            throw new BusinessException("原 Run 没有可用于 Simulation Replay 的 Runtime Event");
        }
        long fromSequence = request.fromSequence() == null ? 1L : request.fromSequence();
        long toSequence = request.toSequence() == null ? lastSequence : request.toSequence();
        if (fromSequence > toSequence) {
            throw new BusinessException("Simulation Replay 事件起始序号不能大于结束序号");
        }
        if (toSequence > lastSequence) {
            throw new BusinessException("Simulation Replay 事件结束序号超过当前已持久化上界: " + lastSequence);
        }
        long eventCount = toSequence - fromSequence + 1;
        if (eventCount > MAX_EVENT_COUNT) {
            throw new BusinessException("Simulation Replay 单次最多允许 " + MAX_EVENT_COUNT + " 条事件");
        }
        return new EventRange(fromSequence, toSequence, eventCount);
    }

    private List<RuntimeEventEnvelope> loadFrozenEvents(String runCode,
                                                         EventRange range,
                                                         SecurityUser securityUser) {
        List<RuntimeEventEnvelope> events = new ArrayList<>();
        long cursor = range.fromSequence() - 1;
        while (cursor < range.toSequence()) {
            int limit = (int) Math.min(EVENT_BATCH_SIZE, range.toSequence() - cursor);
            List<RuntimeEventEnvelope> batch = eventQueryService.findAfter(
                    runCode, cursor, limit, securityUser).events();
            if (batch.isEmpty()) {
                throw new BusinessException("Simulation Replay 来源事件不完整，缺少序号 " + (cursor + 1));
            }
            for (RuntimeEventEnvelope event : batch) {
                if (event.sequenceNo() == null || event.sequenceNo() != cursor + 1) {
                    throw new BusinessException("Simulation Replay 来源事件序号不连续，期望 " + (cursor + 1));
                }
                events.add(event);
                cursor = event.sequenceNo();
            }
        }
        if (events.size() != range.eventCount()) {
            throw new BusinessException("Simulation Replay 来源事件数量与冻结范围不一致");
        }
        return List.copyOf(events);
    }

    private List<RecordedStepResult> loadRecordedResults(Long taskId,
                                                          List<RuntimeEventEnvelope> events) {
        Set<Long> completedStepIds = new LinkedHashSet<>();
        for (RuntimeEventEnvelope event : events) {
            if ("step.completed".equals(event.eventType())) {
                Long taskStepId = longValue(event.payload().get("taskStepId"));
                if (taskStepId == null) {
                    throw new BusinessException("Simulation Replay 的 step.completed 缺少 taskStepId");
                }
                completedStepIds.add(taskStepId);
            }
        }
        Map<Long, TaskStepRecord> steps = new HashMap<>();
        conversationRepository.findTaskStepsByTaskId(taskId)
                .forEach(step -> steps.put(step.taskStepId(), step));
        List<RecordedStepResult> results = new ArrayList<>();
        for (Long taskStepId : completedStepIds) {
            TaskStepRecord step = steps.get(taskStepId);
            if (step == null || !"success".equals(step.status())) {
                throw new BusinessException("Simulation Replay 缺少成功步骤快照: taskStepId=" + taskStepId);
            }
            Map<String, Object> outputSnapshot = parseOutputSnapshot(step);
            String resultType = safe(outputSnapshot.get("resultType"));
            if (resultType.isBlank()) {
                throw new BusinessException("Simulation Replay 步骤输出缺少 resultType: taskStepId=" + taskStepId);
            }
            results.add(new RecordedStepResult(
                    taskStepId, step.stepNo(), step.stepType(), safe(step.stepName()), resultType,
                    outputSnapshot, sha256(step.outputSnapshot())
            ));
        }
        return List.copyOf(results);
    }

    private Map<String, Object> parseOutputSnapshot(TaskStepRecord step) {
        if (step.outputSnapshot() == null || step.outputSnapshot().isBlank()) {
            throw new BusinessException("Simulation Replay 步骤输出快照为空: taskStepId=" + step.taskStepId());
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    step.outputSnapshot(), new TypeReference<Map<String, Object>>() { });
            return Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (Exception exception) {
            throw new BusinessException("Simulation Replay 步骤输出快照不是有效 JSON: taskStepId=" + step.taskStepId());
        }
    }

    private Map<String, Object> resultSnapshot(EventRange range,
                                               ReplayExecutionResult executionResult,
                                               ReplayEventDiff eventDiff,
                                               ArtifactVerificationReport artifactVerification) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("eventRange", range.toMap());
        result.put("output", executionResult.output());
        result.put("injectedObservations", executionResult.injectedObservations());
        result.put("eventDiff", eventDiff);
        result.put("artifactVerification", artifactVerification);
        return result;
    }

    private void recordSpan(Long taskId,
                            ReplayInstance instance,
                            String status,
                            EventRange range,
                            Map<String, Object> output,
                            String errorMessage,
                            LocalDateTime startedAt,
                            Long operatorId) {
        try {
            taskTraceService.recordSpan(
                    taskId, instance.replayTraceId(), null, "simulation_replay", "录制结果模拟重放", status,
                    Map.of("replayCode", instance.replayCode(), "eventRange", range.toMap()), output,
                    errorMessage, startedAt, LocalDateTime.now(),
                    Map.of("replayCode", instance.replayCode(), "replayMode", instance.replayMode().code()),
                    operatorId
            );
        } catch (RuntimeException exception) {
            log.error("Simulation Replay Trace 写入失败, replayCode={}", instance.replayCode(), exception);
        }
    }

    private void requireAdministrator(SecurityUser securityUser) {
        boolean administrator = securityUser.roleCodes() != null && securityUser.roleCodes().stream()
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role));
        if (!administrator) {
            throw new BusinessException("Simulation Replay 当前仅允许平台管理员执行");
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(safe(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private record EventRange(long fromSequence, long toSequence, long eventCount) {
        private Map<String, Object> toMap() {
            return Map.of(
                    "fromSequence", fromSequence,
                    "toSequence", toSequence,
                    "eventCount", eventCount,
                    "available", true
            );
        }
    }
}

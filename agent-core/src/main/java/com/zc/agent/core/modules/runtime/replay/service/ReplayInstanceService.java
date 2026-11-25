package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedArtifact;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayInstance;
import com.zc.agent.core.modules.runtime.replay.model.ReplayMode;
import com.zc.agent.core.modules.runtime.replay.repository.RuntimeReplayRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 创建和完成独立 Replay 实例，保证 Manifest/Input 在执行前冻结。 */
@Service
public class ReplayInstanceService {

    private final RuntimeReplayRepository repository;
    private final ObjectMapper objectMapper;

    public ReplayInstanceService(RuntimeReplayRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 创建已正式标识为 prompt_replay 的不可变 Replay 实例。 */
    public ReplayInstance startPromptReplay(StartPromptReplayCommand command) {
        ReplayMode mode = ReplayMode.PROMPT_REPLAY;
        String replayCode = newCode("REPLAY_");
        String replayTraceId = newCode("REPLAY_TRACE_");
        Map<String, Object> manifest = buildManifest(command, mode, replayCode, replayTraceId);
        Map<String, Object> input = buildInput(command);
        repository.insert(new RuntimeReplayRepository.ReplayInsertCommand(
                replayCode, replayTraceId, mode.code(), command.sourceTaskId(), command.sourceTaskTraceId(),
                command.userId(), writeJson(manifest), writeJson(input)
        ));
        return new ReplayInstance(replayCode, replayTraceId, mode, manifest);
    }

    /** 创建冻结事件范围和录制步骤结果的 Simulation Replay 实例。 */
    public ReplayInstance startSimulationReplay(StartSimulationReplayCommand command) {
        ReplayMode mode = ReplayMode.SIMULATION_REPLAY;
        String replayCode = newCode("REPLAY_");
        String replayTraceId = newCode("REPLAY_TRACE_");
        Map<String, Object> manifest = buildSimulationManifest(command, replayCode, replayTraceId);
        Map<String, Object> input = buildSimulationInput(command);
        repository.insert(new RuntimeReplayRepository.ReplayInsertCommand(
                replayCode, replayTraceId, mode.code(), command.sourceTaskId(), null,
                command.userId(), writeJson(manifest), writeJson(input)
        ));
        return new ReplayInstance(replayCode, replayTraceId, mode, manifest);
    }

    /** 保存 Replay 独立结果，不回写原 Task、Trace 或 Artifact。 */
    public void complete(ReplayInstance instance, Map<String, Object> result, Long operatorId) {
        if (repository.markSucceeded(instance.replayCode(), writeJson(result), operatorId) != 1) {
            throw new BusinessException("Replay 状态已变化，无法写入完成结果");
        }
    }

    /** 保存 Replay 失败原因，Manifest/Input 保持不变。 */
    public void fail(ReplayInstance instance, String errorMessage, Long operatorId) {
        repository.markFailed(instance.replayCode(), errorMessage, operatorId);
    }

    private Map<String, Object> buildManifest(StartPromptReplayCommand command,
                                               ReplayMode mode,
                                               String replayCode,
                                               String replayTraceId) {
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", 1);
        manifest.put("replayCode", replayCode);
        manifest.put("replayTraceId", replayTraceId);
        manifest.put("replayMode", mode.code());
        manifest.put("capturedAt", LocalDateTime.now().toString());
        manifest.put("source", Map.of(
                "taskId", command.sourceTaskId(),
                "taskTraceId", command.sourceTaskTraceId(),
                "traceId", safe(command.sourceTraceId()),
                "agentId", command.agentId(),
                "agentName", safe(command.agentName()),
                "executionType", safe(command.executionType())
        ));
        manifest.put("model", safeModel(command.modelContext()));
        manifest.put("prompts", command.promptReferences() == null ? List.of() : command.promptReferences());
        manifest.put("toolSchemas", List.of());
        manifest.put("toolReferences", command.toolReferences() == null ? List.of() : command.toolReferences());
        manifest.put("policy", Map.of(
                "toolExecutionAllowed", false,
                "workflowExecutionAllowed", false,
                "networkWriteAllowed", false,
                "fileWriteAllowed", false,
                "databaseBusinessWriteAllowed", false
        ));
        manifest.put("featureDecision", command.featureDecision());
        manifest.put("resourceSnapshot", Map.of(
                "promptSnapshotSource", "task_trace",
                "modelSnapshotSource", "registry_at_replay",
                "toolSchemaRequired", false,
                "reason", "prompt_replay 不执行 Tool 或 Workflow"
        ));
        return manifest;
    }

    private Map<String, Object> buildInput(StartPromptReplayCommand command) {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("systemPrompt", command.systemPrompt());
        input.put("userPrompt", command.userPrompt());
        input.put("systemPromptSha256", sha256(command.systemPrompt()));
        input.put("userPromptSha256", sha256(command.userPrompt()));
        input.put("originalOutput", safe(command.originalOutput()));
        input.put("modelCode", command.modelContext().modelCode());
        input.put("eventRange", Map.of("fromSequence", 0, "toSequence", 0, "available", false));
        return input;
    }

    private Map<String, Object> buildSimulationManifest(StartSimulationReplayCommand command,
                                                         String replayCode,
                                                         String replayTraceId) {
        LinkedHashMap<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", 1);
        manifest.put("replayCode", replayCode);
        manifest.put("replayTraceId", replayTraceId);
        manifest.put("replayMode", ReplayMode.SIMULATION_REPLAY.code());
        manifest.put("capturedAt", LocalDateTime.now().toString());
        manifest.put("source", Map.of(
                "runCode", command.sourceRunCode(),
                "taskId", command.sourceTaskId(),
                "traceId", safe(command.sourceTraceId()),
                "agentId", command.agentId(),
                "executionType", safe(command.executionType())
        ));
        manifest.put("eventRange", eventRange(command));
        manifest.put("recordedResultCount", command.recordedStepResults().size());
        manifest.put("recordedArtifactCount", command.recordedArtifacts().size());
        manifest.put("recordedAttachmentFactCount", command.events().stream()
                .filter(event -> event.eventType() != null && event.eventType().startsWith("input.attachment."))
                .count());
        manifest.put("toolSchemas", List.of());
        manifest.put("policy", Map.of(
                "llmInvocationAllowed", false,
                "toolExecutionAllowed", false,
                "workflowExecutionAllowed", false,
                "sandboxExecutionAllowed", false,
                "networkWriteAllowed", false,
                "fileWriteAllowed", false,
                "databaseBusinessWriteAllowed", false
        ));
        manifest.put("featureDecision", command.featureDecision());
        manifest.put("resourceSnapshot", Map.of(
                "eventSource", "agent_run_stream_events",
                "recordedResultSource", "task_steps.output_snapshot",
                "artifactContentSource", "task_steps.output_snapshot.payload.artifacts.contentBase64",
                "attachmentIntegritySource", "input.attachment.* payload attachmentId + sha256",
                "toolSchemaRequired", false,
                "providerInvocationAllowed", false,
                "reason", "simulation_replay 只注入录制结果"
        ));
        return manifest;
    }

    private Map<String, Object> buildSimulationInput(StartSimulationReplayCommand command) {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("sourceRunCode", command.sourceRunCode());
        input.put("eventRange", eventRange(command));
        input.put("events", command.events());
        input.put("recordedStepResults", command.recordedStepResults());
        input.put("recordedArtifacts", command.recordedArtifacts());
        return input;
    }

    private Map<String, Object> eventRange(StartSimulationReplayCommand command) {
        return Map.of(
                "fromSequence", command.fromSequence(),
                "toSequence", command.toSequence(),
                "eventCount", command.events().size(),
                "available", true
        );
    }

    private Map<String, Object> safeModel(ExecutionContext.ModelContext model) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelCode", safe(model.modelCode()));
        snapshot.put("modelName", safe(model.modelName()));
        snapshot.put("provider", safe(model.provider()));
        snapshot.put("modelType", safe(model.modelType()));
        snapshot.put("modelIdentifier", safe(model.modelIdentifier()));
        snapshot.put("temperature", model.temperature());
        snapshot.put("maxTokens", model.maxTokens());
        snapshot.put("contextWindow", model.contextWindow());
        snapshot.put("reservedOutputTokens", model.reservedOutputTokens());
        snapshot.put("reservedSystemTokens", model.reservedSystemTokens());
        snapshot.put("compactionTriggerRatio", model.compactionTriggerRatio());
        snapshot.put("tokenizerFamily", safe(model.tokenizerFamily()));
        snapshot.put("timeoutSeconds", model.timeoutSeconds());
        return snapshot;
    }

    private String newCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Replay 快照序列化失败");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * @param sourceTaskId 原 Task ID
     * @param sourceTaskTraceId 原 Task Trace ID
     * @param sourceTraceId 原 Trace ID
     * @param agentId Agent ID
     * @param agentName Agent 名称
     * @param executionType 原任务执行分类
     * @param modelContext 本次 Prompt Replay 使用的模型快照
     * @param systemPrompt 冻结的 System Prompt
     * @param userPrompt 冻结的 User Prompt
     * @param originalOutput 原输出
     * @param promptReferences Prompt 引用摘要
     * @param toolReferences 原 Trace Tool 引用摘要，不允许执行
     * @param featureDecision Replay Feature Flag 灰度决策
     * @param userId 发起用户 ID
     */
    public record StartPromptReplayCommand(
            Long sourceTaskId,
            Long sourceTaskTraceId,
            String sourceTraceId,
            Long agentId,
            String agentName,
            String executionType,
            ExecutionContext.ModelContext modelContext,
            String systemPrompt,
            String userPrompt,
            String originalOutput,
            List<Map<String, Object>> promptReferences,
            List<Map<String, Object>> toolReferences,
            ReplayFeaturePolicy.Decision featureDecision,
            Long userId
    ) {
    }

    /**
     * @param sourceRunCode 原 Run 编码
     * @param sourceTaskId 原 Task ID
     * @param sourceTraceId 原 Trace ID
     * @param agentId Agent ID
     * @param executionType 原任务执行分类
     * @param fromSequence 冻结事件起始序号
     * @param toSequence 冻结事件结束序号
     * @param events 冻结 Runtime Event
     * @param recordedStepResults 冻结步骤结果
     * @param recordedArtifacts 冻结 Artifact 内容哈希和文件元数据
     * @param featureDecision Replay Feature Flag 灰度决策
     * @param userId 发起管理员 ID
     */
    public record StartSimulationReplayCommand(
            String sourceRunCode,
            Long sourceTaskId,
            String sourceTraceId,
            Long agentId,
            String executionType,
            long fromSequence,
            long toSequence,
            List<RuntimeEventEnvelope> events,
            List<RecordedStepResult> recordedStepResults,
            List<RecordedArtifact> recordedArtifacts,
            ReplayFeaturePolicy.Decision featureDecision,
            Long userId
    ) {
    }
}

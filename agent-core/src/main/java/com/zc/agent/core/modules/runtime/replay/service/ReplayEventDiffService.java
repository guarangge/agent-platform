package com.zc.agent.core.modules.runtime.replay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.protocol.model.RuntimeEventEnvelope;
import com.zc.agent.core.modules.runtime.replay.model.RecordedStepResult;
import com.zc.agent.core.modules.runtime.replay.model.ReplayEventDiff;
import com.zc.agent.core.modules.runtime.replay.model.ReplayExecutionResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 将来源事实与 Replay 实际注入事实进行规范化结构对比。 */
@Service
public class ReplayEventDiffService {

    private static final int DIFF_VERSION = 1;
    private final ObjectMapper objectMapper;

    public ReplayEventDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 输出稳定事实键、规范哈希、字段变化和消息结果差异。 */
    public ReplayEventDiff compare(List<RuntimeEventEnvelope> events,
                                   List<RecordedStepResult> recordedStepResults,
                                   ReplayExecutionResult executionResult) {
        Map<String, DiffFact> expectedFacts = expectedFacts(events, recordedStepResults);
        Map<String, DiffFact> actualFacts = actualFacts(executionResult.injectedObservations());
        List<ReplayEventDiff.Entry> entries = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int matched = 0;
        int changed = 0;
        int missing = 0;
        for (Map.Entry<String, DiffFact> expectedEntry : expectedFacts.entrySet()) {
            String factKey = expectedEntry.getKey();
            DiffFact expected = expectedEntry.getValue();
            DiffFact actual = actualFacts.remove(factKey);
            if (actual == null) {
                missing++;
                entries.add(entry(factKey, expected, null, "missing", List.of()));
                reasons.add("缺少 Replay 注入事实: " + factKey);
                continue;
            }
            String expectedHash = canonicalHash(expected.normalized());
            String actualHash = canonicalHash(actual.normalized());
            if (expectedHash.equals(actualHash)) {
                matched++;
                entries.add(entry(factKey, expected, actual, "matched", List.of()));
            } else {
                changed++;
                List<String> changedFields = changedFields(expected.normalized(), actual.normalized());
                entries.add(entry(factKey, expected, actual, "changed", changedFields));
                reasons.add("Replay 注入事实发生变化: " + factKey + "，字段=" + String.join(",", changedFields));
            }
        }
        int unexpected = actualFacts.size();
        for (Map.Entry<String, DiffFact> actualEntry : actualFacts.entrySet()) {
            entries.add(entry(actualEntry.getKey(), null, actualEntry.getValue(), "unexpected", List.of()));
            reasons.add("存在非预期 Replay 注入事实: " + actualEntry.getKey());
        }
        ReplayEventDiff.ResultDiff resultDiff = resultDiff(events, executionResult.output());
        if ("changed".equals(resultDiff.status())) {
            reasons.add("Replay 重组消息与来源事件投影不一致");
        }
        ReplayEventDiff.Summary summary = new ReplayEventDiff.Summary(
                expectedFacts.size(), executionResult.injectedObservations().size(),
                matched, changed, missing, unexpected
        );
        boolean consistent = changed == 0 && missing == 0 && unexpected == 0
                && "matched".equals(resultDiff.status());
        return new ReplayEventDiff(
                DIFF_VERSION, consistent, summary, resultDiff, List.copyOf(entries), List.copyOf(reasons)
        );
    }

    private Map<String, DiffFact> expectedFacts(List<RuntimeEventEnvelope> events,
                                                 List<RecordedStepResult> recordedStepResults) {
        Map<Long, RecordedStepResult> results = new HashMap<>();
        for (RecordedStepResult result : safeList(recordedStepResults)) {
            results.put(result.taskStepId(), result);
        }
        LinkedHashMap<String, DiffFact> facts = new LinkedHashMap<>();
        for (RuntimeEventEnvelope event : safeList(events)) {
            if ("step.completed".equals(event.eventType())) {
                RecordedStepResult result = results.get(
                        SimulationReplayFactContract.longValue(event.payload().get("taskStepId")));
                if (result != null) {
                    putUnique(facts, SimulationReplayFactContract.sourceFactKey(event),
                            new DiffFact("step_result", event.sequenceNo(), event.eventType(), stepFact(result)));
                }
            } else if (SimulationReplayFactContract.isRecordedFact(event.eventType())) {
                putUnique(facts, SimulationReplayFactContract.sourceFactKey(event),
                        new DiffFact(SimulationReplayFactContract.observationType(event.eventType()),
                                event.sequenceNo(), event.eventType(), eventFact(event)));
            }
        }
        return facts;
    }

    private Map<String, DiffFact> actualFacts(List<Map<String, Object>> observations) {
        LinkedHashMap<String, DiffFact> facts = new LinkedHashMap<>();
        for (Map<String, Object> observation : safeList(observations)) {
            String factKey = SimulationReplayFactContract.observationFactKey(observation);
            String source = text(observation.get("source"));
            boolean stepResult = "recorded_task_step_snapshot".equals(source);
            Map<String, Object> normalized = stepResult
                    ? select(observation, "observationType", "source", "taskStepId", "stepNo", "stepType",
                    "stepName", "outputSha256", "outputSnapshot")
                    : select(observation, "observationType", "source", "payload");
            putUnique(facts, factKey, new DiffFact(
                    stepResult ? "step_result" : text(observation.get("observationType")),
                    SimulationReplayFactContract.longValue(observation.get("sourceSequenceNo")),
                    text(observation.get("sourceEventType")), normalized
            ));
        }
        return facts;
    }

    private Map<String, Object> stepFact(RecordedStepResult result) {
        LinkedHashMap<String, Object> fact = new LinkedHashMap<>();
        fact.put("observationType", result.resultType() + "_result");
        fact.put("source", "recorded_task_step_snapshot");
        fact.put("taskStepId", result.taskStepId());
        fact.put("stepNo", result.stepNo());
        fact.put("stepType", result.stepType());
        fact.put("stepName", result.stepName());
        fact.put("outputSha256", result.outputSha256());
        fact.put("outputSnapshot", result.outputSnapshot());
        return fact;
    }

    private Map<String, Object> eventFact(RuntimeEventEnvelope event) {
        LinkedHashMap<String, Object> fact = new LinkedHashMap<>();
        fact.put("observationType", SimulationReplayFactContract.observationType(event.eventType()));
        fact.put("source", "recorded_runtime_event");
        fact.put("payload", event.payload());
        return fact;
    }

    private Map<String, Object> select(Map<String, Object> source, String... fields) {
        LinkedHashMap<String, Object> selected = new LinkedHashMap<>();
        for (String field : fields) {
            selected.put(field, source.get(field));
        }
        return selected;
    }

    private ReplayEventDiff.Entry entry(String factKey,
                                        DiffFact expected,
                                        DiffFact actual,
                                        String status,
                                        List<String> changedFields) {
        DiffFact reference = expected == null ? actual : expected;
        return new ReplayEventDiff.Entry(
                factKey,
                reference.category(),
                status,
                reference.sourceSequenceNo(),
                reference.sourceEventType(),
                expected == null ? null : canonicalHash(expected.normalized()),
                actual == null ? null : canonicalHash(actual.normalized()),
                changedFields
        );
    }

    private ReplayEventDiff.ResultDiff resultDiff(List<RuntimeEventEnvelope> events,
                                                   Map<String, Object> replayOutput) {
        String originalResult = projectMessage(events);
        String replayResult = text(replayOutput == null ? null : replayOutput.get("assembledMessage"));
        String originalHash = sha256(originalResult);
        String replayHash = sha256(replayResult);
        return new ReplayEventDiff.ResultDiff(
                originalHash.equals(replayHash) ? "matched" : "changed",
                originalResult, replayResult, originalHash, replayHash,
                originalResult.length(), replayResult.length()
        );
    }

    private String projectMessage(List<RuntimeEventEnvelope> events) {
        StringBuilder message = new StringBuilder();
        for (RuntimeEventEnvelope event : safeList(events)) {
            if ("message.reset".equals(event.eventType())) {
                message.setLength(0);
            } else if ("message.delta".equals(event.eventType())) {
                message.append(text(event.payload().get("content")));
            }
        }
        return message.toString();
    }

    private List<String> changedFields(Map<String, Object> expected, Map<String, Object> actual) {
        Set<String> fields = new TreeSet<>();
        fields.addAll(expected.keySet());
        fields.addAll(actual.keySet());
        return fields.stream()
                .filter(field -> !canonicalHash(expected.get(field)).equals(canonicalHash(actual.get(field))))
                .toList();
    }

    private void putUnique(LinkedHashMap<String, DiffFact> facts, String factKey, DiffFact fact) {
        String resolvedKey = factKey;
        int duplicateIndex = 2;
        while (facts.containsKey(resolvedKey)) {
            resolvedKey = factKey + "#duplicate:" + duplicateIndex++;
        }
        facts.put(resolvedKey, fact);
    }

    private String canonicalHash(Object value) {
        try {
            JsonNode normalized = sortNode(objectMapper.valueToTree(value));
            return sha256(objectMapper.writeValueAsString(normalized));
        } catch (Exception exception) {
            throw new BusinessException("Replay Event Diff 规范化失败");
        }
    }

    private JsonNode sortNode(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(item -> sorted.add(sortNode(item)));
            return sorted;
        }
        ObjectNode sorted = objectMapper.createObjectNode();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        fieldNames.stream().sorted().forEach(field -> sorted.set(field, sortNode(node.get(field))));
        return sorted;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private record DiffFact(
            String category,
            Long sourceSequenceNo,
            String sourceEventType,
            Map<String, Object> normalized
    ) {
    }
}

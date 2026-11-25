package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Claim、独立证据、来源权威度、冲突及二次核验统一可信度治理。 */
@Service
public class ResultTrustService {
    private final StepOutputContractService outputContractService;
    private final RuntimeLlmGatewayService llmGatewayService;
    private final ToolRouterService toolRouterService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResultTrustService(StepOutputContractService outputContractService,
                              RuntimeLlmGatewayService llmGatewayService,
                              @Lazy ToolRouterService toolRouterService,
                              ObjectMapper objectMapper) {
        this.outputContractService = outputContractService;
        this.llmGatewayService = llmGatewayService;
        this.toolRouterService = toolRouterService;
        this.objectMapper = objectMapper;
    }

    /** 独立单元测试兼容构造器，增强 verifier 默认关闭。 */
    public ResultTrustService(StepOutputContractService outputContractService) {
        this(outputContractService, null, null, new ObjectMapper());
    }

    public TrustAssessment evaluate(ExecutionContext context,
                                    ExecutionPlan.ExecutionPlanStep step,
                                    String outputSnapshot) {
        JsonNode policy = context.agentContext().metadata() == null ? null : context.agentContext().metadata().path("trustPolicy");
        if (policy == null || !policy.path("enabled").asBoolean(false)) return TrustAssessment.disabled();
        StandardStepOutputSnapshot snapshot = outputContractService.parseSnapshot(outputSnapshot);
        if (snapshot == null) return failed(policy, 0D, 0, 0D, List.of(), List.of("输出不是统一结果契约"), VerifierResult.disabled());

        List<String> issues = new ArrayList<>();
        double confidence = snapshot.confidence() == null ? 0D : snapshot.confidence();
        double minimum = policy.path("minimumConfidence").asDouble(0.65D);
        if (confidence < minimum) issues.add("置信度 " + confidence + " 低于阈值 " + minimum);

        List<Map<String, Object>> evidence = snapshot.evidence() == null ? List.of() : snapshot.evidence();
        Set<String> sources = independentSources(evidence, policy.path("allowInternalStepEvidence").asBoolean(false));
        int minimumSources = policy.path("minimumEvidenceSources").asInt(1);
        if (sources.size() < minimumSources) issues.add("独立证据来源 " + sources.size() + " 少于 " + minimumSources);
        boolean cited = !evidence.isEmpty() || containsCitation(snapshot.responseText());
        if (policy.path("requireCitation").asBoolean(false) && !cited) issues.add("结果缺少可追溯引用");

        List<Claim> claims = extractClaims(snapshot, policy.path("autoExtractClaims").asBoolean(false));
        int supportedClaims = countSupportedClaims(claims, evidence);
        if (policy.path("requireClaimEvidence").asBoolean(false)) {
            if (claims.isEmpty()) issues.add("结果缺少可核验 Claim");
            else if (supportedClaims < claims.size()) issues.add("Claim 证据覆盖 " + supportedClaims + "/" + claims.size());
        }
        List<String> conflicts = detectConflicts(evidence);
        if (policy.path("detectContradictions").asBoolean(false) && !conflicts.isEmpty()) {
            issues.add("检测到冲突证据: " + String.join(",", conflicts));
        }
        double authorityScore = authorityScore(evidence, policy.path("authorityWeights"));
        double minimumAuthority = policy.path("minimumAuthorityScore").asDouble(0D);
        if (authorityScore < minimumAuthority) issues.add("证据权威度 " + round(authorityScore) + " 低于阈值 " + minimumAuthority);

        VerifierResult verifier = verify(context, snapshot, claims, evidence, policy.path("verifier"));
        if (verifier.enabled() && !verifier.passed()) issues.addAll(verifier.issues());
        return new TrustAssessment(true, issues.isEmpty(), policy.path("failureAction").asText("warn"), confidence,
                sources.size(), authorityScore, claims.size(), supportedClaims, conflicts, verifier, List.copyOf(issues));
    }

    public void enforce(TrustAssessment assessment, ExecutionPlan.ExecutionPlanStep step) {
        if (!assessment.enabled() || assessment.passed() || "warn".equals(assessment.action())) return;
        String reason = "结果可信度校验未通过：" + String.join("；", assessment.issues());
        if ("approval".equals(assessment.action())) throw new ApprovalRequiredException(reason,
                "trust_" + step.stepId(), "结果可信度人工复核", null, List.of(), List.of(), "trust_policy",
                step.stepNo() + 1, assessment.toMetadata());
        if ("replan".equals(assessment.action())) throw new ResultTrustException(reason, true);
        throw new ResultTrustException(reason, false);
    }

    private List<Claim> extractClaims(StandardStepOutputSnapshot snapshot, boolean autoExtract) {
        List<Claim> claims = new ArrayList<>();
        collectClaims(snapshot.structuredData(), claims);
        if (snapshot.metadata() != null) collectClaims(snapshot.metadata().get("claims"), claims);
        if (claims.isEmpty() && autoExtract && snapshot.responseText() != null) {
            String[] sentences = snapshot.responseText().split("[。！？!?\\n]");
            for (int i = 0; i < sentences.length && claims.size() < 12; i++) {
                String text = sentences[i].trim();
                if (text.length() >= 8) claims.add(new Claim("auto_" + (i + 1), text));
            }
        }
        return claims.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private void collectClaims(Object value, List<Claim> target) {
        if (value instanceof Map<?, ?> map && map.get("claims") != null) {
            collectClaims(map.get("claims"), target); return;
        }
        if (!(value instanceof List<?> list)) return;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String text = text(first((Map<String, Object>) map, "text", "claim", "statement"));
                if (text != null) target.add(new Claim(defaultText(text(map.get("id")), "claim_" + (target.size() + 1)), text));
            } else if (item != null && !String.valueOf(item).isBlank()) {
                target.add(new Claim("claim_" + (target.size() + 1), String.valueOf(item)));
            }
        }
    }

    private Set<String> independentSources(List<Map<String, Object>> evidence, boolean allowInternal) {
        Set<String> sources = new LinkedHashSet<>();
        for (Map<String, Object> item : evidence) {
            Object source = first(item, "url", "uri", "documentId", "sourceId", "source");
            if (source == null && allowInternal) source = item.get("sourceStepId");
            if (source != null && !String.valueOf(source).isBlank()) sources.add(normalizeSource(String.valueOf(source)));
        }
        return sources;
    }

    private int countSupportedClaims(List<Claim> claims, List<Map<String, Object>> evidence) {
        int supported = 0;
        for (Claim claim : claims) {
            boolean found = evidence.stream().anyMatch(item -> claim.id().equals(text(first(item, "claimId", "claim")))
                    && !"refutes".equalsIgnoreCase(defaultText(text(item.get("stance")), "supports")));
            if (found) supported++;
        }
        return supported;
    }

    private List<String> detectConflicts(List<Map<String, Object>> evidence) {
        Map<String, Set<String>> stances = new LinkedHashMap<>();
        Map<String, Set<String>> values = new LinkedHashMap<>();
        for (Map<String, Object> item : evidence) {
            String claim = text(first(item, "claimId", "claim"));
            if (claim == null) continue;
            stances.computeIfAbsent(claim, key -> new LinkedHashSet<>()).add(defaultText(text(item.get("stance")), "supports").toLowerCase(Locale.ROOT));
            if (item.get("value") != null) values.computeIfAbsent(claim, key -> new LinkedHashSet<>()).add(String.valueOf(item.get("value")).trim().toLowerCase(Locale.ROOT));
        }
        List<String> conflicts = new ArrayList<>();
        stances.forEach((claim, states) -> {
            if (states.contains("supports") && states.contains("refutes") || values.getOrDefault(claim, Set.of()).size() > 1) conflicts.add(claim);
        });
        return conflicts;
    }

    private double authorityScore(List<Map<String, Object>> evidence, JsonNode configuredWeights) {
        if (evidence.isEmpty()) return 0D;
        Map<String, Double> weights = new LinkedHashMap<>(Map.of(
                "official", 1D, "government", 1D, "primary", 0.95D, "knowledge", 0.85D,
                "document", 0.8D, "tool", 0.75D, "web", 0.6D, "internal", 0.35D, "unknown", 0.5D));
        if (configuredWeights.isObject()) configuredWeights.fields().forEachRemaining(entry -> weights.put(entry.getKey(), clamp(entry.getValue().asDouble())));
        double total = 0D;
        for (Map<String, Object> item : evidence) {
            Object explicit = item.get("authorityScore");
            if (explicit instanceof Number number) total += clamp(number.doubleValue());
            else total += weights.getOrDefault(defaultText(text(item.get("sourceType")), "unknown").toLowerCase(Locale.ROOT), 0.5D);
        }
        return total / evidence.size();
    }

    private VerifierResult verify(ExecutionContext context,
                                  StandardStepOutputSnapshot snapshot,
                                  List<Claim> claims,
                                  List<Map<String, Object>> evidence,
                                  JsonNode verifierPolicy) {
        if (!verifierPolicy.path("enabled").asBoolean(false)) return VerifierResult.disabled();
        String mode = verifierPolicy.path("mode").asText("llm");
        try {
            String raw;
            if ("tool".equals(mode)) {
                String toolCode = verifierPolicy.path("toolCode").asText("");
                if (toolRouterService == null || toolCode.isBlank()) throw new IllegalStateException("verifier Tool 未配置");
                ToolExecutionResult result = toolRouterService.execute(context, toolCode, "独立核验 Agent 输出", Map.of(
                        "responseText", defaultText(snapshot.responseText(), ""), "claims", claims, "evidence", evidence))
                        .orElseThrow(() -> new IllegalStateException("verifier Tool 不可用"));
                raw = result.outputSnapshot() == null || result.outputSnapshot().isBlank() ? result.responseText() : result.outputSnapshot();
            } else {
                if (llmGatewayService == null) throw new IllegalStateException("verifier LLM 不可用");
                raw = llmGatewayService.chatCompletion("你是独立事实核验器。只输出 JSON：{passed:boolean,confidence:number,issues:string[]}",
                        "回答：" + snapshot.responseText() + "\nClaims：" + objectMapper.writeValueAsString(claims)
                                + "\nEvidence：" + objectMapper.writeValueAsString(evidence), context.modelForStage("reflection"));
                raw = llmGatewayService.sanitizeJsonText(raw);
            }
            JsonNode result = objectMapper.readTree(raw);
            List<String> verifierIssues = new ArrayList<>();
            if (result.path("issues").isArray()) result.path("issues").forEach(item -> verifierIssues.add(item.asText()));
            return new VerifierResult(true, mode, result.path("passed").asBoolean(false),
                    result.path("confidence").asDouble(0D), List.copyOf(verifierIssues));
        } catch (Exception exception) {
            if ("warn".equals(verifierPolicy.path("errorAction").asText("fail_closed"))) {
                return new VerifierResult(true, mode, true, 0D, List.of("verifier 调用失败但策略允许降级: " + exception.getMessage()));
            }
            return new VerifierResult(true, mode, false, 0D, List.of("verifier 调用失败: " + exception.getMessage()));
        }
    }

    private TrustAssessment failed(JsonNode policy, double confidence, int sources, double authority, List<String> conflicts,
                                   List<String> issues, VerifierResult verifier) {
        return new TrustAssessment(true, false, policy.path("failureAction").asText("block"), confidence, sources,
                authority, 0, 0, conflicts, verifier, issues);
    }

    private boolean containsCitation(String text) { return text != null && (text.contains("[K") || text.contains("http://") || text.contains("https://")); }
    private Object first(Map<String, Object> value, String... keys) { for (String key : keys) if (value.get(key) != null) return value.get(key); return null; }
    private String text(Object value) { return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value); }
    private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String normalizeSource(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("[?#].*$", ""); }
    private double clamp(double value) { return Math.max(0D, Math.min(1D, value)); }
    private double round(double value) { return Math.round(value * 1000D) / 1000D; }

    public record Claim(String id, String text) { }
    public record VerifierResult(boolean enabled, String mode, boolean passed, double confidence, List<String> issues) {
        static VerifierResult disabled() { return new VerifierResult(false, "none", true, 1D, List.of()); }
        public Map<String, Object> toMetadata() { return Map.of("enabled", enabled, "mode", mode, "passed", passed, "confidence", confidence, "issues", issues); }
    }
    public record TrustAssessment(boolean enabled, boolean passed, String action, double confidence, int evidenceSources,
                                  double authorityScore, int claimCount, int supportedClaimCount, List<String> conflicts,
                                  VerifierResult verifier, List<String> issues) {
        static TrustAssessment disabled() { return new TrustAssessment(false, true, "none", 1D, 0, 1D, 0, 0, List.of(), VerifierResult.disabled(), List.of()); }
        public Map<String, Object> toMetadata() {
            Map<String, Object> result = new LinkedHashMap<>(); result.put("enabled", enabled); result.put("passed", passed);
            result.put("action", action); result.put("confidence", confidence); result.put("evidenceSources", evidenceSources);
            result.put("authorityScore", authorityScore); result.put("claimCount", claimCount); result.put("supportedClaimCount", supportedClaimCount);
            result.put("conflicts", conflicts); result.put("verifier", verifier.toMetadata()); result.put("issues", issues); return result;
        }
    }
}

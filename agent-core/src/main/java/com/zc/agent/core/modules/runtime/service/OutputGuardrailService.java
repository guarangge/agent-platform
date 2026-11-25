package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.conversation.service.GuardrailPolicyHitService;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * 最终输出 Guardrail。
 * 在响应落库和记忆写回前执行注册中心策略，避免不安全原文从旁路泄漏。
 */
@Service
public class OutputGuardrailService {

    private static final Logger log = LoggerFactory.getLogger(OutputGuardrailService.class);
    private static final String DEFAULT_BLOCK_RESPONSE = "响应内容命中安全策略，已停止输出。";

    private final GuardrailPolicyHitService hitService;
    private final List<String> activeEnvironments;

    public OutputGuardrailService(GuardrailPolicyHitService hitService,
                                  @Value("${spring.profiles.active:default}") String activeProfiles) {
        this.hitService = hitService;
        this.activeEnvironments = splitProfiles(activeProfiles);
    }

    /**
     * 应用所有匹配的输出策略，并记录每个实际命中。
     */
    public String apply(ExecutionContext context,
                        String content,
                        Long taskId,
                        String traceId,
                        Long operatorId) {
        String result = content == null ? "" : content;
        List<JsonNode> rules = policyRules(context).stream()
                .filter(rule -> rule.path("enabled").asBoolean(true))
                .filter(this::supportsOutputStage)
                .filter(rule -> scopeMatches(rule.path("scope"), context))
                .filter(rule -> rolloutMatches(rule.path("rollout"), context.currentUser().userId()))
                .sorted(Comparator.comparingInt((JsonNode rule) -> rule.path("priority").asInt(100)).reversed())
                .toList();
        for (JsonNode rule : rules) {
            OutputMatch match = matchAndRedact(rule, result);
            if (!match.hit()) {
                continue;
            }
            String action = rule.path("action").path("type").asText("block").toLowerCase(Locale.ROOT);
            if ("redact".equals(action)) {
                result = match.content();
            } else if ("block".equals(action)) {
                result = textOrDefault(rule.path("output").path("safeResponse"), DEFAULT_BLOCK_RESPONSE);
            } else {
                continue;
            }
            Map<String, Object> metadata = hitMetadata(rule, context, action, match.detail());
            hitService.recordHit(
                    context.conversationContext().conversationId(), taskId, null,
                    context.agentContext().agentId(), traceId, action, metadata, operatorId
            );
            log.warn("输出 Guardrail 命中, taskId={}, traceId={}, policyCode={}, action={}, detail={}",
                    taskId, traceId, rule.path("policyCode").asText(), action, match.detail());
            if ("block".equals(action)) {
                break;
            }
        }
        return result;
    }

    private OutputMatch matchAndRedact(JsonNode rule, String content) {
        String replacement = textOrDefault(rule.path("output").path("replacement"), "[REDACTED]");
        String result = content;
        ArrayList<String> hits = new ArrayList<>();
        for (String keyword : mergedKeywords(rule.path("match"))) {
            Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            if (pattern.matcher(result).find()) {
                hits.add("keyword:" + keyword);
                result = pattern.matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
            }
        }
        for (String expression : textList(rule.path("output").path("patterns"))) {
            try {
                Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (pattern.matcher(result).find()) {
                    hits.add("pattern:" + expression);
                    result = pattern.matcher(result).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
                }
            } catch (PatternSyntaxException exception) {
                log.error("输出 Guardrail 正则无效, policyCode={}, pattern={}", rule.path("policyCode").asText(), expression);
            }
        }
        boolean alwaysBlock = "block".equalsIgnoreCase(rule.path("action").path("type").asText())
                && "always".equalsIgnoreCase(rule.path("match").path("mode").asText())
                && mergedKeywords(rule.path("match")).isEmpty()
                && textList(rule.path("output").path("patterns")).isEmpty();
        if (hits.isEmpty() && !alwaysBlock) {
            return new OutputMatch(false, content, null);
        }
        return new OutputMatch(true, result, hits.isEmpty() ? "命中常驻输出策略" : String.join(", ", hits));
    }

    private boolean scopeMatches(JsonNode scope, ExecutionContext context) {
        if (!longList(scope.path("userIds")).isEmpty() && !longList(scope.path("userIds")).contains(context.currentUser().userId())) {
            return false;
        }
        if (!longList(scope.path("agentIds")).isEmpty() && !longList(scope.path("agentIds")).contains(context.agentContext().agentId())) {
            return false;
        }
        if (!longList(scope.path("toolIds")).isEmpty() || !longList(scope.path("workflowIds")).isEmpty()) {
            return false;
        }
        List<String> roleScope = textList(scope.path("roleCodes"));
        if (!roleScope.isEmpty()) {
            Set<String> actual = context.currentUser().roleCodes() == null ? Set.of() : context.currentUser().roleCodes().stream()
                    .map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (roleScope.stream().map(item -> item.toLowerCase(Locale.ROOT)).noneMatch(actual::contains)) {
                return false;
            }
        }
        List<String> envScope = textList(scope.path("envs"));
        return envScope.isEmpty() || activeEnvironments.stream().anyMatch(env -> containsIgnoreCase(envScope, env));
    }

    private boolean rolloutMatches(JsonNode rollout, Long userId) {
        String mode = rollout.path("mode").asText("all").toLowerCase(Locale.ROOT);
        if ("user_whitelist".equals(mode)) {
            return userId != null && longList(rollout.path("userIds")).contains(userId);
        }
        if ("percentage".equals(mode)) {
            int percentage = rollout.path("percentage").asInt(0);
            return userId != null && percentage > 0 && Math.abs(Long.hashCode(userId)) % 100 < percentage;
        }
        return true;
    }

    private Map<String, Object> hitMetadata(JsonNode rule, ExecutionContext context, String action, String detail) {
        LinkedHashMap<String, Object> primary = new LinkedHashMap<>();
        primary.put("policyCode", rule.path("policyCode").asText());
        primary.put("policyName", rule.path("policyName").asText());
        primary.put("version", rule.path("version").asInt(1));
        primary.put("priority", rule.path("priority").asInt(100));
        primary.put("layer", rule.path("layer").asText("global"));
        primary.put("actionType", action);
        primary.put("hitDetail", detail);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyDecision", action);
        metadata.put("stage", "output");
        metadata.put("resourceId", context.agentContext().agentId());
        metadata.put("resourceKind", "agent");
        metadata.put("resourceCode", context.agentContext().agentCode());
        metadata.put("resourceName", context.agentContext().agentName());
        metadata.put("primaryPolicy", primary);
        metadata.put("matchedPolicies", List.of(primary));
        return metadata;
    }

    private List<JsonNode> policyRules(ExecutionContext context) {
        JsonNode metadata = context.agentContext().metadata();
        JsonNode rules = metadata == null ? null : metadata.path("policyCenter").path("rules");
        if (rules == null || !rules.isArray()) {
            return List.of();
        }
        ArrayList<JsonNode> result = new ArrayList<>();
        rules.forEach(result::add);
        return result;
    }

    private boolean supportsOutputStage(JsonNode rule) {
        String stage = rule.path("stage").asText("input").toLowerCase(Locale.ROOT);
        return "output".equals(stage) || "both".equals(stage);
    }

    private List<String> mergedKeywords(JsonNode match) {
        ArrayList<String> result = new ArrayList<>(textList(match.path("blockedKeywords")));
        result.addAll(textList(match.path("keywords")));
        return result.stream().distinct().toList();
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) result.add(value);
        });
        return result;
    }

    private List<Long> longList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        ArrayList<Long> result = new ArrayList<>();
        node.forEach(item -> { if (item.canConvertToLong()) result.add(item.asLong()); });
        return result;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(item -> item.equalsIgnoreCase(target));
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = node == null ? "" : node.asText("").trim();
        return value.isBlank() ? defaultValue : value;
    }

    private static List<String> splitProfiles(String profiles) {
        if (profiles == null || profiles.isBlank()) return List.of("default");
        return java.util.Arrays.stream(profiles.split(",")).map(String::trim).filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private record OutputMatch(boolean hit, String content, String detail) {
    }
}

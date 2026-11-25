package com.zc.agent.core.modules.runtime.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyDecision;
import com.zc.agent.core.modules.runtime.capability.model.CapabilityPolicyRequest;
import com.zc.agent.core.modules.runtime.capability.model.RuntimeCapability;
import com.zc.agent.core.modules.runtime.capability.model.ToolSideEffectLevel;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tool 调用前的确定性 Capability Policy 决策引擎。
 *
 * <p>引擎只消费 Registry 下发的资源声明和策略快照，不读取 Planner 生成的风险描述。</p>
 */
@Service
public class CapabilityPolicyEngine {

    private static final Set<String> WRITE_CAPABILITIES = Set.of(
            "filesystem.write", "network.write", "database.write",
            "artifact.publish", "external.side_effect");
    private final ObjectMapper objectMapper;
    private final List<String> activeEnvironments;

    public CapabilityPolicyEngine(ObjectMapper objectMapper,
                                  @Value("${spring.profiles.active:default}") String activeProfiles) {
        this.objectMapper = objectMapper;
        this.activeEnvironments = splitProfiles(activeProfiles);
    }

    /**
     * 根据身份、Agent、Project、Tool 声明、参数和目标资源生成稳定决策。
     */
    public CapabilityPolicyDecision decide(CapabilityPolicyRequest request) {
        RegistryAgentTool tool = request.tool();
        ExecutionContext context = request.executionContext();
        List<String> capabilities;
        String sideEffectLevel;
        try {
            capabilities = RuntimeCapability.normalize(tool.runtimeCapabilities());
            sideEffectLevel = resolveSideEffectLevel(tool);
            ToolCapabilityInference.InferredDeclaration inferred = ToolCapabilityInference.merge(
                    tool, capabilities, sideEffectLevel);
            capabilities = inferred.capabilities();
            sideEffectLevel = inferred.sideEffectLevel();
        } catch (IllegalArgumentException exception) {
            return decision("block", "capability.invalid_declaration", "非法能力声明", 1,
                    exception.getMessage(), List.of(), "unknown", request, List.of(), null, null);
        }

        JsonNode arguments = canonicalize(objectMapper.valueToTree(
                request.invocation().arguments() == null ? Map.of() : request.invocation().arguments()));
        ObjectNode parameterSnapshot = objectMapper.createObjectNode();
        parameterSnapshot.put("inputText", request.invocation().toolInputText() == null
                ? "" : request.invocation().toolInputText());
        parameterSnapshot.set("arguments", arguments);
        String parameterHash = hash(parameterSnapshot.toString());
        List<RuleHit> hits = explicitRuleHits(context, tool, capabilities, sideEffectLevel, arguments);
        RuleHit hit = hits.isEmpty() ? baseline(tool, capabilities, sideEffectLevel) : hits.get(0);
        String targetHash = hit.targets().isEmpty()
                ? null : hash(objectMapper.valueToTree(hit.targets()).toString());
        String approvalNodeId = "approval".equals(hit.action())
                ? buildApprovalNodeId(tool, hit, capabilities, sideEffectLevel, parameterHash)
                : null;
        boolean approvalGranted = CapabilityApprovalContext.isApproved(approvalNodeId);
        String effectiveAction = approvalGranted ? "allow" : hit.action();
        return decision(effectiveAction, hit.policyCode(), hit.policyName(), hit.version(), hit.reason(),
                hit.matchedCapabilities(), sideEffectLevel, request, hits, parameterHash, targetHash,
                approvalNodeId, approvalGranted, hit);
    }

    private String buildApprovalNodeId(RegistryAgentTool tool,
                                       RuleHit hit,
                                       List<String> capabilities,
                                       String sideEffectLevel,
                                       String parameterHash) {
        String policyFingerprint = String.join("|",
                hit.policyCode(),
                String.valueOf(hit.version()),
                String.join(",", capabilities),
                sideEffectLevel,
                parameterHash);
        return "capability:" + tool.toolCode() + ":" + hash(policyFingerprint).substring(0, 16);
    }

    private List<RuleHit> explicitRuleHits(ExecutionContext context,
                                           RegistryAgentTool tool,
                                           List<String> capabilities,
                                           String sideEffectLevel,
                                           JsonNode arguments) {
        JsonNode rules = tool.metadata() == null
                ? objectMapper.createArrayNode()
                : tool.metadata().path("policyCenter").path("rules");
        if (!rules.isArray()) {
            return List.of();
        }
        ArrayList<RuleHit> hits = new ArrayList<>();
        for (JsonNode rule : rules) {
            JsonNode metadata = rule.path("metadata");
            JsonNode policy = metadata.path("capabilityPolicy");
            if (!"capability".equals(metadata.path("policyType").asText()) || !policy.isObject()) {
                continue;
            }
            if (rule.path("enabled").isBoolean() && !rule.path("enabled").asBoolean()) {
                continue;
            }
            String stage = metadata.path("stage").asText("input");
            if (!"input".equals(stage) && !"both".equals(stage)) {
                continue;
            }
            if (!scopeMatches(rule.path("scope"), policy, context, tool)
                    || !rolloutMatches(rule.path("rollout"), context)
                    || !capabilitiesMatch(policy.path("capabilities"), capabilities)
                    || !sideEffectMatches(policy.path("sideEffectLevels"), sideEffectLevel)
                    || !parameterConditionsMatch(policy.path("parameterConditions"), arguments)) {
                continue;
            }
            List<String> targets = targetValues(policy.path("targetPaths"), arguments);
            if (!targetPrefixesMatch(policy.path("targetPrefixes"), targets)) {
                continue;
            }
            String requestedAction = normalizeAction(rule.path("action").path("type").asText("block"));
            String action = "sandbox".equals(requestedAction) && !sandboxSatisfied(tool)
                    ? "block" : requestedAction;
            String reason = rule.path("action").path("reason").asText("").trim();
            if (reason.isBlank()) {
                reason = "命中 Capability Policy " + rule.path("policyCode").asText("capability.policy");
            }
            if ("sandbox".equals(requestedAction) && "block".equals(action)) {
                reason = reason + "；当前 Tool 未声明隔离运行边界";
            }
            hits.add(new RuleHit(
                    rule.path("policyCode").asText("capability.policy"),
                    rule.path("policyName").asText("Capability Policy"),
                    rule.path("version").asInt(1),
                    rule.path("priority").asInt(100),
                    action,
                    requestedAction,
                    reason,
                    matchedCapabilities(policy.path("capabilities"), capabilities),
                    targets,
                    longList(rule.path("action").path("approvalUserIds")),
                    textList(rule.path("action").path("approvalRoleCodes"))
            ));
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(RuleHit::priority).reversed()
                        .thenComparing(RuleHit::policyCode))
                .toList();
    }

    private RuleHit baseline(RegistryAgentTool tool,
                             List<String> capabilities,
                             String sideEffectLevel) {
        List<String> writes = capabilities.stream().filter(WRITE_CAPABILITIES::contains).toList();
        if ((capabilities.contains("process.execute") || capabilities.contains("filesystem.write"))
                && sandboxSatisfied(tool)
                && writes.stream().allMatch("filesystem.write"::equals)
                && "none".equals(sideEffectLevel)) {
            return new RuleHit("capability.baseline.sandbox", "平台隔离基线", 1, 0,
                    "sandbox", "sandbox", "Tool 的进程或文件写入仅允许在声明的隔离边界内执行",
                    capabilities, List.of(), List.of(), List.of());
        }
        if (!writes.isEmpty() || !"none".equals(sideEffectLevel) || capabilities.contains("process.execute")) {
            return new RuleHit("capability.baseline.approval", "平台高风险能力基线", 1, 0,
                    "approval", "approval", "Tool 触达写入、进程执行或外部副作用边界，需要人工审批",
                    writes.isEmpty() ? capabilities : writes, List.of(), List.of(), List.of());
        }
        return new RuleHit("capability.baseline.allow", "平台只读能力基线", 1, 0,
                "allow", "allow", "Tool 仅声明只读能力或无受控运行时能力",
                capabilities, List.of(), List.of(), List.of());
    }

    private CapabilityPolicyDecision decision(String action,
                                                String policyCode,
                                                String policyName,
                                                Integer version,
                                                String reason,
                                                List<String> matchedCapabilities,
                                                String sideEffectLevel,
                                                CapabilityPolicyRequest request,
                                                List<RuleHit> hits,
                                                String parameterHash,
                                                String targetHash) {
        RuleHit hit = new RuleHit(policyCode, policyName, version, 0, action, action, reason,
                matchedCapabilities, List.of(), List.of(), List.of());
        return decision(action, policyCode, policyName, version, reason, matchedCapabilities,
                sideEffectLevel, request, hits, parameterHash, targetHash, null, false, hit);
    }

    private CapabilityPolicyDecision decision(String action,
                                                String policyCode,
                                                String policyName,
                                                Integer version,
                                                String reason,
                                                List<String> matchedCapabilities,
                                                String sideEffectLevel,
                                                CapabilityPolicyRequest request,
                                                List<RuleHit> hits,
                                                String parameterHash,
                                                String targetHash,
                                                String approvalNodeId,
                                                boolean approvalGranted,
                                                RuleHit primaryHit) {
        RegistryAgentTool tool = request.tool();
        ExecutionContext.CurrentUserContext user = request.executionContext().currentUser();
        LinkedHashMap<String, Object> primaryPolicy = new LinkedHashMap<>();
        primaryPolicy.put("policyCode", policyCode);
        primaryPolicy.put("policyName", policyName);
        primaryPolicy.put("version", version);
        primaryPolicy.put("priority", primaryHit.priority());
        primaryPolicy.put("actionType", primaryHit.requestedAction());
        primaryPolicy.put("hitDetail", reason);
        primaryPolicy.put("approvalUserIds", primaryHit.approvalUserIds());
        primaryPolicy.put("approvalRoleCodes", primaryHit.approvalRoleCodes());
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyDecision", action);
        metadata.put("requestedAction", primaryHit.requestedAction());
        metadata.put("policyType", "capability");
        metadata.put("resourceId", tool.toolId());
        metadata.put("resourceKind", "tool");
        metadata.put("resourceCode", tool.toolCode());
        metadata.put("resourceName", tool.toolName());
        metadata.put("primaryPolicy", primaryPolicy);
        metadata.put("matchedPolicies", hits.stream().map(RuleHit::safeMetadata).toList());
        metadata.put("runtimeCapabilities", matchedCapabilities);
        metadata.put("sideEffectLevel", sideEffectLevel);
        metadata.put("parameterHash", parameterHash == null ? "" : parameterHash);
        metadata.put("targetHash", targetHash == null ? "" : targetHash);
        metadata.put("approvalNodeId", approvalNodeId == null ? "" : approvalNodeId);
        metadata.put("approvalGranted", approvalGranted);
        if (user != null && user.tenantId() != null) {
            metadata.put("tenantId", user.tenantId());
        }
        if (user != null && user.projectId() != null) {
            metadata.put("projectId", user.projectId());
        }
        metadata.put("activeEnvironments", activeEnvironments);
        return new CapabilityPolicyDecision(action, policyCode, policyName, version, reason,
                List.copyOf(matchedCapabilities), sideEffectLevel, parameterHash, targetHash,
                approvalNodeId, approvalGranted, Collections.unmodifiableMap(metadata));
    }

    private boolean scopeMatches(JsonNode scope,
                                 JsonNode policy,
                                 ExecutionContext context,
                                 RegistryAgentTool tool) {
        ExecutionContext.CurrentUserContext user = context.currentUser();
        ExecutionContext.AgentContext agent = context.agentContext();
        if (!containsOrEmpty(longList(scope.path("userIds")), user == null ? null : user.userId())
                || !containsOrEmpty(longList(scope.path("agentIds")), agent == null ? null : agent.agentId())
                || !containsOrEmpty(longList(scope.path("toolIds")), tool.toolId())
                || !containsOrEmpty(longList(policy.path("projectIds")), user == null ? null : user.projectId())) {
            return false;
        }
        if (!matchesIgnoreCase(textList(scope.path("roleCodes")), user == null ? List.of() : user.roleCodes())) {
            return false;
        }
        if (!containsIgnoreCaseOrEmpty(textList(scope.path("agentCodes")), agent == null ? null : agent.agentCode())
                || !containsIgnoreCaseOrEmpty(textList(scope.path("toolCodes")), tool.toolCode())) {
            return false;
        }
        List<String> envs = textList(scope.path("envs"));
        return envs.isEmpty() || activeEnvironments.stream().anyMatch(env -> containsIgnoreCase(envs, env));
    }

    private boolean rolloutMatches(JsonNode rollout, ExecutionContext context) {
        String mode = rollout.path("mode").asText("all");
        Long userId = context.currentUser() == null ? null : context.currentUser().userId();
        if ("user_whitelist".equals(mode)) {
            return userId != null && longList(rollout.path("userIds")).contains(userId);
        }
        if ("percentage".equals(mode)) {
            int percentage = rollout.path("percentage").asInt(0);
            return userId != null && percentage > 0 && Math.floorMod(Long.hashCode(userId), 100) < percentage;
        }
        return true;
    }

    private boolean capabilitiesMatch(JsonNode expected, List<String> actual) {
        List<String> values = textList(expected);
        return values.isEmpty() || values.stream().anyMatch(actual::contains);
    }

    private List<String> matchedCapabilities(JsonNode expected, List<String> actual) {
        List<String> values = textList(expected);
        return values.isEmpty() ? actual : actual.stream().filter(values::contains).toList();
    }

    private boolean sideEffectMatches(JsonNode expected, String actual) {
        List<String> values = textList(expected);
        return values.isEmpty() || values.contains(actual);
    }

    private boolean parameterConditionsMatch(JsonNode conditions, JsonNode arguments) {
        if (!conditions.isArray()) {
            return true;
        }
        for (JsonNode condition : conditions) {
            JsonNode actual = resolvePath(arguments, condition.path("path").asText(""));
            String operator = condition.path("operator").asText("equals");
            List<String> values = textList(condition.path("values"));
            if (values.isEmpty() && condition.has("value")) {
                values = List.of(condition.path("value").asText());
            }
            boolean matched = switch (operator) {
                case "exists" -> !actual.isMissingNode() && !actual.isNull();
                case "in" -> values.contains(actual.asText());
                case "prefix" -> values.stream().anyMatch(value -> actual.asText("").startsWith(value));
                default -> !values.isEmpty() && values.get(0).equals(actual.asText());
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private List<String> targetValues(JsonNode targetPaths, JsonNode arguments) {
        return textList(targetPaths).stream()
                .map(path -> resolvePath(arguments, path).asText(""))
                .filter(value -> !value.isBlank())
                .sorted()
                .toList();
    }

    private boolean targetPrefixesMatch(JsonNode prefixesNode, List<String> targets) {
        List<String> prefixes = textList(prefixesNode);
        return prefixes.isEmpty() || targets.stream().anyMatch(
                target -> prefixes.stream().anyMatch(target::startsWith));
    }

    private JsonNode resolvePath(JsonNode node, String path) {
        if (path == null || path.isBlank()) {
            return node;
        }
        if (path.startsWith("/")) {
            return node.at(path);
        }
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private boolean sandboxSatisfied(RegistryAgentTool tool) {
        JsonNode metadata = tool.metadata();
        return "SANDBOX_EXECUTE".equals(tool.toolCode())
                || metadata != null && (metadata.path("isolated").asBoolean(false)
                || metadata.path("workspaceScoped").asBoolean(false));
    }

    private String resolveSideEffectLevel(RegistryAgentTool tool) {
        String level = tool.sideEffectLevel();
        if (level == null || level.isBlank()) {
            level = tool.metadata() == null ? "none"
                    : tool.metadata().path("sideEffectPolicy").path("level").asText("none");
        }
        return ToolSideEffectLevel.require(level);
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "block" : action.trim().toLowerCase(Locale.ROOT);
        return Set.of("allow", "sandbox", "approval", "block").contains(normalized) ? normalized : "block";
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            ArrayList<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> sorted.set(name, canonicalize(node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(canonicalize(item)));
            return array;
        }
        return node;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算 Capability Policy 参数哈希", exception);
        }
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private List<Long> longList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<Long> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.canConvertToLong()) {
                values.add(item.asLong());
            }
        });
        return List.copyOf(values);
    }

    private boolean containsOrEmpty(List<Long> values, Long target) {
        return values.isEmpty() || target != null && values.contains(target);
    }

    private boolean matchesIgnoreCase(List<String> expected, List<String> actual) {
        return expected.isEmpty() || actual != null && expected.stream().anyMatch(
                value -> actual.stream().anyMatch(value::equalsIgnoreCase));
    }

    private boolean containsIgnoreCaseOrEmpty(List<String> values, String target) {
        return values.isEmpty() || target != null && containsIgnoreCase(values, target);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private static List<String> splitProfiles(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return List.of("default");
        }
        return java.util.Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private record RuleHit(
            String policyCode,
            String policyName,
            int version,
            int priority,
            String action,
            String requestedAction,
            String reason,
            List<String> matchedCapabilities,
            List<String> targets,
            List<Long> approvalUserIds,
            List<String> approvalRoleCodes
    ) {
        private Map<String, Object> safeMetadata() {
            return Map.of(
                    "policyCode", policyCode,
                    "policyName", policyName,
                    "version", version,
                    "priority", priority,
                    "actionType", requestedAction,
                    "matchedCapabilities", matchedCapabilities,
                    "targetCount", targets.size()
            );
        }
    }
}

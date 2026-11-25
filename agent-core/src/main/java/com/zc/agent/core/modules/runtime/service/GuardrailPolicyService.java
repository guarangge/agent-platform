package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import com.zc.agent.core.modules.workflow.service.ApprovalRequiredException;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时 Guardrails 与 Tool 审批策略服务。
 * 统一在执行前校验输入风险，并决定是否需要人工审批。
 */
@Service
public class GuardrailPolicyService {

    private static final int LEGACY_PRIORITY_BASE = 10;
    private static final Logger log = LoggerFactory.getLogger(GuardrailPolicyService.class);

    private final List<String> activeEnvironments;

    public GuardrailPolicyService(@Value("${spring.profiles.active:default}") String activeProfiles) {
        this.activeEnvironments = splitProfiles(activeProfiles);
    }

    /**
     * 校验计划步骤执行前的 guardrails 与审批策略。
     */
    public void validatePlanStep(ExecutionContext executionContext,
                                 ExecutionPlan.ExecutionPlanStep step,
                                 String stepInputText) {
        if ("tool".equals(step.stepType())) {
            RegistryAgentTool tool = executionContext.availableTools().stream()
                    .filter(item -> step.targetToolCode() != null && step.targetToolCode().equals(item.toolCode()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("当前计划指定的 Tool 不存在: " + step.targetToolCode()));
            enforcePolicy(
                    executionContext,
                    ResourceDescriptor.fromTool(tool),
                    stepInputText,
                    "guardrail:" + step.stepId(),
                    "工具审批 - " + tool.toolName(),
                    "step_guardrail",
                    step.stepNo()
            );
            return;
        }
        if ("workflow".equals(step.stepType())) {
            RegistryAgentWorkflow workflow = executionContext.availableWorkflows().stream()
                    .filter(item -> step.targetWorkflowId() != null && step.targetWorkflowId().equals(item.workflowId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("当前计划指定的 Workflow 不存在: " + step.targetWorkflowId()));
            enforcePolicy(
                    executionContext,
                    ResourceDescriptor.fromWorkflow(workflow),
                    stepInputText,
                    "guardrail:" + step.stepId(),
                    "流程审批 - " + workflow.workflowName(),
                    "step_guardrail",
                    step.stepNo()
            );
        }
    }

    /**
     * 校验 Workflow 内部 Tool 节点执行前的 guardrails 与审批策略。
     */
    public void validateWorkflowToolNode(ExecutionContext executionContext,
                                         RegistryAgentTool tool,
                                         String nodeId,
                                         String nodeName,
                                         String toolInputText) {
        Integer maxInputLength = resolveLegacyMaxInputLength(tool == null ? null : tool.metadata());
        log.info("Guardrail校验[workflowTool] toolCode={}, nodeId={}, inputLength={}, maxInputLength={}, nodeName={}",
                tool == null ? null : tool.toolCode(),
                nodeId,
                toolInputText == null ? 0 : toolInputText.length(),
                maxInputLength,
                nodeName);
        enforcePolicy(
                executionContext,
                ResourceDescriptor.fromTool(tool),
                toolInputText,
                nodeId,
                nodeName == null || nodeName.isBlank() ? "工具审批 - " + tool.toolName() : nodeName,
                "workflow",
                null
        );
    }

    private void enforcePolicy(ExecutionContext executionContext,
                               ResourceDescriptor resource,
                               String inputText,
                               String approvalNodeId,
                               String defaultApprovalNodeName,
                               String approvalSourceType,
                               Integer nextPlanStepNo) {
        GuardrailDecision decision = evaluatePolicy(executionContext, resource, inputText);
        if (!decision.hit()) {
            return;
        }
        if ("block".equals(decision.decisionType())) {
            throw new PolicyViolationException(decision.message(), decision.metadata());
        }
        if ("approval".equals(decision.decisionType())) {
            throw new ApprovalRequiredException(
                    decision.message(),
                    approvalNodeId,
                    decision.approvalNodeName(defaultApprovalNodeName),
                    null,
                    List.of(),
                    List.of(),
                    approvalSourceType,
                    nextPlanStepNo,
                    decision.metadata()
            );
        }
    }

    private GuardrailDecision evaluatePolicy(ExecutionContext executionContext,
                                             ResourceDescriptor resource,
                                             String inputText) {
        ArrayList<PolicyRule> allRules = new ArrayList<>();
        allRules.addAll(resolvePolicyCenterRules(resource.metadata()));
        allRules.addAll(resolveLegacyRules(resource.metadata()));
        log.info("Guardrail规则展开 resource={} kind={} code={} inputLength={} rules={}",
                resource.resourceName(),
                resource.resourceKind(),
                resource.resourceCode(),
                inputText == null ? 0 : inputText.trim().length(),
                allRules.stream()
                        .map(rule -> rule.policyCode()
                                + "|layer=" + rule.layer()
                                + "|priority=" + rule.priority()
                                + "|max=" + rule.match().maxInputLength()
                                + "|mode=" + rule.match().mode())
                        .toList());
        List<PolicyHit> hits = allRules.stream()
                .filter(PolicyRule::enabled)
                .filter(rule -> "input".equals(rule.stage()) || "both".equals(rule.stage()))
                .filter(rule -> !"redact".equalsIgnoreCase(rule.action().type()))
                .filter(rule -> scopeMatches(rule.scope(), executionContext, resource))
                .filter(rule -> rolloutMatches(rule.rollout(), executionContext.currentUser().userId()))
                .map(rule -> evaluateRule(rule, inputText))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt((PolicyHit item) -> item.rule().priority()).reversed())
                .toList();
        if (hits.isEmpty()) {
            return GuardrailDecision.noHit();
        }
        log.warn("Guardrail命中 resource={} kind={} code={} hits={}",
                resource.resourceName(),
                resource.resourceKind(),
                resource.resourceCode(),
                hits.stream()
                        .map(hit -> hit.rule().policyCode()
                                + "|layer=" + hit.rule().layer()
                                + "|priority=" + hit.rule().priority()
                                + "|detail=" + hit.hitDetail())
                        .toList());
        PolicyHit primaryHit = hits.get(0);
        List<Map<String, Object>> hitDetails = hits.stream().map(this::toHitMetadata).toList();
        String decisionType = normalizeAction(primaryHit.rule().action().type());
        String reason = resolveDecisionReason(primaryHit, resource);
        String message = switch (decisionType) {
            case "approval" -> "资源「" + resource.resourceName() + "」命中审批策略：" + reason;
            case "block" -> "资源「" + resource.resourceName() + "」执行前校验失败：" + reason;
            default -> reason;
        };
        return new GuardrailDecision(
                true,
                decisionType,
                message,
                primaryHit.rule().action().approvalNodeName(),
                buildDecisionMetadata(decisionType, resource, primaryHit, hitDetails)
        );
    }

    private List<PolicyRule> resolvePolicyCenterRules(JsonNode metadata) {
        JsonNode rulesNode = metadataNode(metadata, "policyCenter").path("rules");
        if (!rulesNode.isArray()) {
            return List.of();
        }
        ArrayList<PolicyRule> rules = new ArrayList<>();
        int index = 0;
        for (JsonNode item : rulesNode) {
            // Capability Policy 由 ToolRouter 调用前的专用引擎处理，避免被文本 Guardrail 重复解释。
            if ("capability".equals(item.path("metadata").path("policyType").asText())) {
                continue;
            }
            index++;
            rules.add(new PolicyRule(
                    textOrDefault(item.path("policyCode"), "policy_" + index),
                    textOrDefault(item.path("policyName"), "策略" + index),
                    !item.path("enabled").isBoolean() || item.path("enabled").asBoolean(true),
                    item.path("priority").asInt(100),
                    item.path("layer").asText("custom"),
                    item.path("version").asInt(1),
                    item.path("stage").asText("input").toLowerCase(Locale.ROOT),
                    parseScope(item.path("scope")),
                    parseRollout(item.path("rollout")),
                    parseMatch(item.path("match")),
                    parseAction(item.path("action"))
            ));
        }
        return rules;
    }

    private List<PolicyRule> resolveLegacyRules(JsonNode metadata) {
        ArrayList<PolicyRule> rules = new ArrayList<>();
        JsonNode guardrailsNode = metadataNode(metadata, "guardrails");
        if (guardrailsNode.path("requireNonEmptyInput").asBoolean(false)) {
            rules.add(new PolicyRule(
                    "legacy.require_non_empty_input",
                    "输入不能为空",
                    true,
                    LEGACY_PRIORITY_BASE + 3,
                    "legacy",
                    1,
                    "input",
                    PolicyScope.empty(),
                    PolicyRollout.all(),
                    new PolicyMatch(true, 0, List.of(), List.of(), "always"),
                    new PolicyAction("block", null, List.of(), List.of(), "输入不能为空")
            ));
        }
        int maxInputLength = guardrailsNode.path("maxInputLength").asInt(0);
        if (maxInputLength > 0) {
            rules.add(new PolicyRule(
                    "legacy.max_input_length",
                    "输入长度限制",
                    true,
                    LEGACY_PRIORITY_BASE + 2,
                    "legacy",
                    1,
                    "input",
                    PolicyScope.empty(),
                    PolicyRollout.all(),
                    new PolicyMatch(false, maxInputLength, List.of(), List.of(), "always"),
                    new PolicyAction("block", null, List.of(), List.of(), "输入长度超过限制")
            ));
        }
        List<String> blockedKeywords = textList(guardrailsNode.path("blockedKeywords"));
        if (!blockedKeywords.isEmpty()) {
            rules.add(new PolicyRule(
                    "legacy.blocked_keywords",
                    "输入拦截词",
                    true,
                    LEGACY_PRIORITY_BASE + 1,
                    "legacy",
                    1,
                    "input",
                    PolicyScope.empty(),
                    PolicyRollout.all(),
                    new PolicyMatch(false, 0, blockedKeywords, List.of(), "always"),
                    new PolicyAction("block", null, List.of(), List.of(), "输入命中拦截词")
            ));
        }
        JsonNode approvalNode = metadataNode(metadata, "approval");
        if (approvalNode.path("required").asBoolean(false)) {
            rules.add(new PolicyRule(
                    "legacy.approval",
                    "默认审批策略",
                    true,
                    LEGACY_PRIORITY_BASE,
                    "legacy",
                    1,
                    "input",
                    PolicyScope.empty(),
                    PolicyRollout.all(),
                    new PolicyMatch(false, 0, List.of(), textList(approvalNode.path("keywords")), approvalNode.path("mode").asText("always")),
                    new PolicyAction(
                            "approval",
                            null,
                            longList(approvalNode.path("approvalUserIds")),
                            textList(approvalNode.path("approvalRoleCodes")),
                            textOrDefault(approvalNode.path("reason"), "命中默认审批策略")
                    )
            ));
        }
        return rules;
    }

    private Integer resolveLegacyMaxInputLength(JsonNode metadata) {
        JsonNode guardrailsNode = metadataNode(metadata, "guardrails");
        int maxInputLength = guardrailsNode.path("maxInputLength").asInt(0);
        return maxInputLength > 0 ? maxInputLength : null;
    }

    private PolicyScope parseScope(JsonNode scopeNode) {
        return new PolicyScope(
                longList(scopeNode.path("userIds")),
                longList(scopeNode.path("agentIds")),
                longList(scopeNode.path("toolIds")),
                longList(scopeNode.path("workflowIds")),
                textList(scopeNode.path("roleCodes")),
                textList(scopeNode.path("agentCodes")),
                textList(scopeNode.path("toolCodes")),
                textList(scopeNode.path("workflowCodes")),
                textList(scopeNode.path("envs"))
        );
    }

    private PolicyRollout parseRollout(JsonNode rolloutNode) {
        return new PolicyRollout(
                textOrDefault(rolloutNode.path("mode"), "all"),
                rolloutNode.path("percentage").asInt(0),
                longList(rolloutNode.path("userIds"))
        );
    }

    private PolicyMatch parseMatch(JsonNode matchNode) {
        return new PolicyMatch(
                matchNode.path("requireNonEmptyInput").asBoolean(false),
                matchNode.path("maxInputLength").asInt(0),
                textList(matchNode.path("blockedKeywords")),
                textList(matchNode.path("keywords")),
                matchNode.path("mode").asText("always")
        );
    }

    private PolicyAction parseAction(JsonNode actionNode) {
        return new PolicyAction(
                actionNode.path("type").asText("block"),
                emptyToNull(actionNode.path("approvalNodeName").asText(null)),
                longList(actionNode.path("approvalUserIds")),
                textList(actionNode.path("approvalRoleCodes")),
                emptyToNull(actionNode.path("reason").asText(null))
        );
    }

    private boolean scopeMatches(PolicyScope scope,
                                 ExecutionContext executionContext,
                                 ResourceDescriptor resource) {
        if (!scope.userIds().isEmpty() && !scope.userIds().contains(executionContext.currentUser().userId())) {
            return false;
        }
        if (!scope.agentIds().isEmpty() && !scope.agentIds().contains(executionContext.agentContext().agentId())) {
            return false;
        }
        if (!scope.roleCodes().isEmpty()) {
            Set<String> roleCodes = executionContext.currentUser().roleCodes() == null
                    ? Set.of()
                    : executionContext.currentUser().roleCodes().stream()
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            boolean matched = scope.roleCodes().stream()
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .anyMatch(roleCodes::contains);
            if (!matched) {
                return false;
            }
        }
        if ("tool".equals(resource.resourceKind())) {
            if (!scope.toolIds().isEmpty() && !scope.toolIds().contains(resource.resourceId())) {
                return false;
            }
            if (!scope.workflowIds().isEmpty()) {
                return false;
            }
        }
        if ("workflow".equals(resource.resourceKind())) {
            if (!scope.workflowIds().isEmpty() && !scope.workflowIds().contains(resource.resourceId())) {
                return false;
            }
            if (!scope.toolIds().isEmpty()) {
                return false;
            }
        }
        if (!scope.agentCodes().isEmpty() && !containsIgnoreCase(scope.agentCodes(), executionContext.agentContext().agentCode())) {
            return false;
        }
        if ("tool".equals(resource.resourceKind()) && !scope.toolCodes().isEmpty() && !containsIgnoreCase(scope.toolCodes(), resource.resourceCode())) {
            return false;
        }
        if ("workflow".equals(resource.resourceKind()) && !scope.workflowCodes().isEmpty() && !containsIgnoreCase(scope.workflowCodes(), resource.resourceCode())) {
            return false;
        }
        if (!scope.envs().isEmpty()) {
            boolean matched = activeEnvironments.stream()
                    .anyMatch(activeEnv -> containsIgnoreCase(scope.envs(), activeEnv));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean rolloutMatches(PolicyRollout rollout, Long userId) {
        String mode = rollout.mode().trim().toLowerCase(Locale.ROOT);
        if ("all".equals(mode)) {
            return true;
        }
        if ("user_whitelist".equals(mode)) {
            return userId != null && rollout.userIds().contains(userId);
        }
        if ("percentage".equals(mode)) {
            if (userId == null || rollout.percentage() <= 0) {
                return false;
            }
            return Math.abs(Long.hashCode(userId)) % 100 < rollout.percentage();
        }
        return true;
    }

    private PolicyHit evaluateRule(PolicyRule rule, String inputText) {
        String normalizedInput = inputText == null ? "" : inputText.trim();
        boolean requireNonEmpty = rule.match().requireNonEmptyInput();
        boolean blankInput = normalizedInput.isBlank();
        int actualLength = normalizedInput.length();
        int maxInputLength = rule.match().maxInputLength();
        boolean overMaxInputLength = maxInputLength > 0 && actualLength > maxInputLength;
        log.info("Guardrail判定 policyCode={}, requireNonEmpty={}, blankInput={}, actualLength={}, maxInputLength={}, overMaxInputLength={}",
                rule.policyCode(),
                requireNonEmpty,
                blankInput,
                actualLength,
                maxInputLength,
                overMaxInputLength);
        if (requireNonEmpty && blankInput) {
            return new PolicyHit(rule, "输入不能为空");
        }
        if (overMaxInputLength) {
            return new PolicyHit(rule, "输入长度超过限制，最大允许 " + maxInputLength + " 字符");
        }
        String blockedKeyword = firstMatchedKeyword(normalizedInput, rule.match().blockedKeywords());
        if (blockedKeyword != null) {
            return new PolicyHit(rule, "输入命中拦截词 `" + blockedKeyword + "`");
        }
        String matchMode = rule.match().mode().trim().toLowerCase(Locale.ROOT);
        if ("keyword_match".equals(matchMode)) {
            String hitKeyword = firstMatchedKeyword(normalizedInput, rule.match().keywords());
            return hitKeyword == null ? null : new PolicyHit(rule, "输入命中关键词 `" + hitKeyword + "`");
        }
        if (!rule.match().keywords().isEmpty()) {
            String hitKeyword = firstMatchedKeyword(normalizedInput, rule.match().keywords());
            if (hitKeyword != null) {
                return new PolicyHit(rule, "输入命中关键词 `" + hitKeyword + "`");
            }
        }
        boolean hasConditionalMatch = requireNonEmpty
                || maxInputLength > 0
                || !rule.match().blockedKeywords().isEmpty()
                || !rule.match().keywords().isEmpty();
        if ("always".equals(matchMode) && !hasConditionalMatch) {
            return new PolicyHit(rule, emptyToNull(rule.action().reason()) == null ? "命中常驻策略" : rule.action().reason());
        }
        return null;
    }

    private String resolveDecisionReason(PolicyHit hit, ResourceDescriptor resource) {
        StringBuilder builder = new StringBuilder();
        builder.append("策略[").append(hit.rule().policyCode()).append(" / ").append(hit.rule().policyName()).append("]");
        builder.append(" layer=").append(hit.rule().layer());
        builder.append(" version=").append(hit.rule().version());
        builder.append(" priority=").append(hit.rule().priority());
        builder.append("，资源=").append(resource.resourceKind()).append(":").append(resource.resourceCode());
        builder.append("，原因：").append(hit.hitDetail());
        if (hit.rule().action().reason() != null && !hit.rule().action().reason().isBlank()
                && !hit.rule().action().reason().equals(hit.hitDetail())) {
            builder.append("，说明：").append(hit.rule().action().reason());
        }
        return builder.toString();
    }

    private Map<String, Object> buildDecisionMetadata(String decisionType,
                                                      ResourceDescriptor resource,
                                                      PolicyHit primaryHit,
                                                      List<Map<String, Object>> hitDetails) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyDecision", decisionType);
        metadata.put("resourceId", resource.resourceId());
        metadata.put("resourceKind", resource.resourceKind());
        metadata.put("resourceCode", resource.resourceCode());
        metadata.put("resourceName", resource.resourceName());
        metadata.put("primaryPolicy", toHitMetadata(primaryHit));
        metadata.put("matchedPolicies", hitDetails);
        metadata.put("activeEnvironments", activeEnvironments);
        return metadata;
    }

    private Map<String, Object> toHitMetadata(PolicyHit hit) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyCode", hit.rule().policyCode());
        metadata.put("policyName", hit.rule().policyName());
        metadata.put("version", hit.rule().version());
        metadata.put("priority", hit.rule().priority());
        metadata.put("layer", hit.rule().layer());
        metadata.put("actionType", normalizeAction(hit.rule().action().type()));
        metadata.put("approvalUserIds", hit.rule().action().approvalUserIds());
        metadata.put("approvalRoleCodes", hit.rule().action().approvalRoleCodes());
        metadata.put("hitDetail", hit.hitDetail());
        metadata.put("reason", hit.rule().action().reason());
        return metadata;
    }

    private JsonNode metadataNode(JsonNode metadata, String fieldName) {
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return MissingNode.getInstance();
        }
        return metadata.path(fieldName);
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = item.asText("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private List<Long> longList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<Long> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.canConvertToLong()) {
                values.add(item.asLong());
            }
        }
        return values;
    }

    private String firstMatchedKeyword(String inputText, List<String> keywords) {
        if (inputText == null || inputText.isBlank() || keywords.isEmpty()) {
            return null;
        }
        String normalizedInput = inputText.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if (!normalizedKeyword.isBlank() && normalizedInput.contains(normalizedKeyword)) {
                return keyword;
            }
        }
        return null;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return values.stream().anyMatch(item -> item != null && item.equalsIgnoreCase(target));
    }

    private String normalizeAction(String actionType) {
        String normalized = actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
        if ("approval".equals(normalized)) {
            return "approval";
        }
        if ("allow".equals(normalized)) {
            return "allow";
        }
        return "block";
    }

    private String textOrDefault(JsonNode node, String defaultValue) {
        String value = emptyToNull(node.asText(null));
        return value == null ? defaultValue : value;
    }

    private String emptyToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> splitProfiles(String activeProfiles) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return List.of("default");
        }
        return java.util.Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private record ResourceDescriptor(
            Long resourceId,
            String resourceKind,
            String resourceCode,
            String resourceName,
            JsonNode metadata
    ) {
        private static ResourceDescriptor fromTool(RegistryAgentTool tool) {
            return new ResourceDescriptor(tool.toolId(), "tool", tool.toolCode(), tool.toolName(), tool.metadata());
        }

        private static ResourceDescriptor fromWorkflow(RegistryAgentWorkflow workflow) {
            return new ResourceDescriptor(workflow.workflowId(), "workflow", workflow.workflowCode(), workflow.workflowName(), workflow.metadata());
        }
    }

    private record PolicyRule(
            String policyCode,
            String policyName,
            boolean enabled,
            int priority,
            String layer,
            int version,
            String stage,
            PolicyScope scope,
            PolicyRollout rollout,
            PolicyMatch match,
            PolicyAction action
    ) {
    }

    private record PolicyScope(
            List<Long> userIds,
            List<Long> agentIds,
            List<Long> toolIds,
            List<Long> workflowIds,
            List<String> roleCodes,
            List<String> agentCodes,
            List<String> toolCodes,
            List<String> workflowCodes,
            List<String> envs
    ) {
        private static PolicyScope empty() {
            return new PolicyScope(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private record PolicyRollout(
            String mode,
            int percentage,
            List<Long> userIds
    ) {
        private static PolicyRollout all() {
            return new PolicyRollout("all", 0, List.of());
        }
    }

    private record PolicyMatch(
            boolean requireNonEmptyInput,
            int maxInputLength,
            List<String> blockedKeywords,
            List<String> keywords,
            String mode
    ) {
    }

    private record PolicyAction(
            String type,
            String approvalNodeName,
            List<Long> approvalUserIds,
            List<String> approvalRoleCodes,
            String reason
    ) {
    }

    private record PolicyHit(
            PolicyRule rule,
            String hitDetail
    ) {
    }

    private record GuardrailDecision(
            boolean hit,
            String decisionType,
            String message,
            String approvalNodeName,
            Map<String, Object> metadata
    ) {
        private static GuardrailDecision noHit() {
            return new GuardrailDecision(false, "allow", null, null, Map.of());
        }

        private String approvalNodeName(String defaultValue) {
            return approvalNodeName == null || approvalNodeName.isBlank() ? defaultValue : approvalNodeName;
        }
    }
}

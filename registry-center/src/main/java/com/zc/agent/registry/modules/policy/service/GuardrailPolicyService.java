package com.zc.agent.registry.modules.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.capability.RuntimeCapabilityContract;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.policy.dto.CreateGuardrailPolicyRequest;
import com.zc.agent.registry.modules.policy.dto.GuardrailPolicyDebugRequest;
import com.zc.agent.registry.modules.policy.dto.GuardrailPolicyPageQueryRequest;
import com.zc.agent.registry.modules.policy.dto.UpdateGuardrailPolicyRequest;
import com.zc.agent.registry.modules.policy.model.GuardrailPolicyDetailRecord;
import com.zc.agent.registry.modules.policy.model.GuardrailPolicyPageRecord;
import com.zc.agent.registry.modules.policy.repository.GuardrailPolicyRepository;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyDebugResponse;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyDetailResponse;
import com.zc.agent.registry.modules.policy.vo.GuardrailPolicyPageItemResponse;
import com.zc.agent.registry.modules.policy.vo.RuntimeGuardrailPolicyResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Guardrail 策略服务。
 */
@Service
public class GuardrailPolicyService {

    private static final List<String> ALLOWED_STATUS = List.of("draft", "enabled", "disabled");
    private static final List<String> ALLOWED_ACTION_TYPE = List.of("allow", "sandbox", "block", "approval", "redact");
    private static final List<String> ALLOWED_ROLLOUT_MODE = List.of("all", "percentage", "user_whitelist");
    private static final List<String> ALLOWED_MATCH_MODE = List.of("always", "keyword_match");

    private final GuardrailPolicyRepository repository;
    private final ObjectMapper objectMapper;

    public GuardrailPolicyService(GuardrailPolicyRepository repository,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public PageResponse<GuardrailPolicyPageItemResponse> pagePolicies(GuardrailPolicyPageQueryRequest request) {
        GuardrailPolicyPageQueryRequest normalizedRequest = new GuardrailPolicyPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.policyCode()),
                normalizeNullableText(request.policyName()),
                normalizeNullableText(request.status()),
                normalizeNullableText(request.actionType())
        );
        validateStatusNullable(normalizedRequest.status());
        validateActionTypeNullable(normalizedRequest.actionType());
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        long total = repository.countPolicies(
                normalizedRequest.policyCode(),
                normalizedRequest.policyName(),
                normalizedRequest.status(),
                normalizedRequest.actionType()
        );
        List<GuardrailPolicyPageItemResponse> records = repository.pagePolicies(
                        normalizedRequest.policyCode(),
                        normalizedRequest.policyName(),
                        normalizedRequest.status(),
                        normalizedRequest.actionType(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toPageItemResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public GuardrailPolicyDetailResponse getPolicyDetail(Long policyId) {
        return toDetailResponse(getPolicyRecord(policyId));
    }

    @Transactional
    public GuardrailPolicyDetailResponse createPolicy(CreateGuardrailPolicyRequest request, SecurityUser securityUser) {
        CreateGuardrailPolicyRequest normalized = normalizeCreateRequest(request);
        validateRequest(
                normalized.policyCode(),
                normalized.policyVersion(),
                normalized.priority(),
                normalized.status(),
                normalized.rolloutMode(),
                normalized.rolloutPercentage(),
                normalized.matchMode(),
                normalized.actionType()
        );
        validateCapabilityPolicy(normalized.actionType(), normalized.metadata());
        ensurePolicyCodeUnique(normalized.policyCode(), null);
        Long policyId = repository.insertPolicy(
                normalized.policyCode(),
                normalized.policyName(),
                normalized.policyVersion(),
                normalized.priority(),
                normalized.status(),
                normalized.description(),
                normalized.rolloutMode(),
                normalized.rolloutPercentage(),
                toJson(normalized.rolloutUserIds()),
                toJson(normalized.scopeUserIds()),
                toJson(normalized.scopeRoleCodes()),
                toJson(normalized.scopeAgentIds()),
                toJson(normalized.scopeToolIds()),
                toJson(normalized.scopeWorkflowIds()),
                toJson(normalized.scopeEnvs()),
                normalized.matchMode(),
                defaultBoolean(normalized.requireNonEmptyInput()),
                normalized.maxInputLength(),
                toJson(normalized.blockedKeywords()),
                toJson(normalized.keywords()),
                normalized.actionType(),
                normalized.approvalNodeName(),
                toJson(normalized.approvalUserIds()),
                toJson(normalized.approvalRoleCodes()),
                normalized.reason(),
                toJson(normalized.metadata()),
                normalized.remark(),
                securityUser.userId()
        );
        return getPolicyDetail(policyId);
    }

    @Transactional
    public GuardrailPolicyDetailResponse updatePolicy(Long policyId,
                                                      UpdateGuardrailPolicyRequest request,
                                                      SecurityUser securityUser) {
        UpdateGuardrailPolicyRequest normalized = normalizeUpdateRequest(request);
        validateRequest(
                normalized.policyCode(),
                normalized.policyVersion(),
                normalized.priority(),
                normalized.status(),
                normalized.rolloutMode(),
                normalized.rolloutPercentage(),
                normalized.matchMode(),
                normalized.actionType()
        );
        validateCapabilityPolicy(normalized.actionType(), normalized.metadata());
        ensurePolicyExists(policyId);
        ensurePolicyCodeUnique(normalized.policyCode(), policyId);
        int updated = repository.updatePolicy(
                policyId,
                normalized.policyCode(),
                normalized.policyName(),
                normalized.policyVersion(),
                normalized.priority(),
                normalized.status(),
                normalized.description(),
                normalized.rolloutMode(),
                normalized.rolloutPercentage(),
                toJson(normalized.rolloutUserIds()),
                toJson(normalized.scopeUserIds()),
                toJson(normalized.scopeRoleCodes()),
                toJson(normalized.scopeAgentIds()),
                toJson(normalized.scopeToolIds()),
                toJson(normalized.scopeWorkflowIds()),
                toJson(normalized.scopeEnvs()),
                normalized.matchMode(),
                defaultBoolean(normalized.requireNonEmptyInput()),
                normalized.maxInputLength(),
                toJson(normalized.blockedKeywords()),
                toJson(normalized.keywords()),
                normalized.actionType(),
                normalized.approvalNodeName(),
                toJson(normalized.approvalUserIds()),
                toJson(normalized.approvalRoleCodes()),
                normalized.reason(),
                toJson(normalized.metadata()),
                normalized.remark(),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("Guardrail 策略不存在");
        }
        return getPolicyDetail(policyId);
    }

    @Transactional
    public void updatePolicyStatus(Long policyId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensurePolicyExists(policyId);
        if (repository.updatePolicyStatus(policyId, status, securityUser.userId()) <= 0) {
            throw new BusinessException("Guardrail 策略不存在");
        }
    }

    public List<RuntimeGuardrailPolicyResponse> listRuntimePolicies() {
        return repository.findEnabledPolicies().stream()
                .map(this::toRuntimePolicyResponse)
                .toList();
    }

    public GuardrailPolicyDebugResponse debugPolicies(GuardrailPolicyDebugRequest request) {
        List<RuntimeGuardrailPolicyResponse> policies = listRuntimePolicies();
        List<Map<String, Object>> allEffectivePolicies = policies.stream()
                .map(this::toRuntimeMap)
                .toList();
        List<Map<String, Object>> matched = new ArrayList<>();
        for (RuntimeGuardrailPolicyResponse policy : policies) {
            String hitDetail = evaluateDebugHit(policy, request);
            if (hitDetail == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>(toRuntimeMap(policy));
            item.put("hitDetail", hitDetail);
            matched.add(item);
        }
        matched.sort((left, right) -> Integer.compare((Integer) right.get("priority"), (Integer) left.get("priority")));
        Map<String, Object> primary = matched.isEmpty() ? null : matched.get(0);
        String finalDecision = primary == null ? "allow" : String.valueOf(primary.get("actionType"));
        return new GuardrailPolicyDebugResponse(finalDecision, primary, matched, allEffectivePolicies);
    }

    private String evaluateDebugHit(RuntimeGuardrailPolicyResponse policy, GuardrailPolicyDebugRequest request) {
        if (!scopeMatches(policy.scope(), request)) {
            return null;
        }
        if (!rolloutMatches(policy.rollout(), request.userId())) {
            return null;
        }
        String inputText = request.inputText() == null ? "" : request.inputText().trim();
        boolean requireNonEmptyInput = Boolean.TRUE.equals(policy.match().get("requireNonEmptyInput"));
        if (requireNonEmptyInput && inputText.isBlank()) {
            return "输入不能为空";
        }
        Integer maxInputLength = toInteger(policy.match().get("maxInputLength"));
        if (maxInputLength != null && maxInputLength > 0 && inputText.length() > maxInputLength) {
            return "输入长度超过限制，最大允许 " + maxInputLength + " 字符";
        }
        String hitBlocked = firstMatchedKeyword(inputText, stringList(policy.match().get("blockedKeywords")));
        if (hitBlocked != null) {
            return "输入命中拦截词 `" + hitBlocked + "`";
        }
        String mode = String.valueOf(policy.match().getOrDefault("mode", "always")).toLowerCase(Locale.ROOT);
        if ("keyword_match".equals(mode)) {
            String hitKeyword = firstMatchedKeyword(inputText, stringList(policy.match().get("keywords")));
            return hitKeyword == null ? null : "输入命中关键词 `" + hitKeyword + "`";
        }
        if ("always".equals(mode)) {
            return "命中常驻策略";
        }
        return null;
    }

    private boolean scopeMatches(Map<String, Object> scope, GuardrailPolicyDebugRequest request) {
        if (!longList(scope.get("userIds")).isEmpty() && !longList(scope.get("userIds")).contains(request.userId())) {
            return false;
        }
        List<String> roleCodes = lowerList(request.roleCodes());
        List<String> scopeRoleCodes = lowerList(stringList(scope.get("roleCodes")));
        if (!scopeRoleCodes.isEmpty() && scopeRoleCodes.stream().noneMatch(roleCodes::contains)) {
            return false;
        }
        List<Long> agentIds = longList(scope.get("agentIds"));
        if (!agentIds.isEmpty() && !agentIds.contains(request.agentId())) {
            return false;
        }
        List<Long> toolIds = longList(scope.get("toolIds"));
        if (!toolIds.isEmpty() && !toolIds.contains(request.toolId())) {
            return false;
        }
        List<Long> workflowIds = longList(scope.get("workflowIds"));
        if (!workflowIds.isEmpty() && !workflowIds.contains(request.workflowId())) {
            return false;
        }
        List<String> envs = lowerList(stringList(scope.get("envs")));
        return envs.isEmpty() || envs.contains(request.environment().trim().toLowerCase(Locale.ROOT));
    }

    private boolean rolloutMatches(Map<String, Object> rollout, Long userId) {
        String rolloutMode = String.valueOf(rollout.getOrDefault("mode", "all")).toLowerCase(Locale.ROOT);
        if ("all".equals(rolloutMode)) {
            return true;
        }
        if ("user_whitelist".equals(rolloutMode)) {
            return userId != null && longList(rollout.get("userIds")).contains(userId);
        }
        if ("percentage".equals(rolloutMode)) {
            Integer percentage = toInteger(rollout.get("percentage"));
            if (percentage == null || percentage <= 0 || userId == null) {
                return false;
            }
            return Math.abs(Long.hashCode(userId)) % 100 < percentage;
        }
        return true;
    }

    private GuardrailPolicyPageItemResponse toPageItemResponse(GuardrailPolicyPageRecord record) {
        return new GuardrailPolicyPageItemResponse(
                record.policyId(),
                record.policyCode(),
                record.policyName(),
                record.policyVersion(),
                record.priority(),
                record.actionType(),
                record.rolloutMode(),
                record.status(),
                record.description(),
                record.scopeUserCount(),
                record.scopeRoleCount(),
                record.scopeAgentCount(),
                record.scopeToolCount(),
                record.scopeWorkflowCount(),
                record.scopeEnvCount(),
                record.createdAt()
        );
    }

    private GuardrailPolicyDetailResponse toDetailResponse(GuardrailPolicyDetailRecord record) {
        return new GuardrailPolicyDetailResponse(
                record.policyId(),
                record.policyCode(),
                record.policyName(),
                record.policyVersion(),
                record.priority(),
                record.status(),
                record.description(),
                record.rolloutMode(),
                record.rolloutPercentage(),
                parseLongList(record.rolloutUserIdsJson()),
                parseLongList(record.scopeUserIdsJson()),
                parseStringList(record.scopeRoleCodesJson()),
                parseLongList(record.scopeAgentIdsJson()),
                parseLongList(record.scopeToolIdsJson()),
                parseLongList(record.scopeWorkflowIdsJson()),
                parseStringList(record.scopeEnvsJson()),
                record.matchMode(),
                record.requireNonEmptyInput(),
                record.maxInputLength(),
                parseStringList(record.blockedKeywordsJson()),
                parseStringList(record.keywordsJson()),
                record.actionType(),
                record.approvalNodeName(),
                parseLongList(record.approvalUserIdsJson()),
                parseStringList(record.approvalRoleCodesJson()),
                record.reason(),
                parseJsonObject(record.metadataJson()),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private RuntimeGuardrailPolicyResponse toRuntimePolicyResponse(GuardrailPolicyDetailRecord record) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("userIds", parseLongList(record.scopeUserIdsJson()));
        scope.put("roleCodes", parseStringList(record.scopeRoleCodesJson()));
        scope.put("agentIds", parseLongList(record.scopeAgentIdsJson()));
        scope.put("toolIds", parseLongList(record.scopeToolIdsJson()));
        scope.put("workflowIds", parseLongList(record.scopeWorkflowIdsJson()));
        scope.put("envs", parseStringList(record.scopeEnvsJson()));
        Map<String, Object> rollout = new LinkedHashMap<>();
        rollout.put("mode", record.rolloutMode());
        rollout.put("percentage", record.rolloutPercentage());
        rollout.put("userIds", parseLongList(record.rolloutUserIdsJson()));
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("mode", record.matchMode());
        match.put("requireNonEmptyInput", record.requireNonEmptyInput());
        match.put("maxInputLength", record.maxInputLength());
        match.put("blockedKeywords", parseStringList(record.blockedKeywordsJson()));
        match.put("keywords", parseStringList(record.keywordsJson()));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", record.actionType());
        action.put("approvalNodeName", record.approvalNodeName());
        action.put("approvalUserIds", parseLongList(record.approvalUserIdsJson()));
        action.put("approvalRoleCodes", parseStringList(record.approvalRoleCodesJson()));
        action.put("reason", record.reason());
        return new RuntimeGuardrailPolicyResponse(
                record.policyCode(),
                record.policyName(),
                record.policyVersion(),
                "enabled".equals(record.status()),
                record.priority(),
                resolveLayer(record),
                scope,
                rollout,
                match,
                action,
                parseJsonObject(record.metadataJson())
        );
    }

    private String resolveLayer(GuardrailPolicyDetailRecord record) {
        if (!parseLongList(record.scopeToolIdsJson()).isEmpty()) {
            return "tool";
        }
        if (!parseLongList(record.scopeWorkflowIdsJson()).isEmpty()) {
            return "workflow";
        }
        if (!parseLongList(record.scopeAgentIdsJson()).isEmpty()) {
            return "agent";
        }
        if (!parseLongList(record.scopeUserIdsJson()).isEmpty() || !parseStringList(record.scopeRoleCodesJson()).isEmpty()) {
            return "identity";
        }
        if (!parseStringList(record.scopeEnvsJson()).isEmpty()) {
            return "environment";
        }
        return "global";
    }

    private Map<String, Object> toRuntimeMap(RuntimeGuardrailPolicyResponse response) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("policyCode", response.policyCode());
        item.put("policyName", response.policyName());
        item.put("version", response.version());
        item.put("priority", response.priority());
        item.put("layer", response.layer());
        item.put("actionType", response.action().get("type"));
        item.put("scope", response.scope());
        item.put("rollout", response.rollout());
        item.put("match", response.match());
        item.put("action", response.action());
        item.put("metadata", response.metadata());
        return item;
    }

    private GuardrailPolicyDetailRecord getPolicyRecord(Long policyId) {
        return repository.findDetailById(policyId)
                .orElseThrow(() -> new BusinessException("Guardrail 策略不存在"));
    }

    private void ensurePolicyExists(Long policyId) {
        getPolicyRecord(policyId);
    }

    private void ensurePolicyCodeUnique(String policyCode, Long currentPolicyId) {
        repository.findPolicyIdByCode(policyCode).ifPresent(existingId -> {
            if (currentPolicyId == null || !existingId.equals(currentPolicyId)) {
                throw new BusinessException("策略编码已存在");
            }
        });
    }

    private void validateRequest(String policyCode,
                                 Integer policyVersion,
                                 Integer priority,
                                 String status,
                                 String rolloutMode,
                                 Integer rolloutPercentage,
                                 String matchMode,
                                 String actionType) {
        if (policyCode == null || policyCode.isBlank()) {
            throw new BusinessException("策略编码不能为空");
        }
        if (policyVersion == null || policyVersion < 1) {
            throw new BusinessException("策略版本必须大于等于 1");
        }
        if (priority == null) {
            throw new BusinessException("优先级不能为空");
        }
        validateStatus(status);
        validateActionType(actionType);
        validateRolloutMode(rolloutMode);
        validateMatchMode(matchMode);
        if ("percentage".equals(rolloutMode) && (rolloutPercentage == null || rolloutPercentage < 0 || rolloutPercentage > 100)) {
            throw new BusinessException("灰度百分比必须在 0 到 100 之间");
        }
    }

    private void validateCapabilityPolicy(String actionType, JsonNode metadata) {
        boolean capabilityPolicy = metadata != null
                && "capability".equals(metadata.path("policyType").asText());
        if (!capabilityPolicy) {
            if ("allow".equals(actionType) || "sandbox".equals(actionType)) {
                throw new BusinessException("allow/sandbox 仅允许用于 Capability Policy");
            }
            return;
        }
        JsonNode policy = metadata.path("capabilityPolicy");
        if (!policy.isObject()) {
            throw new BusinessException("Capability Policy 必须配置 metadata.capabilityPolicy");
        }
        if ("redact".equals(actionType)) {
            throw new BusinessException("Capability Policy 不支持 redact 动作");
        }
        RuntimeCapabilityContract.normalizeCapabilities(parseTextArray(
                policy.path("capabilities"), "capabilities"));
        List<String> sideEffectLevels = parseTextArray(policy.path("sideEffectLevels"), "sideEffectLevels");
        if (!RuntimeCapabilityContract.SIDE_EFFECT_LEVELS.containsAll(sideEffectLevels)) {
            throw new BusinessException("Capability Policy 包含不支持的 Side Effect Level");
        }
        JsonNode conditions = policy.path("parameterConditions");
        if (!conditions.isMissingNode() && !conditions.isArray()) {
            throw new BusinessException("Capability Policy parameterConditions 必须为数组");
        }
        for (JsonNode condition : conditions) {
            if (!condition.isObject() || condition.path("path").asText("").isBlank()) {
                throw new BusinessException("Capability Policy 参数条件必须配置 path");
            }
            String operator = condition.path("operator").asText("equals");
            if (!List.of("exists", "equals", "in", "prefix").contains(operator)) {
                throw new BusinessException("Capability Policy 参数条件 operator 不支持: " + operator);
            }
            if (!"exists".equals(operator)) {
                List<String> values = condition.has("values")
                        ? parseTextArray(condition.path("values"), "parameterConditions.values")
                        : List.of(condition.path("value").asText("").trim());
                if (values.isEmpty() || values.stream().anyMatch(String::isBlank)) {
                    throw new BusinessException("Capability Policy 参数条件必须配置 value 或 values");
                }
            }
        }
        JsonNode projectIds = policy.path("projectIds");
        if (!projectIds.isMissingNode() && (!projectIds.isArray()
                || java.util.stream.StreamSupport.stream(projectIds.spliterator(), false)
                .anyMatch(item -> !item.isIntegralNumber() || !item.canConvertToLong()))) {
            throw new BusinessException("Capability Policy projectIds 必须为整数数组");
        }
        parseTextArray(policy.path("targetPaths"), "targetPaths");
        parseTextArray(policy.path("targetPrefixes"), "targetPrefixes");
    }

    private List<String> parseTextArray(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new BusinessException("Capability Policy " + field + " 必须为数组");
        }
        ArrayList<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (value.isBlank()) {
                throw new BusinessException("Capability Policy " + field + " 不能包含空值");
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private CreateGuardrailPolicyRequest normalizeCreateRequest(CreateGuardrailPolicyRequest request) {
        return new CreateGuardrailPolicyRequest(
                request.policyCode().trim(),
                request.policyName().trim(),
                request.policyVersion(),
                request.priority(),
                request.status().trim(),
                normalizeNullableText(request.description()),
                request.rolloutMode().trim().toLowerCase(Locale.ROOT),
                request.rolloutPercentage(),
                normalizeLongList(request.rolloutUserIds()),
                normalizeLongList(request.scopeUserIds()),
                normalizeTextList(request.scopeRoleCodes()),
                normalizeLongList(request.scopeAgentIds()),
                normalizeLongList(request.scopeToolIds()),
                normalizeLongList(request.scopeWorkflowIds()),
                normalizeTextList(request.scopeEnvs()),
                request.matchMode().trim().toLowerCase(Locale.ROOT),
                request.requireNonEmptyInput(),
                request.maxInputLength(),
                normalizeTextList(request.blockedKeywords()),
                normalizeTextList(request.keywords()),
                request.actionType().trim().toLowerCase(Locale.ROOT),
                normalizeNullableText(request.approvalNodeName()),
                normalizeLongList(request.approvalUserIds()),
                normalizeTextList(request.approvalRoleCodes()),
                normalizeNullableText(request.reason()),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateGuardrailPolicyRequest normalizeUpdateRequest(UpdateGuardrailPolicyRequest request) {
        return new UpdateGuardrailPolicyRequest(
                request.policyCode().trim(),
                request.policyName().trim(),
                request.policyVersion(),
                request.priority(),
                request.status().trim(),
                normalizeNullableText(request.description()),
                request.rolloutMode().trim().toLowerCase(Locale.ROOT),
                request.rolloutPercentage(),
                normalizeLongList(request.rolloutUserIds()),
                normalizeLongList(request.scopeUserIds()),
                normalizeTextList(request.scopeRoleCodes()),
                normalizeLongList(request.scopeAgentIds()),
                normalizeLongList(request.scopeToolIds()),
                normalizeLongList(request.scopeWorkflowIds()),
                normalizeTextList(request.scopeEnvs()),
                request.matchMode().trim().toLowerCase(Locale.ROOT),
                request.requireNonEmptyInput(),
                request.maxInputLength(),
                normalizeTextList(request.blockedKeywords()),
                normalizeTextList(request.keywords()),
                request.actionType().trim().toLowerCase(Locale.ROOT),
                normalizeNullableText(request.approvalNodeName()),
                normalizeLongList(request.approvalUserIds()),
                normalizeTextList(request.approvalRoleCodes()),
                normalizeNullableText(request.reason()),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private JsonNode normalizeMetadata(JsonNode metadata) {
        return metadata == null ? objectMapper.createObjectNode() : metadata;
    }

    private boolean defaultBoolean(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("不支持的状态: " + status);
        }
    }

    private void validateStatusNullable(String status) {
        if (status != null) {
            validateStatus(status);
        }
    }

    private void validateActionType(String actionType) {
        if (!ALLOWED_ACTION_TYPE.contains(actionType)) {
            throw new BusinessException("不支持的动作类型: " + actionType);
        }
    }

    private void validateActionTypeNullable(String actionType) {
        if (actionType != null) {
            validateActionType(actionType);
        }
    }

    private void validateRolloutMode(String rolloutMode) {
        if (!ALLOWED_ROLLOUT_MODE.contains(rolloutMode)) {
            throw new BusinessException("不支持的灰度模式: " + rolloutMode);
        }
    }

    private void validateMatchMode(String matchMode) {
        if (!ALLOWED_MATCH_MODE.contains(matchMode)) {
            throw new BusinessException("不支持的匹配模式: " + matchMode);
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::normalizeNullableText)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
    }

    private List<Long> normalizeLongList(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(item -> item != null && item > 0).distinct().toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("策略数据序列化失败");
        }
    }

    private List<Long> parseLongList(String jsonText) {
        try {
            JsonNode node = objectMapper.readTree(jsonText == null ? "[]" : jsonText);
            ArrayList<Long> values = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.canConvertToLong()) {
                        values.add(item.asLong());
                    }
                }
            }
            return values;
        } catch (Exception exception) {
            throw new BusinessException("策略 JSON 解析失败");
        }
    }

    private List<String> parseStringList(String jsonText) {
        try {
            JsonNode node = objectMapper.readTree(jsonText == null ? "[]" : jsonText);
            ArrayList<String> values = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String text = normalizeNullableText(item.asText(null));
                    if (text != null) {
                        values.add(text);
                    }
                }
            }
            return values;
        } catch (Exception exception) {
            throw new BusinessException("策略 JSON 解析失败");
        }
    }

    private JsonNode parseJsonObject(String jsonText) {
        try {
            return objectMapper.readTree(jsonText == null ? "{}" : jsonText);
        } catch (Exception exception) {
            throw new BusinessException("策略 metadata 解析失败");
        }
    }

    private List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Number)
                    .map(item -> ((Number) item).longValue())
                    .toList();
        }
        return List.of();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }

    private List<String> lowerList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(item -> item == null ? null : item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
}

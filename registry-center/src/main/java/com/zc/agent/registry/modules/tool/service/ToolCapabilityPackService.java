package com.zc.agent.registry.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.tool.dto.AgentToolCapabilityPackRequest;
import com.zc.agent.registry.modules.tool.dto.ToolCapabilityPackRequest;
import com.zc.agent.registry.modules.tool.repository.ToolCapabilityPackRepository;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tool Capability Pack 配置、成员和 Agent 关联服务。
 */
@Service
public class ToolCapabilityPackService {

    private static final Set<String> ALLOWED_STATUS = Set.of("draft", "enabled", "disabled");
    private final ToolCapabilityPackRepository repository;
    private final ObjectMapper objectMapper;

    public ToolCapabilityPackService(ToolCapabilityPackRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public JsonNode list() {
        ArrayNode result = objectMapper.createArrayNode();
        repository.findPacks().forEach(row -> result.add(toNode(row)));
        return result;
    }

    public JsonNode detail(Long packId) {
        ObjectNode result = toNode(requirePack(packId));
        ArrayNode tools = objectMapper.createArrayNode();
        repository.findPackTools(packId).forEach(row -> tools.add(toNode(row)));
        result.set("tools", tools);
        return result;
    }

    @Transactional
    public JsonNode create(ToolCapabilityPackRequest request, SecurityUser user) {
        validate(request);
        List<Long> toolIds = normalizedIds(request.toolIds());
        validateTools(toolIds);
        try {
            Long id = repository.insertPack(request.packCode().trim(), request.packName().trim(),
                    trim(request.description()), request.version().trim(), request.status(), json(request.metadata()),
                    user.userId());
            repository.replacePackTools(id, toolIds, user.userId());
            return detail(id);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("能力包编码已存在或包含重复 Tool");
        }
    }

    @Transactional
    public JsonNode update(Long packId, ToolCapabilityPackRequest request, SecurityUser user) {
        validate(request);
        Map<String, Object> existing = requirePack(packId);
        if (!request.packCode().trim().equals(existing.get("pack_code"))) {
            throw new BusinessException("能力包编码不允许修改");
        }
        List<Long> toolIds = normalizedIds(request.toolIds());
        validateTools(toolIds);
        repository.updatePack(packId, request.packName().trim(), trim(request.description()),
                request.version().trim(), request.status(), json(request.metadata()), user.userId());
        repository.replacePackTools(packId, toolIds, user.userId());
        return detail(packId);
    }

    @Transactional
    public void delete(Long packId, SecurityUser user) {
        requirePack(packId);
        if (repository.softDeletePack(packId, user.userId()) == 0) {
            throw new BusinessException("能力包仍被 Agent 关联，不能删除");
        }
    }

    public JsonNode agentPacks(Long agentId) {
        requireAgent(agentId);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("agentId", agentId);
        result.set("selectedPackIds", objectMapper.valueToTree(repository.findAgentPackIds(agentId)));
        result.set("availablePacks", list());
        return result;
    }

    @Transactional
    public JsonNode updateAgentPacks(Long agentId, AgentToolCapabilityPackRequest request, SecurityUser user) {
        requireAgent(agentId);
        List<Long> packIds = normalizedIds(request.packIds());
        List<Long> missing = repository.findMissingEnabledPackIds(packIds);
        if (!missing.isEmpty()) {
            throw new BusinessException("能力包不存在或未启用：" + missing);
        }
        repository.replaceAgentPacks(agentId, packIds, user.userId());
        return agentPacks(agentId);
    }

    public List<Long> effectiveToolIds(Long agentId) {
        return repository.findEffectiveToolIdsByAgent(agentId);
    }

    private void validate(ToolCapabilityPackRequest request) {
        if (!ALLOWED_STATUS.contains(request.status())) {
            throw new BusinessException("能力包状态不合法：" + request.status());
        }
        if (request.metadata() != null && !request.metadata().isObject()) {
            throw new BusinessException("能力包 metadata 必须是 JSON 对象");
        }
    }

    private void validateTools(List<Long> toolIds) {
        List<Long> missing = repository.findMissingToolIds(toolIds);
        if (!missing.isEmpty()) {
            throw new BusinessException("Tool 不存在：" + missing);
        }
    }

    private Map<String, Object> requirePack(Long packId) {
        return repository.findPack(packId).orElseThrow(() -> new BusinessException("Tool 能力包不存在"));
    }

    private void requireAgent(Long agentId) {
        if (!repository.agentExists(agentId)) {
            throw new BusinessException("Agent 不存在");
        }
    }

    private List<Long> normalizedIds(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(new LinkedHashSet<>(ids));
    }

    private ObjectNode toNode(Map<String, Object> row) {
        ObjectNode result = objectMapper.createObjectNode();
        row.forEach((key, value) -> {
            if (value == null) {
                result.putNull(key);
            } else if ("metadata".equals(key)) {
                result.set(key, parse(String.valueOf(value)));
            } else {
                result.set(key, objectMapper.valueToTree(value));
            }
        });
        return result;
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value == null || value.isNull()
                    ? objectMapper.createObjectNode() : value);
        } catch (Exception exception) {
            throw new BusinessException("能力包 metadata 序列化失败");
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

package com.zc.agent.registry.modules.prompt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.prompt.dto.CreatePromptRequest;
import com.zc.agent.registry.modules.prompt.dto.PromptPageQueryRequest;
import com.zc.agent.registry.modules.prompt.dto.UpdatePromptRequest;
import com.zc.agent.registry.modules.prompt.model.PromptDetailRecord;
import com.zc.agent.registry.modules.prompt.repository.PromptRepository;
import com.zc.agent.registry.modules.prompt.vo.PromptDetailResponse;
import com.zc.agent.registry.modules.prompt.vo.PromptPageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Prompt 服务。
 */
@Service
public class PromptService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_DRAFT, STATUS_ENABLED, STATUS_DISABLED);

    private final PromptRepository promptRepository;
    private final DictService dictService;
    private final ObjectMapper objectMapper;

    public PromptService(PromptRepository promptRepository, DictService dictService, ObjectMapper objectMapper) {
        this.promptRepository = promptRepository;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询 Prompt。
     */
    public PageResponse<PromptPageItemResponse> pagePrompts(PromptPageQueryRequest request) {
        PromptPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = promptRepository.countPrompts(
                normalizedRequest.promptCode(),
                normalizedRequest.promptName(),
                normalizedRequest.promptType(),
                normalizedRequest.promptCategory(),
                normalizedRequest.status()
        );
        List<PromptPageItemResponse> records = promptRepository.pagePrompts(
                        normalizedRequest.promptCode(),
                        normalizedRequest.promptName(),
                        normalizedRequest.promptType(),
                        normalizedRequest.promptCategory(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new PromptPageItemResponse(
                        item.promptId(),
                        item.promptCode(),
                        item.promptName(),
                        item.promptType(),
                        item.promptCategory(),
                        item.version(),
                        item.status(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询 Prompt 详情。
     */
    public PromptDetailResponse getPromptDetail(Long promptId) {
        return toPromptDetailResponse(getPromptRecord(promptId));
    }

    /**
     * 新增 Prompt。
     */
    @Transactional
    public PromptDetailResponse createPrompt(CreatePromptRequest request, SecurityUser securityUser) {
        CreatePromptRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.promptType(), normalizedRequest.promptCategory());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        ensurePromptCodeUnique(normalizedRequest.promptCode(), null);
        Long promptId = promptRepository.insertPrompt(
                normalizedRequest,
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        return getPromptDetail(promptId);
    }

    /**
     * 编辑 Prompt。
     */
    @Transactional
    public PromptDetailResponse updatePrompt(Long promptId, UpdatePromptRequest request, SecurityUser securityUser) {
        UpdatePromptRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.promptType(), normalizedRequest.promptCategory());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        ensurePromptExists(promptId);
        ensurePromptCodeUnique(normalizedRequest.promptCode(), promptId);
        int updated = promptRepository.updatePrompt(
                promptId,
                normalizedRequest,
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("Prompt 不存在");
        }
        return getPromptDetail(promptId);
    }

    /**
     * 启用 Prompt。
     */
    @Transactional
    public void enablePrompt(Long promptId, SecurityUser securityUser) {
        updatePromptStatus(promptId, STATUS_ENABLED, securityUser);
    }

    /**
     * 停用 Prompt。
     */
    @Transactional
    public void disablePrompt(Long promptId, SecurityUser securityUser) {
        updatePromptStatus(promptId, STATUS_DISABLED, securityUser);
    }

    private void updatePromptStatus(Long promptId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensurePromptExists(promptId);
        int updated = promptRepository.updatePromptStatus(promptId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Prompt 不存在");
        }
    }

    private PromptPageQueryRequest normalizePageQueryRequest(PromptPageQueryRequest request) {
        return new PromptPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.promptCode()),
                normalizeNullableText(request.promptName()),
                normalizeNullableText(request.promptType()),
                normalizeNullableText(request.promptCategory()),
                normalizeNullableText(request.status())
        );
    }

    private CreatePromptRequest normalizeCreateRequest(CreatePromptRequest request) {
        return new CreatePromptRequest(
                request.promptCode().trim(),
                request.promptName().trim(),
                request.promptType().trim(),
                request.promptCategory().trim(),
                normalizeNullableText(request.description()),
                request.content().trim(),
                request.version().trim(),
                request.status().trim(),
                request.metadata(),
                normalizeNullableText(request.remark())
        );
    }

    private UpdatePromptRequest normalizeUpdateRequest(UpdatePromptRequest request) {
        return new UpdatePromptRequest(
                request.promptCode().trim(),
                request.promptName().trim(),
                request.promptType().trim(),
                request.promptCategory().trim(),
                normalizeNullableText(request.description()),
                request.content().trim(),
                request.version().trim(),
                request.status().trim(),
                request.metadata(),
                normalizeNullableText(request.remark())
        );
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("Prompt 状态不合法");
        }
    }

    private void validateDictFields(String promptType, String promptCategory) {
        dictService.validateEnabledDictItem("prompt_type", promptType, "Prompt 类型");
        dictService.validateEnabledDictItem("prompt_category", promptCategory, "Prompt 分类");
    }

    private void validateJsonObject(String fieldName, JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isObject()) {
            throw new BusinessException(fieldName + "必须为 JSON 对象");
        }
    }

    private void ensurePromptCodeUnique(String promptCode, Long excludePromptId) {
        promptRepository.findPromptIdByCode(promptCode).ifPresent(existingPromptId -> {
            if (excludePromptId == null || !existingPromptId.equals(excludePromptId)) {
                throw new BusinessException("Prompt 编码已存在");
            }
        });
    }

    private void ensurePromptExists(Long promptId) {
        getPromptRecord(promptId);
    }

    private PromptDetailRecord getPromptRecord(Long promptId) {
        return promptRepository.findPromptDetailById(promptId)
                .orElseThrow(() -> new BusinessException("Prompt 不存在"));
    }

    private PromptDetailResponse toPromptDetailResponse(PromptDetailRecord record) {
        return new PromptDetailResponse(
                record.promptId(),
                record.promptCode(),
                record.promptName(),
                record.promptType(),
                record.promptCategory(),
                record.description(),
                record.content(),
                record.version(),
                record.status(),
                parseJson(record.metadataJsonText()),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Prompt JSON 数据格式不合法");
        }
    }

    private String toJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Prompt JSON 数据格式不合法");
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

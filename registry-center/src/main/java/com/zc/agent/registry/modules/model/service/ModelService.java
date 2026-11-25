package com.zc.agent.registry.modules.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.model.dto.CreateModelRequest;
import com.zc.agent.registry.modules.model.dto.ModelPageQueryRequest;
import com.zc.agent.registry.modules.model.dto.UpdateModelRequest;
import com.zc.agent.registry.modules.model.model.ModelDetailRecord;
import com.zc.agent.registry.modules.model.repository.ModelRepository;
import com.zc.agent.registry.modules.model.vo.ModelDetailResponse;
import com.zc.agent.registry.modules.model.vo.ModelPageItemResponse;
import com.zc.agent.registry.modules.model.vo.RuntimeModelDetailResponse;
import com.zc.agent.registry.modules.tool.service.ToolSecretCryptoService;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模型服务。
 */
@Service
public class ModelService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_DRAFT, STATUS_ENABLED, STATUS_DISABLED);

    private final ModelRepository modelRepository;
    private final DictService dictService;
    private final ObjectMapper objectMapper;
    private final ToolSecretCryptoService secretCryptoService;

    public ModelService(ModelRepository modelRepository,
                        DictService dictService,
                        ObjectMapper objectMapper,
                        ToolSecretCryptoService secretCryptoService) {
        this.modelRepository = modelRepository;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
        this.secretCryptoService = secretCryptoService;
    }

    public PageResponse<ModelPageItemResponse> pageModels(ModelPageQueryRequest request) {
        ModelPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = modelRepository.countModels(
                normalizedRequest.modelCode(),
                normalizedRequest.modelName(),
                normalizedRequest.provider(),
                normalizedRequest.modelType(),
                normalizedRequest.status()
        );
        List<ModelPageItemResponse> records = modelRepository.pageModels(
                        normalizedRequest.modelCode(),
                        normalizedRequest.modelName(),
                        normalizedRequest.provider(),
                        normalizedRequest.modelType(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new ModelPageItemResponse(
                        item.modelId(),
                        item.modelCode(),
                        item.modelName(),
                        item.provider(),
                        item.modelType(),
                        item.status(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public ModelDetailResponse getModelDetail(Long modelId) {
        return toModelDetailResponse(getModelRecord(modelId));
    }

    public ModelDetailResponse getModelDetailByCode(String modelCode) {
        return toModelDetailResponse(getModelRecordByCode(modelCode));
    }

    /**
     * 获取仅供执行服务使用的模型运行时配置。
     *
     * @param modelCode 模型编码
     * @return 运行时模型配置
     */
    public RuntimeModelDetailResponse getRuntimeModelDetailByCode(String modelCode) {
        ModelDetailRecord record = getModelRecordByCode(modelCode);
        return new RuntimeModelDetailResponse(
                record.modelId(),
                record.modelCode(),
                record.modelName(),
                record.provider(),
                record.modelType(),
                record.baseUrl(),
                secretCryptoService.decryptSecret(record.apiKey()),
                record.apiPath(),
                record.modelIdentifier(),
                record.temperature(),
                record.maxTokens(),
                record.contextWindow(),
                record.reservedOutputTokens(),
                record.reservedSystemTokens(),
                record.compactionTriggerRatio(),
                record.tokenizerFamily(),
                record.timeoutSeconds(),
                record.status(),
                parseJson(record.metadataJsonText()),
                record.remark()
        );
    }

    @Transactional
    public ModelDetailResponse createModel(CreateModelRequest request, SecurityUser securityUser) {
        CreateModelRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.provider(), normalizedRequest.modelType());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        ensureModelCodeUnique(normalizedRequest.modelCode(), null);
        normalizedRequest = withEncryptedApiKey(normalizedRequest);
        Long modelId = modelRepository.insertModel(normalizedRequest, toJson(normalizedRequest.metadata()), securityUser.userId());
        return getModelDetail(modelId);
    }

    @Transactional
    public ModelDetailResponse updateModel(Long modelId, UpdateModelRequest request, SecurityUser securityUser) {
        UpdateModelRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.provider(), normalizedRequest.modelType());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        ModelDetailRecord existingRecord = getModelRecord(modelId);
        ensureModelCodeUnique(normalizedRequest.modelCode(), modelId);
        normalizedRequest = withStoredApiKey(normalizedRequest, existingRecord.apiKey());
        int updated = modelRepository.updateModel(modelId, normalizedRequest, toJson(normalizedRequest.metadata()), securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("模型不存在");
        }
        return getModelDetail(modelId);
    }

    @Transactional
    public void enableModel(Long modelId, SecurityUser securityUser) {
        updateModelStatus(modelId, STATUS_ENABLED, securityUser);
    }

    @Transactional
    public void disableModel(Long modelId, SecurityUser securityUser) {
        updateModelStatus(modelId, STATUS_DISABLED, securityUser);
    }

    public boolean existsEnabledModelCode(String modelCode) {
        return modelRepository.existsEnabledModelCode(modelCode);
    }

    /**
     * 判断模型编码是否对应已启用的 Embedding 模型。
     *
     * @param modelCode 模型编码
     * @return 是否为可用的 Embedding 模型
     */
    public boolean existsEnabledEmbeddingModelCode(String modelCode) {
        return modelRepository.existsEnabledModelCodeAndType(modelCode, "embedding");
    }

    /**
     * 将历史明文模型密钥原地升级为带版本标识的密文。
     *
     * @return 升级记录数
     */
    @Transactional
    public int migrateLegacyApiKeys() {
        int migrated = 0;
        for (var credential : modelRepository.findModelCredentials()) {
            String encrypted = secretCryptoService.encryptSecret(credential.apiKey());
            if (!encrypted.equals(credential.apiKey())) {
                migrated += modelRepository.updateModelApiKey(credential.modelId(), encrypted);
            }
        }
        return migrated;
    }

    private void updateModelStatus(Long modelId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensureModelExists(modelId);
        int updated = modelRepository.updateModelStatus(modelId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("模型不存在");
        }
    }

    private ModelPageQueryRequest normalizePageQueryRequest(ModelPageQueryRequest request) {
        return new ModelPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.modelCode()),
                normalizeNullableText(request.modelName()),
                normalizeNullableText(request.provider()),
                normalizeNullableText(request.modelType()),
                normalizeNullableText(request.status())
        );
    }

    private CreateModelRequest normalizeCreateRequest(CreateModelRequest request) {
        return new CreateModelRequest(
                request.modelCode().trim(),
                request.modelName().trim(),
                request.provider().trim(),
                request.modelType().trim(),
                normalizeNullableText(request.baseUrl()),
                normalizeNullableText(request.apiKey()),
                normalizeNullableText(request.apiPath()),
                normalizeNullableText(request.modelIdentifier()),
                request.temperature(),
                request.maxTokens(),
                request.contextWindow(),
                request.reservedOutputTokens(),
                request.reservedSystemTokens(),
                request.compactionTriggerRatio(),
                normalizeNullableText(request.tokenizerFamily()),
                request.timeoutSeconds(),
                request.status().trim(),
                request.metadata(),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateModelRequest normalizeUpdateRequest(UpdateModelRequest request) {
        return new UpdateModelRequest(
                request.modelCode().trim(),
                request.modelName().trim(),
                request.provider().trim(),
                request.modelType().trim(),
                normalizeNullableText(request.baseUrl()),
                normalizeNullableText(request.apiKey()),
                normalizeNullableText(request.apiPath()),
                normalizeNullableText(request.modelIdentifier()),
                request.temperature(),
                request.maxTokens(),
                request.contextWindow(),
                request.reservedOutputTokens(),
                request.reservedSystemTokens(),
                request.compactionTriggerRatio(),
                normalizeNullableText(request.tokenizerFamily()),
                request.timeoutSeconds(),
                request.status().trim(),
                request.metadata(),
                normalizeNullableText(request.remark())
        );
    }

    private CreateModelRequest withEncryptedApiKey(CreateModelRequest request) {
        return new CreateModelRequest(
                request.modelCode(), request.modelName(), request.provider(), request.modelType(), request.baseUrl(),
                secretCryptoService.encryptSecret(request.apiKey()), request.apiPath(), request.modelIdentifier(),
                request.temperature(), request.maxTokens(), request.contextWindow(), request.reservedOutputTokens(),
                request.reservedSystemTokens(), request.compactionTriggerRatio(), request.tokenizerFamily(),
                request.timeoutSeconds(), request.status(), request.metadata(), request.remark()
        );
    }

    private UpdateModelRequest withStoredApiKey(UpdateModelRequest request, String existingApiKey) {
        String storedApiKey = request.apiKey() == null || request.apiKey().isBlank()
                ? existingApiKey
                : secretCryptoService.encryptSecret(request.apiKey());
        return new UpdateModelRequest(
                request.modelCode(), request.modelName(), request.provider(), request.modelType(), request.baseUrl(),
                storedApiKey, request.apiPath(), request.modelIdentifier(), request.temperature(), request.maxTokens(),
                request.contextWindow(), request.reservedOutputTokens(), request.reservedSystemTokens(),
                request.compactionTriggerRatio(), request.tokenizerFamily(), request.timeoutSeconds(), request.status(),
                request.metadata(), request.remark()
        );
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("模型状态不合法");
        }
    }

    private void validateDictFields(String provider, String modelType) {
        dictService.validateEnabledDictItem("model_provider", provider, "模型供应商");
        dictService.validateEnabledDictItem("model_type", modelType, "模型类型");
    }

    private void validateJsonObject(String fieldName, JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isObject()) {
            throw new BusinessException(fieldName + "必须为 JSON 对象");
        }
    }

    private void ensureModelCodeUnique(String modelCode, Long excludeModelId) {
        modelRepository.findModelIdByCode(modelCode).ifPresent(existingModelId -> {
            if (excludeModelId == null || !existingModelId.equals(excludeModelId)) {
                throw new BusinessException("模型编码已存在");
            }
        });
    }

    private void ensureModelExists(Long modelId) {
        getModelRecord(modelId);
    }

    private ModelDetailRecord getModelRecord(Long modelId) {
        return modelRepository.findModelDetailById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
    }

    private ModelDetailRecord getModelRecordByCode(String modelCode) {
        return modelRepository.findModelDetailByCode(modelCode)
                .orElseThrow(() -> new BusinessException("模型不存在"));
    }

    private ModelDetailResponse toModelDetailResponse(ModelDetailRecord record) {
        return new ModelDetailResponse(
                record.modelId(),
                record.modelCode(),
                record.modelName(),
                record.provider(),
                record.modelType(),
                record.baseUrl(),
                record.apiKey() != null && !record.apiKey().isBlank(),
                secretCryptoService.maskStoredSecret(record.apiKey()),
                record.apiPath(),
                record.modelIdentifier(),
                record.temperature(),
                record.maxTokens(),
                record.contextWindow(),
                record.reservedOutputTokens(),
                record.reservedSystemTokens(),
                record.compactionTriggerRatio(),
                record.tokenizerFamily(),
                record.timeoutSeconds(),
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
            throw new BusinessException("模型 JSON 数据格式不合法");
        }
    }

    private String toJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("模型 JSON 数据格式不合法");
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

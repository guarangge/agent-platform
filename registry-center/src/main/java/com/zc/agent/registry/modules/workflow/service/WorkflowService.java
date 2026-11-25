package com.zc.agent.registry.modules.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.workflow.dto.CreateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.dto.UpdateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.dto.WorkflowPageQueryRequest;
import com.zc.agent.registry.modules.workflow.model.WorkflowDetailRecord;
import com.zc.agent.registry.modules.workflow.repository.WorkflowRepository;
import com.zc.agent.registry.modules.workflow.vo.WorkflowDetailResponse;
import com.zc.agent.registry.modules.workflow.vo.WorkflowPageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Workflow 服务。
 */
@Service
public class WorkflowService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_DISABLED);

    private final WorkflowRepository workflowRepository;
    private final DictService dictService;
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionValidator definitionValidator;

    public WorkflowService(WorkflowRepository workflowRepository, DictService dictService, ObjectMapper objectMapper,
                           WorkflowDefinitionValidator definitionValidator) {
        this.workflowRepository = workflowRepository;
        this.dictService = dictService;
        this.objectMapper = objectMapper;
        this.definitionValidator = definitionValidator;
    }

    /**
     * 分页查询 Workflow。
     */
    public PageResponse<WorkflowPageItemResponse> pageWorkflows(WorkflowPageQueryRequest request) {
        WorkflowPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = workflowRepository.countWorkflows(
                normalizedRequest.workflowCode(),
                normalizedRequest.workflowName(),
                normalizedRequest.workflowType(),
                normalizedRequest.workflowCategory(),
                normalizedRequest.version(),
                normalizedRequest.status()
        );
        List<WorkflowPageItemResponse> records = workflowRepository.pageWorkflows(
                        normalizedRequest.workflowCode(),
                        normalizedRequest.workflowName(),
                        normalizedRequest.workflowType(),
                        normalizedRequest.workflowCategory(),
                        normalizedRequest.version(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new WorkflowPageItemResponse(
                        item.workflowId(),
                        item.workflowCode(),
                        item.workflowName(),
                        item.workflowType(),
                        item.workflowCategory(),
                        item.version(),
                        item.status(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询 Workflow 详情。
     */
    public WorkflowDetailResponse getWorkflowDetail(Long workflowId) {
        return toWorkflowDetailResponse(getWorkflowRecord(workflowId));
    }

    /**
     * 新增 Workflow。
     */
    @Transactional
    public WorkflowDetailResponse createWorkflow(CreateWorkflowRequest request, SecurityUser securityUser) {
        CreateWorkflowRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.workflowType(), normalizedRequest.workflowCategory());
        validateJsonObject("Workflow 定义", normalizedRequest.definitionJson());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        validatePublishedDefinition(normalizedRequest.status(), normalizedRequest.definitionJson());
        ensureWorkflowCodeUnique(normalizedRequest.workflowCode(), null);
        Long workflowId = workflowRepository.insertWorkflow(
                normalizedRequest,
                toJson(normalizedRequest.definitionJson()),
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        return getWorkflowDetail(workflowId);
    }

    /**
     * 编辑 Workflow。
     */
    @Transactional
    public WorkflowDetailResponse updateWorkflow(Long workflowId, UpdateWorkflowRequest request, SecurityUser securityUser) {
        UpdateWorkflowRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        validateDictFields(normalizedRequest.workflowType(), normalizedRequest.workflowCategory());
        validateJsonObject("Workflow 定义", normalizedRequest.definitionJson());
        validateJsonObject("扩展元数据", normalizedRequest.metadata());
        validatePublishedDefinition(normalizedRequest.status(), normalizedRequest.definitionJson());
        ensureWorkflowExists(workflowId);
        ensureWorkflowCodeUnique(normalizedRequest.workflowCode(), workflowId);
        int updated = workflowRepository.updateWorkflow(
                workflowId,
                normalizedRequest,
                toJson(normalizedRequest.definitionJson()),
                toJson(normalizedRequest.metadata()),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("Workflow 不存在");
        }
        return getWorkflowDetail(workflowId);
    }

    /**
     * 发布 Workflow。
     */
    @Transactional
    public void publishWorkflow(Long workflowId, SecurityUser securityUser) {
        WorkflowDetailRecord record = getWorkflowRecord(workflowId);
        requireValidDefinition(parseJson(record.definitionJsonText()));
        updateWorkflowStatus(workflowId, STATUS_PUBLISHED, securityUser);
    }

    /** 返回 Workflow 定义的发布校验报告。 */
    public WorkflowDefinitionValidator.ValidationReport validateDefinition(JsonNode definitionJson) {
        return definitionValidator.validate(definitionJson);
    }

    /**
     * 停用 Workflow。
     */
    @Transactional
    public void disableWorkflow(Long workflowId, SecurityUser securityUser) {
        updateWorkflowStatus(workflowId, STATUS_DISABLED, securityUser);
    }

    private void updateWorkflowStatus(Long workflowId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensureWorkflowExists(workflowId);
        int updated = workflowRepository.updateWorkflowStatus(workflowId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("Workflow 不存在");
        }
    }

    private WorkflowPageQueryRequest normalizePageQueryRequest(WorkflowPageQueryRequest request) {
        return new WorkflowPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.workflowCode()),
                normalizeNullableText(request.workflowName()),
                normalizeNullableText(request.workflowType()),
                normalizeNullableText(request.workflowCategory()),
                normalizeNullableText(request.version()),
                normalizeNullableText(request.status())
        );
    }

    private CreateWorkflowRequest normalizeCreateRequest(CreateWorkflowRequest request) {
        return new CreateWorkflowRequest(
                request.workflowCode().trim(),
                request.workflowName().trim(),
                request.workflowType().trim(),
                request.workflowCategory().trim(),
                normalizeNullableText(request.description()),
                request.version().trim(),
                request.definitionJson(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateWorkflowRequest normalizeUpdateRequest(UpdateWorkflowRequest request) {
        return new UpdateWorkflowRequest(
                request.workflowCode().trim(),
                request.workflowName().trim(),
                request.workflowType().trim(),
                request.workflowCategory().trim(),
                normalizeNullableText(request.description()),
                request.version().trim(),
                request.definitionJson(),
                request.status().trim(),
                normalizeMetadata(request.metadata()),
                normalizeNullableText(request.remark())
        );
    }

    private JsonNode normalizeMetadata(JsonNode metadata) {
        return metadata == null ? objectMapper.createObjectNode() : metadata;
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("Workflow 状态不合法");
        }
    }

    private void validateDictFields(String workflowType, String workflowCategory) {
        dictService.validateEnabledDictItem("workflow_type", workflowType, "Workflow 类型");
        dictService.validateEnabledDictItem("workflow_category", workflowCategory, "Workflow 分类");
    }

    private void validateJsonObject(String fieldName, JsonNode jsonNode) {
        if (jsonNode == null || !jsonNode.isObject()) {
            throw new BusinessException(fieldName + "必须为 JSON 对象");
        }
    }

    private void validatePublishedDefinition(String status, JsonNode definitionJson) {
        if (STATUS_PUBLISHED.equals(status)) requireValidDefinition(definitionJson);
    }

    private void requireValidDefinition(JsonNode definitionJson) {
        WorkflowDefinitionValidator.ValidationReport report = definitionValidator.validate(definitionJson);
        if (!report.valid()) {
            String details = report.issues().stream().limit(8)
                    .map(issue -> (issue.nodeId() == null ? "" : "节点[" + issue.nodeId() + "] ") + issue.message())
                    .reduce((left, right) -> left + "；" + right).orElse("定义不合法");
            throw new BusinessException("Workflow 发布校验失败：" + details);
        }
    }

    private void ensureWorkflowCodeUnique(String workflowCode, Long excludeWorkflowId) {
        workflowRepository.findWorkflowIdByCode(workflowCode).ifPresent(existingWorkflowId -> {
            if (excludeWorkflowId == null || !existingWorkflowId.equals(excludeWorkflowId)) {
                throw new BusinessException("Workflow 编码已存在");
            }
        });
    }

    private void ensureWorkflowExists(Long workflowId) {
        getWorkflowRecord(workflowId);
    }

    private WorkflowDetailRecord getWorkflowRecord(Long workflowId) {
        return workflowRepository.findWorkflowDetailById(workflowId)
                .orElseThrow(() -> new BusinessException("Workflow 不存在"));
    }

    private WorkflowDetailResponse toWorkflowDetailResponse(WorkflowDetailRecord record) {
        return new WorkflowDetailResponse(
                record.workflowId(),
                record.workflowCode(),
                record.workflowName(),
                record.workflowType(),
                record.workflowCategory(),
                record.description(),
                record.version(),
                parseJson(record.definitionJsonText()),
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
            throw new BusinessException("Workflow JSON 数据格式不合法");
        }
    }

    private String toJson(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Workflow JSON 数据格式不合法");
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

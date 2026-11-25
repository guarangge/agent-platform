package com.zc.agent.registry.modules.dict.service;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.dto.CreateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.CreateDictTypeRequest;
import com.zc.agent.registry.modules.dict.dto.DictItemPageQueryRequest;
import com.zc.agent.registry.modules.dict.dto.DictTypePageQueryRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictTypeRequest;
import com.zc.agent.registry.modules.dict.model.DictItemDetailRecord;
import com.zc.agent.registry.modules.dict.model.DictOptionRecord;
import com.zc.agent.registry.modules.dict.model.DictTypeDetailRecord;
import com.zc.agent.registry.modules.dict.repository.DictRepository;
import com.zc.agent.registry.modules.dict.vo.DictItemDetailResponse;
import com.zc.agent.registry.modules.dict.vo.DictItemPageItemResponse;
import com.zc.agent.registry.modules.dict.vo.DictOptionItemResponse;
import com.zc.agent.registry.modules.dict.vo.DictTypeDetailResponse;
import com.zc.agent.registry.modules.dict.vo.DictTypePageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典服务。
 */
@Service
public class DictService {

    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_ENABLED, STATUS_DISABLED);

    private final DictRepository dictRepository;

    public DictService(DictRepository dictRepository) {
        this.dictRepository = dictRepository;
    }

    /**
     * 分页查询字典类型。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResponse<DictTypePageItemResponse> pageDictTypes(DictTypePageQueryRequest request) {
        DictTypePageQueryRequest normalizedRequest = normalizeDictTypeQuery(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status(), "字典类型状态");
        }
        long total = dictRepository.countDictTypes(
                normalizedRequest.typeCode(),
                normalizedRequest.typeName(),
                normalizedRequest.status()
        );
        List<DictTypePageItemResponse> records = dictRepository.pageDictTypes(
                        normalizedRequest.typeCode(),
                        normalizedRequest.typeName(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new DictTypePageItemResponse(
                        item.dictTypeId(),
                        item.typeCode(),
                        item.typeName(),
                        item.status(),
                        item.builtIn(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询字典类型详情。
     *
     * @param dictTypeId 字典类型 ID
     * @return 字典类型详情
     */
    public DictTypeDetailResponse getDictTypeDetail(Long dictTypeId) {
        return toDictTypeDetailResponse(getDictTypeRecord(dictTypeId));
    }

    /**
     * 新增字典类型。
     *
     * @param request 新增请求
     * @param securityUser 当前用户
     * @return 字典类型详情
     */
    @Transactional
    public DictTypeDetailResponse createDictType(CreateDictTypeRequest request, SecurityUser securityUser) {
        CreateDictTypeRequest normalizedRequest = normalizeCreateDictTypeRequest(request);
        validateStatus(normalizedRequest.status(), "字典类型状态");
        ensureDictTypeCodeUnique(normalizedRequest.typeCode(), null);
        Long dictTypeId = dictRepository.insertDictType(normalizedRequest, securityUser.userId());
        return getDictTypeDetail(dictTypeId);
    }

    /**
     * 编辑字典类型。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 编辑请求
     * @param securityUser 当前用户
     * @return 字典类型详情
     */
    @Transactional
    public DictTypeDetailResponse updateDictType(Long dictTypeId,
                                                 UpdateDictTypeRequest request,
                                                 SecurityUser securityUser) {
        UpdateDictTypeRequest normalizedRequest = normalizeUpdateDictTypeRequest(request);
        validateStatus(normalizedRequest.status(), "字典类型状态");
        ensureDictTypeExists(dictTypeId);
        ensureDictTypeCodeUnique(normalizedRequest.typeCode(), dictTypeId);
        int updated = dictRepository.updateDictType(dictTypeId, normalizedRequest, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("字典类型不存在");
        }
        return getDictTypeDetail(dictTypeId);
    }

    /**
     * 更新字典类型状态。
     *
     * @param dictTypeId 字典类型 ID
     * @param status 状态
     * @param securityUser 当前用户
     */
    @Transactional
    public void updateDictTypeStatus(Long dictTypeId, String status, SecurityUser securityUser) {
        validateStatus(status, "字典类型状态");
        ensureDictTypeExists(dictTypeId);
        int updated = dictRepository.updateDictTypeStatus(dictTypeId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("字典类型不存在");
        }
    }

    /**
     * 分页查询字典项。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResponse<DictItemPageItemResponse> pageDictItems(Long dictTypeId, DictItemPageQueryRequest request) {
        ensureDictTypeExists(dictTypeId);
        DictItemPageQueryRequest normalizedRequest = normalizeDictItemQuery(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status(), "字典项状态");
        }
        long total = dictRepository.countDictItems(
                dictTypeId,
                normalizedRequest.itemCode(),
                normalizedRequest.itemName(),
                normalizedRequest.status()
        );
        List<DictItemPageItemResponse> records = dictRepository.pageDictItems(
                        dictTypeId,
                        normalizedRequest.itemCode(),
                        normalizedRequest.itemName(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new DictItemPageItemResponse(
                        item.dictItemId(),
                        item.dictTypeId(),
                        item.typeCode(),
                        item.itemCode(),
                        item.itemName(),
                        item.sortOrder(),
                        item.status(),
                        item.isDefault(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询字典项详情。
     *
     * @param dictItemId 字典项 ID
     * @return 字典项详情
     */
    public DictItemDetailResponse getDictItemDetail(Long dictItemId) {
        return toDictItemDetailResponse(getDictItemRecord(dictItemId));
    }

    /**
     * 新增字典项。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 新增请求
     * @param securityUser 当前用户
     * @return 字典项详情
     */
    @Transactional
    public DictItemDetailResponse createDictItem(Long dictTypeId,
                                                 CreateDictItemRequest request,
                                                 SecurityUser securityUser) {
        ensureDictTypeExists(dictTypeId);
        CreateDictItemRequest normalizedRequest = normalizeCreateDictItemRequest(request);
        validateStatus(normalizedRequest.status(), "字典项状态");
        ensureDictItemCodeUnique(dictTypeId, normalizedRequest.itemCode(), null);
        Long dictItemId = dictRepository.insertDictItem(dictTypeId, normalizedRequest, securityUser.userId());
        return getDictItemDetail(dictItemId);
    }

    /**
     * 编辑字典项。
     *
     * @param dictItemId 字典项 ID
     * @param request 编辑请求
     * @param securityUser 当前用户
     * @return 字典项详情
     */
    @Transactional
    public DictItemDetailResponse updateDictItem(Long dictItemId,
                                                 UpdateDictItemRequest request,
                                                 SecurityUser securityUser) {
        DictItemDetailRecord existingRecord = getDictItemRecord(dictItemId);
        UpdateDictItemRequest normalizedRequest = normalizeUpdateDictItemRequest(request);
        validateStatus(normalizedRequest.status(), "字典项状态");
        ensureDictItemCodeUnique(existingRecord.dictTypeId(), normalizedRequest.itemCode(), dictItemId);
        int updated = dictRepository.updateDictItem(dictItemId, normalizedRequest, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("字典项不存在");
        }
        return getDictItemDetail(dictItemId);
    }

    /**
     * 更新字典项状态。
     *
     * @param dictItemId 字典项 ID
     * @param status 状态
     * @param securityUser 当前用户
     */
    @Transactional
    public void updateDictItemStatus(Long dictItemId, String status, SecurityUser securityUser) {
        validateStatus(status, "字典项状态");
        getDictItemRecord(dictItemId);
        int updated = dictRepository.updateDictItemStatus(dictItemId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("字典项不存在");
        }
    }

    /**
     * 查询字典下拉选项。
     *
     * @param typeCodes 字典类型编码列表
     * @return 字典选项映射
     */
    public Map<String, List<DictOptionItemResponse>> getDictOptions(List<String> typeCodes) {
        List<String> normalizedTypeCodes = typeCodes.stream()
                .map(this::normalizeNullableText)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        Map<String, List<DictOptionItemResponse>> result = new LinkedHashMap<>();
        for (String typeCode : normalizedTypeCodes) {
            result.put(typeCode, List.of());
        }
        if (normalizedTypeCodes.isEmpty()) {
            return result;
        }
        Map<String, List<DictOptionItemResponse>> grouped = new LinkedHashMap<>();
        for (DictOptionRecord item : dictRepository.findEnabledOptionsByTypeCodes(normalizedTypeCodes)) {
            grouped.computeIfAbsent(item.typeCode(), key -> new java.util.ArrayList<>())
                    .add(toDictOptionResponse(item));
        }
        normalizedTypeCodes.forEach(typeCode -> result.put(typeCode, grouped.getOrDefault(typeCode, List.of())));
        return result;
    }

    /**
     * 校验字典项是否启用。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @param fieldName 字段名称
     */
    public void validateEnabledDictItem(String typeCode, String itemCode, String fieldName) {
        if (!dictRepository.existsEnabledDictItem(typeCode, itemCode)) {
            throw new BusinessException(fieldName + "不在可用字典范围内");
        }
    }

    private DictTypePageQueryRequest normalizeDictTypeQuery(DictTypePageQueryRequest request) {
        return new DictTypePageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.typeCode()),
                normalizeNullableText(request.typeName()),
                normalizeNullableText(request.status())
        );
    }

    private DictItemPageQueryRequest normalizeDictItemQuery(DictItemPageQueryRequest request) {
        return new DictItemPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.itemCode()),
                normalizeNullableText(request.itemName()),
                normalizeNullableText(request.status())
        );
    }

    private CreateDictTypeRequest normalizeCreateDictTypeRequest(CreateDictTypeRequest request) {
        return new CreateDictTypeRequest(
                request.typeCode().trim(),
                request.typeName().trim(),
                request.status().trim(),
                request.builtIn(),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateDictTypeRequest normalizeUpdateDictTypeRequest(UpdateDictTypeRequest request) {
        return new UpdateDictTypeRequest(
                request.typeCode().trim(),
                request.typeName().trim(),
                request.status().trim(),
                request.builtIn(),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private CreateDictItemRequest normalizeCreateDictItemRequest(CreateDictItemRequest request) {
        return new CreateDictItemRequest(
                request.itemCode().trim(),
                request.itemName().trim(),
                normalizeSortOrder(request.sortOrder()),
                request.status().trim(),
                request.isDefault(),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateDictItemRequest normalizeUpdateDictItemRequest(UpdateDictItemRequest request) {
        return new UpdateDictItemRequest(
                request.itemCode().trim(),
                request.itemName().trim(),
                normalizeSortOrder(request.sortOrder()),
                request.status().trim(),
                request.isDefault(),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private int normalizeSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private void validateStatus(String status, String fieldName) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException(fieldName + "不合法");
        }
    }

    private void ensureDictTypeCodeUnique(String typeCode, Long excludeDictTypeId) {
        dictRepository.findDictTypeIdByCode(typeCode).ifPresent(existingId -> {
            if (excludeDictTypeId == null || !existingId.equals(excludeDictTypeId)) {
                throw new BusinessException("字典类型编码已存在");
            }
        });
    }

    private void ensureDictItemCodeUnique(Long dictTypeId, String itemCode, Long excludeDictItemId) {
        dictRepository.findDictItemIdByTypeAndCode(dictTypeId, itemCode).ifPresent(existingId -> {
            if (excludeDictItemId == null || !existingId.equals(excludeDictItemId)) {
                throw new BusinessException("字典项编码已存在");
            }
        });
    }

    private void ensureDictTypeExists(Long dictTypeId) {
        getDictTypeRecord(dictTypeId);
    }

    private DictTypeDetailRecord getDictTypeRecord(Long dictTypeId) {
        return dictRepository.findDictTypeById(dictTypeId)
                .orElseThrow(() -> new BusinessException("字典类型不存在"));
    }

    private DictItemDetailRecord getDictItemRecord(Long dictItemId) {
        return dictRepository.findDictItemById(dictItemId)
                .orElseThrow(() -> new BusinessException("字典项不存在"));
    }

    private DictTypeDetailResponse toDictTypeDetailResponse(DictTypeDetailRecord record) {
        return new DictTypeDetailResponse(
                record.dictTypeId(),
                record.typeCode(),
                record.typeName(),
                record.status(),
                record.builtIn(),
                record.description(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private DictItemDetailResponse toDictItemDetailResponse(DictItemDetailRecord record) {
        return new DictItemDetailResponse(
                record.dictItemId(),
                record.dictTypeId(),
                record.typeCode(),
                record.itemCode(),
                record.itemName(),
                record.sortOrder(),
                record.status(),
                record.isDefault(),
                record.description(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private DictOptionItemResponse toDictOptionResponse(DictOptionRecord record) {
        return new DictOptionItemResponse(record.itemCode(), record.itemName(), record.description());
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

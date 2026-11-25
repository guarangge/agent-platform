package com.zc.agent.registry.modules.dict.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.dict.dto.CreateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.CreateDictTypeRequest;
import com.zc.agent.registry.modules.dict.dto.DictItemPageQueryRequest;
import com.zc.agent.registry.modules.dict.dto.DictTypePageQueryRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictTypeRequest;
import com.zc.agent.registry.modules.dict.service.DictService;
import com.zc.agent.registry.modules.dict.vo.DictItemDetailResponse;
import com.zc.agent.registry.modules.dict.vo.DictItemPageItemResponse;
import com.zc.agent.registry.modules.dict.vo.DictOptionItemResponse;
import com.zc.agent.registry.modules.dict.vo.DictTypeDetailResponse;
import com.zc.agent.registry.modules.dict.vo.DictTypePageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 字典管理接口。
 */
@RestController
@RequestMapping("/api/registry")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 分页查询字典类型。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/dict-types")
    public ApiResponse<PageResponse<DictTypePageItemResponse>> pageDictTypes(@Valid DictTypePageQueryRequest request) {
        return ApiResponse.success(dictService.pageDictTypes(request));
    }

    /**
     * 查询字典类型详情。
     *
     * @param dictTypeId 字典类型 ID
     * @return 字典类型详情
     */
    @GetMapping("/dict-types/{dictTypeId}")
    public ApiResponse<DictTypeDetailResponse> getDictTypeDetail(@PathVariable Long dictTypeId) {
        return ApiResponse.success(dictService.getDictTypeDetail(dictTypeId));
    }

    /**
     * 新增字典类型。
     *
     * @param request 新增请求
     * @param authentication 当前认证信息
     * @return 字典类型详情
     */
    @PostMapping("/dict-types")
    public ApiResponse<DictTypeDetailResponse> createDictType(@Valid @RequestBody CreateDictTypeRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(dictService.createDictType(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑字典类型。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 编辑请求
     * @param authentication 当前认证信息
     * @return 字典类型详情
     */
    @PutMapping("/dict-types/{dictTypeId}")
    public ApiResponse<DictTypeDetailResponse> updateDictType(@PathVariable Long dictTypeId,
                                                              @Valid @RequestBody UpdateDictTypeRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(dictService.updateDictType(dictTypeId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用字典类型。
     *
     * @param dictTypeId 字典类型 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/dict-types/{dictTypeId}/enable")
    public ApiResponse<Void> enableDictType(@PathVariable Long dictTypeId, Authentication authentication) {
        dictService.updateDictTypeStatus(dictTypeId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用字典类型。
     *
     * @param dictTypeId 字典类型 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/dict-types/{dictTypeId}/disable")
    public ApiResponse<Void> disableDictType(@PathVariable Long dictTypeId, Authentication authentication) {
        dictService.updateDictTypeStatus(dictTypeId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 分页查询字典项。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/dict-types/{dictTypeId}/items")
    public ApiResponse<PageResponse<DictItemPageItemResponse>> pageDictItems(@PathVariable Long dictTypeId,
                                                                             @Valid DictItemPageQueryRequest request) {
        return ApiResponse.success(dictService.pageDictItems(dictTypeId, request));
    }

    /**
     * 新增字典项。
     *
     * @param dictTypeId 字典类型 ID
     * @param request 新增请求
     * @param authentication 当前认证信息
     * @return 字典项详情
     */
    @PostMapping("/dict-types/{dictTypeId}/items")
    public ApiResponse<DictItemDetailResponse> createDictItem(@PathVariable Long dictTypeId,
                                                              @Valid @RequestBody CreateDictItemRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(dictService.createDictItem(dictTypeId, request, getSecurityUser(authentication)));
    }

    /**
     * 查询字典项详情。
     *
     * @param dictItemId 字典项 ID
     * @return 字典项详情
     */
    @GetMapping("/dict-items/{dictItemId}")
    public ApiResponse<DictItemDetailResponse> getDictItemDetail(@PathVariable Long dictItemId) {
        return ApiResponse.success(dictService.getDictItemDetail(dictItemId));
    }

    /**
     * 编辑字典项。
     *
     * @param dictItemId 字典项 ID
     * @param request 编辑请求
     * @param authentication 当前认证信息
     * @return 字典项详情
     */
    @PutMapping("/dict-items/{dictItemId}")
    public ApiResponse<DictItemDetailResponse> updateDictItem(@PathVariable Long dictItemId,
                                                              @Valid @RequestBody UpdateDictItemRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(dictService.updateDictItem(dictItemId, request, getSecurityUser(authentication)));
    }

    /**
     * 启用字典项。
     *
     * @param dictItemId 字典项 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/dict-items/{dictItemId}/enable")
    public ApiResponse<Void> enableDictItem(@PathVariable Long dictItemId, Authentication authentication) {
        dictService.updateDictItemStatus(dictItemId, "enabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 停用字典项。
     *
     * @param dictItemId 字典项 ID
     * @param authentication 当前认证信息
     * @return 操作结果
     */
    @PutMapping("/dict-items/{dictItemId}/disable")
    public ApiResponse<Void> disableDictItem(@PathVariable Long dictItemId, Authentication authentication) {
        dictService.updateDictItemStatus(dictItemId, "disabled", getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /**
     * 查询字典下拉选项。
     *
     * @param typeCodes 字典类型编码列表
     * @return 字典选项映射
     */
    @GetMapping("/dicts/options")
    public ApiResponse<Map<String, List<DictOptionItemResponse>>> getDictOptions(@RequestParam List<String> typeCodes) {
        return ApiResponse.success(dictService.getDictOptions(typeCodes));
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

package com.zc.agent.registry.modules.role.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.role.dto.CreateRoleRequest;
import com.zc.agent.registry.modules.role.dto.RolePageQueryRequest;
import com.zc.agent.registry.modules.role.dto.UpdateRoleAgentsRequest;
import com.zc.agent.registry.modules.role.dto.UpdateRoleRequest;
import com.zc.agent.registry.modules.role.model.RoleAgentRecord;
import com.zc.agent.registry.modules.role.model.RoleDetailRecord;
import com.zc.agent.registry.modules.role.repository.RoleRepository;
import com.zc.agent.registry.modules.role.vo.RoleAgentResponse;
import com.zc.agent.registry.modules.role.vo.RoleDetailResponse;
import com.zc.agent.registry.modules.role.vo.RolePageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色服务。
 */
@Service
public class RoleService {

    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_ENABLED, STATUS_DISABLED);

    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public RoleService(RoleRepository roleRepository, ObjectMapper objectMapper) {
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询角色。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResponse<RolePageItemResponse> pageRoles(RolePageQueryRequest request) {
        RolePageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = roleRepository.countRoles(
                normalizedRequest.roleCode(),
                normalizedRequest.roleName(),
                normalizedRequest.status()
        );
        List<RolePageItemResponse> records = roleRepository.pageRoles(
                        normalizedRequest.roleCode(),
                        normalizedRequest.roleName(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new RolePageItemResponse(
                        item.roleId(),
                        item.roleCode(),
                        item.roleName(),
                        item.status(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询角色详情。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    public RoleDetailResponse getRoleDetail(Long roleId) {
        return toRoleDetailResponse(getRoleRecord(roleId));
    }

    /**
     * 查询角色已绑定 Agent。
     *
     * @param roleId 角色 ID
     * @return Agent 列表
     */
    public List<RoleAgentResponse> getRoleAgents(Long roleId) {
        ensureRoleExists(roleId);
        return roleRepository.findRoleAgentsByRoleId(roleId).stream()
                .map(this::toRoleAgentResponse)
                .toList();
    }

    /**
     * 新增角色。
     *
     * @param request      新增请求
     * @param securityUser 当前登录用户
     * @return 角色详情
     */
    @Transactional
    public RoleDetailResponse createRole(CreateRoleRequest request, SecurityUser securityUser) {
        CreateRoleRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        ensureRoleCodeUnique(normalizedRequest.roleCode(), null);
        Long roleId = roleRepository.insertRole(
                normalizedRequest,
                toMenuScopeJson(normalizedRequest.menuScope()),
                securityUser.userId()
        );
        return getRoleDetail(roleId);
    }

    /**
     * 编辑角色。
     *
     * @param roleId       角色 ID
     * @param request      编辑请求
     * @param securityUser 当前登录用户
     * @return 角色详情
     */
    @Transactional
    public RoleDetailResponse updateRole(Long roleId, UpdateRoleRequest request, SecurityUser securityUser) {
        UpdateRoleRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        ensureRoleExists(roleId);
        ensureRoleCodeUnique(normalizedRequest.roleCode(), roleId);
        int updated = roleRepository.updateRole(
                roleId,
                normalizedRequest,
                toMenuScopeJson(normalizedRequest.menuScope()),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("角色不存在");
        }
        return getRoleDetail(roleId);
    }

    /**
     * 更新角色状态。
     *
     * @param roleId       角色 ID
     * @param status       角色状态
     * @param securityUser 当前登录用户
     */
    @Transactional
    public void updateRoleStatus(Long roleId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensureRoleExists(roleId);
        int updated = roleRepository.updateRoleStatus(roleId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("角色不存在");
        }
    }

    /**
     * 覆盖更新角色 Agent。
     *
     * @param roleId       角色 ID
     * @param request      更新请求
     * @param securityUser 当前登录用户
     * @return 最新 Agent 列表
     */
    @Transactional
    public List<RoleAgentResponse> updateRoleAgents(Long roleId,
                                                    UpdateRoleAgentsRequest request,
                                                    SecurityUser securityUser) {
        ensureRoleExists(roleId);
        List<Long> targetAgentIds = normalizeAgentIds(request);
        validateAgentIdsExist(targetAgentIds);

        Set<Long> currentAgentIds = new LinkedHashSet<>(roleRepository.findBoundAgentIdsByRoleId(roleId));
        List<Long> agentIdsToAdd = targetAgentIds.stream()
                .filter(agentId -> !currentAgentIds.contains(agentId))
                .toList();
        List<Long> agentIdsToRemove = currentAgentIds.stream()
                .filter(agentId -> !targetAgentIds.contains(agentId))
                .toList();

        if (!agentIdsToRemove.isEmpty()) {
            roleRepository.softDeleteRoleAgents(roleId, agentIdsToRemove, securityUser.userId());
        }
        if (!agentIdsToAdd.isEmpty()) {
            roleRepository.insertRoleAgents(roleId, agentIdsToAdd, securityUser.userId());
        }
        return getRoleAgents(roleId);
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeRoleId) {
        roleRepository.findRoleIdByRoleCode(roleCode.trim()).ifPresent(existingRoleId -> {
            if (excludeRoleId == null || !existingRoleId.equals(excludeRoleId)) {
                throw new BusinessException("角色编码已存在");
            }
        });
    }

    private void ensureRoleExists(Long roleId) {
        getRoleRecord(roleId);
    }

    private RoleDetailRecord getRoleRecord(Long roleId) {
        return roleRepository.findRoleDetailById(roleId)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("角色状态不合法");
        }
    }

    private CreateRoleRequest normalizeCreateRequest(CreateRoleRequest request) {
        return new CreateRoleRequest(
                request.roleCode().trim(),
                request.roleName().trim(),
                request.status().trim(),
                normalizeMenuScope(request.menuScope()),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateRoleRequest normalizeUpdateRequest(UpdateRoleRequest request) {
        return new UpdateRoleRequest(
                request.roleCode().trim(),
                request.roleName().trim(),
                request.status().trim(),
                normalizeMenuScope(request.menuScope()),
                normalizeNullableText(request.description()),
                normalizeNullableText(request.remark())
        );
    }

    private RolePageQueryRequest normalizePageQueryRequest(RolePageQueryRequest request) {
        return new RolePageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.roleCode()),
                normalizeNullableText(request.roleName()),
                normalizeNullableText(request.status())
        );
    }

    private List<Long> normalizeAgentIds(UpdateRoleAgentsRequest request) {
        boolean containsInvalidAgentId = request.agentIds().stream()
                .anyMatch(agentId -> agentId == null || agentId <= 0);
        if (containsInvalidAgentId) {
            throw new BusinessException("Agent ID 必须为正整数");
        }
        return request.agentIds().stream()
                .distinct()
                .toList();
    }

    private void validateAgentIdsExist(List<Long> agentIds) {
        if (agentIds.isEmpty()) {
            return;
        }
        Set<Long> existingAgentIds = new LinkedHashSet<>(roleRepository.findExistingAgentIds(agentIds));
        List<Long> missingAgentIds = agentIds.stream()
                .filter(agentId -> !existingAgentIds.contains(agentId))
                .toList();
        if (!missingAgentIds.isEmpty()) {
            throw new BusinessException("Agent 不存在: " + missingAgentIds);
        }
    }

    private List<String> normalizeMenuScope(List<String> menuScope) {
        List<String> normalizedMenuScope = menuScope.stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
        if (normalizedMenuScope.isEmpty()) {
            throw new BusinessException("菜单范围不能为空");
        }
        return normalizedMenuScope;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RoleDetailResponse toRoleDetailResponse(RoleDetailRecord record) {
        return new RoleDetailResponse(
                record.roleId(),
                record.roleCode(),
                record.roleName(),
                record.status(),
                parseMenuScope(record.menuScopeJson()),
                record.description(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private RoleAgentResponse toRoleAgentResponse(RoleAgentRecord record) {
        return new RoleAgentResponse(
                record.agentId(),
                record.agentCode(),
                record.agentName(),
                record.status(),
                record.version(),
                record.description(),
                record.roleBoundAt()
        );
    }

    private List<String> parseMenuScope(String menuScopeJson) {
        try {
            return objectMapper.readValue(menuScopeJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException("角色菜单范围数据格式不正确");
        }
    }

    private String toMenuScopeJson(List<String> menuScope) {
        try {
            return objectMapper.writeValueAsString(menuScope);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("角色菜单范围数据格式不正确");
        }
    }
}

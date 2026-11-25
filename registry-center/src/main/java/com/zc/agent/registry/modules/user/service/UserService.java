package com.zc.agent.registry.modules.user.service;

import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.user.dto.CreateUserRequest;
import com.zc.agent.registry.modules.user.dto.ResetPasswordRequest;
import com.zc.agent.registry.modules.user.dto.UpdateUserRolesRequest;
import com.zc.agent.registry.modules.user.dto.UpdateUserRequest;
import com.zc.agent.registry.modules.user.dto.UserPageQueryRequest;
import com.zc.agent.registry.modules.user.model.UserDetailRecord;
import com.zc.agent.registry.modules.user.model.UserRoleRecord;
import com.zc.agent.registry.modules.user.repository.UserRepository;
import com.zc.agent.registry.modules.user.vo.UserDetailResponse;
import com.zc.agent.registry.modules.user.vo.UserPageItemResponse;
import com.zc.agent.registry.modules.user.vo.UserRoleResponse;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务。
 */
@Service
public class UserService {

    private static final String STATUS_ENABLED = "enabled";
    private static final String STATUS_DISABLED = "disabled";
    private static final List<String> ALLOWED_STATUS = List.of(STATUS_ENABLED, STATUS_DISABLED);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResponse<UserPageItemResponse> pageUsers(UserPageQueryRequest request) {
        UserPageQueryRequest normalizedRequest = normalizePageQueryRequest(request);
        int pageNum = normalizedRequest.pageNum() == null ? 1 : normalizedRequest.pageNum();
        int pageSize = normalizedRequest.pageSize() == null ? 10 : normalizedRequest.pageSize();
        if (normalizedRequest.status() != null) {
            validateStatus(normalizedRequest.status());
        }
        long total = userRepository.countUsers(
                normalizedRequest.username(),
                normalizedRequest.displayName(),
                normalizedRequest.status()
        );
        List<UserPageItemResponse> records = userRepository.pageUsers(
                        normalizedRequest.username(),
                        normalizedRequest.displayName(),
                        normalizedRequest.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new UserPageItemResponse(
                        item.userId(),
                        item.username(),
                        item.displayName(),
                        item.email(),
                        item.mobile(),
                        item.status(),
                        item.lastLoginAt(),
                        item.createdAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    /**
     * 查询用户详情。
     *
     * @param userId 用户 ID
     * @return 用户详情
     */
    public UserDetailResponse getUserDetail(Long userId) {
        return toUserDetailResponse(getUserRecord(userId));
    }

    /**
     * 查询用户已绑定角色。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    public List<UserRoleResponse> getUserRoles(Long userId) {
        ensureUserExists(userId);
        return userRepository.findUserRolesByUserId(userId).stream()
                .map(this::toUserRoleResponse)
                .toList();
    }

    /**
     * 新增用户。
     *
     * @param request      新增请求
     * @param securityUser 当前登录用户
     * @return 用户详情
     */
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request, SecurityUser securityUser) {
        CreateUserRequest normalizedRequest = normalizeCreateRequest(request);
        validateStatus(normalizedRequest.status());
        ensureUsernameUnique(normalizedRequest.username(), null);
        Long userId = userRepository.insertUser(
                normalizedRequest,
                passwordEncoder.encode(normalizedRequest.password()),
                securityUser.userId()
        );
        return getUserDetail(userId);
    }

    /**
     * 编辑用户。
     *
     * @param userId       用户 ID
     * @param request      编辑请求
     * @param securityUser 当前登录用户
     * @return 用户详情
     */
    @Transactional
    public UserDetailResponse updateUser(Long userId, UpdateUserRequest request, SecurityUser securityUser) {
        UpdateUserRequest normalizedRequest = normalizeUpdateRequest(request);
        validateStatus(normalizedRequest.status());
        ensureUserExists(userId);
        int updated = userRepository.updateUser(userId, normalizedRequest, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("用户不存在");
        }
        return getUserDetail(userId);
    }

    /**
     * 更新用户状态。
     *
     * @param userId       用户 ID
     * @param status       用户状态
     * @param securityUser 当前登录用户
     */
    @Transactional
    public void updateUserStatus(Long userId, String status, SecurityUser securityUser) {
        validateStatus(status);
        ensureUserExists(userId);
        int updated = userRepository.updateUserStatus(userId, status, securityUser.userId());
        if (updated <= 0) {
            throw new BusinessException("用户不存在");
        }
    }

    /**
     * 重置密码。
     *
     * @param userId       用户 ID
     * @param request      重置密码请求
     * @param securityUser 当前登录用户
     */
    @Transactional
    public void resetPassword(Long userId, ResetPasswordRequest request, SecurityUser securityUser) {
        ensureUserExists(userId);
        ResetPasswordRequest normalizedRequest = normalizeResetPasswordRequest(request);
        int updated = userRepository.updatePassword(
                userId,
                passwordEncoder.encode(normalizedRequest.newPassword()),
                securityUser.userId()
        );
        if (updated <= 0) {
            throw new BusinessException("用户不存在");
        }
    }

    /**
     * 覆盖更新用户角色。
     *
     * @param userId       用户 ID
     * @param request      更新请求
     * @param securityUser 当前登录用户
     * @return 最新角色列表
     */
    @Transactional
    public List<UserRoleResponse> updateUserRoles(Long userId, UpdateUserRolesRequest request, SecurityUser securityUser) {
        ensureUserExists(userId);
        List<Long> targetRoleIds = normalizeRoleIds(request);
        validateRoleIdsExist(targetRoleIds);

        Set<Long> currentRoleIds = new LinkedHashSet<>(userRepository.findBoundRoleIdsByUserId(userId));
        List<Long> roleIdsToAdd = targetRoleIds.stream()
                .filter(roleId -> !currentRoleIds.contains(roleId))
                .toList();
        List<Long> roleIdsToRemove = currentRoleIds.stream()
                .filter(roleId -> !targetRoleIds.contains(roleId))
                .toList();

        if (!roleIdsToRemove.isEmpty()) {
            userRepository.softDeleteUserRoles(userId, roleIdsToRemove, securityUser.userId());
        }
        if (!roleIdsToAdd.isEmpty()) {
            userRepository.insertUserRoles(userId, roleIdsToAdd, securityUser.userId());
        }
        return getUserRoles(userId);
    }

    private void ensureUsernameUnique(String username, Long excludeUserId) {
        userRepository.findUserIdByUsername(username.trim()).ifPresent(existingUserId -> {
            if (excludeUserId == null || !existingUserId.equals(excludeUserId)) {
                throw new BusinessException("用户名已存在");
            }
        });
    }

    private void ensureUserExists(Long userId) {
        getUserRecord(userId);
    }

    private UserDetailRecord getUserRecord(Long userId) {
        return userRepository.findUserDetailById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private void validateStatus(String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BusinessException("用户状态不合法");
        }
    }

    private CreateUserRequest normalizeCreateRequest(CreateUserRequest request) {
        return new CreateUserRequest(
                request.username().trim(),
                request.password().trim(),
                request.displayName().trim(),
                normalizeNullableText(request.email()),
                normalizeNullableText(request.mobile()),
                request.status().trim(),
                normalizeNullableText(request.remark())
        );
    }

    private UpdateUserRequest normalizeUpdateRequest(UpdateUserRequest request) {
        return new UpdateUserRequest(
                request.displayName().trim(),
                normalizeNullableText(request.email()),
                normalizeNullableText(request.mobile()),
                request.status().trim(),
                normalizeNullableText(request.remark())
        );
    }

    private ResetPasswordRequest normalizeResetPasswordRequest(ResetPasswordRequest request) {
        return new ResetPasswordRequest(request.newPassword().trim());
    }

    private List<Long> normalizeRoleIds(UpdateUserRolesRequest request) {
        boolean containsInvalidRoleId = request.roleIds().stream()
                .anyMatch(roleId -> roleId == null || roleId <= 0);
        if (containsInvalidRoleId) {
            throw new BusinessException("角色 ID 必须为正整数");
        }
        return request.roleIds().stream()
                .distinct()
                .toList();
    }

    private void validateRoleIdsExist(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Set<Long> existingRoleIds = new LinkedHashSet<>(userRepository.findExistingRoleIds(roleIds));
        List<Long> missingRoleIds = roleIds.stream()
                .filter(roleId -> !existingRoleIds.contains(roleId))
                .toList();
        if (!missingRoleIds.isEmpty()) {
            throw new BusinessException("角色不存在: " + missingRoleIds);
        }
    }

    private UserPageQueryRequest normalizePageQueryRequest(UserPageQueryRequest request) {
        return new UserPageQueryRequest(
                request.pageNum(),
                request.pageSize(),
                normalizeNullableText(request.username()),
                normalizeNullableText(request.displayName()),
                normalizeNullableText(request.status())
        );
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private UserDetailResponse toUserDetailResponse(UserDetailRecord record) {
        return new UserDetailResponse(
                record.userId(),
                record.username(),
                record.displayName(),
                record.email(),
                record.mobile(),
                record.status(),
                record.lastLoginAt(),
                record.remark(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy()
        );
    }

    private UserRoleResponse toUserRoleResponse(UserRoleRecord record) {
        return new UserRoleResponse(
                record.roleId(),
                record.roleCode(),
                record.roleName(),
                record.status(),
                record.description(),
                record.boundAt()
        );
    }
}

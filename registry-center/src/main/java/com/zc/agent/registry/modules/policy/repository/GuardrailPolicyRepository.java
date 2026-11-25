package com.zc.agent.registry.modules.policy.repository;

import com.zc.agent.registry.modules.policy.model.GuardrailPolicyDetailRecord;
import com.zc.agent.registry.modules.policy.model.GuardrailPolicyPageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guardrail 策略仓储。
 */
@Repository
public class GuardrailPolicyRepository {

    private static final RowMapper<GuardrailPolicyPageRecord> PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public GuardrailPolicyPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GuardrailPolicyPageRecord(
                    rs.getLong("id"),
                    rs.getString("policy_code"),
                    rs.getString("policy_name"),
                    rs.getInt("policy_version"),
                    rs.getInt("priority"),
                    rs.getString("action_type"),
                    rs.getString("rollout_mode"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getInt("scope_user_count"),
                    rs.getInt("scope_role_count"),
                    rs.getInt("scope_agent_count"),
                    rs.getInt("scope_tool_count"),
                    rs.getInt("scope_workflow_count"),
                    rs.getInt("scope_env_count"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<GuardrailPolicyDetailRecord> DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public GuardrailPolicyDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new GuardrailPolicyDetailRecord(
                    rs.getLong("id"),
                    rs.getString("policy_code"),
                    rs.getString("policy_name"),
                    rs.getInt("policy_version"),
                    rs.getInt("priority"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getString("rollout_mode"),
                    getNullableInteger(rs, "rollout_percentage"),
                    rs.getString("rollout_user_ids"),
                    rs.getString("scope_user_ids"),
                    rs.getString("scope_role_codes"),
                    rs.getString("scope_agent_ids"),
                    rs.getString("scope_tool_ids"),
                    rs.getString("scope_workflow_ids"),
                    rs.getString("scope_envs"),
                    rs.getString("match_mode"),
                    rs.getBoolean("require_non_empty_input"),
                    getNullableInteger(rs, "max_input_length"),
                    rs.getString("blocked_keywords"),
                    rs.getString("keywords"),
                    rs.getString("action_type"),
                    rs.getString("approval_node_name"),
                    rs.getString("approval_user_ids"),
                    rs.getString("approval_role_codes"),
                    rs.getString("reason"),
                    rs.getString("metadata"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at")),
                    getNullableLong(rs, "created_by"),
                    getNullableLong(rs, "updated_by")
            );
        }
    };

    private final JdbcTemplate jdbcTemplate;

    public GuardrailPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countPolicies(String policyCode, String policyName, String status, String actionType) {
        SqlAndParams sqlAndParams = buildPageSql("""
                SELECT COUNT(1)
                FROM guardrail_policies
                WHERE is_deleted = FALSE
                """, policyCode, policyName, status, actionType);
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    public List<GuardrailPolicyPageRecord> pagePolicies(String policyCode,
                                                        String policyName,
                                                        String status,
                                                        String actionType,
                                                        int offset,
                                                        int limit) {
        SqlAndParams sqlAndParams = buildPageSql("""
                SELECT id,
                       policy_code,
                       policy_name,
                       policy_version,
                       priority,
                       action_type,
                       rollout_mode,
                       status,
                       description,
                       jsonb_array_length(scope_user_ids) AS scope_user_count,
                       jsonb_array_length(scope_role_codes) AS scope_role_count,
                       jsonb_array_length(scope_agent_ids) AS scope_agent_count,
                       jsonb_array_length(scope_tool_ids) AS scope_tool_count,
                       jsonb_array_length(scope_workflow_ids) AS scope_workflow_count,
                       jsonb_array_length(scope_envs) AS scope_env_count,
                       created_at
                FROM guardrail_policies
                WHERE is_deleted = FALSE
                """, policyCode, policyName, status, actionType);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY priority DESC, id DESC LIMIT ? OFFSET ?", PAGE_ROW_MAPPER, params.toArray());
    }

    public Optional<GuardrailPolicyDetailRecord> findDetailById(Long policyId) {
        String sql = """
                SELECT id,
                       policy_code,
                       policy_name,
                       policy_version,
                       priority,
                       status,
                       description,
                       rollout_mode,
                       rollout_percentage,
                       COALESCE(rollout_user_ids::text, '[]') AS rollout_user_ids,
                       COALESCE(scope_user_ids::text, '[]') AS scope_user_ids,
                       COALESCE(scope_role_codes::text, '[]') AS scope_role_codes,
                       COALESCE(scope_agent_ids::text, '[]') AS scope_agent_ids,
                       COALESCE(scope_tool_ids::text, '[]') AS scope_tool_ids,
                       COALESCE(scope_workflow_ids::text, '[]') AS scope_workflow_ids,
                       COALESCE(scope_envs::text, '[]') AS scope_envs,
                       match_mode,
                       require_non_empty_input,
                       max_input_length,
                       COALESCE(blocked_keywords::text, '[]') AS blocked_keywords,
                       COALESCE(keywords::text, '[]') AS keywords,
                       action_type,
                       approval_node_name,
                       COALESCE(approval_user_ids::text, '[]') AS approval_user_ids,
                       COALESCE(approval_role_codes::text, '[]') AS approval_role_codes,
                       reason,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM guardrail_policies
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<GuardrailPolicyDetailRecord> records = jdbcTemplate.query(sql, DETAIL_ROW_MAPPER, policyId);
        return records.stream().findFirst();
    }

    public Optional<Long> findPolicyIdByCode(String policyCode) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id
                FROM guardrail_policies
                WHERE policy_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("id"), policyCode);
        return ids.stream().findFirst();
    }

    public Long insertPolicy(String policyCode,
                             String policyName,
                             Integer policyVersion,
                             Integer priority,
                             String status,
                             String description,
                             String rolloutMode,
                             Integer rolloutPercentage,
                             String rolloutUserIdsJson,
                             String scopeUserIdsJson,
                             String scopeRoleCodesJson,
                             String scopeAgentIdsJson,
                             String scopeToolIdsJson,
                             String scopeWorkflowIdsJson,
                             String scopeEnvsJson,
                             String matchMode,
                             Boolean requireNonEmptyInput,
                             Integer maxInputLength,
                             String blockedKeywordsJson,
                             String keywordsJson,
                             String actionType,
                             String approvalNodeName,
                             String approvalUserIdsJson,
                             String approvalRoleCodesJson,
                             String reason,
                             String metadataJson,
                             String remark,
                             Long operatorId) {
        String sql = """
                INSERT INTO guardrail_policies (
                    policy_code,
                    policy_name,
                    policy_version,
                    priority,
                    status,
                    description,
                    rollout_mode,
                    rollout_percentage,
                    rollout_user_ids,
                    scope_user_ids,
                    scope_role_codes,
                    scope_agent_ids,
                    scope_tool_ids,
                    scope_workflow_ids,
                    scope_envs,
                    match_mode,
                    require_non_empty_input,
                    max_input_length,
                    blocked_keywords,
                    keywords,
                    action_type,
                    approval_node_name,
                    approval_user_ids,
                    approval_role_codes,
                    reason,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb),
                          CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
                          ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(sql, Long.class,
                policyCode, policyName, policyVersion, priority, status, description, rolloutMode, rolloutPercentage,
                rolloutUserIdsJson, scopeUserIdsJson, scopeRoleCodesJson, scopeAgentIdsJson, scopeToolIdsJson,
                scopeWorkflowIdsJson, scopeEnvsJson, matchMode, requireNonEmptyInput, maxInputLength,
                blockedKeywordsJson, keywordsJson, actionType, approvalNodeName, approvalUserIdsJson,
                approvalRoleCodesJson, reason, metadataJson, remark, now, operatorId, now, operatorId);
    }

    public int updatePolicy(Long policyId,
                            String policyCode,
                            String policyName,
                            Integer policyVersion,
                            Integer priority,
                            String status,
                            String description,
                            String rolloutMode,
                            Integer rolloutPercentage,
                            String rolloutUserIdsJson,
                            String scopeUserIdsJson,
                            String scopeRoleCodesJson,
                            String scopeAgentIdsJson,
                            String scopeToolIdsJson,
                            String scopeWorkflowIdsJson,
                            String scopeEnvsJson,
                            String matchMode,
                            Boolean requireNonEmptyInput,
                            Integer maxInputLength,
                            String blockedKeywordsJson,
                            String keywordsJson,
                            String actionType,
                            String approvalNodeName,
                            String approvalUserIdsJson,
                            String approvalRoleCodesJson,
                            String reason,
                            String metadataJson,
                            String remark,
                            Long operatorId) {
        String sql = """
                UPDATE guardrail_policies
                SET policy_code = ?,
                    policy_name = ?,
                    policy_version = ?,
                    priority = ?,
                    status = ?,
                    description = ?,
                    rollout_mode = ?,
                    rollout_percentage = ?,
                    rollout_user_ids = CAST(? AS jsonb),
                    scope_user_ids = CAST(? AS jsonb),
                    scope_role_codes = CAST(? AS jsonb),
                    scope_agent_ids = CAST(? AS jsonb),
                    scope_tool_ids = CAST(? AS jsonb),
                    scope_workflow_ids = CAST(? AS jsonb),
                    scope_envs = CAST(? AS jsonb),
                    match_mode = ?,
                    require_non_empty_input = ?,
                    max_input_length = ?,
                    blocked_keywords = CAST(? AS jsonb),
                    keywords = CAST(? AS jsonb),
                    action_type = ?,
                    approval_node_name = ?,
                    approval_user_ids = CAST(? AS jsonb),
                    approval_role_codes = CAST(? AS jsonb),
                    reason = ?,
                    metadata = CAST(? AS jsonb),
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(sql,
                policyCode, policyName, policyVersion, priority, status, description, rolloutMode, rolloutPercentage,
                rolloutUserIdsJson, scopeUserIdsJson, scopeRoleCodesJson, scopeAgentIdsJson, scopeToolIdsJson,
                scopeWorkflowIdsJson, scopeEnvsJson, matchMode, requireNonEmptyInput, maxInputLength,
                blockedKeywordsJson, keywordsJson, actionType, approvalNodeName, approvalUserIdsJson,
                approvalRoleCodesJson, reason, metadataJson, remark, now, operatorId, policyId);
    }

    public int updatePolicyStatus(Long policyId, String status, Long operatorId) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update("""
                UPDATE guardrail_policies
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """, status, now, operatorId, policyId);
    }

    public List<GuardrailPolicyDetailRecord> findEnabledPolicies() {
        return jdbcTemplate.query("""
                SELECT id,
                       policy_code,
                       policy_name,
                       policy_version,
                       priority,
                       status,
                       description,
                       rollout_mode,
                       rollout_percentage,
                       COALESCE(rollout_user_ids::text, '[]') AS rollout_user_ids,
                       COALESCE(scope_user_ids::text, '[]') AS scope_user_ids,
                       COALESCE(scope_role_codes::text, '[]') AS scope_role_codes,
                       COALESCE(scope_agent_ids::text, '[]') AS scope_agent_ids,
                       COALESCE(scope_tool_ids::text, '[]') AS scope_tool_ids,
                       COALESCE(scope_workflow_ids::text, '[]') AS scope_workflow_ids,
                       COALESCE(scope_envs::text, '[]') AS scope_envs,
                       match_mode,
                       require_non_empty_input,
                       max_input_length,
                       COALESCE(blocked_keywords::text, '[]') AS blocked_keywords,
                       COALESCE(keywords::text, '[]') AS keywords,
                       action_type,
                       approval_node_name,
                       COALESCE(approval_user_ids::text, '[]') AS approval_user_ids,
                       COALESCE(approval_role_codes::text, '[]') AS approval_role_codes,
                       reason,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM guardrail_policies
                WHERE status = 'enabled'
                  AND is_deleted = FALSE
                ORDER BY priority DESC, id DESC
                """, DETAIL_ROW_MAPPER);
    }

    private SqlAndParams buildPageSql(String baseSql,
                                      String policyCode,
                                      String policyName,
                                      String status,
                                      String actionType) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (policyCode != null && !policyCode.isBlank()) {
            sql.append(" AND policy_code ILIKE ?");
            params.add("%" + policyCode.trim() + "%");
        }
        if (policyName != null && !policyName.isBlank()) {
            sql.append(" AND policy_name ILIKE ?");
            params.add("%" + policyName.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (actionType != null && !actionType.isBlank()) {
            sql.append(" AND action_type = ?");
            params.add(actionType.trim());
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record SqlAndParams(String sql, List<Object> params) {
    }
}

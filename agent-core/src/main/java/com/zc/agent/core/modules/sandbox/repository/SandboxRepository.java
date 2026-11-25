package com.zc.agent.core.modules.sandbox.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.sandbox.model.SandboxExecutionRecord;
import com.zc.agent.core.modules.sandbox.model.SandboxProfileRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sandbox 配置与审计数据访问。
 */
@Repository
public class SandboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SandboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long countProfiles(String keyword, String status) {
        Query query = profileFilter("SELECT COUNT(1) FROM sandbox_profiles WHERE is_deleted = FALSE", keyword, status);
        Long count = jdbcTemplate.queryForObject(query.sql(), Long.class, query.params().toArray());
        return count == null ? 0 : count;
    }

    public List<SandboxProfileRecord> pageProfiles(String keyword, String status, int offset, int limit) {
        Query query = profileFilter(profileSelect() + " WHERE is_deleted = FALSE", keyword, status);
        List<Object> params = new ArrayList<>(query.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(query.sql() + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", profileMapper(), params.toArray());
    }

    public Optional<SandboxProfileRecord> findProfile(Long profileId) {
        return jdbcTemplate.query(profileSelect() + " WHERE id = ? AND is_deleted = FALSE", profileMapper(), profileId).stream().findFirst();
    }

    public Optional<SandboxProfileRecord> findProfileByCode(String profileCode) {
        return jdbcTemplate.query(profileSelect() + " WHERE profile_code = ? AND is_deleted = FALSE", profileMapper(), profileCode).stream().findFirst();
    }

    public Long insertProfile(SandboxProfileRecord profile, Long operatorId) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sandbox_profiles (profile_code, profile_name, image, allowed_commands, network_mode,
                        memory_bytes, nano_cpus, pids_limit, timeout_seconds, max_output_bytes, max_workspace_bytes,
                        status, created_by, updated_by)
                    VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, profile.profileCode());
            statement.setString(2, profile.profileName());
            statement.setString(3, profile.image());
            statement.setString(4, toJson(profile.allowedCommands()));
            statement.setString(5, profile.networkMode());
            statement.setLong(6, profile.memoryBytes());
            statement.setLong(7, profile.nanoCpus());
            statement.setLong(8, profile.pidsLimit());
            statement.setInt(9, profile.timeoutSeconds());
            statement.setLong(10, profile.maxOutputBytes());
            statement.setLong(11, profile.maxWorkspaceBytes());
            statement.setString(12, profile.status());
            statement.setObject(13, operatorId);
            statement.setObject(14, operatorId);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    public void updateProfile(Long profileId, SandboxProfileRecord profile, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE sandbox_profiles SET profile_name = ?, image = ?, allowed_commands = CAST(? AS jsonb),
                    network_mode = ?, memory_bytes = ?, nano_cpus = ?, pids_limit = ?, timeout_seconds = ?,
                    max_output_bytes = ?, max_workspace_bytes = ?, status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = FALSE
                """, profile.profileName(), profile.image(), toJson(profile.allowedCommands()), profile.networkMode(),
                profile.memoryBytes(), profile.nanoCpus(), profile.pidsLimit(), profile.timeoutSeconds(),
                profile.maxOutputBytes(), profile.maxWorkspaceBytes(), profile.status(), operatorId, profileId);
    }

    public Long insertExecution(String executionCode, SandboxProfileRecord profile, List<String> command,
                                long inputBytes, Long userId, String username) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sandbox_executions (execution_code, profile_id, profile_code, image, command_json,
                        status, input_bytes, requested_by, requested_username)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'pending', ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, executionCode);
            statement.setLong(2, profile.profileId());
            statement.setString(3, profile.profileCode());
            statement.setString(4, profile.image());
            statement.setString(5, toJson(command));
            statement.setLong(6, inputBytes);
            statement.setObject(7, userId);
            statement.setString(8, username);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    public boolean markRunning(Long executionId, String containerId) {
        return jdbcTemplate.update("""
                UPDATE sandbox_executions SET status = 'running', container_id = ?, started_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'pending'
                """, containerId, executionId) > 0;
    }

    public void completeExecution(Long executionId, String status, Integer exitCode, boolean timedOut,
                                  boolean truncated, String stdout, String stderr, String errorMessage,
                                  long outputBytes, long workspaceBytes, long durationMs) {
        jdbcTemplate.update("""
                UPDATE sandbox_executions SET status = ?, exit_code = ?, timed_out = ?, output_truncated = ?,
                    stdout_text = ?, stderr_text = ?, error_message = ?, output_bytes = ?, workspace_bytes = ?,
                    duration_ms = ?, finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status <> 'cancelled'
                """, status, exitCode, timedOut, truncated, stdout, stderr, errorMessage, outputBytes,
                workspaceBytes, durationMs, executionId);
    }

    public boolean markCancelled(Long executionId) {
        return jdbcTemplate.update("""
                UPDATE sandbox_executions SET status = 'cancelled', error_message = '用户取消执行',
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('pending', 'running')
                """, executionId) > 0;
    }

    public Optional<SandboxExecutionRecord> findExecution(Long executionId) {
        return jdbcTemplate.query(executionSelect() + " WHERE id = ?", executionMapper(), executionId).stream().findFirst();
    }

    public long countExecutions(Long profileId, String status, String executionCode,
                                LocalDateTime after, LocalDateTime before) {
        Query query = executionFilter("SELECT COUNT(1) FROM sandbox_executions WHERE 1 = 1", profileId, status, executionCode, after, before);
        Long count = jdbcTemplate.queryForObject(query.sql(), Long.class, query.params().toArray());
        return count == null ? 0 : count;
    }

    public List<SandboxExecutionRecord> pageExecutions(Long profileId, String status, String executionCode,
                                                       LocalDateTime after, LocalDateTime before, int offset, int limit) {
        Query query = executionFilter(executionSelect() + " WHERE 1 = 1", profileId, status, executionCode, after, before);
        List<Object> params = new ArrayList<>(query.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(query.sql() + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", executionMapper(), params.toArray());
    }

    private Query profileFilter(String base, String keyword, String status) {
        StringBuilder sql = new StringBuilder(base);
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) { sql.append(" AND (profile_code ILIKE ? OR profile_name ILIKE ?)"); params.add("%" + keyword.trim() + "%"); params.add("%" + keyword.trim() + "%"); }
        if (status != null && !status.isBlank()) { sql.append(" AND status = ?"); params.add(status.trim()); }
        return new Query(sql.toString(), params);
    }

    private Query executionFilter(String base, Long profileId, String status, String code, LocalDateTime after, LocalDateTime before) {
        StringBuilder sql = new StringBuilder(base);
        List<Object> params = new ArrayList<>();
        if (profileId != null) { sql.append(" AND profile_id = ?"); params.add(profileId); }
        if (status != null && !status.isBlank()) { sql.append(" AND status = ?"); params.add(status.trim()); }
        if (code != null && !code.isBlank()) { sql.append(" AND execution_code ILIKE ?"); params.add("%" + code.trim() + "%"); }
        if (after != null) { sql.append(" AND created_at >= ?"); params.add(after); }
        if (before != null) { sql.append(" AND created_at <= ?"); params.add(before); }
        return new Query(sql.toString(), params);
    }

    private String profileSelect() {
        return "SELECT id AS profile_id, profile_code, profile_name, image, allowed_commands::text AS allowed_commands, network_mode, memory_bytes, nano_cpus, pids_limit, timeout_seconds, max_output_bytes, max_workspace_bytes, status, created_at, updated_at FROM sandbox_profiles";
    }

    private RowMapper<SandboxProfileRecord> profileMapper() {
        return (rs, row) -> new SandboxProfileRecord(rs.getLong("profile_id"), rs.getString("profile_code"),
                rs.getString("profile_name"), rs.getString("image"), fromJsonList(rs.getString("allowed_commands")),
                rs.getString("network_mode"), rs.getLong("memory_bytes"), rs.getLong("nano_cpus"),
                rs.getLong("pids_limit"), rs.getInt("timeout_seconds"), rs.getLong("max_output_bytes"),
                rs.getLong("max_workspace_bytes"), rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private String executionSelect() {
        return "SELECT id AS execution_id, execution_code, profile_id, profile_code, image, command_json::text AS command_json, status, container_id, exit_code, timed_out, output_truncated, stdout_text, stderr_text, error_message, input_bytes, output_bytes, workspace_bytes, duration_ms, requested_by, requested_username, started_at, finished_at, created_at FROM sandbox_executions";
    }

    private RowMapper<SandboxExecutionRecord> executionMapper() {
        return (rs, row) -> new SandboxExecutionRecord(rs.getLong("execution_id"), rs.getString("execution_code"),
                rs.getLong("profile_id"), rs.getString("profile_code"), rs.getString("image"),
                fromJsonList(rs.getString("command_json")), rs.getString("status"), rs.getString("container_id"),
                rs.getObject("exit_code", Integer.class), rs.getBoolean("timed_out"), rs.getBoolean("output_truncated"),
                rs.getString("stdout_text"), rs.getString("stderr_text"), rs.getString("error_message"),
                rs.getLong("input_bytes"), rs.getLong("output_bytes"), rs.getLong("workspace_bytes"),
                rs.getObject("duration_ms", Long.class), rs.getObject("requested_by", Long.class),
                rs.getString("requested_username"), rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class));
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException("Sandbox 数据序列化失败", exception); }
    }

    private List<String> fromJsonList(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new BusinessException("Sandbox 数据解析失败", exception); }
    }

    private record Query(String sql, List<Object> params) { }
}

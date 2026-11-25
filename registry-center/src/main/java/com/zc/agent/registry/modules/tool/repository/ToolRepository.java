package com.zc.agent.registry.modules.tool.repository;

import com.zc.agent.registry.modules.tool.dto.CreateToolRequest;
import com.zc.agent.registry.modules.tool.dto.UpdateToolRequest;
import com.zc.agent.registry.modules.tool.model.ToolDetailRecord;
import com.zc.agent.registry.modules.tool.model.ToolPageRecord;
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
 * Tool 仓储。
 */
@Repository
public class ToolRepository {

    private static final RowMapper<ToolPageRecord> TOOL_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ToolPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ToolPageRecord(
                    rs.getLong("id"),
                    rs.getString("tool_code"),
                    rs.getString("tool_name"),
                    rs.getString("tool_type"),
                    rs.getString("tool_category"),
                    rs.getString("provider_type"),
                    rs.getString("transport_type"),
                    rs.getString("permission_level"),
                    getNullableInteger(rs, "timeout_seconds"),
                    rs.getString("status"),
                    rs.getString("description"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<ToolDetailRecord> TOOL_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ToolDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ToolDetailRecord(
                    rs.getLong("id"),
                    rs.getString("tool_code"),
                    rs.getString("tool_name"),
                    rs.getString("tool_type"),
                    rs.getString("tool_category"),
                    rs.getString("runtime_capabilities"),
                    rs.getString("side_effect_level"),
                    rs.getString("permission_level"),
                    getNullableInteger(rs, "timeout_seconds"),
                    rs.getString("status"),
                    rs.getString("description"),
                    rs.getString("input_schema"),
                    rs.getString("output_schema"),
                    rs.getString("provider_code"),
                    rs.getString("provider_name"),
                    rs.getString("provider_type"),
                    rs.getString("transport_type"),
                    rs.getString("provider_config_schema"),
                    rs.getString("provider_status"),
                    rs.getString("provider_instance_code"),
                    rs.getString("provider_instance_name"),
                    rs.getString("environment_code"),
                    rs.getString("endpoint"),
                    rs.getString("auth_type"),
                    rs.getString("auth_config"),
                    rs.getString("headers"),
                    getNullableInteger(rs, "provider_timeout_seconds"),
                    getNullableInteger(rs, "retry_times"),
                    rs.getString("provider_instance_status"),
                    rs.getString("operation_name"),
                    rs.getString("operation_type"),
                    rs.getString("invoke_config"),
                    rs.getString("argument_mapping"),
                    rs.getString("output_mapping"),
                    rs.getString("binding_status"),
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

    public ToolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计 Tool 总数。
     *
     * @param toolCode      Tool 编码
     * @param toolName      Tool 名称
     * @param toolType      Tool 类型
     * @param toolCategory  Tool 分类
     * @param status        Tool 状态
     * @return 总数
     */
    public long countTools(String toolCode,
                           String toolName,
                           String toolType,
                           String toolCategory,
                           String status) {
        SqlAndParams sqlAndParams = buildToolQuerySql(
                """
                SELECT COUNT(1)
                FROM tools t
                LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
                LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
                WHERE t.is_deleted = FALSE
                """,
                toolCode,
                toolName,
                toolType,
                toolCategory,
                status
        );
        return jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
    }

    /**
     * 分页查询 Tool。
     *
     * @param toolCode      Tool 编码
     * @param toolName      Tool 名称
     * @param toolType      Tool 类型
     * @param toolCategory  Tool 分类
     * @param status        Tool 状态
     * @param offset        偏移量
     * @param limit         每页条数
     * @return Tool 分页结果
     */
    public List<ToolPageRecord> pageTools(String toolCode,
                                          String toolName,
                                          String toolType,
                                          String toolCategory,
                                          String status,
                                          int offset,
                                          int limit) {
        SqlAndParams sqlAndParams = buildToolQuerySql(
                """
                SELECT t.id,
                       t.tool_code,
                       t.tool_name,
                       t.tool_type,
                       t.tool_category,
                       tp.provider_type,
                       tp.transport_type,
                       t.permission_level,
                       t.timeout_seconds,
                       t.status,
                       t.description,
                       t.created_at
                FROM tools t
                LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
                LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
                WHERE t.is_deleted = FALSE
                """,
                toolCode,
                toolName,
                toolType,
                toolCategory,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, TOOL_PAGE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按 Tool ID 查询详情。
     *
     * @param toolId Tool ID
     * @return Tool 详情
     */
    public Optional<ToolDetailRecord> findToolDetailById(Long toolId) {
        String sql = """
                SELECT t.id,
                       t.tool_code,
                       t.tool_name,
                       t.tool_type,
                       t.tool_category,
                       COALESCE(t.runtime_capabilities::text, '[]') AS runtime_capabilities,
                       t.side_effect_level,
                       t.description,
                       COALESCE(t.input_schema::text, '{}') AS input_schema,
                       COALESCE(t.output_schema::text, '{}') AS output_schema,
                       COALESCE(tp.provider_code, '') AS provider_code,
                       COALESCE(tp.provider_name, '') AS provider_name,
                       COALESCE(tp.provider_type, '') AS provider_type,
                       COALESCE(tp.transport_type, '') AS transport_type,
                       COALESCE(tp.config_schema::text, '{}') AS provider_config_schema,
                       COALESCE(tp.status, '') AS provider_status,
                       COALESCE(tpi.instance_code, '') AS provider_instance_code,
                       COALESCE(tpi.instance_name, '') AS provider_instance_name,
                       COALESCE(tpi.environment_code, 'default') AS environment_code,
                       tpi.endpoint,
                       COALESCE(tpi.auth_type, 'none') AS auth_type,
                       COALESCE(tpi.auth_config::text, '{}') AS auth_config,
                       COALESCE(tpi.headers::text, '{}') AS headers,
                       tpi.timeout_seconds AS provider_timeout_seconds,
                       tpi.retry_times,
                       COALESCE(tpi.status, '') AS provider_instance_status,
                       COALESCE(tb.operation_name, '') AS operation_name,
                       COALESCE(tb.operation_type, '') AS operation_type,
                       COALESCE(tb.invoke_config::text, '{}') AS invoke_config,
                       COALESCE(tb.argument_mapping::text, '{}') AS argument_mapping,
                       COALESCE(tb.output_mapping::text, '{}') AS output_mapping,
                       COALESCE(tb.status, '') AS binding_status,
                       t.permission_level,
                       t.timeout_seconds,
                       t.status,
                       COALESCE(t.metadata::text, '{}') AS metadata,
                       t.remark,
                       t.created_at,
                       t.updated_at,
                       t.created_by,
                       t.updated_by
                FROM tools t
                LEFT JOIN tool_bindings tb ON tb.tool_id = t.id AND tb.is_deleted = FALSE
                LEFT JOIN tool_providers tp ON tp.id = tb.provider_id AND tp.is_deleted = FALSE
                LEFT JOIN tool_provider_instances tpi ON tpi.id = tb.provider_instance_id AND tpi.is_deleted = FALSE
                WHERE t.id = ?
                  AND t.is_deleted = FALSE
                LIMIT 1
                """;
        List<ToolDetailRecord> records = jdbcTemplate.query(sql, TOOL_DETAIL_ROW_MAPPER, toolId);
        return records.stream().findFirst();
    }

    /**
     * 按 Tool 编码查询 Tool ID。
     *
     * @param toolCode Tool 编码
     * @return Tool ID
     */
    public Optional<Long> findToolIdByToolCode(String toolCode) {
        String sql = """
                SELECT id
                FROM tools
                WHERE tool_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), toolCode);
        return records.stream().findFirst();
    }

    /**
     * 新增 Tool。
     *
     * @param request          新增请求
     * @param inputSchemaJson  输入参数 Schema JSON
     * @param outputSchemaJson 输出结果 Schema JSON
     * @param runtimeCapabilitiesJson 运行时能力声明 JSON
     * @param metadataJson     扩展元数据 JSON
     * @param operatorId       操作人
     * @return 新 Tool ID
     */
    public Long insertTool(CreateToolRequest request,
                           String inputSchemaJson,
                           String outputSchemaJson,
                           String runtimeCapabilitiesJson,
                           String metadataJson,
                           Long operatorId) {
        String sql = """
                INSERT INTO tools (
                    tool_code,
                    tool_name,
                    tool_type,
                    tool_category,
                    runtime_capabilities,
                    side_effect_level,
                    description,
                    input_schema,
                    output_schema,
                    permission_level,
                    timeout_seconds,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.toolCode(),
                request.toolName(),
                request.toolType(),
                request.toolCategory(),
                runtimeCapabilitiesJson,
                request.sideEffectLevel(),
                request.description(),
                inputSchemaJson,
                outputSchemaJson,
                request.permissionLevel(),
                request.timeoutSeconds(),
                request.status(),
                metadataJson,
                request.remark(),
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    /**
     * 编辑 Tool。
     *
     * @param toolId           Tool ID
     * @param request          编辑请求
     * @param inputSchemaJson  输入参数 Schema JSON
     * @param outputSchemaJson 输出结果 Schema JSON
     * @param runtimeCapabilitiesJson 运行时能力声明 JSON
     * @param metadataJson     扩展元数据 JSON
     * @param operatorId       操作人
     * @return 影响行数
     */
    public int updateTool(Long toolId,
                          UpdateToolRequest request,
                          String inputSchemaJson,
                          String outputSchemaJson,
                          String runtimeCapabilitiesJson,
                          String metadataJson,
                          Long operatorId) {
        String sql = """
                UPDATE tools
                SET tool_code = ?,
                    tool_name = ?,
                    tool_type = ?,
                    tool_category = ?,
                    runtime_capabilities = CAST(? AS jsonb),
                    side_effect_level = ?,
                    description = ?,
                    input_schema = CAST(? AS jsonb),
                    output_schema = CAST(? AS jsonb),
                    permission_level = ?,
                    timeout_seconds = ?,
                    status = ?,
                    metadata = CAST(? AS jsonb),
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(
                sql,
                request.toolCode(),
                request.toolName(),
                request.toolType(),
                request.toolCategory(),
                runtimeCapabilitiesJson,
                request.sideEffectLevel(),
                request.description(),
                inputSchemaJson,
                outputSchemaJson,
                request.permissionLevel(),
                request.timeoutSeconds(),
                request.status(),
                metadataJson,
                request.remark(),
                timestamp,
                operatorId,
                toolId
        );
    }

    /**
     * 更新 Tool 状态。
     *
     * @param toolId     Tool ID
     * @param status     Tool 状态
     * @param operatorId 操作人
     * @return 影响行数
     */
    public int updateToolStatus(Long toolId, String status, Long operatorId) {
        String sql = """
                UPDATE tools
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        return jdbcTemplate.update(sql, status, timestamp, operatorId, toolId);
    }

    public Optional<Long> findProviderIdByProviderCode(String providerCode) {
        String sql = """
                SELECT id
                FROM tool_providers
                WHERE provider_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), providerCode);
        return records.stream().findFirst();
    }

    public Optional<Long> findProviderInstanceIdByInstanceCode(String instanceCode) {
        String sql = """
                SELECT id
                FROM tool_provider_instances
                WHERE instance_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), instanceCode);
        return records.stream().findFirst();
    }

    public Long upsertToolProvider(String providerCode,
                                   String providerName,
                                   String providerType,
                                   String transportType,
                                   String configSchemaJson,
                                   String status,
                                   Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        Optional<Long> existingId = findProviderIdByProviderCode(providerCode);
        if (existingId.isPresent()) {
            jdbcTemplate.update(
                    """
                    UPDATE tool_providers
                    SET provider_name = ?,
                        provider_type = ?,
                        transport_type = ?,
                        config_schema = CAST(? AS jsonb),
                        status = ?,
                        updated_at = ?,
                        updated_by = ?
                    WHERE id = ?
                      AND is_deleted = FALSE
                    """,
                    providerName,
                    providerType,
                    transportType,
                    configSchemaJson,
                    status,
                    timestamp,
                    operatorId,
                    existingId.get()
            );
            return existingId.get();
        }
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO tool_providers (
                    provider_code,
                    provider_name,
                    provider_type,
                    transport_type,
                    config_schema,
                    status,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                RETURNING id
                """,
                Long.class,
                providerCode,
                providerName,
                providerType,
                transportType,
                configSchemaJson,
                status,
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    public Long upsertToolProviderInstance(Long providerId,
                                           String instanceCode,
                                           String instanceName,
                                           String environmentCode,
                                           String endpoint,
                                           String authType,
                                           String authConfigJson,
                                           String headersJson,
                                           Integer timeoutSeconds,
                                           Integer retryTimes,
                                           String status,
                                           Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        Optional<Long> existingId = findProviderInstanceIdByInstanceCode(instanceCode);
        if (existingId.isPresent()) {
            jdbcTemplate.update(
                    """
                    UPDATE tool_provider_instances
                    SET provider_id = ?,
                        instance_name = ?,
                        environment_code = ?,
                        endpoint = ?,
                        auth_type = ?,
                        auth_config = CAST(? AS jsonb),
                        headers = CAST(? AS jsonb),
                        timeout_seconds = ?,
                        retry_times = ?,
                        status = ?,
                        updated_at = ?,
                        updated_by = ?
                    WHERE id = ?
                      AND is_deleted = FALSE
                    """,
                    providerId,
                    instanceName,
                    environmentCode,
                    endpoint,
                    authType,
                    authConfigJson,
                    headersJson,
                    timeoutSeconds,
                    retryTimes,
                    status,
                    timestamp,
                    operatorId,
                    existingId.get()
            );
            return existingId.get();
        }
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO tool_provider_instances (
                    provider_id,
                    instance_code,
                    instance_name,
                    environment_code,
                    endpoint,
                    auth_type,
                    auth_config,
                    headers,
                    timeout_seconds,
                    retry_times,
                    status,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                RETURNING id
                """,
                Long.class,
                providerId,
                instanceCode,
                instanceName,
                environmentCode,
                endpoint,
                authType,
                authConfigJson,
                headersJson,
                timeoutSeconds,
                retryTimes,
                status,
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    public void upsertToolBinding(Long toolId,
                                  Long providerId,
                                  Long providerInstanceId,
                                  String operationName,
                                  String operationType,
                                  String invokeConfigJson,
                                  String argumentMappingJson,
                                  String outputMappingJson,
                                  String status,
                                  Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp timestamp = Timestamp.valueOf(now);
        Long existingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM tool_bindings
                WHERE tool_id = ?
                  AND is_deleted = FALSE
                """,
                Long.class,
                toolId
        );
        if (existingCount != null && existingCount > 0) {
            jdbcTemplate.update(
                    """
                    UPDATE tool_bindings
                    SET provider_id = ?,
                        provider_instance_id = ?,
                        operation_name = ?,
                        operation_type = ?,
                        invoke_config = CAST(? AS jsonb),
                        argument_mapping = CAST(? AS jsonb),
                        output_mapping = CAST(? AS jsonb),
                        status = ?,
                        updated_at = ?,
                        updated_by = ?
                    WHERE tool_id = ?
                      AND is_deleted = FALSE
                    """,
                    providerId,
                    providerInstanceId,
                    operationName,
                    operationType,
                    invokeConfigJson,
                    argumentMappingJson,
                    outputMappingJson,
                    status,
                    timestamp,
                    operatorId,
                    toolId
            );
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO tool_bindings (
                    tool_id,
                    provider_id,
                    provider_instance_id,
                    operation_name,
                    operation_type,
                    invoke_config,
                    argument_mapping,
                    output_mapping,
                    status,
                    metadata,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, '{}'::jsonb, ?, ?, ?, ?, FALSE)
                """,
                toolId,
                providerId,
                providerInstanceId,
                operationName,
                operationType,
                invokeConfigJson,
                argumentMappingJson,
                outputMappingJson,
                status,
                timestamp,
                operatorId,
                timestamp,
                operatorId
        );
    }

    private SqlAndParams buildToolQuerySql(String baseSql,
                                           String toolCode,
                                           String toolName,
                                           String toolType,
                                           String toolCategory,
                                           String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (toolCode != null) {
            sql.append(" AND t.tool_code ILIKE ?");
            params.add("%" + toolCode + "%");
        }
        if (toolName != null) {
            sql.append(" AND t.tool_name ILIKE ?");
            params.add("%" + toolName + "%");
        }
        if (toolType != null) {
            sql.append(" AND t.tool_type = ?");
            params.add(toolType);
        }
        if (toolCategory != null) {
            sql.append(" AND t.tool_category = ?");
            params.add(toolCategory);
        }
        if (status != null) {
            sql.append(" AND t.status = ?");
            params.add(status);
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static Integer getNullableInteger(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    /**
     * SQL 及参数。
     *
     * @param sql SQL 语句
     * @param params 参数列表
     */
    private record SqlAndParams(String sql, List<Object> params) {
    }
}

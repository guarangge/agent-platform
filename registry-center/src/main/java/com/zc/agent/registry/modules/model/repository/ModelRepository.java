package com.zc.agent.registry.modules.model.repository;

import com.zc.agent.registry.modules.model.dto.CreateModelRequest;
import com.zc.agent.registry.modules.model.dto.UpdateModelRequest;
import com.zc.agent.registry.modules.model.model.ModelDetailRecord;
import com.zc.agent.registry.modules.model.model.ModelCredentialRecord;
import com.zc.agent.registry.modules.model.model.ModelPageRecord;
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
 * 模型仓储。
 */
@Repository
public class ModelRepository {

    private static final RowMapper<ModelPageRecord> MODEL_PAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ModelPageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ModelPageRecord(
                    rs.getLong("id"),
                    rs.getString("model_code"),
                    rs.getString("model_name"),
                    rs.getString("provider"),
                    rs.getString("model_type"),
                    rs.getString("status"),
                    toLocalDateTime(rs.getTimestamp("created_at"))
            );
        }
    };

    private static final RowMapper<ModelDetailRecord> MODEL_DETAIL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ModelDetailRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ModelDetailRecord(
                    rs.getLong("id"),
                    rs.getString("model_code"),
                    rs.getString("model_name"),
                    rs.getString("provider"),
                    rs.getString("model_type"),
                    rs.getString("base_url"),
                    rs.getString("api_key"),
                    rs.getString("api_path"),
                    rs.getString("model_identifier"),
                    getNullableDouble(rs, "temperature"),
                    getNullableInteger(rs, "max_tokens"),
                    getNullableInteger(rs, "context_window"),
                    getNullableInteger(rs, "reserved_output_tokens"),
                    getNullableInteger(rs, "reserved_system_tokens"),
                    getNullableDouble(rs, "compaction_trigger_ratio"),
                    rs.getString("tokenizer_family"),
                    getNullableInteger(rs, "timeout_seconds"),
                    rs.getString("status"),
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

    public ModelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countModels(String modelCode, String modelName, String provider, String modelType, String status) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT COUNT(1)
                FROM models
                WHERE is_deleted = FALSE
                """,
                modelCode,
                modelName,
                provider,
                modelType,
                status
        );
        Long total = jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
        return total == null ? 0L : total;
    }

    public List<ModelPageRecord> pageModels(String modelCode,
                                            String modelName,
                                            String provider,
                                            String modelType,
                                            String status,
                                            int offset,
                                            int limit) {
        SqlAndParams sqlAndParams = buildQuerySql(
                """
                SELECT id,
                       model_code,
                       model_name,
                       provider,
                       model_type,
                       status,
                       created_at
                FROM models
                WHERE is_deleted = FALSE
                """,
                modelCode,
                modelName,
                provider,
                modelType,
                status
        );
        String sql = sqlAndParams.sql() + " ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, MODEL_PAGE_ROW_MAPPER, params.toArray());
    }

    public Optional<ModelDetailRecord> findModelDetailById(Long modelId) {
        String sql = """
                SELECT id,
                       model_code,
                       model_name,
                       provider,
                       model_type,
                       base_url,
                       api_key,
                       api_path,
                       model_identifier,
                       temperature,
                       max_tokens,
                       context_window,
                       reserved_output_tokens,
                       reserved_system_tokens,
                       compaction_trigger_ratio,
                       tokenizer_family,
                       timeout_seconds,
                       status,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM models
                WHERE id = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ModelDetailRecord> records = jdbcTemplate.query(sql, MODEL_DETAIL_ROW_MAPPER, modelId);
        return records.stream().findFirst();
    }

    public Optional<ModelDetailRecord> findModelDetailByCode(String modelCode) {
        String sql = """
                SELECT id,
                       model_code,
                       model_name,
                       provider,
                       model_type,
                       base_url,
                       api_key,
                       api_path,
                       model_identifier,
                       temperature,
                       max_tokens,
                       context_window,
                       reserved_output_tokens,
                       reserved_system_tokens,
                       compaction_trigger_ratio,
                       tokenizer_family,
                       timeout_seconds,
                       status,
                       COALESCE(metadata::text, '{}') AS metadata,
                       remark,
                       created_at,
                       updated_at,
                       created_by,
                       updated_by
                FROM models
                WHERE model_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<ModelDetailRecord> records = jdbcTemplate.query(sql, MODEL_DETAIL_ROW_MAPPER, modelCode);
        return records.stream().findFirst();
    }

    public Optional<Long> findModelIdByCode(String modelCode) {
        String sql = """
                SELECT id
                FROM models
                WHERE model_code = ?
                  AND is_deleted = FALSE
                LIMIT 1
                """;
        List<Long> records = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), modelCode);
        return records.stream().findFirst();
    }

    public boolean existsEnabledModelCode(String modelCode) {
        String sql = """
                SELECT COUNT(1)
                FROM models
                WHERE model_code = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, modelCode);
        return count != null && count > 0;
    }

    /**
     * 判断指定编码和类型的模型是否处于启用状态。
     *
     * @param modelCode 模型编码
     * @param modelType 模型类型
     * @return 是否存在可用模型
     */
    public boolean existsEnabledModelCodeAndType(String modelCode, String modelType) {
        String sql = """
                SELECT COUNT(1)
                FROM models
                WHERE model_code = ?
                  AND model_type = ?
                  AND status = 'enabled'
                  AND is_deleted = FALSE
                """;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, modelCode, modelType);
        return count != null && count > 0;
    }

    public Long insertModel(CreateModelRequest request, String metadataJsonText, Long operatorId) {
        String sql = """
                INSERT INTO models (
                    model_code,
                    model_name,
                    provider,
                    model_type,
                    base_url,
                    api_key,
                    api_path,
                    model_identifier,
                    temperature,
                    max_tokens,
                    context_window,
                    reserved_output_tokens,
                    reserved_system_tokens,
                    compaction_trigger_ratio,
                    tokenizer_family,
                    timeout_seconds,
                    status,
                    metadata,
                    remark,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by,
                    is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, FALSE)
                RETURNING id
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                request.modelCode(),
                request.modelName(),
                request.provider(),
                request.modelType(),
                request.baseUrl(),
                request.apiKey(),
                request.apiPath(),
                request.modelIdentifier(),
                request.temperature(),
                request.maxTokens(),
                request.contextWindow(),
                request.reservedOutputTokens(),
                request.reservedSystemTokens(),
                request.compactionTriggerRatio(),
                request.tokenizerFamily(),
                request.timeoutSeconds(),
                request.status(),
                metadataJsonText,
                request.remark(),
                now,
                operatorId,
                now,
                operatorId
        );
    }

    public int updateModel(Long modelId, UpdateModelRequest request, String metadataJsonText, Long operatorId) {
        String sql = """
                UPDATE models
                SET model_code = ?,
                    model_name = ?,
                    provider = ?,
                    model_type = ?,
                    base_url = ?,
                    api_key = ?,
                    api_path = ?,
                    model_identifier = ?,
                    temperature = ?,
                    max_tokens = ?,
                    context_window = ?,
                    reserved_output_tokens = ?,
                    reserved_system_tokens = ?,
                    compaction_trigger_ratio = ?,
                    tokenizer_family = ?,
                    timeout_seconds = ?,
                    status = ?,
                    metadata = CAST(? AS jsonb),
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(
                sql,
                request.modelCode(),
                request.modelName(),
                request.provider(),
                request.modelType(),
                request.baseUrl(),
                request.apiKey(),
                request.apiPath(),
                request.modelIdentifier(),
                request.temperature(),
                request.maxTokens(),
                request.contextWindow(),
                request.reservedOutputTokens(),
                request.reservedSystemTokens(),
                request.compactionTriggerRatio(),
                request.tokenizerFamily(),
                request.timeoutSeconds(),
                request.status(),
                metadataJsonText,
                request.remark(),
                now,
                operatorId,
                modelId
        );
    }

    public int updateModelStatus(Long modelId, String status, Long operatorId) {
        String sql = """
                UPDATE models
                SET status = ?,
                    updated_at = ?,
                    updated_by = ?
                WHERE id = ?
                  AND is_deleted = FALSE
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        return jdbcTemplate.update(sql, status, now, operatorId, modelId);
    }

    private SqlAndParams buildQuerySql(String baseSql,
                                       String modelCode,
                                       String modelName,
                                       String provider,
                                       String modelType,
                                       String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (modelCode != null) {
            sql.append(" AND model_code ILIKE ?");
            params.add("%" + modelCode + "%");
        }
        if (modelName != null) {
            sql.append(" AND model_name ILIKE ?");
            params.add("%" + modelName + "%");
        }
        if (provider != null) {
            sql.append(" AND provider = ?");
            params.add(provider);
        }
        if (modelType != null) {
            sql.append(" AND model_type = ?");
            params.add(modelType);
        }
        if (status != null) {
            sql.append(" AND status = ?");
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

    private static Double getNullableDouble(ResultSet rs, String columnLabel) throws SQLException {
        double value = rs.getDouble(columnLabel);
        return rs.wasNull() ? null : value;
    }

    /**
     * 查询需要检查加密状态的模型凭据。
     */
    public List<ModelCredentialRecord> findModelCredentials() {
        String sql = """
                SELECT id, api_key
                FROM models
                WHERE api_key IS NOT NULL
                  AND api_key <> ''
                  AND is_deleted = FALSE
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ModelCredentialRecord(
                rs.getLong("id"),
                rs.getString("api_key")
        ));
    }

    /**
     * 更新模型加密凭据。
     */
    public int updateModelApiKey(Long modelId, String encryptedApiKey) {
        return jdbcTemplate.update(
                "UPDATE models SET api_key = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND is_deleted = FALSE",
                encryptedApiKey,
                modelId
        );
    }

    private record SqlAndParams(String sql, List<Object> params) {
    }
}

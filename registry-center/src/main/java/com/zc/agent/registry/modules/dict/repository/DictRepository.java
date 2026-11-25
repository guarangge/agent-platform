package com.zc.agent.registry.modules.dict.repository;

import com.zc.agent.registry.modules.dict.dto.CreateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.CreateDictTypeRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictItemRequest;
import com.zc.agent.registry.modules.dict.dto.UpdateDictTypeRequest;
import com.zc.agent.registry.modules.dict.model.DictItemDetailRecord;
import com.zc.agent.registry.modules.dict.model.DictItemPageRecord;
import com.zc.agent.registry.modules.dict.model.DictOptionRecord;
import com.zc.agent.registry.modules.dict.model.DictTypeDetailRecord;
import com.zc.agent.registry.modules.dict.model.DictTypePageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 字典仓储。
 */
@Repository
public class DictRepository {

    private final JdbcTemplate jdbcTemplate;

    public DictRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countDictTypes(String typeCode, String typeName, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM dict_types
                WHERE is_deleted = FALSE
                """);
        List<Object> args = new ArrayList<>();
        appendDictTypeConditions(sql, args, typeCode, typeName, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public List<DictTypePageRecord> pageDictTypes(String typeCode,
                                                  String typeName,
                                                  String status,
                                                  int offset,
                                                  int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,
                       type_code,
                       type_name,
                       status,
                       built_in,
                       description,
                       created_at
                FROM dict_types
                WHERE is_deleted = FALSE
                """);
        List<Object> args = new ArrayList<>();
        appendDictTypeConditions(sql, args, typeCode, typeName, status);
        sql.append(" ORDER BY built_in DESC, created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DictTypePageRecord(
                rs.getLong("id"),
                rs.getString("type_code"),
                rs.getString("type_name"),
                rs.getString("status"),
                rs.getBoolean("built_in"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), args.toArray());
    }

    public Optional<DictTypeDetailRecord> findDictTypeById(Long dictTypeId) {
        List<DictTypeDetailRecord> records = jdbcTemplate.query("""
                        SELECT id,
                               type_code,
                               type_name,
                               status,
                               built_in,
                               description,
                               remark,
                               created_at,
                               updated_at,
                               created_by,
                               updated_by
                        FROM dict_types
                        WHERE id = ?
                          AND is_deleted = FALSE
                        """,
                (rs, rowNum) -> new DictTypeDetailRecord(
                        rs.getLong("id"),
                        rs.getString("type_code"),
                        rs.getString("type_name"),
                        rs.getString("status"),
                        rs.getBoolean("built_in"),
                        rs.getString("description"),
                        rs.getString("remark"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        getLong(rs, "created_by"),
                        getLong(rs, "updated_by")
                ),
                dictTypeId
        );
        return records.stream().findFirst();
    }

    public Optional<Long> findDictTypeIdByCode(String typeCode) {
        List<Long> ids = jdbcTemplate.query("""
                        SELECT id
                        FROM dict_types
                        WHERE type_code = ?
                          AND is_deleted = FALSE
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                typeCode
        );
        return ids.stream().findFirst();
    }

    public Long insertDictType(CreateDictTypeRequest request, Long userId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO dict_types (
                            type_code,
                            type_name,
                            status,
                            built_in,
                            description,
                            metadata,
                            remark,
                            created_by,
                            updated_by
                        )
                        VALUES (?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                request.typeCode(),
                request.typeName(),
                request.status(),
                request.builtIn(),
                request.description(),
                request.remark(),
                userId,
                userId
        );
    }

    public int updateDictType(Long dictTypeId, UpdateDictTypeRequest request, Long userId) {
        return jdbcTemplate.update("""
                        UPDATE dict_types
                        SET type_code = ?,
                            type_name = ?,
                            status = ?,
                            built_in = ?,
                            description = ?,
                            remark = ?,
                            updated_at = CURRENT_TIMESTAMP,
                            updated_by = ?
                        WHERE id = ?
                          AND is_deleted = FALSE
                        """,
                request.typeCode(),
                request.typeName(),
                request.status(),
                request.builtIn(),
                request.description(),
                request.remark(),
                userId,
                dictTypeId
        );
    }

    public int updateDictTypeStatus(Long dictTypeId, String status, Long userId) {
        return jdbcTemplate.update("""
                        UPDATE dict_types
                        SET status = ?,
                            updated_at = CURRENT_TIMESTAMP,
                            updated_by = ?
                        WHERE id = ?
                          AND is_deleted = FALSE
                        """,
                status,
                userId,
                dictTypeId
        );
    }

    public long countDictItems(Long dictTypeId, String itemCode, String itemName, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM dict_items
                WHERE type_id = ?
                  AND is_deleted = FALSE
                """);
        List<Object> args = new ArrayList<>();
        args.add(dictTypeId);
        appendDictItemConditions(sql, args, itemCode, itemName, status);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public List<DictItemPageRecord> pageDictItems(Long dictTypeId,
                                                  String itemCode,
                                                  String itemName,
                                                  String status,
                                                  int offset,
                                                  int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id,
                       i.type_id,
                       t.type_code,
                       i.item_code,
                       i.item_name,
                       i.sort_order,
                       i.status,
                       i.is_default,
                       i.description,
                       i.created_at
                FROM dict_items i
                JOIN dict_types t ON t.id = i.type_id
                WHERE i.type_id = ?
                  AND i.is_deleted = FALSE
                  AND t.is_deleted = FALSE
                """);
        List<Object> args = new ArrayList<>();
        args.add(dictTypeId);
        appendDictItemConditions(sql, args, itemCode, itemName, status);
        sql.append(" ORDER BY i.sort_order ASC, i.created_at ASC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new DictItemPageRecord(
                rs.getLong("id"),
                rs.getLong("type_id"),
                rs.getString("type_code"),
                rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getInt("sort_order"),
                rs.getString("status"),
                rs.getBoolean("is_default"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), args.toArray());
    }

    public Optional<DictItemDetailRecord> findDictItemById(Long dictItemId) {
        List<DictItemDetailRecord> records = jdbcTemplate.query("""
                        SELECT i.id,
                               i.type_id,
                               t.type_code,
                               i.item_code,
                               i.item_name,
                               i.sort_order,
                               i.status,
                               i.is_default,
                               i.description,
                               i.remark,
                               i.created_at,
                               i.updated_at,
                               i.created_by,
                               i.updated_by
                        FROM dict_items i
                        JOIN dict_types t ON t.id = i.type_id
                        WHERE i.id = ?
                          AND i.is_deleted = FALSE
                          AND t.is_deleted = FALSE
                        """,
                (rs, rowNum) -> new DictItemDetailRecord(
                        rs.getLong("id"),
                        rs.getLong("type_id"),
                        rs.getString("type_code"),
                        rs.getString("item_code"),
                        rs.getString("item_name"),
                        rs.getInt("sort_order"),
                        rs.getString("status"),
                        rs.getBoolean("is_default"),
                        rs.getString("description"),
                        rs.getString("remark"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime(),
                        getLong(rs, "created_by"),
                        getLong(rs, "updated_by")
                ),
                dictItemId
        );
        return records.stream().findFirst();
    }

    public Optional<Long> findDictItemIdByTypeAndCode(Long dictTypeId, String itemCode) {
        List<Long> ids = jdbcTemplate.query("""
                        SELECT id
                        FROM dict_items
                        WHERE type_id = ?
                          AND item_code = ?
                          AND is_deleted = FALSE
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                dictTypeId,
                itemCode
        );
        return ids.stream().findFirst();
    }

    public Long insertDictItem(Long dictTypeId, CreateDictItemRequest request, Long userId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO dict_items (
                            type_id,
                            item_code,
                            item_name,
                            sort_order,
                            status,
                            is_default,
                            description,
                            metadata,
                            remark,
                            created_by,
                            updated_by
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                dictTypeId,
                request.itemCode(),
                request.itemName(),
                request.sortOrder(),
                request.status(),
                request.isDefault(),
                request.description(),
                request.remark(),
                userId,
                userId
        );
    }

    public int updateDictItem(Long dictItemId, UpdateDictItemRequest request, Long userId) {
        return jdbcTemplate.update("""
                        UPDATE dict_items
                        SET item_code = ?,
                            item_name = ?,
                            sort_order = ?,
                            status = ?,
                            is_default = ?,
                            description = ?,
                            remark = ?,
                            updated_at = CURRENT_TIMESTAMP,
                            updated_by = ?
                        WHERE id = ?
                          AND is_deleted = FALSE
                        """,
                request.itemCode(),
                request.itemName(),
                request.sortOrder(),
                request.status(),
                request.isDefault(),
                request.description(),
                request.remark(),
                userId,
                dictItemId
        );
    }

    public int updateDictItemStatus(Long dictItemId, String status, Long userId) {
        return jdbcTemplate.update("""
                        UPDATE dict_items
                        SET status = ?,
                            updated_at = CURRENT_TIMESTAMP,
                            updated_by = ?
                        WHERE id = ?
                          AND is_deleted = FALSE
                        """,
                status,
                userId,
                dictItemId
        );
    }

    public List<DictOptionRecord> findEnabledOptionsByTypeCodes(List<String> typeCodes) {
        if (typeCodes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", typeCodes.stream().map(item -> "?").toList());
        String sql = """
                SELECT t.type_code,
                       i.item_code,
                       i.item_name,
                       i.description,
                       i.sort_order
                FROM dict_items i
                JOIN dict_types t ON t.id = i.type_id
                WHERE t.type_code IN (%s)
                  AND t.status = 'enabled'
                  AND t.is_deleted = FALSE
                  AND i.status = 'enabled'
                  AND i.is_deleted = FALSE
                ORDER BY t.type_code ASC, i.sort_order ASC, i.created_at ASC
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DictOptionRecord(
                rs.getString("type_code"),
                rs.getString("item_code"),
                rs.getString("item_name"),
                rs.getString("description"),
                rs.getInt("sort_order")
        ), typeCodes.toArray());
    }

    public boolean existsEnabledDictItem(String typeCode, String itemCode) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM dict_items i
                        JOIN dict_types t ON t.id = i.type_id
                        WHERE t.type_code = ?
                          AND t.status = 'enabled'
                          AND t.is_deleted = FALSE
                          AND i.item_code = ?
                          AND i.status = 'enabled'
                          AND i.is_deleted = FALSE
                        """,
                Long.class,
                typeCode,
                itemCode
        );
        return count != null && count > 0;
    }

    private void appendDictTypeConditions(StringBuilder sql,
                                          List<Object> args,
                                          String typeCode,
                                          String typeName,
                                          String status) {
        if (typeCode != null && !typeCode.isBlank()) {
            sql.append(" AND type_code = ?");
            args.add(typeCode);
        }
        if (typeName != null && !typeName.isBlank()) {
            sql.append(" AND type_name ILIKE ?");
            args.add("%" + typeName + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
    }

    private void appendDictItemConditions(StringBuilder sql,
                                          List<Object> args,
                                          String itemCode,
                                          String itemName,
                                          String status) {
        if (itemCode != null && !itemCode.isBlank()) {
            sql.append(" AND item_code = ?");
            args.add(itemCode);
        }
        if (itemName != null && !itemName.isBlank()) {
            sql.append(" AND item_name ILIKE ?");
            args.add("%" + itemName + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
    }

    private Long getLong(java.sql.ResultSet resultSet, String columnName) throws java.sql.SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}

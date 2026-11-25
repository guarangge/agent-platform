package com.zc.agent.core.modules.eval.repository;

import com.zc.agent.core.modules.eval.model.EvalCaseRecord;
import com.zc.agent.core.modules.eval.model.EvalRunCaseRecord;
import com.zc.agent.core.modules.eval.model.EvalRunRecord;
import com.zc.agent.core.modules.eval.model.EvalSuiteRecord;
import com.zc.agent.core.modules.eval.model.EvalSuiteVersionRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Eval Harness 数据访问。
 */
@Repository
public class EvalRepository {

    private static final RowMapper<EvalSuiteRecord> EVAL_SUITE_ROW_MAPPER = (rs, rowNum) -> new EvalSuiteRecord(
            rs.getLong("suite_id"),
            rs.getInt("current_version"),
            rs.getString("suite_code"),
            rs.getString("suite_name"),
            rs.getLong("agent_id"),
            rs.getString("agent_name"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("metadata"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private static final RowMapper<EvalCaseRecord> EVAL_CASE_ROW_MAPPER = (rs, rowNum) -> new EvalCaseRecord(
            rs.getLong("case_id"),
            rs.getLong("suite_id"),
            rs.getString("case_code"),
            rs.getString("case_name"),
            rs.getString("user_input"),
            rs.getString("expected_reply_keywords"),
            rs.getString("forbidden_reply_keywords"),
            rs.getString("expected_tool_codes"),
            rs.getString("expected_step_types"),
            rs.getString("expected_final_status"),
            rs.getInt("pass_threshold"),
            rs.getInt("order_no"),
            rs.getString("status"),
            rs.getString("metadata"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private static final RowMapper<EvalRunRecord> EVAL_RUN_ROW_MAPPER = (rs, rowNum) -> new EvalRunRecord(
            rs.getLong("run_id"),
            rs.getLong("suite_id"),
            rs.getInt("suite_version"),
            rs.getString("suite_code"),
            rs.getString("suite_name"),
            rs.getLong("agent_id"),
            rs.getString("agent_name"),
            rs.getString("run_code"),
            rs.getString("run_status"),
            rs.getInt("total_cases"),
            rs.getInt("passed_cases"),
            rs.getInt("failed_cases"),
            rs.getBigDecimal("average_score"),
            rs.getString("trigger_type"),
            rs.getString("metadata"),
            rs.getObject("started_at", LocalDateTime.class),
            rs.getObject("finished_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private static final RowMapper<EvalSuiteVersionRecord> EVAL_SUITE_VERSION_ROW_MAPPER = (rs, rowNum) -> new EvalSuiteVersionRecord(
            rs.getLong("version_id"),
            rs.getLong("suite_id"),
            rs.getInt("version_no"),
            rs.getString("suite_code"),
            rs.getString("suite_name"),
            rs.getLong("agent_id"),
            rs.getString("agent_name"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("metadata"),
            rs.getString("cases_json"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("created_by", Long.class)
    );

    private static final RowMapper<EvalRunCaseRecord> EVAL_RUN_CASE_ROW_MAPPER = (rs, rowNum) -> new EvalRunCaseRecord(
            rs.getLong("run_case_id"),
            rs.getLong("run_id"),
            rs.getLong("case_id"),
            rs.getString("case_code"),
            rs.getString("case_name"),
            rs.getObject("conversation_id", Long.class),
            rs.getObject("task_id", Long.class),
            rs.getString("trace_id"),
            rs.getString("run_status"),
            rs.getObject("passed", Boolean.class),
            rs.getBigDecimal("score"),
            rs.getString("actual_reply"),
            rs.getString("actual_tool_codes"),
            rs.getString("actual_step_types"),
            rs.getString("actual_final_status"),
            rs.getString("failure_reasons"),
            rs.getString("metadata"),
            rs.getObject("started_at", LocalDateTime.class),
            rs.getObject("finished_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public EvalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countSuites(String suiteCode, String suiteName, Long agentId, String status) {
        SqlAndParams sqlAndParams = buildSuiteQuery("""
                SELECT COUNT(1)
                FROM eval_suites es
                WHERE es.is_deleted = FALSE
                """, suiteCode, suiteName, agentId, status);
        return jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
    }

    public List<EvalSuiteRecord> pageSuites(String suiteCode, String suiteName, Long agentId, String status, int offset, int pageSize) {
        SqlAndParams sqlAndParams = buildSuiteQuery("""
                SELECT es.id AS suite_id,
                       es.current_version,
                       es.suite_code,
                       es.suite_name,
                       es.agent_id,
                       es.agent_name,
                       es.description,
                       es.status,
                       es.metadata::text AS metadata,
                       es.created_at,
                       es.updated_at
                FROM eval_suites es
                WHERE es.is_deleted = FALSE
                """, suiteCode, suiteName, agentId, status);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY es.updated_at DESC, es.id DESC LIMIT ? OFFSET ?", EVAL_SUITE_ROW_MAPPER, params.toArray());
    }

    public int countCasesBySuiteId(Long suiteId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM eval_cases
                WHERE suite_id = ? AND is_deleted = FALSE
                """, Integer.class, suiteId);
        return count == null ? 0 : count;
    }

    public Optional<EvalSuiteRecord> findSuiteById(Long suiteId) {
        List<EvalSuiteRecord> records = jdbcTemplate.query("""
                SELECT es.id AS suite_id,
                       es.current_version,
                       es.suite_code,
                       es.suite_name,
                       es.agent_id,
                       es.agent_name,
                       es.description,
                       es.status,
                       es.metadata::text AS metadata,
                       es.created_at,
                       es.updated_at
                FROM eval_suites es
                WHERE es.id = ? AND es.is_deleted = FALSE
                """, EVAL_SUITE_ROW_MAPPER, suiteId);
        return records.stream().findFirst();
    }

    public List<EvalCaseRecord> findCasesBySuiteId(Long suiteId) {
        return jdbcTemplate.query("""
                SELECT id AS case_id,
                       suite_id,
                       case_code,
                       case_name,
                       user_input,
                       expected_reply_keywords::text AS expected_reply_keywords,
                       forbidden_reply_keywords::text AS forbidden_reply_keywords,
                       expected_tool_codes::text AS expected_tool_codes,
                       expected_step_types::text AS expected_step_types,
                       expected_final_status,
                       pass_threshold,
                       order_no,
                       status,
                       metadata::text AS metadata,
                       created_at,
                       updated_at
                FROM eval_cases
                WHERE suite_id = ? AND is_deleted = FALSE
                ORDER BY order_no ASC, id ASC
                """, EVAL_CASE_ROW_MAPPER, suiteId);
    }

    public Long insertSuite(String suiteCode,
                            String suiteName,
                            Long agentId,
                            String agentName,
                            String description,
                            String status,
                            String metadata,
                            Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO eval_suites (
                        suite_code, suite_name, agent_id, agent_name, description, status, metadata, current_version, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 1, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, suiteCode);
            statement.setString(2, suiteName);
            statement.setLong(3, agentId);
            statement.setString(4, agentName);
            statement.setString(5, description);
            statement.setString(6, status);
            statement.setString(7, metadata);
            statement.setObject(8, operatorId);
            statement.setObject(9, operatorId);
            return statement;
        }, keyHolder);
        return extractKey(keyHolder);
    }

    public void updateSuite(Long suiteId,
                            Integer currentVersion,
                            String suiteCode,
                            String suiteName,
                            Long agentId,
                            String agentName,
                            String description,
                            String status,
                            String metadata,
                            Long operatorId) {
        jdbcTemplate.update("""
                UPDATE eval_suites
                SET suite_code = ?,
                    suite_name = ?,
                    agent_id = ?,
                    agent_name = ?,
                    description = ?,
                    status = ?,
                    metadata = CAST(? AS jsonb),
                    current_version = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, suiteCode, suiteName, agentId, agentName, description, status, metadata, currentVersion, operatorId, suiteId);
    }

    public void updateSuiteMetadata(Long suiteId,
                                    String metadata,
                                    Long operatorId) {
        jdbcTemplate.update("""
                UPDATE eval_suites
                SET metadata = CAST(? AS jsonb),
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = ? AND is_deleted = FALSE
                """, metadata, operatorId, suiteId);
    }

    public Long insertSuiteVersion(Long suiteId,
                                   Integer versionNo,
                                   String suiteCode,
                                   String suiteName,
                                   Long agentId,
                                   String agentName,
                                   String description,
                                   String status,
                                   String metadata,
                                   String casesJson,
                                   Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO eval_suite_versions (
                        suite_id, version_no, suite_code, suite_name, agent_id, agent_name, description, status,
                        metadata, cases_json, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, suiteId);
            statement.setInt(2, versionNo);
            statement.setString(3, suiteCode);
            statement.setString(4, suiteName);
            statement.setLong(5, agentId);
            statement.setString(6, agentName);
            statement.setString(7, description);
            statement.setString(8, status);
            statement.setString(9, metadata);
            statement.setString(10, casesJson);
            statement.setObject(11, operatorId);
            statement.setObject(12, operatorId);
            return statement;
        }, keyHolder);
        return extractKey(keyHolder);
    }

    public void softDeleteCasesBySuiteId(Long suiteId, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE eval_cases
                SET is_deleted = TRUE,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE suite_id = ? AND is_deleted = FALSE
                """, operatorId, suiteId);
    }

    public Long insertCase(Long suiteId,
                           String caseCode,
                           String caseName,
                           String userInput,
                           String expectedReplyKeywords,
                           String forbiddenReplyKeywords,
                           String expectedToolCodes,
                           String expectedStepTypes,
                           String expectedFinalStatus,
                           Integer passThreshold,
                           Integer orderNo,
                           String status,
                           String metadata,
                           Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO eval_cases (
                        suite_id, case_code, case_name, user_input, expected_reply_keywords, forbidden_reply_keywords,
                        expected_tool_codes, expected_step_types, expected_final_status, pass_threshold, order_no,
                        status, metadata, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, suiteId);
            statement.setString(2, caseCode);
            statement.setString(3, caseName);
            statement.setString(4, userInput);
            statement.setString(5, expectedReplyKeywords);
            statement.setString(6, forbiddenReplyKeywords);
            statement.setString(7, expectedToolCodes);
            statement.setString(8, expectedStepTypes);
            statement.setString(9, expectedFinalStatus);
            statement.setInt(10, passThreshold);
            statement.setInt(11, orderNo);
            statement.setString(12, status);
            statement.setString(13, metadata);
            statement.setObject(14, operatorId);
            statement.setObject(15, operatorId);
            return statement;
        }, keyHolder);
        return extractKey(keyHolder);
    }

    public Long insertRun(Long suiteId,
                          Integer suiteVersion,
                          Long agentId,
                          String agentName,
                          String runCode,
                          String runStatus,
                          Integer totalCases,
                          String triggerType,
                          String metadata,
                          LocalDateTime startedAt,
                          Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO eval_runs (
                        suite_id, suite_version, agent_id, agent_name, run_code, run_status, total_cases, passed_cases, failed_cases, average_score,
                        trigger_type, metadata, started_at, created_by, updated_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, CAST(? AS jsonb), ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, suiteId);
            statement.setInt(2, suiteVersion);
            statement.setLong(3, agentId);
            statement.setString(4, agentName);
            statement.setString(5, runCode);
            statement.setString(6, runStatus);
            statement.setInt(7, totalCases);
            statement.setString(8, triggerType);
            statement.setString(9, metadata);
            statement.setObject(10, startedAt);
            statement.setObject(11, operatorId);
            statement.setObject(12, operatorId);
            return statement;
        }, keyHolder);
        return extractKey(keyHolder);
    }

    public void updateRunResult(Long runId,
                                String runStatus,
                                Integer passedCases,
                                Integer failedCases,
                                BigDecimal averageScore,
                                String metadata,
                                LocalDateTime finishedAt,
                                Long operatorId) {
        jdbcTemplate.update("""
                UPDATE eval_runs
                SET run_status = ?,
                    passed_cases = ?,
                    failed_cases = ?,
                    average_score = ?,
                    metadata = CAST(? AS jsonb),
                    finished_at = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = ?
                """, runStatus, passedCases, failedCases, averageScore, metadata, finishedAt, operatorId, runId);
    }

    public Long insertRunCase(Long runId,
                              Long caseId,
                              String runStatus,
                              LocalDateTime startedAt,
                              Long operatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO eval_run_cases (
                        run_id, case_id, run_status, metadata, started_at, created_by, updated_by
                    ) VALUES (?, ?, ?, '{}'::jsonb, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, runId);
            statement.setLong(2, caseId);
            statement.setString(3, runStatus);
            statement.setObject(4, startedAt);
            statement.setObject(5, operatorId);
            statement.setObject(6, operatorId);
            return statement;
        }, keyHolder);
        return extractKey(keyHolder);
    }

    public void updateRunCaseResult(Long runCaseId,
                                    Long conversationId,
                                    Long taskId,
                                    String traceId,
                                    String runStatus,
                                    Boolean passed,
                                    BigDecimal score,
                                    String actualReply,
                                    String actualToolCodes,
                                    String actualStepTypes,
                                    String actualFinalStatus,
                                    String failureReasons,
                                    String metadata,
                                    LocalDateTime finishedAt,
                                    Long operatorId) {
        jdbcTemplate.update("""
                UPDATE eval_run_cases
                SET conversation_id = ?,
                    task_id = ?,
                    trace_id = ?,
                    run_status = ?,
                    passed = ?,
                    score = ?,
                    actual_reply = ?,
                    actual_tool_codes = CAST(? AS jsonb),
                    actual_step_types = CAST(? AS jsonb),
                    actual_final_status = ?,
                    failure_reasons = CAST(? AS jsonb),
                    metadata = CAST(? AS jsonb),
                    finished_at = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = ?
                WHERE id = ?
                """, conversationId, taskId, traceId, runStatus, passed, score, actualReply, actualToolCodes, actualStepTypes,
                actualFinalStatus, failureReasons, metadata, finishedAt, operatorId, runCaseId);
    }

    public long countRuns(Long suiteId, Long agentId, String runStatus) {
        SqlAndParams sqlAndParams = buildRunQuery("""
                SELECT COUNT(1)
                FROM eval_runs er
                JOIN eval_suites es ON es.id = er.suite_id AND es.is_deleted = FALSE
                WHERE er.is_deleted = FALSE
                """, suiteId, agentId, runStatus);
        return jdbcTemplate.queryForObject(sqlAndParams.sql(), Long.class, sqlAndParams.params().toArray());
    }

    public List<EvalRunRecord> pageRuns(Long suiteId, Long agentId, String runStatus, int offset, int pageSize) {
        SqlAndParams sqlAndParams = buildRunQuery("""
                SELECT er.id AS run_id,
                       er.suite_id,
                       er.suite_version,
                       es.suite_code,
                       es.suite_name,
                       er.agent_id,
                       er.agent_name,
                       er.run_code,
                       er.run_status,
                       er.total_cases,
                       er.passed_cases,
                       er.failed_cases,
                       er.average_score,
                       er.trigger_type,
                       er.metadata::text AS metadata,
                       er.started_at,
                       er.finished_at,
                       er.created_at
                FROM eval_runs er
                JOIN eval_suites es ON es.id = er.suite_id AND es.is_deleted = FALSE
                WHERE er.is_deleted = FALSE
                """, suiteId, agentId, runStatus);
        List<Object> params = new ArrayList<>(sqlAndParams.params());
        params.add(pageSize);
        params.add(offset);
        return jdbcTemplate.query(sqlAndParams.sql() + " ORDER BY er.created_at DESC, er.id DESC LIMIT ? OFFSET ?", EVAL_RUN_ROW_MAPPER, params.toArray());
    }

    public Optional<EvalRunRecord> findRunById(Long runId) {
        List<EvalRunRecord> records = jdbcTemplate.query("""
                SELECT er.id AS run_id,
                       er.suite_id,
                       er.suite_version,
                       es.suite_code,
                       es.suite_name,
                       er.agent_id,
                       er.agent_name,
                       er.run_code,
                       er.run_status,
                       er.total_cases,
                       er.passed_cases,
                       er.failed_cases,
                       er.average_score,
                       er.trigger_type,
                       er.metadata::text AS metadata,
                       er.started_at,
                       er.finished_at,
                       er.created_at
                FROM eval_runs er
                JOIN eval_suites es ON es.id = er.suite_id AND es.is_deleted = FALSE
                WHERE er.id = ? AND er.is_deleted = FALSE
                """, EVAL_RUN_ROW_MAPPER, runId);
        return records.stream().findFirst();
    }

    public Optional<EvalRunRecord> findLatestBaselineRun(Long suiteId, Long currentRunId) {
        List<EvalRunRecord> records = jdbcTemplate.query("""
                SELECT er.id AS run_id,
                       er.suite_id,
                       er.suite_version,
                       es.suite_code,
                       es.suite_name,
                       er.agent_id,
                       er.agent_name,
                       er.run_code,
                       er.run_status,
                       er.total_cases,
                       er.passed_cases,
                       er.failed_cases,
                       er.average_score,
                       er.trigger_type,
                       er.metadata::text AS metadata,
                       er.started_at,
                       er.finished_at,
                       er.created_at
                FROM eval_runs er
                JOIN eval_suites es ON es.id = er.suite_id AND es.is_deleted = FALSE
                WHERE er.suite_id = ?
                  AND er.id <> ?
                  AND er.is_deleted = FALSE
                  AND er.run_status IN ('success', 'failed')
                ORDER BY er.created_at DESC, er.id DESC
                LIMIT 1
                """, EVAL_RUN_ROW_MAPPER, suiteId, currentRunId);
        return records.stream().findFirst();
    }

    public Optional<EvalRunRecord> findLatestCompletedRunByAgent(Long agentId) {
        List<EvalRunRecord> records = jdbcTemplate.query("""
                SELECT er.id AS run_id,
                       er.suite_id,
                       er.suite_version,
                       es.suite_code,
                       es.suite_name,
                       er.agent_id,
                       er.agent_name,
                       er.run_code,
                       er.run_status,
                       er.total_cases,
                       er.passed_cases,
                       er.failed_cases,
                       er.average_score,
                       er.trigger_type,
                       er.metadata::text AS metadata,
                       er.started_at,
                       er.finished_at,
                       er.created_at
                FROM eval_runs er
                JOIN eval_suites es ON es.id = er.suite_id AND es.is_deleted = FALSE
                WHERE er.agent_id = ?
                  AND er.is_deleted = FALSE
                  AND er.run_status IN ('success', 'failed')
                ORDER BY er.created_at DESC, er.id DESC
                LIMIT 1
                """, EVAL_RUN_ROW_MAPPER, agentId);
        return records.stream().findFirst();
    }

    public List<String> findRecentSuccessfulToolCodesByAgent(Long agentId, int limit) {
        return jdbcTemplate.query("""
                SELECT erc.actual_tool_codes::text AS actual_tool_codes
                FROM eval_run_cases erc
                JOIN eval_runs er ON er.id = erc.run_id AND er.is_deleted = FALSE
                WHERE er.agent_id = ?
                  AND erc.is_deleted = FALSE
                  AND erc.passed = TRUE
                  AND erc.actual_tool_codes IS NOT NULL
                  AND erc.actual_tool_codes::text <> ''
                  AND erc.actual_tool_codes::text <> '[]'
                ORDER BY erc.created_at DESC, erc.id DESC
                LIMIT ?
                """, (rs, rowNum) -> rs.getString("actual_tool_codes"), agentId, limit);
    }

    public List<EvalRunCaseRecord> findRunCasesByRunId(Long runId) {
        return jdbcTemplate.query("""
                SELECT erc.id AS run_case_id,
                       erc.run_id,
                       erc.case_id,
                       ec.case_code,
                       ec.case_name,
                       erc.conversation_id,
                       erc.task_id,
                       erc.trace_id,
                       erc.run_status,
                       erc.passed,
                       erc.score,
                       erc.actual_reply,
                       erc.actual_tool_codes::text AS actual_tool_codes,
                       erc.actual_step_types::text AS actual_step_types,
                       erc.actual_final_status,
                       erc.failure_reasons::text AS failure_reasons,
                       erc.metadata::text AS metadata,
                       erc.started_at,
                       erc.finished_at,
                       erc.created_at
                FROM eval_run_cases erc
                JOIN eval_cases ec ON ec.id = erc.case_id AND ec.is_deleted = FALSE
                WHERE erc.run_id = ? AND erc.is_deleted = FALSE
                ORDER BY ec.order_no ASC, erc.id ASC
                """, EVAL_RUN_CASE_ROW_MAPPER, runId);
    }

    public List<EvalSuiteVersionRecord> findSuiteVersions(Long suiteId) {
        return jdbcTemplate.query("""
                SELECT id AS version_id,
                       suite_id,
                       version_no,
                       suite_code,
                       suite_name,
                       agent_id,
                       agent_name,
                       description,
                       status,
                       metadata::text AS metadata,
                       cases_json::text AS cases_json,
                       created_at,
                       created_by
                FROM eval_suite_versions
                WHERE suite_id = ? AND is_deleted = FALSE
                ORDER BY version_no DESC, id DESC
                """, EVAL_SUITE_VERSION_ROW_MAPPER, suiteId);
    }

    public Optional<EvalSuiteVersionRecord> findSuiteVersion(Long suiteId, Integer versionNo) {
        List<EvalSuiteVersionRecord> records = jdbcTemplate.query("""
                SELECT id AS version_id,
                       suite_id,
                       version_no,
                       suite_code,
                       suite_name,
                       agent_id,
                       agent_name,
                       description,
                       status,
                       metadata::text AS metadata,
                       cases_json::text AS cases_json,
                       created_at,
                       created_by
                FROM eval_suite_versions
                WHERE suite_id = ? AND version_no = ? AND is_deleted = FALSE
                """, EVAL_SUITE_VERSION_ROW_MAPPER, suiteId, versionNo);
        return records.stream().findFirst();
    }

    private SqlAndParams buildSuiteQuery(String baseSql, String suiteCode, String suiteName, Long agentId, String status) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (suiteCode != null && !suiteCode.isBlank()) {
            sql.append(" AND es.suite_code ILIKE ?");
            params.add("%" + suiteCode.trim() + "%");
        }
        if (suiteName != null && !suiteName.isBlank()) {
            sql.append(" AND es.suite_name ILIKE ?");
            params.add("%" + suiteName.trim() + "%");
        }
        if (agentId != null) {
            sql.append(" AND es.agent_id = ?");
            params.add(agentId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND es.status = ?");
            params.add(status.trim());
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private SqlAndParams buildRunQuery(String baseSql, Long suiteId, Long agentId, String runStatus) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();
        if (suiteId != null) {
            sql.append(" AND er.suite_id = ?");
            params.add(suiteId);
        }
        if (agentId != null) {
            sql.append(" AND er.agent_id = ?");
            params.add(agentId);
        }
        if (runStatus != null && !runStatus.isBlank()) {
            sql.append(" AND er.run_status = ?");
            params.add(runStatus.trim());
        }
        return new SqlAndParams(sql.toString(), params);
    }

    private Long extractKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("写入 Eval 数据失败，未返回主键");
        }
        return key.longValue();
    }

    private record SqlAndParams(String sql, List<Object> params) {
    }
}

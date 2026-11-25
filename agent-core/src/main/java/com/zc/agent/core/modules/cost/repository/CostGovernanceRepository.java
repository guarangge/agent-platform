package com.zc.agent.core.modules.cost.repository;

import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.BudgetPolicyRequest;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.PriceRequest;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetAlert;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetPolicy;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.ResourcePrice;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.UsageEvent;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.UsageSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cost 治理持久层，所有查询均受数据库 RLS 约束。 */
@Repository
public class CostGovernanceRepository {
    private final JdbcTemplate jdbcTemplate;

    public CostGovernanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ResourcePrice> findEffectivePrice(String resourceType, String resourceCode) {
        return jdbcTemplate.query("""
                SELECT * FROM resource_prices
                 WHERE resource_type=? AND resource_code=? AND status='enabled'
                   AND effective_from<=CURRENT_TIMESTAMP
                   AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)
                 ORDER BY project_id DESC, effective_from DESC LIMIT 1
                """, priceMapper(), resourceType, resourceCode).stream().findFirst();
    }

    public List<ResourcePrice> listPrices() {
        return jdbcTemplate.query("SELECT * FROM resource_prices ORDER BY resource_type, resource_code, effective_from DESC", priceMapper());
    }

    public ResourcePrice insertPrice(PriceRequest request, Long operatorId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO resource_prices(resource_type,resource_code,input_price_per_1000,output_price_per_1000,
                      unit_price,currency,status,effective_from,effective_to,created_by,updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            bindPrice(statement, request, operatorId);
            return statement;
        }, key);
        return findPrice(key.getKey().longValue()).orElseThrow();
    }

    public ResourcePrice updatePrice(Long id, PriceRequest request, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE resource_prices SET resource_type=?,resource_code=?,input_price_per_1000=?,output_price_per_1000=?,
                  unit_price=?,currency=?,status=?,effective_from=?,effective_to=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, request.resourceType(), request.resourceCode(), request.inputPricePer1000(), request.outputPricePer1000(),
                request.unitPrice(), request.currency(), request.status(), effectiveFrom(request.effectiveFrom()),
                request.effectiveTo(), operatorId, id);
        return findPrice(id).orElseThrow();
    }

    public Optional<ResourcePrice> findPrice(Long id) {
        return jdbcTemplate.query("SELECT * FROM resource_prices WHERE id=?", priceMapper(), id).stream().findFirst();
    }

    public List<BudgetPolicy> listPolicies() {
        return jdbcTemplate.query("SELECT * FROM budget_policies ORDER BY priority,id", policyMapper());
    }

    public List<BudgetPolicy> findApplicablePolicies(Long tenantId, Long projectId, Long agentId, Long userId) {
        return jdbcTemplate.query("""
                SELECT * FROM budget_policies
                 WHERE status='enabled' AND effective_from<=CURRENT_TIMESTAMP
                   AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)
                   AND ((scope_type='tenant' AND scope_id=?) OR (scope_type='project' AND scope_id=?)
                     OR (scope_type='agent' AND scope_id=?) OR (scope_type='user' AND scope_id=?))
                 ORDER BY priority,id
                """, policyMapper(), value(tenantId), value(projectId), value(agentId), value(userId));
    }

    public BudgetPolicy insertPolicy(BudgetPolicyRequest request, Long operatorId) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO budget_policies(policy_code,policy_name,scope_type,scope_id,period_type,budget_amount,
                      llm_token_limit,tool_call_limit,sandbox_second_limit,delegation_call_limit,warning_percent,
                      hard_limit,currency,priority,status,effective_from,effective_to,created_by,updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            int i = 1;
            statement.setString(i++, request.policyCode()); statement.setString(i++, request.policyName());
            statement.setString(i++, request.scopeType()); statement.setLong(i++, request.scopeId());
            statement.setString(i++, request.periodType()); statement.setObject(i++, request.budgetAmount());
            statement.setObject(i++, request.llmTokenLimit()); statement.setObject(i++, request.toolCallLimit());
            statement.setObject(i++, request.sandboxSecondLimit()); statement.setObject(i++, request.delegationCallLimit());
            statement.setBigDecimal(i++, request.warningPercent()); statement.setBoolean(i++, request.hardLimit());
            statement.setString(i++, request.currency()); statement.setInt(i++, request.priority());
            statement.setString(i++, request.status()); statement.setTimestamp(i++, Timestamp.valueOf(effectiveFrom(request.effectiveFrom())));
            statement.setObject(i++, timestamp(request.effectiveTo())); statement.setObject(i++, operatorId); statement.setObject(i, operatorId);
            return statement;
        }, key);
        return findPolicy(key.getKey().longValue()).orElseThrow();
    }

    public BudgetPolicy updatePolicy(Long id, BudgetPolicyRequest request, Long operatorId) {
        jdbcTemplate.update("""
                UPDATE budget_policies SET policy_code=?,policy_name=?,scope_type=?,scope_id=?,period_type=?,budget_amount=?,
                  llm_token_limit=?,tool_call_limit=?,sandbox_second_limit=?,delegation_call_limit=?,warning_percent=?,
                  hard_limit=?,currency=?,priority=?,status=?,effective_from=?,effective_to=?,updated_by=?,updated_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, request.policyCode(), request.policyName(), request.scopeType(), request.scopeId(), request.periodType(),
                request.budgetAmount(), request.llmTokenLimit(), request.toolCallLimit(), request.sandboxSecondLimit(),
                request.delegationCallLimit(), request.warningPercent(), request.hardLimit(), request.currency(), request.priority(),
                request.status(), effectiveFrom(request.effectiveFrom()), request.effectiveTo(), operatorId, id);
        return findPolicy(id).orElseThrow();
    }

    public Optional<BudgetPolicy> findPolicy(Long id) {
        return jdbcTemplate.query("SELECT * FROM budget_policies WHERE id=?", policyMapper(), id).stream().findFirst();
    }

    public UsageSummary summarize(String scopeType, Long scopeId, LocalDateTime start, LocalDateTime end) {
        String condition = switch (scopeType) {
            case "tenant" -> "tenant_id=?";
            case "project" -> "project_id=?";
            case "agent" -> "agent_id=?";
            case "user" -> "user_id=?";
            default -> throw new IllegalArgumentException("不支持的预算作用域: " + scopeType);
        };
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount),0) amount,
                       COALESCE(SUM(CASE WHEN resource_type='llm' THEN input_units+output_units ELSE 0 END),0) llm_tokens,
                       COALESCE(SUM(CASE WHEN resource_type IN ('tool','mcp') THEN quantity ELSE 0 END),0) tool_calls,
                       COALESCE(SUM(CASE WHEN resource_type='sandbox' THEN quantity ELSE 0 END),0) sandbox_seconds,
                       COALESCE(SUM(CASE WHEN resource_type='delegation' THEN quantity ELSE 0 END),0) delegation_calls,
                       COUNT(*) event_count,
                       COUNT(*) FILTER (WHERE pricing_status='unpriced') unpriced_events
                  FROM usage_events WHERE """ + " " + condition + " AND occurred_at>=? AND occurred_at<?",
                (rs, rowNum) -> new UsageSummary(rs.getBigDecimal("amount"), rs.getLong("llm_tokens"),
                        rs.getLong("tool_calls"), rs.getBigDecimal("sandbox_seconds"), rs.getLong("delegation_calls"),
                        rs.getLong("event_count"), rs.getLong("unpriced_events")), scopeId, start, end);
    }

    public void insertUsage(String eventCode, String resourceType, String resourceCode, String provider,
                            Long userId, Long agentId, Long conversationId, Long taskId,
                            long inputUnits, long outputUnits, BigDecimal quantity, String unit,
                            BigDecimal amount, String currency, String pricingStatus,
                            String executionStatus, Long durationMs, String metadata) {
        jdbcTemplate.update("""
                INSERT INTO usage_events(event_code,resource_type,resource_code,provider,user_id,agent_id,conversation_id,
                  task_id,input_units,output_units,quantity,unit,amount,currency,pricing_status,execution_status,duration_ms,metadata)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSONB))
                """, eventCode,resourceType,resourceCode,provider,userId,agentId,conversationId,taskId,inputUnits,
                outputUnits,quantity,unit,amount,currency,pricingStatus,executionStatus,durationMs,metadata);
    }

    public List<UsageEvent> listUsage(String resourceType, Long agentId, Long userId,
                                      LocalDateTime start, LocalDateTime end, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM usage_events WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (resourceType != null && !resourceType.isBlank()) { sql.append(" AND resource_type=?"); args.add(resourceType); }
        if (agentId != null) { sql.append(" AND agent_id=?"); args.add(agentId); }
        if (userId != null) { sql.append(" AND user_id=?"); args.add(userId); }
        if (start != null) { sql.append(" AND occurred_at>=?"); args.add(start); }
        if (end != null) { sql.append(" AND occurred_at<?"); args.add(end); }
        sql.append(" ORDER BY occurred_at DESC LIMIT ?"); args.add(Math.min(Math.max(limit, 1), 1000));
        return jdbcTemplate.query(sql.toString(), usageMapper(), args.toArray());
    }

    public void insertAlert(BudgetPolicy policy, String alertType, String metricType, BigDecimal current,
                            BigDecimal limit, BigDecimal percent, String message,
                            LocalDateTime start, LocalDateTime end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM budget_alerts WHERE policy_id=? AND metric_type=? AND alert_type=?
                  AND status='open' AND period_start=? AND period_end=?
                """, Integer.class, policy.id(), metricType, alertType, start, end);
        if (count != null && count > 0) return;
        jdbcTemplate.update("""
                INSERT INTO budget_alerts(policy_id,alert_type,metric_type,current_value,limit_value,usage_percent,
                  message,period_start,period_end) VALUES (?,?,?,?,?,?,?,?,?)
                """, policy.id(), alertType, metricType, current, limit, percent, message, start, end);
    }

    public List<BudgetAlert> listAlerts(String status) {
        String sql = """
                SELECT a.*,p.policy_code,p.policy_name FROM budget_alerts a JOIN budget_policies p ON p.id=a.policy_id
                """ + (status == null || status.isBlank() ? "" : " WHERE a.status=?") + " ORDER BY a.triggered_at DESC LIMIT 500";
        return status == null || status.isBlank() ? jdbcTemplate.query(sql, alertMapper()) : jdbcTemplate.query(sql, alertMapper(), status);
    }

    public void updateAlertStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE budget_alerts SET status=?,resolved_at=CASE WHEN ?='resolved' THEN CURRENT_TIMESTAMP ELSE resolved_at END WHERE id=?",
                status, status, id);
    }

    private void bindPrice(PreparedStatement statement, PriceRequest request, Long operatorId) throws java.sql.SQLException {
        statement.setString(1, request.resourceType()); statement.setString(2, request.resourceCode());
        statement.setBigDecimal(3, request.inputPricePer1000()); statement.setBigDecimal(4, request.outputPricePer1000());
        statement.setBigDecimal(5, request.unitPrice()); statement.setString(6, request.currency());
        statement.setString(7, request.status()); statement.setTimestamp(8, Timestamp.valueOf(effectiveFrom(request.effectiveFrom())));
        statement.setObject(9, timestamp(request.effectiveTo())); statement.setObject(10, operatorId); statement.setObject(11, operatorId);
    }

    private org.springframework.jdbc.core.RowMapper<ResourcePrice> priceMapper() { return (rs,n) -> new ResourcePrice(rs.getLong("id"),rs.getString("resource_type"),rs.getString("resource_code"),rs.getBigDecimal("input_price_per_1000"),rs.getBigDecimal("output_price_per_1000"),rs.getBigDecimal("unit_price"),rs.getString("currency"),rs.getString("status"),rs.getTimestamp("effective_from").toLocalDateTime(),nullableTime(rs.getTimestamp("effective_to"))); }
    private org.springframework.jdbc.core.RowMapper<BudgetPolicy> policyMapper() { return (rs,n) -> new BudgetPolicy(rs.getLong("id"),rs.getString("policy_code"),rs.getString("policy_name"),rs.getString("scope_type"),rs.getLong("scope_id"),rs.getString("period_type"),rs.getBigDecimal("budget_amount"),(Long)rs.getObject("llm_token_limit"),(Long)rs.getObject("tool_call_limit"),(Long)rs.getObject("sandbox_second_limit"),(Long)rs.getObject("delegation_call_limit"),rs.getBigDecimal("warning_percent"),rs.getBoolean("hard_limit"),rs.getString("currency"),rs.getInt("priority"),rs.getString("status"),rs.getTimestamp("effective_from").toLocalDateTime(),nullableTime(rs.getTimestamp("effective_to"))); }
    private org.springframework.jdbc.core.RowMapper<UsageEvent> usageMapper() { return (rs,n) -> new UsageEvent(rs.getLong("id"),rs.getString("event_code"),rs.getString("resource_type"),rs.getString("resource_code"),rs.getString("provider"),(Long)rs.getObject("user_id"),(Long)rs.getObject("agent_id"),(Long)rs.getObject("conversation_id"),(Long)rs.getObject("task_id"),rs.getLong("input_units"),rs.getLong("output_units"),rs.getBigDecimal("quantity"),rs.getString("unit"),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("pricing_status"),rs.getString("execution_status"),(Long)rs.getObject("duration_ms"),rs.getTimestamp("occurred_at").toLocalDateTime()); }
    private org.springframework.jdbc.core.RowMapper<BudgetAlert> alertMapper() { return (rs,n) -> new BudgetAlert(rs.getLong("id"),rs.getLong("policy_id"),rs.getString("policy_code"),rs.getString("policy_name"),rs.getString("alert_type"),rs.getString("metric_type"),rs.getBigDecimal("current_value"),rs.getBigDecimal("limit_value"),rs.getBigDecimal("usage_percent"),rs.getString("status"),rs.getString("message"),rs.getTimestamp("period_start").toLocalDateTime(),rs.getTimestamp("period_end").toLocalDateTime(),rs.getTimestamp("triggered_at").toLocalDateTime(),nullableTime(rs.getTimestamp("resolved_at"))); }
    private LocalDateTime effectiveFrom(LocalDateTime value) { return value == null ? LocalDateTime.now() : value; }
    private Timestamp timestamp(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private LocalDateTime nullableTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private long value(Long value) { return value == null ? Long.MIN_VALUE : value; }
}

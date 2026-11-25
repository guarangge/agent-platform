package com.zc.agent.core.modules.cost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.BudgetPolicyRequest;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.PriceRequest;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetAlert;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetPolicy;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.ResourcePrice;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.UsageEvent;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.UsageSummary;
import com.zc.agent.core.modules.cost.repository.CostGovernanceRepository;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.LlmCompletionResult;
import com.zc.agent.core.security.SecurityUser;
import com.zc.agent.core.security.TenantScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一成本、配额与预算治理服务。
 * 运行前按所有命中作用域做最严格判断，运行后将实际用量写入不可变 Ledger 并生成阈值告警。
 */
@Slf4j
@Service
public class CostGovernanceService {
    private static final Set<String> RESOURCE_TYPES = Set.of("llm", "tool", "mcp", "sandbox", "delegation");
    private static final Set<String> SCOPE_TYPES = Set.of("tenant", "project", "agent", "user");
    private static final Set<String> PERIOD_TYPES = Set.of("daily", "monthly");
    private final CostGovernanceRepository repository;
    private final ObjectMapper objectMapper;

    public CostGovernanceService(CostGovernanceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 在资源调用前判断预计用量是否触发硬限制。 */
    public void assertAllowed(String resourceType, long projectedInput, long projectedOutput, BigDecimal projectedQuantity) {
        assertAllowed(resourceType, "", projectedInput, projectedOutput, projectedQuantity);
    }

    /** 在已知具体资源编码时同时预测本次调用金额。 */
    public void assertAllowed(String resourceType, String resourceCode, long projectedInput,
                              long projectedOutput, BigDecimal projectedQuantity) {
        ScopeValues scope = currentScope();
        BigDecimal projectedAmount = projectedAmount(resourceType, resourceCode, projectedInput, projectedOutput, projectedQuantity);
        for (BudgetPolicy policy : repository.findApplicablePolicies(scope.tenantId(), scope.projectId(), scope.agentId(), scope.userId())) {
            Period period = period(policy.periodType());
            UsageSummary usage = repository.summarize(policy.scopeType(), policy.scopeId(), period.start(), period.end());
            MetricHit hit = exceededMetric(policy, usage, resourceType, projectedInput, projectedOutput, projectedQuantity, projectedAmount);
            if (hit != null && policy.hardLimit()) {
                createAlert(policy, "exceeded", hit, period);
                log.warn("预算硬限制阻止资源调用 policyCode={}, scopeType={}, scopeId={}, resourceType={}, metric={}, current={}, limit={}",
                        policy.policyCode(), policy.scopeType(), policy.scopeId(), resourceType, hit.metric(), hit.current(), hit.limit());
                throw new BudgetExceededException("预算策略[" + policy.policyCode() + "/" + policy.policyName() + "]已超过"
                        + metricLabel(hit.metric()) + "，本次" + resourceType + "调用已熔断");
            }
        }
    }

    /** 按当前生效价格估算资源调用金额；未配置价格时返回 0。 */
    public BigDecimal estimateAmount(String resourceType,
                                     String resourceCode,
                                     long projectedInput,
                                     long projectedOutput,
                                     BigDecimal projectedQuantity) {
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("不支持的资源类型: " + resourceType);
        }
        return projectedAmount(resourceType, resourceCode, projectedInput, projectedOutput, projectedQuantity);
    }

    /** 记录 LLM Token 和按价格计算的实际成本。 */
    public void recordLlm(ExecutionContext.ModelContext model, LlmCompletionResult result) {
        long input = positive(result.promptTokens());
        long output = positive(result.completionTokens());
        record("llm", model.modelCode(), model.provider(), input, output, BigDecimal.ONE, "call",
                result.durationMs(), "success", Map.of("modelIdentifier", safe(model.modelIdentifier())));
    }

    /** 记录 Tool 或 MCP 调用。 */
    public void recordTool(ExecutionContext context, String resourceType, String toolCode, String provider,
                           long durationMs, String status, Map<String, Object> metadata) {
        UsageAttributionContext.bind(context);
        record(resourceType, toolCode, provider, 0, 0, BigDecimal.ONE, "call", durationMs, status, metadata);
    }

    /** 记录 Sandbox 容器占用秒数。 */
    public void recordSandbox(String profileCode, long durationMs, String status) {
        BigDecimal seconds = BigDecimal.valueOf(Math.max(durationMs, 0)).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        record("sandbox", profileCode, "docker", 0, 0, seconds, "second", durationMs, status, Map.of());
    }

    /** 记录 Delegation 调用。 */
    public void recordDelegation(ExecutionContext context, String agentCode, long durationMs, String status) {
        UsageAttributionContext.bind(context);
        record("delegation", agentCode, "agent", 0, 0, BigDecimal.ONE, "call", durationMs, status, Map.of());
    }

    public List<ResourcePrice> listPrices() { return repository.listPrices(); }

    @Transactional
    public ResourcePrice createPrice(PriceRequest request, SecurityUser user) {
        validatePrice(request);
        return repository.insertPrice(request, user.userId());
    }

    @Transactional
    public ResourcePrice updatePrice(Long id, PriceRequest request, SecurityUser user) {
        validatePrice(request);
        repository.findPrice(id).orElseThrow(() -> new BusinessException("资源价格不存在"));
        return repository.updatePrice(id, request, user.userId());
    }

    public List<BudgetPolicy> listPolicies() { return repository.listPolicies(); }

    @Transactional
    public BudgetPolicy createPolicy(BudgetPolicyRequest request, SecurityUser user) {
        validatePolicy(request);
        return repository.insertPolicy(request, user.userId());
    }

    @Transactional
    public BudgetPolicy updatePolicy(Long id, BudgetPolicyRequest request, SecurityUser user) {
        validatePolicy(request);
        repository.findPolicy(id).orElseThrow(() -> new BusinessException("预算策略不存在"));
        return repository.updatePolicy(id, request, user.userId());
    }

    public List<UsageEvent> listUsage(String resourceType, Long agentId, Long userId,
                                      LocalDateTime start, LocalDateTime end, Integer limit) {
        return repository.listUsage(resourceType, agentId, userId, start, end, limit == null ? 200 : limit);
    }

    /** 返回当前项目本月用量及所有命中预算的使用率。 */
    public Map<String, Object> dashboard() {
        ScopeValues scope = currentScope();
        Period month = period("monthly");
        UsageSummary projectUsage = repository.summarize("project", scope.projectId(), month.start(), month.end());
        List<Map<String, Object>> policies = repository.findApplicablePolicies(scope.tenantId(), scope.projectId(), scope.agentId(), scope.userId())
                .stream().map(policy -> policyDashboard(policy)).toList();
        return Map.of("periodStart", month.start(), "periodEnd", month.end(), "projectUsage", projectUsage, "policies", policies);
    }

    public List<BudgetAlert> listAlerts(String status) { return repository.listAlerts(status); }

    public void updateAlertStatus(Long id, String status) {
        if (!Set.of("open", "acknowledged", "resolved").contains(status)) throw new BusinessException("不支持的告警状态");
        repository.updateAlertStatus(id, status);
    }

    private void record(String resourceType, String resourceCode, String provider, long input, long output,
                        BigDecimal quantity, String unit, Long durationMs, String status, Map<String, Object> metadata) {
        if (!RESOURCE_TYPES.contains(resourceType)) throw new IllegalArgumentException("不支持的资源类型: " + resourceType);
        ResourcePrice price = repository.findEffectivePrice(resourceType, resourceCode).orElse(null);
        BigDecimal amount = price == null ? BigDecimal.ZERO : price.inputPricePer1000().multiply(BigDecimal.valueOf(input))
                .add(price.outputPricePer1000().multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .add(price.unitPrice().multiply(quantity)).setScale(6, RoundingMode.HALF_UP);
        UsageAttributionContext.Attribution attribution = attribution();
        repository.insertUsage("USG_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(), resourceType,
                safe(resourceCode), provider, attribution.userId(), attribution.agentId(), attribution.conversationId(),
                attribution.taskId(), input, output, quantity, unit, amount, price == null ? "CNY" : price.currency(),
                price == null ? "unpriced" : "priced", status, durationMs, json(metadata));
        evaluateWarnings();
    }

    private void evaluateWarnings() {
        ScopeValues scope = currentScope();
        for (BudgetPolicy policy : repository.findApplicablePolicies(scope.tenantId(), scope.projectId(), scope.agentId(), scope.userId())) {
            Period period = period(policy.periodType());
            UsageSummary usage = repository.summarize(policy.scopeType(), policy.scopeId(), period.start(), period.end());
            for (MetricHit hit : metrics(policy, usage)) {
                BigDecimal percent = percent(hit.current(), hit.limit());
                if (percent.compareTo(policy.warningPercent()) >= 0) {
                    createAlert(policy, percent.compareTo(BigDecimal.valueOf(100)) >= 0 ? "exceeded" : "warning", hit, period);
                }
            }
        }
    }

    private MetricHit exceededMetric(BudgetPolicy p, UsageSummary u, String type, long input, long output,
                                     BigDecimal quantity, BigDecimal projectedAmount) {
        BigDecimal amount = u.amount().add(projectedAmount);
        if (p.budgetAmount() != null && amount.compareTo(p.budgetAmount()) > 0) return new MetricHit("amount", amount, p.budgetAmount());
        if ("llm".equals(type) && p.llmTokenLimit() != null) {
            BigDecimal current = BigDecimal.valueOf(u.llmTokens() + input + output);
            if (current.compareTo(BigDecimal.valueOf(p.llmTokenLimit())) > 0) return new MetricHit("llm_tokens", current, BigDecimal.valueOf(p.llmTokenLimit()));
        }
        if (("tool".equals(type) || "mcp".equals(type)) && p.toolCallLimit() != null) {
            BigDecimal current = BigDecimal.valueOf(u.toolCalls()).add(quantity);
            if (current.compareTo(BigDecimal.valueOf(p.toolCallLimit())) > 0) return new MetricHit("tool_calls", current, BigDecimal.valueOf(p.toolCallLimit()));
        }
        if ("sandbox".equals(type) && p.sandboxSecondLimit() != null) {
            BigDecimal current = u.sandboxSeconds().add(quantity);
            if (current.compareTo(BigDecimal.valueOf(p.sandboxSecondLimit())) > 0) return new MetricHit("sandbox_seconds", current, BigDecimal.valueOf(p.sandboxSecondLimit()));
        }
        if ("delegation".equals(type) && p.delegationCallLimit() != null) {
            BigDecimal current = BigDecimal.valueOf(u.delegationCalls()).add(quantity);
            if (current.compareTo(BigDecimal.valueOf(p.delegationCallLimit())) > 0) return new MetricHit("delegation_calls", current, BigDecimal.valueOf(p.delegationCallLimit()));
        }
        return null;
    }

    private BigDecimal projectedAmount(String resourceType, String resourceCode, long input, long output, BigDecimal quantity) {
        ResourcePrice price = repository.findEffectivePrice(resourceType, resourceCode).orElse(null);
        if (price == null) return BigDecimal.ZERO;
        return price.inputPricePer1000().multiply(BigDecimal.valueOf(input))
                .add(price.outputPricePer1000().multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .add(price.unitPrice().multiply(quantity)).setScale(6, RoundingMode.HALF_UP);
    }

    private List<MetricHit> metrics(BudgetPolicy p, UsageSummary u) {
        java.util.ArrayList<MetricHit> values = new java.util.ArrayList<>();
        if (p.budgetAmount() != null) values.add(new MetricHit("amount", u.amount(), p.budgetAmount()));
        if (p.llmTokenLimit() != null) values.add(new MetricHit("llm_tokens", BigDecimal.valueOf(u.llmTokens()), BigDecimal.valueOf(p.llmTokenLimit())));
        if (p.toolCallLimit() != null) values.add(new MetricHit("tool_calls", BigDecimal.valueOf(u.toolCalls()), BigDecimal.valueOf(p.toolCallLimit())));
        if (p.sandboxSecondLimit() != null) values.add(new MetricHit("sandbox_seconds", u.sandboxSeconds(), BigDecimal.valueOf(p.sandboxSecondLimit())));
        if (p.delegationCallLimit() != null) values.add(new MetricHit("delegation_calls", BigDecimal.valueOf(u.delegationCalls()), BigDecimal.valueOf(p.delegationCallLimit())));
        return values;
    }

    private Map<String, Object> policyDashboard(BudgetPolicy policy) {
        Period period = period(policy.periodType());
        UsageSummary usage = repository.summarize(policy.scopeType(), policy.scopeId(), period.start(), period.end());
        List<Map<String, Object>> metrics = metrics(policy, usage).stream().map(hit -> Map.<String,Object>of(
                "metric", hit.metric(), "current", hit.current(), "limit", hit.limit(), "usagePercent", percent(hit.current(), hit.limit()))).toList();
        return Map.of("policy", policy, "periodStart", period.start(), "periodEnd", period.end(), "usage", usage, "metrics", metrics);
    }

    private void createAlert(BudgetPolicy p, String type, MetricHit hit, Period period) {
        BigDecimal percent = percent(hit.current(), hit.limit());
        repository.insertAlert(p, type, hit.metric(), hit.current(), hit.limit(), percent,
                "预算策略[" + p.policyName() + "]" + metricLabel(hit.metric()) + "使用率达到 " + percent + "%", period.start(), period.end());
    }

    private Period period(String type) {
        LocalDate today = LocalDate.now();
        if ("daily".equals(type)) return new Period(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        return new Period(start.atStartOfDay(), start.plusMonths(1).atTime(LocalTime.MIDNIGHT));
    }

    private ScopeValues currentScope() {
        TenantScopeContext.Scope tenant = TenantScopeContext.current();
        UsageAttributionContext.Attribution attribution = attribution();
        return new ScopeValues(tenant == null ? 1L : tenant.tenantId(), tenant == null ? 1L : tenant.projectId(), attribution.agentId(), attribution.userId());
    }

    private UsageAttributionContext.Attribution attribution() {
        UsageAttributionContext.Attribution value = UsageAttributionContext.current();
        if (value != null) return value;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return new UsageAttributionContext.Attribution(user.userId(), null, null, null);
        }
        return new UsageAttributionContext.Attribution(null, null, null, null);
    }

    private void validatePrice(PriceRequest request) {
        if (!RESOURCE_TYPES.contains(request.resourceType())) throw new BusinessException("不支持的资源类型");
        if (!Set.of("enabled", "disabled").contains(request.status())) throw new BusinessException("不支持的价格状态");
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(effectiveFrom(request.effectiveFrom()))) throw new BusinessException("价格失效时间必须晚于生效时间");
    }

    private void validatePolicy(BudgetPolicyRequest request) {
        if (!SCOPE_TYPES.contains(request.scopeType())) throw new BusinessException("不支持的预算作用域");
        if (!PERIOD_TYPES.contains(request.periodType())) throw new BusinessException("不支持的预算周期");
        if (!Set.of("enabled", "disabled").contains(request.status())) throw new BusinessException("不支持的策略状态");
        if (request.budgetAmount() == null && request.llmTokenLimit() == null && request.toolCallLimit() == null
                && request.sandboxSecondLimit() == null && request.delegationCallLimit() == null) throw new BusinessException("预算策略至少配置一个限制");
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(effectiveFrom(request.effectiveFrom()))) throw new BusinessException("策略失效时间必须晚于生效时间");
    }

    private BigDecimal percent(BigDecimal current, BigDecimal limit) { return limit.signum() == 0 ? BigDecimal.valueOf(100) : current.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP); }
    private long positive(Integer value) { return value == null ? 0 : Math.max(value, 0); }
    private String safe(String value) { return value == null ? "" : value; }
    private LocalDateTime effectiveFrom(LocalDateTime value) { return value == null ? LocalDateTime.now() : value; }
    private String metricLabel(String metric) { return switch (metric) { case "amount" -> "费用"; case "llm_tokens" -> "LLM Token"; case "tool_calls" -> "Tool 调用次数"; case "sandbox_seconds" -> "Sandbox 时长"; default -> "Delegation 调用次数"; }; }
    private String json(Map<String,Object> value) { try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); } catch (JsonProcessingException e) { return "{}"; } }
    private record ScopeValues(Long tenantId, Long projectId, Long agentId, Long userId) { }
    private record Period(LocalDateTime start, LocalDateTime end) { }
    private record MetricHit(String metric, BigDecimal current, BigDecimal limit) { }
}

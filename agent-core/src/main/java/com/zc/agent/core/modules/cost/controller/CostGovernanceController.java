package com.zc.agent.core.modules.cost.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.AlertStatusRequest;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.BudgetPolicyRequest;
import com.zc.agent.core.modules.cost.dto.CostGovernanceRequests.PriceRequest;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetAlert;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.BudgetPolicy;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.ResourcePrice;
import com.zc.agent.core.modules.cost.model.CostGovernanceModels.UsageEvent;
import com.zc.agent.core.modules.cost.service.CostGovernanceService;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Cost、配额、预算和告警运营接口。 */
@RestController
@RequestMapping("/api/core/cost-governance")
public class CostGovernanceController {
    private final CostGovernanceService service;

    public CostGovernanceController(CostGovernanceService service) { this.service = service; }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String,Object>> dashboard() { return ApiResponse.success(service.dashboard()); }

    @GetMapping("/usage")
    public ApiResponse<List<UsageEvent>> usage(@RequestParam(required=false) String resourceType,
                                               @RequestParam(required=false) Long agentId,
                                               @RequestParam(required=false) Long userId,
                                               @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                               @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                               @RequestParam(required=false) Integer limit) {
        return ApiResponse.success(service.listUsage(resourceType,agentId,userId,start,end,limit));
    }

    @GetMapping("/prices")
    public ApiResponse<List<ResourcePrice>> prices() { return ApiResponse.success(service.listPrices()); }

    @PostMapping("/prices")
    public ApiResponse<ResourcePrice> createPrice(@Valid @RequestBody PriceRequest request, Authentication auth) { return ApiResponse.success(service.createPrice(request,user(auth))); }

    @PutMapping("/prices/{id}")
    public ApiResponse<ResourcePrice> updatePrice(@PathVariable Long id,@Valid @RequestBody PriceRequest request,Authentication auth) { return ApiResponse.success(service.updatePrice(id,request,user(auth))); }

    @GetMapping("/budgets")
    public ApiResponse<List<BudgetPolicy>> budgets() { return ApiResponse.success(service.listPolicies()); }

    @PostMapping("/budgets")
    public ApiResponse<BudgetPolicy> createBudget(@Valid @RequestBody BudgetPolicyRequest request,Authentication auth) { return ApiResponse.success(service.createPolicy(request,user(auth))); }

    @PutMapping("/budgets/{id}")
    public ApiResponse<BudgetPolicy> updateBudget(@PathVariable Long id,@Valid @RequestBody BudgetPolicyRequest request,Authentication auth) { return ApiResponse.success(service.updatePolicy(id,request,user(auth))); }

    @GetMapping("/alerts")
    public ApiResponse<List<BudgetAlert>> alerts(@RequestParam(required=false) String status) { return ApiResponse.success(service.listAlerts(status)); }

    @PutMapping("/alerts/{id}/status")
    public ApiResponse<Void> alertStatus(@PathVariable Long id,@Valid @RequestBody AlertStatusRequest request) { service.updateAlertStatus(id,request.status()); return ApiResponse.success(null); }

    private SecurityUser user(Authentication authentication) { return (SecurityUser) authentication.getPrincipal(); }
}

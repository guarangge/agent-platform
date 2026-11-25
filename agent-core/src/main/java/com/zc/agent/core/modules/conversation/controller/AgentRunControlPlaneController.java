package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.NodeStatusRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.RoutingRuleRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.WorkerGroupRequest;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.ControlPlaneDashboard;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.RoutingRule;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.WorkerGroup;
import com.zc.agent.core.modules.conversation.service.AgentRunControlPlaneService;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Agent Run 分布式调度控制面 API。 */
@RestController
@RequestMapping("/api/core/agent-runs/control-plane")
public class AgentRunControlPlaneController {
    private final AgentRunControlPlaneService service;
    public AgentRunControlPlaneController(AgentRunControlPlaneService service){this.service=service;}
    @GetMapping public ApiResponse<ControlPlaneDashboard> dashboard(Authentication a){return ApiResponse.success(service.dashboard(user(a)));}
    @GetMapping("/groups") public ApiResponse<List<WorkerGroup>> groups(Authentication a){return ApiResponse.success(service.groups(user(a)));}
    @PostMapping("/groups") public ApiResponse<Void> createGroup(@Valid @RequestBody WorkerGroupRequest r,Authentication a){service.saveGroup(null,r,user(a));return ApiResponse.success(null);}
    @PutMapping("/groups/{id}") public ApiResponse<Void> updateGroup(@PathVariable Long id,@Valid @RequestBody WorkerGroupRequest r,Authentication a){service.saveGroup(id,r,user(a));return ApiResponse.success(null);}
    @GetMapping("/rules") public ApiResponse<List<RoutingRule>> rules(Authentication a){return ApiResponse.success(service.rules(user(a)));}
    @PostMapping("/rules") public ApiResponse<Void> createRule(@Valid @RequestBody RoutingRuleRequest r,Authentication a){service.saveRule(null,r,user(a));return ApiResponse.success(null);}
    @PutMapping("/rules/{id}") public ApiResponse<Void> updateRule(@PathVariable Long id,@Valid @RequestBody RoutingRuleRequest r,Authentication a){service.saveRule(id,r,user(a));return ApiResponse.success(null);}
    @PutMapping("/nodes/{workerId}/status") public ApiResponse<Void> nodeStatus(@PathVariable String workerId,@Valid @RequestBody NodeStatusRequest r,Authentication a){service.setNodeStatus(workerId,r.status(),user(a));return ApiResponse.success(null);}
    private SecurityUser user(Authentication a){return(SecurityUser)a.getPrincipal();}
}

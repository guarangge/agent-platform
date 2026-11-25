package com.zc.agent.core.modules.conversation.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.config.AgentRunWorkerProperties;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.RoutingRuleRequest;
import com.zc.agent.core.modules.conversation.dto.AgentRunControlRequests.WorkerGroupRequest;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.ControlPlaneDashboard;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.RoutingDecision;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.RoutingRule;
import com.zc.agent.core.modules.conversation.model.AgentRunControlModels.WorkerGroup;
import com.zc.agent.core.modules.conversation.repository.AgentRunControlPlaneRepository;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Worker Group、节点容量和 Run 路由控制面。 */
@Service
public class AgentRunControlPlaneService {
    private final AgentRunControlPlaneRepository repository;
    private final AgentRunWorkerProperties properties;
    public AgentRunControlPlaneService(AgentRunControlPlaneRepository repository,AgentRunWorkerProperties properties){this.repository=repository;this.properties=properties;}

    /** 按规则解析 Run 的目标组并固化能力要求。 */
    @Transactional
    public RoutingDecision route(Long agentId,int priority){ensureLocalGroup();return repository.route(agentId,priority,properties.getGroupCode()).orElseThrow(()->new BusinessException("没有可接收任务的 Worker Group"));}
    public List<WorkerGroup> groups(SecurityUser user){requireAdmin(user);return repository.groups();}
    public List<RoutingRule> rules(SecurityUser user){requireAdmin(user);return repository.rules();}
    public ControlPlaneDashboard dashboard(SecurityUser user){requireAdmin(user);var nodes=repository.nodes();Map<String,Long> counts=new LinkedHashMap<>();nodes.forEach(n->counts.merge(n.status(),1L,Long::sum));return new ControlPlaneDashboard(repository.groups(),nodes,counts);}

    @Transactional public void saveGroup(Long id,WorkerGroupRequest request,SecurityUser user){requireAdmin(user);validateGroup(request);repository.saveGroup(id,request,user.userId());}
    @Transactional public void saveRule(Long id,RoutingRuleRequest request,SecurityUser user){requireAdmin(user);validateRule(request);repository.saveRule(id,request,user.userId());}
    public void setNodeStatus(String workerId,String status,SecurityUser user){requireAdmin(user);if(!Set.of("online","draining","offline").contains(status))throw new BusinessException("不支持的 Worker 状态");repository.nodeStatus(workerId,status);}

    /** Worker 定时注册并上报当前负载。 */
    public void heartbeat(String workerId,int currentLoad){ensureLocalGroup();repository.heartbeat(workerId,properties.getGroupCode(),properties.getInstanceAddress(),properties.getCapabilities(),Math.max(1,properties.getConcurrency()),Math.max(0,currentLoad));}
    public boolean accepting(String workerId){try{return repository.workerAccepting(workerId);}catch(Exception e){return true;}}
    public int markStaleNodes(){return repository.markStaleNodes(Math.max(30,properties.getNodeOfflineSeconds()));}
    public void offline(String workerId){repository.nodeStatus(workerId,"offline");}

    private void ensureLocalGroup(){repository.ensureGroup(properties.getGroupCode(),properties.getGroupName(),Math.max(1,properties.getGlobalConcurrency()),Math.max(1,properties.getQueueCapacity()),properties.getCapabilities());}
    private void validateGroup(WorkerGroupRequest r){if(!Set.of("enabled","draining","disabled").contains(r.status()))throw new BusinessException("不支持的 Worker Group 状态");}
    private void validateRule(RoutingRuleRequest r){if(!Set.of("enabled","disabled").contains(r.status()))throw new BusinessException("不支持的路由规则状态");if(r.minPriority()!=null&&r.maxPriority()!=null&&r.minPriority()>r.maxPriority())throw new BusinessException("最小优先级不能大于最大优先级");}
    private void requireAdmin(SecurityUser user){if(user.roleCodes()==null||user.roleCodes().stream().noneMatch(v->"ADMIN".equalsIgnoreCase(v)||"SUPER_ADMIN".equalsIgnoreCase(v)))throw new BusinessException("无权管理 Agent Run 调度控制面");}
}

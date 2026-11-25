package com.zc.agent.core.modules.conversation.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Agent Run 分布式调度控制面查询模型。 */
public final class AgentRunControlModels {
    private AgentRunControlModels() { }

    /** Worker Group 配置与实时队列指标。 */
    public record WorkerGroup(Long id,String groupCode,String groupName,Integer maxConcurrency,Integer queueCapacity,
                              List<String> requiredCapabilities,String status,long queuedCount,long runningCount,
                              long onlineWorkerCount,LocalDateTime oldestQueuedAt) { }
    /** Worker 节点状态。 */
    public record WorkerNode(Long id,String workerId,String groupCode,String instanceAddress,List<String> capabilities,
                             Integer maxConcurrency,Integer currentLoad,String status,LocalDateTime startedAt,
                             LocalDateTime lastHeartbeatAt,LocalDateTime stoppedAt) { }
    /** Run 路由规则。 */
    public record RoutingRule(Long id,String ruleCode,String ruleName,Long agentId,Integer minPriority,Integer maxPriority,
                              List<String> requiredCapabilities,String targetGroupCode,Integer priority,String status) { }
    /** 入队时固化的路由结果。 */
    public record RoutingDecision(String groupCode,List<String> requiredCapabilities,Integer queueCapacity,Integer maxConcurrency,String ruleCode) { }
    /** 控制面总览。 */
    public record ControlPlaneDashboard(List<WorkerGroup> groups,List<WorkerNode> nodes,Map<String,Long> nodeStatusCounts) { }
}

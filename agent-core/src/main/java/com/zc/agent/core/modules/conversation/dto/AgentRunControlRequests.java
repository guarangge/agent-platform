package com.zc.agent.core.modules.conversation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Agent Run 调度控制面写请求。 */
public final class AgentRunControlRequests {
    private AgentRunControlRequests() { }
    /** Worker Group 保存请求。 */
    public record WorkerGroupRequest(@NotBlank String groupCode,@NotBlank String groupName,
                                     @NotNull @Min(1) Integer maxConcurrency,@NotNull @Min(1) Integer queueCapacity,
                                     List<String> requiredCapabilities,@NotBlank String status) { }
    /** 路由规则保存请求。 */
    public record RoutingRuleRequest(@NotBlank String ruleCode,@NotBlank String ruleName,Long agentId,
                                     @Min(0) @Max(100) Integer minPriority,@Min(0) @Max(100) Integer maxPriority,
                                     List<String> requiredCapabilities,@NotBlank String targetGroupCode,
                                     @NotNull Integer priority,@NotBlank String status) { }
    /** 节点状态操作请求。 */
    public record NodeStatusRequest(@NotBlank String status) { }
}

package com.zc.agent.core.modules.runtime.kernel;

import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;

import java.util.List;

/** 可替换的最终响应汇总、流式边界和 Artifact 一致性收口契约。 */
public interface RuntimeResponseAssembler {

    /** 返回稳定响应组装器编码。 */
    String assemblerCode();

    /** 根据完整执行事实组装最终用户响应。 */
    String assemble(ResponseAssemblyRequest request);

    /**
     * 最终响应组装请求。
     *
     * @param executionContext 运行时上下文
     * @param executionDecision 执行决策
     * @param executionPlan 执行计划
     * @param responseParts 步骤响应片段
     * @param stepResults 已完成步骤结果
     * @param conversationId 会话 ID
     * @param taskId Task ID
     */
    record ResponseAssemblyRequest(
            ExecutionContext executionContext,
            ExecutionDecision executionDecision,
            ExecutionPlan executionPlan,
            List<String> responseParts,
            List<RuntimeStepResultContext> stepResults,
            Long conversationId,
            Long taskId
    ) {
    }
}

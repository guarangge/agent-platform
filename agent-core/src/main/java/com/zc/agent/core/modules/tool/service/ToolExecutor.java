package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;

/**
 * Tool 执行器统一接口。
 */
public interface ToolExecutor {

    /**
     * 判断当前执行器是否支持指定 Tool 编码。
     *
     * @param toolCode Tool 编码
     * @return 是否支持
     */
    boolean supports(String toolCode);

    /**
     * 执行指定 Tool。
     *
     * @param executionContext 运行时上下文
     * @param tool             Tool 定义
     * @param toolInputText    当前 Tool 执行输入文本
     * @return 执行结果
     */
    ToolExecutionResult execute(ExecutionContext executionContext, RegistryAgentTool tool, String toolInputText);

    /**
     * 执行带结构化参数的 Tool；默认兼容只接收文本的存量执行器。
     *
     * @param executionContext 运行时上下文
     * @param tool             Tool 定义
     * @param request          Tool 调用请求
     * @return 执行结果
     */
    default ToolExecutionResult execute(ExecutionContext executionContext,
                                        RegistryAgentTool tool,
                                        ToolInvocationRequest request) {
        return execute(executionContext, tool, request.toolInputText());
    }
}

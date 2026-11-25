package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;

import java.util.Set;

/**
 * 标准步骤执行扩展点。
 */
public interface ExecutionStepHandler {

    /**
     * 返回当前处理器负责的步骤类型。
     *
     * @return 不可变步骤类型集合
     */
    Set<ExecutionStepType> supportedTypes();

    /**
     * 执行标准化步骤。
     *
     * @param request 执行请求
     * @return 标准执行结果
     */
    RuntimeStepExecutionOutcome execute(RuntimeStepExecutionCommand request);
}

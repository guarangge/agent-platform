package com.zc.agent.core.modules.runtime.service;

import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 标准步骤处理器注册表。
 * 启动时阻断重复类型注册，避免执行顺序依赖 Spring Bean 顺序。
 */
@Service
public class ExecutionStepHandlerRegistry {

    private final Map<ExecutionStepType, ExecutionStepHandler> handlers;

    public ExecutionStepHandlerRegistry(List<ExecutionStepHandler> handlerList) {
        EnumMap<ExecutionStepType, ExecutionStepHandler> registry = new EnumMap<>(ExecutionStepType.class);
        for (ExecutionStepHandler handler : handlerList) {
            for (ExecutionStepType type : handler.supportedTypes()) {
                ExecutionStepHandler existing = registry.putIfAbsent(type, handler);
                if (existing != null) {
                    throw new IllegalStateException("步骤类型重复注册: " + type.code());
                }
            }
        }
        this.handlers = Map.copyOf(registry);
    }

    /**
     * 按标准步骤类型路由执行。
     *
     * @param request 执行请求
     * @return 标准执行结果
     */
    public RuntimeStepExecutionOutcome execute(RuntimeStepExecutionCommand request) {
        ExecutionStepType type;
        try {
            type = ExecutionStepType.requirePlanType(request.step().stepType());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
        ExecutionStepHandler handler = handlers.get(type);
        if (handler == null) {
            throw new BusinessException("步骤类型尚未注册执行器: " + type.code());
        }
        return handler.execute(request);
    }
}

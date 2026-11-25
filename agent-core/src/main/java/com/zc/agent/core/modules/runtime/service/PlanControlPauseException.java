package com.zc.agent.core.modules.runtime.service;

/** 人工计划控制在安全步骤边界暂停执行。 */
public class PlanControlPauseException extends RuntimeException {
    public PlanControlPauseException(String message) {
        super(message);
    }
}

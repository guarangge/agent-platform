package com.zc.agent.core.modules.workflow.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow 运行时变量集合。
 */
public class WorkflowRuntimeVariables {

    private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

    /**
     * 写入运行时变量。
     *
     * @param key   变量名
     * @param value 变量值
     * @return 当前变量对象
     */
    public WorkflowRuntimeVariables put(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        values.put(key, value);
        return this;
    }

    /**
     * 读取当前变量快照。
     *
     * @return 变量快照
     */
    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(values);
    }
}

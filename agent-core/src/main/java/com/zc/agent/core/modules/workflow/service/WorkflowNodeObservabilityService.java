package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.TaskTraceService;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow 内部节点统一观测入口。
 * 节点类型只负责业务语义，本服务统一处理生命周期、父步骤关联和排查元数据。
 */
@Service
public class WorkflowNodeObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNodeObservabilityService.class);
    private final TaskTraceService taskTraceService;

    public WorkflowNodeObservabilityService(TaskTraceService taskTraceService) {
        this.taskTraceService = taskTraceService;
    }

    /**
     * 创建节点观测作用域并立即记录 started 事件，避免进程异常退出后完全没有节点现场。
     */
    public NodeTraceScope start(ExecutionContext context,
                                RegistryWorkflowSnapshot workflow,
                                JsonNode node,
                                String nodeId,
                                String nodeType,
                                Long parentTaskStepId,
                                Long operatorId,
                                Map<String, Object> runtimeVariables,
                                List<?> previousNodeResults) {
        LocalDateTime startedAt = LocalDateTime.now();
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("nodeDefinition", node);
        input.put("runtimeVariables", runtimeVariables);
        input.put("previousNodeResults", previousNodeResults);
        LinkedHashMap<String, Object> metadata = baseMetadata(workflow, node, nodeId, nodeType, parentTaskStepId);
        safeRecord(
                context.taskContext().taskId(), context.taskContext().traceId(), parentTaskStepId,
                "workflow_node_started", spanName(workflow, node, nodeId), "started",
                input, Map.of(), null, startedAt, startedAt, metadata, safeOperatorId(operatorId)
        );
        return new NodeTraceScope(context, workflow, node, nodeId, nodeType, parentTaskStepId,
                safeOperatorId(operatorId), startedAt, input, metadata);
    }

    private LinkedHashMap<String, Object> baseMetadata(RegistryWorkflowSnapshot workflow,
                                                        JsonNode node,
                                                        String nodeId,
                                                        String nodeType,
                                                        Long parentTaskStepId) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", "workflow_node");
        metadata.put("workflowId", workflow.workflowId());
        metadata.put("workflowCode", workflow.workflowCode());
        metadata.put("workflowName", workflow.workflowName());
        metadata.put("nodeId", nodeId);
        metadata.put("nodeType", nodeType);
        metadata.put("nodeName", node.path("name").asText("Workflow节点"));
        metadata.put("parentTaskStepId", parentTaskStepId);
        String toolCode = node.path("toolCode").asText("");
        if (!toolCode.isBlank()) metadata.put("toolCode", toolCode);
        return metadata;
    }

    private String spanName(RegistryWorkflowSnapshot workflow, JsonNode node, String nodeId) {
        return "workflow:" + workflow.workflowCode() + "/" + nodeId + ":" + node.path("name").asText("Workflow节点");
    }

    private Long safeOperatorId(Long operatorId) {
        return operatorId == null ? 0L : operatorId;
    }

    private void safeRecord(Long taskId,
                            String traceId,
                            Long taskStepId,
                            String eventType,
                            String spanName,
                            String status,
                            Object input,
                            Object output,
                            String error,
                            LocalDateTime startedAt,
                            LocalDateTime finishedAt,
                            Object metadata,
                            Long operatorId) {
        try {
            taskTraceService.recordSpan(taskId, traceId, taskStepId, eventType, spanName, status,
                    input, output, error, startedAt, finishedAt, metadata, operatorId);
        } catch (RuntimeException exception) {
            // 观测降级不能反向中断业务执行，错误仍进入应用日志供运维排查。
            log.error("Workflow 节点 Trace 写入失败, taskId={}, traceId={}, eventType={}, message={}",
                    taskId, traceId, eventType, exception.getMessage(), exception);
        }
    }

    /** 节点生命周期作用域。 */
    public final class NodeTraceScope {
        private final ExecutionContext context;
        private final RegistryWorkflowSnapshot workflow;
        private final JsonNode node;
        private final String nodeId;
        private final String nodeType;
        private final Long parentTaskStepId;
        private final Long operatorId;
        private final LocalDateTime startedAt;
        private final Map<String, Object> input;
        private final Map<String, Object> metadata;
        private boolean terminal;

        private NodeTraceScope(ExecutionContext context,
                               RegistryWorkflowSnapshot workflow,
                               JsonNode node,
                               String nodeId,
                               String nodeType,
                               Long parentTaskStepId,
                               Long operatorId,
                               LocalDateTime startedAt,
                               Map<String, Object> input,
                               Map<String, Object> metadata) {
            this.context = context;
            this.workflow = workflow;
            this.node = node;
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.parentTaskStepId = parentTaskStepId;
            this.operatorId = operatorId;
            this.startedAt = startedAt;
            this.input = input;
            this.metadata = metadata;
        }

        /** 记录审批挂起。 */
        public void waiting(Object output) {
            finish("workflow_node_waiting", "waiting_approval", output, null);
        }

        /** 记录节点失败。 */
        public void failed(Throwable throwable) {
            finish("workflow_node_failed", "failed", Map.of(), throwable == null ? null : throwable.getMessage());
        }

        /** finally 中统一补成功记录，确保 continue/return 都不会漏记。 */
        public void succeedIfNecessary(Object output) {
            if (!terminal) finish("workflow_node_completed", "succeeded", output == null ? Map.of() : output, null);
        }

        private void finish(String eventType, String status, Object output, String errorMessage) {
            if (terminal) return;
            terminal = true;
            LinkedHashMap<String, Object> finalMetadata = new LinkedHashMap<>(metadata);
            finalMetadata.put("terminalEvent", eventType);
            finalMetadata.put("nodeType", nodeType);
            safeRecord(
                    context.taskContext().taskId(), context.taskContext().traceId(), parentTaskStepId,
                    eventType, spanName(workflow, node, nodeId), status,
                    input, output, errorMessage, startedAt, LocalDateTime.now(), finalMetadata, operatorId
            );
        }
    }
}

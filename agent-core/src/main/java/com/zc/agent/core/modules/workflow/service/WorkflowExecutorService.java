package com.zc.agent.core.modules.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.modules.conversation.client.RegistryAgentClient;
import com.zc.agent.core.modules.memory.service.MemoryInjectionService;
import com.zc.agent.core.modules.runtime.capability.CapabilityApprovalContext;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionCommand;
import com.zc.agent.core.modules.runtime.model.RuntimeStepExecutionOutcome;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import com.zc.agent.core.modules.runtime.service.ConditionStepService;
import com.zc.agent.core.modules.runtime.service.ExecutionStepHandlerRegistry;
import com.zc.agent.core.modules.runtime.service.ExecutionResponseAssemblerService;
import com.zc.agent.core.modules.runtime.service.ExecutionInputPolicyService;
import com.zc.agent.core.modules.runtime.service.GuardrailPolicyService;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.service.ToolRouterService;
import com.zc.agent.core.modules.workflow.model.RegistryWorkflowSnapshot;
import com.zc.agent.core.modules.workflow.model.WorkflowExecutionResult;
import com.zc.agent.core.modules.workflow.model.WorkflowNodeExecutionContext;
import com.zc.agent.core.modules.workflow.model.WorkflowResumeContext;
import com.zc.agent.core.modules.workflow.model.WorkflowRuntimeVariables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workflow 执行服务。
 */
@Service
public class WorkflowExecutorService {

    private static final int MAX_NODE_EXECUTION_COUNT = 20;
    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutorService.class);
    private final RegistryAgentClient registryAgentClient;
    private final ToolRouterService toolRouterService;
    private final MemoryInjectionService memoryInjectionService;
    private final ConditionStepService conditionStepService;
    private final ExecutionStepHandlerRegistry executionStepHandlerRegistry;
    private final ExecutionResponseAssemblerService executionResponseAssemblerService;
    private final ExecutionInputPolicyService executionInputPolicyService;
    private final GuardrailPolicyService guardrailPolicyService;
    private final WorkflowRecoveryService workflowRecoveryService;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowResultAssemblerService workflowResultAssemblerService;
    private final WorkflowNodeObservabilityService workflowNodeObservabilityService;
    private final ObjectMapper objectMapper;

    public WorkflowExecutorService(RegistryAgentClient registryAgentClient,
                                   ToolRouterService toolRouterService,
                                   MemoryInjectionService memoryInjectionService,
                                   ConditionStepService conditionStepService,
                                   ExecutionStepHandlerRegistry executionStepHandlerRegistry,
                                   ExecutionResponseAssemblerService executionResponseAssemblerService,
                                   ExecutionInputPolicyService executionInputPolicyService,
                                   GuardrailPolicyService guardrailPolicyService,
                                   WorkflowRecoveryService workflowRecoveryService,
                                   WorkflowGraphNavigator graphNavigator,
                                   WorkflowResultAssemblerService workflowResultAssemblerService,
                                   WorkflowNodeObservabilityService workflowNodeObservabilityService,
                                   ObjectMapper objectMapper) {
        this.registryAgentClient = registryAgentClient;
        this.toolRouterService = toolRouterService;
        this.memoryInjectionService = memoryInjectionService;
        this.conditionStepService = conditionStepService;
        this.executionStepHandlerRegistry = executionStepHandlerRegistry;
        this.executionResponseAssemblerService = executionResponseAssemblerService;
        this.executionInputPolicyService = executionInputPolicyService;
        this.guardrailPolicyService = guardrailPolicyService;
        this.workflowRecoveryService = workflowRecoveryService;
        this.graphNavigator = graphNavigator;
        this.workflowResultAssemblerService = workflowResultAssemblerService;
        this.workflowNodeObservabilityService = workflowNodeObservabilityService;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行最小 Workflow 骨架。
     *
     * @param executionContext 运行时上下文
     * @param planStep         计划步骤
     * @param authorization    原始 Authorization 请求头
     * @return Workflow 执行结果
     */
    public WorkflowExecutionResult execute(ExecutionContext executionContext,
                                           ExecutionPlan.ExecutionPlanStep planStep,
                                           String authorization) {
        return execute(executionContext, planStep, authorization, null, null, 0L);
    }

    /**
     * 执行 Workflow，并可从指定节点恢复。
     *
     * @param executionContext 运行时上下文
     * @param planStep         计划步骤
     * @param authorization    原始 Authorization 请求头
     * @param resumeContext    Workflow 恢复上下文
     * @return Workflow 执行结果
     */
    public WorkflowExecutionResult execute(ExecutionContext executionContext,
                                           ExecutionPlan.ExecutionPlanStep planStep,
                                           String authorization,
                                           WorkflowResumeContext resumeContext) {
        return execute(executionContext, planStep, authorization, resumeContext, null, 0L);
    }

    /**
     * 执行 Workflow，并将内部节点 Trace 关联到父计划步骤。
     */
    public WorkflowExecutionResult execute(ExecutionContext executionContext,
                                           ExecutionPlan.ExecutionPlanStep planStep,
                                           String authorization,
                                           WorkflowResumeContext resumeContext,
                                           Long parentTaskStepId,
                                           Long operatorId) {
        if (planStep.targetWorkflowId() == null) {
            throw new BusinessException("Workflow 执行缺少 workflowId");
        }
        RegistryWorkflowSnapshot workflowSnapshot = registryAgentClient.getWorkflowSnapshot(planStep.targetWorkflowId(), authorization);
        workflowRecoveryService.validateResumeContext(workflowSnapshot, resumeContext);
        List<Map<String, Object>> executedNodes = resumeContext == null
                ? new ArrayList<>()
                : new ArrayList<>(resumeContext.executedNodes());
        List<WorkflowNodeResultContext> previousNodeResults = resumeContext == null
                ? new ArrayList<>()
                : new ArrayList<>(resumeContext.previousNodes().stream()
                .map(item -> new WorkflowNodeResultContext(
                        item.nodeId(),
                        item.nodeType(),
                        item.summary(),
                        item.responseText(),
                        null
                ))
                .toList());
        String startNodeId = graphNavigator.startNodeId(workflowSnapshot.definitionJson());
        String nextNodeId = resumeContext == null
                ? (startNodeId == null ? null : graphNavigator.next(workflowSnapshot.definitionJson(), startNodeId))
                : resumeContext.resumeNodeId();
        if (nextNodeId == null) {
            throw buildWorkflowExecutionException(startNodeId == null ? "start" : startNodeId, "start", executedNodes, previousNodeResults,
                    new IllegalStateException("Workflow start 节点未配置可执行出口"));
        }
        String responseText = null;
        String summary = "当前 Workflow 未产出明确结果，返回最小 Workflow 响应。";
        int executionCount = 0;
        WorkflowRuntimeVariables runtimeVariables = initRuntimeVariables(workflowSnapshot, executionContext, resumeContext, previousNodeResults);
        while (nextNodeId != null) {
            executionCount++;
            if (executionCount > MAX_NODE_EXECUTION_COUNT) {
                throw buildWorkflowExecutionException(nextNodeId, "unknown", executedNodes, previousNodeResults,
                        new IllegalStateException("Workflow 节点执行次数超过限制，疑似存在循环"));
            }
            JsonNode nextNode = graphNavigator.node(workflowSnapshot.definitionJson(), nextNodeId);
            if (nextNode == null) {
                throw buildWorkflowExecutionException(nextNodeId, "missing", executedNodes, previousNodeResults,
                        new IllegalStateException("Workflow 目标节点不存在: " + nextNodeId));
            }
            String nodeType = nextNode.path("type").asText();
            try {
                ExecutionStepType.requireWorkflowType(nodeType);
            } catch (IllegalArgumentException exception) {
                throw buildWorkflowExecutionException(nextNodeId, nodeType, executedNodes, previousNodeResults, exception);
            }
            int executedNodeCountBefore = executedNodes.size();
            WorkflowNodeObservabilityService.NodeTraceScope nodeTrace = workflowNodeObservabilityService.start(
                    executionContext,
                    workflowSnapshot,
                    nextNode,
                    nextNodeId,
                    nodeType,
                    parentTaskStepId,
                    operatorId,
                    runtimeVariables.snapshot(),
                    previousNodeResults
            );
            try {
            if ("tool".equals(nodeType)) {
                ToolExecutionResult toolExecutionResult;
                try {
                    toolExecutionResult = executeToolNode(
                            executionContext,
                            workflowSnapshot,
                            nextNode,
                            nextNodeId,
                            executedNodes,
                            previousNodeResults,
                            runtimeVariables
                    );
                } catch (ApprovalRequiredException exception) {
                    if (!"capability_policy".equals(exception.approvalSourceType())) {
                        throw exception;
                    }
                    throw new ApprovalRequiredException(
                            exception.getMessage(),
                            exception.approvalNodeId(),
                            exception.approvalNodeName(),
                            nextNodeId,
                            new ArrayList<>(executedNodes),
                            previousNodeResults.stream().map(item -> {
                                Map<String, Object> result = new LinkedHashMap<>();
                                result.put("nodeId", item.nodeId());
                                result.put("nodeType", item.nodeType());
                                result.put("summary", item.summary());
                                result.put("responseText", item.responseText());
                                result.put("outputSnapshot", item.outputSnapshot());
                                return result;
                            }).toList(),
                            "workflow_capability_policy",
                            null,
                            exception.approvalMetadata()
                    );
                } catch (Exception exception) {
                    log.error("Workflow Tool 节点执行失败, workflowCode={}, nodeId={}, nodeType={}, message={}",
                            workflowSnapshot.workflowCode(),
                            nextNodeId,
                            nodeType,
                            exception.getMessage(),
                            exception);
                    throw buildWorkflowExecutionException(nextNodeId, nodeType, executedNodes, previousNodeResults, exception);
                }
                responseText = toolExecutionResult.responseText();
                summary = "已按 Workflow 节点顺序执行 Tool";
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Tool节点"))
                        .put("lastToolCode", toolExecutionResult.toolCode())
                        .put("lastResponseText", toolExecutionResult.responseText())
                        .put("lastSummary", toolExecutionResult.summary());
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        toolExecutionResult.summary(),
                        toolExecutionResult.responseText(),
                        toolExecutionResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("condition".equals(nodeType)) {
                WorkflowNodeExecutionContext nodeExecutionContext = buildWorkflowNodeExecutionContext(
                        executionContext,
                        workflowSnapshot.workflowId(),
                        nextNode,
                        nextNodeId,
                        previousNodeResults,
                        runtimeVariables,
                        null
                );
                boolean matched;
                try {
                    matched = conditionStepService.evaluateWorkflowCondition(nextNode, executionContext, nodeExecutionContext.executionInputText());
                } catch (Exception exception) {
                    log.error("Workflow 条件节点执行失败, workflowCode={}, nodeId={}, nodeType={}, message={}",
                            workflowSnapshot.workflowCode(),
                            nextNodeId,
                            nodeType,
                            exception.getMessage(),
                            exception);
                    throw buildWorkflowExecutionException(nextNodeId, nodeType, executedNodes, previousNodeResults, exception);
                }
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("条件节点"))
                        .put("lastConditionMatched", matched)
                        .put("lastSummary", matched ? "条件分支命中 true" : "条件分支命中 false");
                Map<String, Object> conditionSnapshot = new LinkedHashMap<>();
                conditionSnapshot.put("nodeId", nextNodeId);
                conditionSnapshot.put("nodeType", nodeType);
                conditionSnapshot.put("nodeName", nextNode.path("name").asText("条件节点"));
                conditionSnapshot.put("matched", matched);
                conditionSnapshot.put("memoryStrategy", nodeExecutionContext.memoryStrategy());
                conditionSnapshot.put("selectedMemoryItems", nodeExecutionContext.selectedMemoryItems());
                conditionSnapshot.put("conditionInputText", nodeExecutionContext.executionInputText());
                conditionSnapshot.put("toolInputObservation", executionInputPolicyService.buildWorkflowNodeInputObservation(
                        executionContext, nodeType, null,
                        nodeExecutionContext.executionInputText(), nodeExecutionContext.executionInputText()));
                conditionSnapshot.put("resolvedInputText", nodeExecutionContext.inputText());
                conditionSnapshot.put("previousNodeResults", nodeExecutionContext.previousNodeResults());
                conditionSnapshot.put("runtimeVariables", nodeExecutionContext.runtimeVariables());
                executedNodes.add(conditionSnapshot);
                summary = "已按 Workflow 条件节点完成分支判断";
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        matched ? "条件分支命中 true" : "条件分支命中 false",
                        nodeExecutionContext.executionInputText(),
                        toJsonText(conditionSnapshot)
                ));
                nextNodeId = graphNavigator.conditionNext(workflowSnapshot.definitionJson(), nextNodeId, matched);
                if (nextNodeId == null) {
                    throw buildWorkflowExecutionException(conditionSnapshot.get("nodeId").toString(), nodeType,
                            executedNodes, previousNodeResults,
                            new IllegalStateException("Workflow 条件节点未配置匹配分支: " + matched));
                }
                continue;
            }
            if ("reply".equals(nodeType)) {
                ExecutionResponseAssemblerService.StructuredStepResult replyResult = executionResponseAssemblerService.buildWorkflowReplyNodeResult(
                        executionContext,
                        workflowSnapshot.workflowName(),
                        nextNode.path("name").asText("Reply节点"),
                        resolveNodeObjective(nextNode),
                        toStepResultContexts(previousNodeResults)
                );
                responseText = replyResult.assistantMessage();
                summary = replyResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Reply节点"))
                        .put("lastResponseText", replyResult.assistantMessage())
                        .put("lastSummary", replyResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        replyResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        replyResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("clarify".equals(nodeType)) {
                RuntimeStepExecutionOutcome clarifyResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = clarifyResult.responseText();
                summary = clarifyResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Clarify节点"))
                        .put("lastResponseText", clarifyResult.responseText())
                        .put("lastSummary", clarifyResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        clarifyResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        clarifyResult.outputSnapshot()
                ));
                return workflowResultAssemblerService.assemble(workflowSnapshot, executedNodes, summary, responseText, resumeContext);
            }
            if ("judge".equals(nodeType)) {
                RuntimeStepExecutionOutcome judgeResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = judgeResult.responseText();
                summary = judgeResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Judge节点"))
                        .put("lastResponseText", judgeResult.responseText())
                        .put("lastSummary", judgeResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        judgeResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        judgeResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("parallel".equals(nodeType)) {
                RuntimeStepExecutionOutcome parallelResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = parallelResult.responseText();
                summary = parallelResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Parallel节点"))
                        .put("lastResponseText", parallelResult.responseText())
                        .put("lastSummary", parallelResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        parallelResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        parallelResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("join".equals(nodeType)) {
                RuntimeStepExecutionOutcome joinResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = joinResult.responseText();
                summary = joinResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Join节点"))
                        .put("lastResponseText", joinResult.responseText())
                        .put("lastSummary", joinResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        joinResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        joinResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("reflection".equals(nodeType)) {
                ExecutionResponseAssemblerService.StructuredStepResult reflectionResult = executionResponseAssemblerService.buildWorkflowReflectionNodeResult(
                        executionContext,
                        workflowSnapshot.workflowName(),
                        nextNode.path("name").asText("Reflection节点"),
                        resolveNodeObjective(nextNode),
                        jsonNodeToMap(nextNode),
                        toStepResultContexts(previousNodeResults)
                );
                responseText = reflectionResult.assistantMessage();
                summary = reflectionResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Reflection节点"))
                        .put("lastResponseText", reflectionResult.assistantMessage())
                        .put("lastSummary", reflectionResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        reflectionResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        reflectionResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("handoff".equals(nodeType)) {
                RuntimeStepExecutionOutcome delegationExecutionResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = delegationExecutionResult.responseText();
                summary = delegationExecutionResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("委派节点"))
                        .put("lastResponseText", delegationExecutionResult.responseText())
                        .put("lastSummary", delegationExecutionResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        delegationExecutionResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        toJsonText(nodeSnapshot)
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("wait".equals(nodeType)) {
                RuntimeStepExecutionOutcome waitResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = waitResult.responseText();
                summary = waitResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("Wait节点"))
                        .put("lastResponseText", waitResult.responseText())
                        .put("lastSummary", waitResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        waitResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        waitResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("memory_write".equals(nodeType)) {
                RuntimeStepExecutionOutcome memoryResult = executeSharedNode(
                        executionContext, workflowSnapshot, nextNode, nextNodeId, nodeType, previousNodeResults, authorization);
                responseText = memoryResult.responseText();
                summary = memoryResult.summary();
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("MemoryWrite节点"))
                        .put("lastResponseText", memoryResult.responseText())
                        .put("lastSummary", memoryResult.summary());
                Map<String, Object> nodeSnapshot = buildAgenticNodeSnapshot(
                        nextNode,
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        memoryResult.outputSnapshot(),
                        runtimeVariables,
                        previousNodeResults
                );
                executedNodes.add(nodeSnapshot);
                previousNodeResults.add(new WorkflowNodeResultContext(
                        nextNodeId,
                        nodeType,
                        summary,
                        responseText,
                        memoryResult.outputSnapshot()
                ));
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            if ("approval".equals(nodeType)) {
                String nextAfterApprovalNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                ArrayList<Map<String, Object>> approvalExecutedNodes = new ArrayList<>(executedNodes);
                LinkedHashMap<String, Object> approvalSnapshot = new LinkedHashMap<>();
                approvalSnapshot.put("nodeId", nextNodeId);
                approvalSnapshot.put("nodeType", nodeType);
                approvalSnapshot.put("nodeName", nextNode.path("name").asText("审批节点"));
                approvalSnapshot.put("status", "waiting_approval");
                approvalSnapshot.put("runtimeVariables", runtimeVariables.snapshot());
                approvalExecutedNodes.add(approvalSnapshot);
                throw new ApprovalRequiredException(
                        "当前 Workflow 等待审批",
                        nextNodeId,
                        nextNode.path("name").asText("审批节点"),
                        nextAfterApprovalNodeId,
                        approvalExecutedNodes,
                        previousNodeResults.stream()
                                .map(item -> {
                                    Map<String, Object> nodeResult = new HashMap<>();
                                    nodeResult.put("nodeId", item.nodeId());
                                    nodeResult.put("nodeType", item.nodeType());
                                    nodeResult.put("summary", Optional.ofNullable(item.summary()).orElse(""));
                                    nodeResult.put("responseText", Optional.ofNullable(item.responseText()).orElse(""));
                                    nodeResult.put("outputSnapshot", Optional.ofNullable(item.outputSnapshot()).orElse(""));
                                    return nodeResult;
                                })
                                .toList()
                );
            }
            if ("end".equals(nodeType)) {
                return workflowResultAssemblerService.assemble(workflowSnapshot, executedNodes, summary, responseText, resumeContext);
            }
            if ("start".equals(nodeType)) {
                runtimeVariables.put("lastNodeId", nextNodeId)
                        .put("lastNodeType", nodeType)
                        .put("lastNodeName", nextNode.path("name").asText("开始节点"));
                LinkedHashMap<String, Object> startSnapshot = new LinkedHashMap<>();
                startSnapshot.put("nodeId", nextNodeId);
                startSnapshot.put("nodeType", nodeType);
                startSnapshot.put("nodeName", nextNode.path("name").asText("开始节点"));
                startSnapshot.put("runtimeVariables", runtimeVariables.snapshot());
                executedNodes.add(startSnapshot);
                nextNodeId = graphNavigator.next(workflowSnapshot.definitionJson(), nextNodeId);
                continue;
            }
            throw buildWorkflowExecutionException(nextNodeId, nodeType, executedNodes, previousNodeResults,
                    new IllegalStateException("Workflow 节点类型没有可执行处理器: " + nodeType));
            } catch (ApprovalRequiredException exception) {
                nodeTrace.waiting(Map.of(
                        "approvalNodeId", exception.approvalNodeId(),
                        "approvalNodeName", exception.approvalNodeName(),
                        "nextNodeId", exception.nextNodeId() == null ? "" : exception.nextNodeId()
                ));
                throw exception;
            } catch (WorkflowExecutionException exception) {
                nodeTrace.failed(exception);
                throw exception;
            } catch (Exception exception) {
                nodeTrace.failed(exception);
                log.error("Workflow 节点执行失败, workflowCode={}, nodeId={}, nodeType={}, message={}",
                        workflowSnapshot.workflowCode(), nextNodeId, nodeType, exception.getMessage(), exception);
                throw buildWorkflowExecutionException(nextNodeId, nodeType, executedNodes, previousNodeResults, exception);
            } finally {
                Object output = executedNodes.size() > executedNodeCountBefore
                        ? executedNodes.get(executedNodes.size() - 1)
                        : Map.of("runtimeVariables", runtimeVariables.snapshot());
                nodeTrace.succeedIfNecessary(output);
            }
        }
        throw buildWorkflowExecutionException("end", "end", executedNodes, previousNodeResults,
                new IllegalStateException("Workflow 未到达 end 节点即终止"));
    }

    private WorkflowExecutionException buildWorkflowExecutionException(String failedNodeId,
                                                                      String failedNodeType,
                                                                      List<Map<String, Object>> executedNodes,
                                                                      List<WorkflowNodeResultContext> previousNodeResults,
                                                                      Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return new WorkflowExecutionException(
                message,
                exception,
                failedNodeId,
                failedNodeType,
                new ArrayList<>(executedNodes),
                previousNodeResults.stream()
                        .map(item -> {
                            Map<String, Object> nodeResult = new LinkedHashMap<>();
                            nodeResult.put("nodeId", item.nodeId());
                            nodeResult.put("nodeType", item.nodeType());
                            nodeResult.put("summary", item.summary());
                            nodeResult.put("responseText", item.responseText());
                            nodeResult.put("outputSnapshot", item.outputSnapshot());
                            return nodeResult;
                        })
                        .toList()
        );
    }

    /**
     * Tool 节点执行。
     *
     * @param executionContext 运行时上下文
     * @param workflowSnapshot Workflow 快照
     * @param node             当前节点
     * @param nodeId           当前节点 ID
     * @param executedNodes    已执行节点记录
     * @param previousNodeResults 前序节点结果
     * @return Tool 执行结果
     */
    private ToolExecutionResult executeToolNode(ExecutionContext executionContext,
                                                RegistryWorkflowSnapshot workflowSnapshot,
                                                JsonNode node,
                                                String nodeId,
                                                List<Map<String, Object>> executedNodes,
                                                List<WorkflowNodeResultContext> previousNodeResults,
                                                WorkflowRuntimeVariables runtimeVariables) {
        String toolCode = node.path("toolCode").asText(null);
        RegistryAgentTool targetTool = executionContext.availableTools().stream()
                .filter(item -> toolCode != null && toolCode.equals(item.toolCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Workflow 指定的 Tool 当前不可用"));
        WorkflowNodeExecutionContext nodeExecutionContext = buildWorkflowNodeExecutionContext(
                executionContext,
                workflowSnapshot.workflowId(),
                node,
                nodeId,
                previousNodeResults,
                runtimeVariables,
                targetTool
        );
        String toolExecutionInputText = executionInputPolicyService.clampWorkflowToolExecutionInput(
                targetTool,
                nodeExecutionContext.executionInputText()
        );
        Map<String, Object> inputObservation = executionInputPolicyService.buildWorkflowNodeInputObservation(
                executionContext,
                "tool",
                targetTool,
                nodeExecutionContext.executionInputText(),
                toolExecutionInputText
        );
        if (!CapabilityApprovalContext.hasApprovedCapability()) {
            guardrailPolicyService.validateWorkflowToolNode(
                    executionContext,
                    targetTool,
                    nodeId,
                    node.path("name").asText("工具审批 - " + targetTool.toolName()),
                    toolExecutionInputText
            );
        }
        Optional<ToolExecutionResult> toolExecutionResult = toolRouterService.execute(
                executionContext,
                toolCode,
                toolExecutionInputText,
                Map.of("inputText", toolExecutionInputText)
        );
        if (toolExecutionResult.isEmpty()) {
            throw new BusinessException("Workflow 指定的 Tool 当前不可用");
        }
        ToolExecutionResult result = toolExecutionResult.get();
        Map<String, Object> nodeSnapshot = new LinkedHashMap<>();
        nodeSnapshot.put("nodeId", nodeId);
        nodeSnapshot.put("nodeType", "tool");
        nodeSnapshot.put("nodeName", node.path("name").asText("Tool节点"));
        nodeSnapshot.put("toolCode", result.toolCode());
        nodeSnapshot.put("toolName", result.toolName());
        nodeSnapshot.put("providerType", targetTool.providerType());
        nodeSnapshot.put("transportType", targetTool.transportType());
        nodeSnapshot.put("operationName", targetTool.operationName());
        nodeSnapshot.put("summary", result.summary());
        nodeSnapshot.put("memoryStrategy", nodeExecutionContext.memoryStrategy());
        nodeSnapshot.put("selectedMemoryItems", nodeExecutionContext.selectedMemoryItems());
        nodeSnapshot.put("toolInputText", toolExecutionInputText);
        nodeSnapshot.put("toolInputObservation", inputObservation);
        nodeSnapshot.put("resolvedInputText", nodeExecutionContext.inputText());
        nodeSnapshot.put("previousNodeResults", nodeExecutionContext.previousNodeResults());
        nodeSnapshot.put("runtimeVariables", nodeExecutionContext.runtimeVariables());
        nodeSnapshot.put("responseText", result.responseText());
        nodeSnapshot.put("delegatedOutputSnapshot", safeJson(result.outputSnapshot()));
        executedNodes.add(nodeSnapshot);
        return result;
    }

    private WorkflowNodeExecutionContext buildWorkflowNodeExecutionContext(ExecutionContext executionContext,
                                                                          Long workflowId,
                                                                          JsonNode node,
                                                                          String nodeId,
                                                                          List<WorkflowNodeResultContext> previousNodeResults,
                                                                          WorkflowRuntimeVariables runtimeVariables,
                                                                          RegistryAgentTool targetTool) {
        MemoryInjectionService.ResolvedMemoryInput resolvedWorkflowInput = memoryInjectionService.resolveWorkflowInput(
                executionContext,
                workflowId,
                previousNodeResults.stream()
                        .map(item -> item.nodeId() + " [" + item.nodeType() + "] " + item.summary())
                        .toList()
        );
        List<Map<String, Object>> previousNodeSnapshots = previousNodeResults.stream()
                .map(item -> Map.<String, Object>of(
                        "nodeId", item.nodeId(),
                        "nodeType", item.nodeType(),
                        "summary", item.summary(),
                        "responseText", item.responseText(),
                        "outputSnapshot", item.outputSnapshot()
                ))
                .toList();
        return new WorkflowNodeExecutionContext(
                nodeId,
                node.path("type").asText(),
                node.path("name").asText("Workflow节点"),
                resolvedWorkflowInput.resolvedInputText(),
                buildExecutionInputText(executionContext, previousNodeResults, targetTool),
                resolvedWorkflowInput.memoryStrategy(),
                resolvedWorkflowInput.selectedMemoryItems(),
                previousNodeSnapshots,
                runtimeVariables.snapshot()
        );
    }

    private String buildExecutionInputText(ExecutionContext executionContext,
                                           List<WorkflowNodeResultContext> previousNodeResults,
                                           RegistryAgentTool targetTool) {
        List<String> summaries = previousNodeResults.stream()
                .map(WorkflowNodeResultContext::summary)
                .filter(item -> item != null && !item.isBlank())
                .toList();
        if (targetTool != null) {
            return executionInputPolicyService.buildWorkflowToolExecutionInput(executionContext, targetTool, summaries);
        }
        return executionInputPolicyService.buildWorkflowControlExecutionInput(executionContext, summaries);
    }

    /**
     * 将 Workflow 节点适配为公共步骤契约执行，Workflow 本身仍负责节点路由和运行变量回写。
     */
    private RuntimeStepExecutionOutcome executeSharedNode(ExecutionContext executionContext,
                                                           RegistryWorkflowSnapshot workflowSnapshot,
                                                           JsonNode node,
                                                           String nodeId,
                                                           String nodeType,
                                                           List<WorkflowNodeResultContext> previousNodeResults,
                                                           String authorization) {
        ExecutionPlan.ExecutionPlanStep syntheticStep = buildWorkflowSyntheticStep(
                node,
                nodeId,
                nodeType,
                workflowSnapshot.workflowId()
        );
        String executionInputText = buildExecutionInputText(executionContext, previousNodeResults, null);
        Map<String, Object> inputObservation = executionInputPolicyService.buildWorkflowNodeInputObservation(
                executionContext, nodeType, null, executionInputText, executionInputText);
        return executionStepHandlerRegistry.execute(new RuntimeStepExecutionCommand(
                executionContext,
                null,
                null,
                syntheticStep,
                toStepResultContexts(previousNodeResults),
                executionInputText,
                inputObservation,
                authorization,
                null,
                0L
        ));
    }

    private List<RuntimeStepResultContext> toStepResultContexts(List<WorkflowNodeResultContext> previousNodeResults) {
        List<RuntimeStepResultContext> contexts = new ArrayList<>();
        for (int index = 0; index < previousNodeResults.size(); index++) {
            WorkflowNodeResultContext item = previousNodeResults.get(index);
            contexts.add(new RuntimeStepResultContext(
                    index + 1,
                    item.nodeId(),
                    item.nodeType(),
                    item.nodeId(),
                    item.summary(),
                    item.responseText(),
                    item.outputSnapshot()
            ));
        }
        return contexts;
    }

    private Map<String, Object> buildAgenticNodeSnapshot(JsonNode node,
                                                         String nodeId,
                                                         String nodeType,
                                                         String summary,
                                                         String responseText,
                                                         String outputSnapshot,
                                                         WorkflowRuntimeVariables runtimeVariables,
                                                         List<WorkflowNodeResultContext> previousNodeResults) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeId", nodeId);
        snapshot.put("nodeType", nodeType);
        snapshot.put("nodeName", node.path("name").asText("Workflow节点"));
        snapshot.put("summary", summary);
        snapshot.put("responseText", responseText);
        snapshot.put("runtimeVariables", runtimeVariables.snapshot());
        snapshot.put("previousNodeResults", previousNodeResults.stream()
                .map(item -> Map.<String, Object>of(
                        "nodeId", item.nodeId(),
                        "nodeType", item.nodeType(),
                        "summary", item.summary(),
                        "responseText", item.responseText()
                ))
                .toList());
        StandardStepOutputSnapshot standardSnapshot = parseStandardSnapshot(outputSnapshot);
        if (standardSnapshot != null) {
            snapshot.put("observation", standardSnapshot.observation());
            snapshot.put("confidence", standardSnapshot.confidence());
            snapshot.put("needReplan", standardSnapshot.needReplan());
            snapshot.put("needMoreInfo", standardSnapshot.needMoreInfo());
            snapshot.put("nextActionHint", standardSnapshot.nextActionHint());
            snapshot.put("evidence", standardSnapshot.evidence());
            snapshot.put("outputSnapshot", safeJson(outputSnapshot));
        } else {
            snapshot.put("outputSnapshot", safeJson(outputSnapshot));
        }
        return snapshot;
    }

    private ExecutionPlan.ExecutionPlanStep buildWorkflowSyntheticStep(JsonNode node,
                                                                      String nodeId,
                                                                      String nodeType,
                                                                      Long workflowId) {
        Map<String, Object> inputs = jsonNodeToMap(node);
        inputs.remove("id");
        inputs.remove("type");
        inputs.remove("name");
        inputs.putIfAbsent("sourceWorkflowId", workflowId);
        return new ExecutionPlan.ExecutionPlanStep(
                0,
                nodeId,
                nodeType,
                node.path("name").asText("Workflow节点"),
                resolveNodeObjective(node),
                List.of(),
                null,
                null,
                inputs,
                resolveNodeObjective(node),
                "fail_fast",
                true,
                "draft",
                resolveNodeObjective(node)
        );
    }

    private String resolveNodeObjective(JsonNode node) {
        String objective = node.path("objective").asText("");
        if (objective == null || objective.isBlank()) {
            objective = node.path("description").asText("");
        }
        if (objective == null || objective.isBlank()) {
            objective = node.path("name").asText("Workflow节点执行");
        }
        return objective;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
    }

    private StandardStepOutputSnapshot parseStandardSnapshot(String outputSnapshot) {
        if (outputSnapshot == null || outputSnapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = objectMapper.readTree(outputSnapshot);
            if (!rootNode.isObject() || rootNode.path("resultType").asText("").isBlank()) {
                return null;
            }
            return objectMapper.treeToValue(rootNode, StandardStepOutputSnapshot.class);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 条件节点判断。
     *
     * 当前最小规则：
     * 1. `keywords` / `keyword`：命中任一关键词即返回 true
     * 2. `availableToolCode`：当前 Agent 可用 Tool 中包含指定编码则返回 true
     *
     * @param node             条件节点
     * @param executionContext 运行时上下文
     * @param conditionInputText 条件判断输入文本
     * @return 是否命中
     */
    private WorkflowRuntimeVariables initRuntimeVariables(RegistryWorkflowSnapshot workflowSnapshot,
                                                          ExecutionContext executionContext,
                                                          WorkflowResumeContext resumeContext,
                                                          List<WorkflowNodeResultContext> previousNodeResults) {
        WorkflowRuntimeVariables runtimeVariables = new WorkflowRuntimeVariables()
                .put("workflowCode", workflowSnapshot.workflowCode())
                .put("workflowName", workflowSnapshot.workflowName())
                .put("userMessage", executionContext.userMessage())
                .put("conversationState", executionContext.conversationStatePayload())
                .put("conversationStateText", executionContext.conversationStatePromptText())
                .put("availableToolCodes", executionContext.availableTools().stream()
                        .map(item -> item.toolCode())
                        .toList());
        if (resumeContext != null) {
            runtimeVariables.put("resumeMode", resumeContext.resumeMode())
                    .put("resumeNodeId", resumeContext.resumeNodeId())
                    .put("replayedNodeCount", previousNodeResults.size());
        }
        return runtimeVariables;
    }

    private Object safeJson(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", jsonText);
        }
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Workflow 执行结果序列化失败");
        }
    }

    /**
     * Workflow 前序节点结果上下文。
     *
     * @param nodeId       节点 ID
     * @param nodeType     节点类型
     * @param summary      节点摘要
     * @param responseText 节点响应文本
     */
    private record WorkflowNodeResultContext(
            String nodeId,
            String nodeType,
            String summary,
            String responseText,
            String outputSnapshot
    ) {
    }
}

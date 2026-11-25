package com.zc.agent.core.modules.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.core.modules.eval.service.EvalFeedbackService;
import com.zc.agent.core.modules.runtime.model.RuntimeStepResultContext;
import com.zc.agent.core.modules.runtime.model.StandardStepOutputSnapshot;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.model.ExecutionDecision;
import com.zc.agent.core.modules.runtime.model.ExecutionPlan;
import com.zc.agent.core.modules.runtime.model.ExecutionStepType;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.workflow.model.RegistryAgentWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 真正任务拆解 Planner。
 * 优先调用 LLM 生成结构化计划，失败时回退到规则降级计划。
 */
@Service
public class ExecutionPlannerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlannerService.class);
    private static final String PLAN_STATUS_DRAFT = "draft";

    private final ObjectMapper objectMapper;
    private final RuntimeLlmGatewayService runtimeLlmGatewayService;
    private final PlanValidatorService planValidatorService;
    private final ExecutionPlanService fallbackExecutionPlanService;
    private final EvalFeedbackService evalFeedbackService;
    private final PlanOutcomeFeedbackService planOutcomeFeedbackService;
    private final PromptContextBudgetService promptContextBudgetService;
    private final ContextCompactionService contextCompactionService;
    private final AgentExecutionPolicyResolver executionPolicyResolver;

    public ExecutionPlannerService(ObjectMapper objectMapper,
                                   RuntimeLlmGatewayService runtimeLlmGatewayService,
                                   PlanValidatorService planValidatorService,
                                   ExecutionPlanService fallbackExecutionPlanService,
                                   EvalFeedbackService evalFeedbackService,
                                   PlanOutcomeFeedbackService planOutcomeFeedbackService,
                                   PromptContextBudgetService promptContextBudgetService,
                                   ContextCompactionService contextCompactionService,
                                   AgentExecutionPolicyResolver executionPolicyResolver) {
        this.objectMapper = objectMapper;
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
        this.planValidatorService = planValidatorService;
        this.fallbackExecutionPlanService = fallbackExecutionPlanService;
        this.evalFeedbackService = evalFeedbackService;
        this.planOutcomeFeedbackService = planOutcomeFeedbackService;
        this.promptContextBudgetService = promptContextBudgetService;
        this.contextCompactionService = contextCompactionService;
        this.executionPolicyResolver = executionPolicyResolver;
    }

    /**
     * 根据执行上下文和决策构建执行计划。
     *
     * @param executionContext  执行上下文
     * @param executionDecision 决策结果
     * @return 执行计划
     */
    public ExecutionPlan buildPlan(ExecutionContext executionContext, ExecutionDecision executionDecision) {
        return buildPlan(executionContext, executionDecision, null, List.of());
    }

    /**
     * 根据执行上下文和决策构建执行计划。
     *
     * @param executionContext  执行上下文
     * @param executionDecision 决策结果
     * @param replanContext     重规划上下文
     * @return 执行计划
     */
    public ExecutionPlan buildPlan(ExecutionContext executionContext,
                                   ExecutionDecision executionDecision,
                                   ReplanContext replanContext) {
        return buildPlan(executionContext, executionDecision, replanContext, List.of());
    }

    public ExecutionPlan buildPlan(ExecutionContext executionContext,
                                   ExecutionDecision executionDecision,
                                   ReplanContext replanContext,
                                   List<PlanTemplateHint> templateHints) {
        if (!shouldUseLlmPlanner(executionContext, executionDecision, replanContext)) {
            return fallbackExecutionPlanService.buildPlan(executionContext, executionDecision);
        }
        try {
            PromptTracePackage promptTracePackage = buildPlanningPromptTracePackage(
                    executionContext,
                    executionDecision,
                    replanContext,
                    templateHints
            );
            String responseText = runtimeLlmGatewayService.chatCompletion(
                    promptTracePackage.systemPrompt(),
                    promptTracePackage.userPrompt(),
                    executionContext.modelForStage("planning")
            );
            ExecutionPlan rawPlan = parseLlmPlan(responseText, executionContext);
            ExecutionPlan validatedPlan = planValidatorService.validateAndNormalize(rawPlan, executionContext);
            return enrichPlan(validatedPlan, executionContext, executionDecision);
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            log.warn("LLM Planner 生成计划失败，回退最小计划：{}", exception.getMessage());
            return fallbackExecutionPlanService.buildPlan(executionContext, executionDecision);
        }
    }

    public Map<String, Object> buildPlanningTraceMetadata(ExecutionContext executionContext,
                                                          ExecutionDecision executionDecision,
                                                          ReplanContext replanContext,
                                                          List<PlanTemplateHint> templateHints) {
        PlanningStrategy strategy = resolvePlanningStrategy(executionContext, executionDecision, replanContext);
        if (!strategy.useLlmPlanner()) {
            return Map.of(
                    "planningStrategy", strategy.traceMetadata(),
                    "contextCompaction", Map.of("skipped", true, "reason", strategy.reason())
            );
        }
        try {
            return Map.of(
                    "contextCompaction", buildPlanningPromptTracePackage(executionContext, executionDecision, replanContext, templateHints).compactionMetadata(),
                    "planningStrategy", strategy.traceMetadata()
            );
        } catch (Exception exception) {
            log.warn("构建规划 Prompt Trace 元数据失败：{}", exception.getMessage());
            return Map.of(
                    "contextCompaction", contextCompactionService.compactStageContext(
                            executionContext, PromptContextBudgetService.PromptStage.PLANNING).metadata(),
                    "planningStrategy", strategy.traceMetadata()
            );
        }
    }

    private boolean shouldUseLlmPlanner(ExecutionContext executionContext,
                                        ExecutionDecision executionDecision,
                                        ReplanContext replanContext) {
        return resolvePlanningStrategy(executionContext, executionDecision, replanContext).useLlmPlanner();
    }

    PlanningStrategy resolvePlanningStrategy(ExecutionContext executionContext,
                                             ExecutionDecision executionDecision,
                                             ReplanContext replanContext) {
        if (!executionContext.hasRunnableModel("planning")) {
            return new PlanningStrategy(false, "model_unavailable");
        }
        if (planningPromptContent(executionContext) == null) {
            return new PlanningStrategy(false, "planning_prompt_unavailable");
        }
        if (replanContext != null) {
            return new PlanningStrategy(true, "replan_requires_planner");
        }
        AgentExecutionPolicyResolver.Policy policy = executionPolicyResolver.resolve(executionContext);
        if ("always_plan".equals(policy.planningMode())) {
            return new PlanningStrategy(true, "policy_always_plan");
        }
        if (policy.skipPlannerForDirectReply()
                && "direct_reply".equals(executionDecision.decisionType())
                && !executionDecision.hasToolSelection()
                && !executionDecision.hasWorkflowSelection()) {
            return new PlanningStrategy(false, "direct_reply_fast_path");
        }
        if (policy.skipPlannerForSingleWorkflow() && isSingleUserReplyWorkflow(executionContext, executionDecision)) {
            return new PlanningStrategy(false, "single_user_reply_workflow_fast_path");
        }
        return new PlanningStrategy(true, "complex_execution_requires_planner");
    }

    private boolean isSingleUserReplyWorkflow(ExecutionContext executionContext, ExecutionDecision executionDecision) {
        if (executionDecision.hasToolSelection() || executionDecision.selectedWorkflows() == null
                || executionDecision.selectedWorkflows().size() != 1) {
            return false;
        }
        Long selectedId = executionDecision.selectedWorkflows().get(0).workflowId();
        return executionContext.availableWorkflows().stream()
                .filter(workflow -> workflow.workflowId().equals(selectedId))
                .anyMatch(workflow -> "user_reply".equalsIgnoreCase(
                        workflow.metadata() == null ? null : workflow.metadata().path("routing").path("outputMode").asText(null)));
    }

    record PlanningStrategy(boolean useLlmPlanner, String reason) {
        Map<String, Object> traceMetadata() {
            return Map.of("useLlmPlanner", useLlmPlanner, "reason", reason);
        }
    }

    private ExecutionPlan parseLlmPlan(String responseText,
                                       ExecutionContext executionContext) throws JsonProcessingException {
        String normalizedText = runtimeLlmGatewayService.sanitizeJsonText(responseText);
        JsonNode rootNode = objectMapper.readTree(normalizedText);
        repairPlanJson(rootNode, executionContext);
        return objectMapper.treeToValue(rootNode, ExecutionPlan.class);
    }

    private void repairPlanJson(JsonNode rootNode, ExecutionContext executionContext) {
        if (!(rootNode instanceof ObjectNode objectNode)) {
            return;
        }
        JsonNode stepsNode = objectNode.path("steps");
        if (!(stepsNode instanceof ArrayNode stepsArray)) {
            return;
        }
        for (JsonNode stepNode : stepsArray) {
            if (!(stepNode instanceof ObjectNode stepObject)) {
                continue;
            }
            JsonNode inputsNode = stepObject.path("inputs");
            if (inputsNode.isArray()) {
                stepObject.set("inputs", objectMapper.createObjectNode());
            }
            repairWorkflowReference(stepObject, executionContext);
        }
    }

    private void repairWorkflowReference(ObjectNode stepObject, ExecutionContext executionContext) {
        if (!"workflow".equalsIgnoreCase(stepObject.path("stepType").asText(""))) return;
        JsonNode targetNode = stepObject.get("targetWorkflowId");
        if (targetNode == null || !targetNode.isTextual()) return;
        String reference = targetNode.asText("").trim();
        if (reference.isBlank() || reference.chars().allMatch(Character::isDigit)) return;
        executionContext.availableWorkflows().stream()
                .filter(workflow -> reference.equalsIgnoreCase(workflow.workflowCode())
                        || reference.equalsIgnoreCase(workflow.workflowName()))
                .findFirst()
                .ifPresent(workflow -> stepObject.put("targetWorkflowId", workflow.workflowId()));
    }

    private ExecutionPlan enrichPlan(ExecutionPlan executionPlan,
                                     ExecutionContext executionContext,
                                     ExecutionDecision executionDecision) {
        String planId = executionPlan.planId() == null || executionPlan.planId().isBlank()
                ? "PLAN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase()
                : executionPlan.planId();
        String planType = executionPlan.planType() == null || executionPlan.planType().isBlank()
                ? resolvePlanType(executionDecision)
                : executionPlan.planType();
        String goal = executionPlan.goal() == null || executionPlan.goal().isBlank()
                ? resolvePlanningGoal(executionContext)
                : executionPlan.goal();
        return new ExecutionPlan(
                planId,
                planType,
                goal,
                PLAN_STATUS_DRAFT,
                executionPlan.assumptions(),
                executionPlan.constraints(),
                executionPlan.risks(),
                executionPlan.steps()
        );
    }

    private String buildSystemPrompt(ExecutionContext executionContext,
                                     ExecutionDecision executionDecision,
                                     ReplanContext replanContext) {
        String planningPrompt = planningPromptContent(executionContext);
        String executableStepTypes = ExecutionStepType.planCodesForPrompt();
        return """
                你是 Agent 任务拆解 Planner。
                你的职责是把用户目标拆成可以顺序执行的计划步骤。
                必须遵守以下规则：
                1. 只能引用输入里给定的 selectedTools 和 selectedWorkflows，不能编造新的 Tool 编码或 Workflow ID。
                2. 当前执行器仅支持 %s，请不要输出其他 stepType。
                3. 步骤必须是有序数组，stepNo 从 1 开始连续递增。
                4. dependsOnStepIds 只能依赖前面已定义的步骤 ID。
                5. 输出必须是严格 JSON，不要 Markdown，不要解释，不要额外文本。
                6. reflection 只用于高风险结论、结果冲突、低置信度或明确要求复核的任务；不要仅为格式、完整性或一般回答追加 reflection。
                6.1 所有 reflection 步骤必须输出 failurePolicy="replan" 且 replannable=true，不能输出 continue 或 fail_fast。
                6.2 如果所选 Workflow 本身会产出可直接面向用户的完整回复，优先只执行该 Workflow，不要再追加 reflection、reply 或重复汇总步骤。
                6.3 一个计划只能有一个最终用户回复生成步骤，禁止 Workflow、reply 和汇总器重复生成同一答案。
                6.4 reply 只能基于前序步骤已经取得的事实生成最终答复，不能用文字建议代替尚未执行的 Tool、Workflow、查询或其他必要动作。
                6.5 如果完成 goal 需要某个 Tool 或 Workflow 的结果，必须先把该动作编排为可执行步骤，再使用 reply 收口。
                7. 当关键信息缺失时，可使用 clarify 步骤向用户补问；当需要比较候选结论时，可使用 judge 步骤；当需要写入运行时记忆时，可使用 memory_write 步骤；当需要受控等待时，可使用 wait 步骤。
                8. 当多个 Tool 可并行执行时，可使用 parallel 步骤；并在后续使用 join 步骤聚合并行结果。
                9. 如某一步执行后可能暴露信息缺口或路径偏差，可在该步骤 inputs 中设置 postStepReplan=true 或 replanOnNeedMoreInfo=true。
                9.1 当 goal 要求生成文件时，优先使用输入中的 suggestedArtifactFilename；command 实际输出路径与 artifactPaths 必须完全一致。用户未指定名称时，文件名应根据正文标题或内容主题生成，禁止使用无语义的 output.pdf、document.pdf 等通用名称。
                10. JSON 结构固定为：
                {
                  "planId": "可选",
                  "planType": "tool|tool_sequence|workflow|hybrid|direct_reply",
                  "goal": "任务目标",
                  "status": "draft",
                  "assumptions": [],
                  "constraints": [],
                  "risks": [],
                  "steps": [
                    {
                      "stepNo": 1,
                      "stepId": "step_1",
                      "stepType": "%s",
                      "stepName": "步骤名称",
                      "objective": "本步骤目标",
                      "dependsOnStepIds": [],
                      "targetToolCode": "仅 tool 步骤必填",
                      "targetWorkflowId": "仅 workflow 步骤必填",
                      "inputs": {
                        "delegateAgentId": "handoff 可选；与 requiredCapabilities 至少提供一个",
                        "requiredCapabilities": ["java", "security"],
                        "delegateInstruction": "仅 handoff 步骤建议填写",
                        "question": "clarify 步骤可选",
                        "criteria": "judge 步骤可选",
                        "waitMillis": 1000,
                        "waitType": "timer|event|webhook",
                        "eventKey": "event/webhook 等待时可选",
                        "scope": "memory_write 步骤可选，conversation|user|task|all",
                        "content": "memory_write 步骤可选",
                        "parallelToolCodes": ["TOOL_A", "TOOL_B"],
                        "joinInstruction": "join 步骤可选",
                        "postStepReplan": true,
                        "replanOnNeedMoreInfo": false,
                        "confidenceBelow": 0.7
                        ,"artifactPaths": ["根据内容主题生成的文件名.pdf"]
                        ,"dataContract": {
                          "version": 1,
                          "compatibleVersions": [1],
                          "bindings": {
                            "targetField": {"sourceStepId": "step_1", "sourceContractVersion": 1, "sourcePointer": "/structuredData/value", "targetPointer": "/targetField", "required": true}
                          },
                          "inputSchema": {"type": "object", "required": ["targetField"], "properties": {"targetField": {"type": "string"}}},
                          "outputSchema": {"type": "object", "required": ["summary"]}
                        }
                      },
                      "expectedOutput": "预期结果",
                      "failurePolicy": "fail_fast|continue|replan",
                      "replannable": true,
                      "status": "draft",
                      "description": "简要说明"
                    }
                  ]
                }
                """.formatted(executableStepTypes, executableStepTypes) + "\n\n规划 Prompt：\n" + planningPrompt
                + "\n\n时间上下文：\n" + nullSafe(executionContext.currentTimeText())
                + "\n\n决策结论：\n"
                + "decisionType=" + executionDecision.decisionType()
                + "\nreason=" + executionDecision.reason()
                + (replanContext == null ? "" : "\n\n当前是自动重规划场景：你必须基于触发原因与已完成步骤，只输出剩余未完成步骤。"
                + "\n触发类型：" + replanContext.triggerType()
                + "\n当前重规划轮次：" + replanContext.replanRound()
                + "\n历史失败指纹：" + replanContext.previousFailureFingerprints()
                + "\n触发原因：" + replanContext.triggerReason());
    }

    private PromptTracePackage buildPlanningPromptTracePackage(ExecutionContext executionContext,
                                                              ExecutionDecision executionDecision,
                                                              ReplanContext replanContext,
                                                              List<PlanTemplateHint> templateHints) throws JsonProcessingException {
        PromptContextBudgetService.StageBudget budget = promptContextBudgetService.resolveBudget(
                executionContext.modelForStage("planning"),
                PromptContextBudgetService.PromptStage.PLANNING
        );
        ContextCompactionService.StageCompactionBundle compacted = contextCompactionService.compactStageContext(executionContext, budget);
        List<Map<String, Object>> selectedTools = executionDecision.selectedTools().stream()
                .map(selectedTool -> toSelectedTool(selectedTool, executionContext))
                .toList();
        List<Map<String, Object>> selectedWorkflows = executionDecision.selectedWorkflows().stream()
                .map(selectedWorkflow -> toSelectedWorkflow(selectedWorkflow, executionContext))
                .toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("goal", promptContextBudgetService.truncateToChars(resolvePlanningGoal(executionContext), budget.userMessageChars()));
        input.put("userMessage", compacted.conversationAwareUserMessage());
        input.put("protectedFacts", compacted.protectedFactsPayload());
        input.put("conversationState", executionContext.conversationStatePayload());
        input.put("conversationStateText", compacted.conversationStateText());
        input.put("knowledgeContext", promptContextBudgetService.truncateToChars(
                nullSafe(executionContext.knowledgeContextText()), budget.factChars() * 2));
        input.put("currentDate", nullSafe(executionContext.timeContext() == null ? null : executionContext.timeContext().currentDate()));
        input.put("currentDateTime", nullSafe(executionContext.timeContext() == null ? null : executionContext.timeContext().localDateTime()));
        input.put("zoneId", nullSafe(executionContext.timeContext() == null ? null : executionContext.timeContext().zoneId()));
        input.put("conversationTitle", nullSafe(executionContext.conversationContext().title()));
        if (ArtifactFilenameResolver.isPdfGenerationRequest(executionContext.userMessage())) {
            input.put("suggestedArtifactFilename", ArtifactFilenameResolver.resolvePdfFilename(
                    executionContext.userMessage(), resolveArtifactNamingContent(executionContext)));
        }
        input.put("conversationMemory", compacted.conversationMemory());
        input.put("userMemory", compacted.userMemory());
        input.put("recentTaskMemory", compacted.recentTaskMemory());
        input.put("recentConversationSummary", compacted.recentConversationSummary());
        input.put("recentUserMessages", compacted.recentUserMessages());
        input.put("decisionType", executionDecision.decisionType());
        input.put("decisionReason", executionDecision.reason());
        input.put("contextCompaction", compacted.metadata());
        input.put("selectedTools", selectedTools);
        input.put("selectedWorkflows", selectedWorkflows);
        EvalFeedbackService.EvalFeedbackSummary evalFeedback = evalFeedbackService.summarizeAgentFeedback(executionContext.agentContext().agentId());
        input.put("evalFeedback", Map.of(
                "latestAverageScore", evalFeedback.latestAverageScore() == null ? "" : evalFeedback.latestAverageScore(),
                "latestPassRate", evalFeedback.latestPassRate() == null ? "" : evalFeedback.latestPassRate(),
                "latestRunCode", nullSafe(evalFeedback.latestRunCode()),
                "preferredToolCodes", evalFeedback.preferredToolCodes()
        ));
        input.put("availableDelegates", executionContext.availableDelegates().stream()
                .map(item -> Map.<String, Object>of(
                        "agentId", item.agentId(),
                        "agentCode", item.agentCode(),
                        "agentName", item.agentName(),
                        "description", item.description() == null ? "" : item.description()
                ))
                .toList());
        input.put("planningConstraints", List.of(
                "步骤总数不超过 12",
                        "优先拆成可执行、可验证的步骤",
                        "如无需 Tool 或 Workflow，可直接输出 reply 步骤",
                        "仅在高风险、冲突、低置信度或明确复核要求下加入 reflection 步骤",
                        "所选 Workflow 已声明产出用户回复时，只执行该 Workflow，不再追加 reply 或 reflection",
                        "一个计划只保留一个最终用户回复生成步骤，避免重复调用模型",
                        "reply 不能代替未执行的 Tool、Workflow、查询或副作用动作；目标依赖的数据必须由前序步骤真实取得",
                        "如关键信息缺失，优先插入 clarify 步骤而不是盲目继续执行",
                        "如需要比较多个候选结果或判断路径优劣，可加入 judge 步骤",
                        "如多个 Tool 彼此独立，优先使用 parallel + join 组合缩短执行时长",
                        "如需委派其他 Agent，只能从 availableDelegates 中选择 delegateAgentId",
                        "condition 步骤如需控制后续执行，请在后续步骤的 inputs.runWhen 中声明 conditionStepId 和 expectedResult"
                ));
        if (templateHints != null && !templateHints.isEmpty()) {
            input.put("planTemplates", templateHints.stream()
                    .map(item -> Map.<String, Object>of(
                            "planId", item.planId(),
                            "planVersion", item.planVersion(),
                            "planType", item.planType(),
                            "goal", item.goal(),
                            "qualityScore", item.qualityScore(),
                            "summary", item.summary(),
                            "finalStatus", item.finalStatus(),
                            "errorMessage", item.errorMessage(),
                            "stepOutline", item.stepOutline()
                    ))
                    .toList());
        }
        input.put("planOutcomeFeedback", planOutcomeFeedbackService.summarize(templateHints));
        if (replanContext != null) {
            Map<String, Object> failedStep = new LinkedHashMap<>();
            failedStep.put("stepNo", replanContext.failedStep().stepNo());
            failedStep.put("stepId", replanContext.failedStep().stepId());
            failedStep.put("stepType", replanContext.failedStep().stepType());
            failedStep.put("stepName", replanContext.failedStep().stepName());
            failedStep.put("objective", replanContext.failedStep().objective());
            failedStep.put("targetToolCode", replanContext.failedStep().targetToolCode());
            failedStep.put("targetWorkflowId", replanContext.failedStep().targetWorkflowId());
            failedStep.put("failurePolicy", replanContext.failedStep().failurePolicy());
            List<Map<String, Object>> completedSteps = replanContext.previousStepResults().stream()
                    .map(item -> {
                        Map<String, Object> stepItem = new LinkedHashMap<>();
                        stepItem.put("stepNo", item.stepNo());
                        stepItem.put("stepId", item.stepId());
                        stepItem.put("stepType", item.stepType());
                        stepItem.put("stepName", item.stepName());
                        stepItem.put("summary", item.summary());
                        stepItem.put("responseText", item.responseText());
                        StandardStepOutputSnapshot snapshot = parseStandardSnapshot(item.outputSnapshot());
                        if (snapshot != null) {
                            stepItem.put("observation", snapshot.observation());
                            stepItem.put("confidence", snapshot.confidence());
                            stepItem.put("needReplan", snapshot.needReplan());
                            stepItem.put("needMoreInfo", snapshot.needMoreInfo());
                            stepItem.put("nextActionHint", snapshot.nextActionHint());
                        }
                        return stepItem;
                    })
                    .toList();
            Map<String, Object> replanPayload = new LinkedHashMap<>();
            replanPayload.put("reason", replanContext.triggerReason());
            replanPayload.put("triggerType", replanContext.triggerType());
            replanPayload.put("failedStep", failedStep);
            replanPayload.put("failedMessage", replanContext.triggerReason());
            replanPayload.put("currentPlanId", replanContext.currentPlan().planId());
            replanPayload.put("currentPlanGoal", replanContext.currentPlan().goal());
            replanPayload.put("replanRound", replanContext.replanRound());
            replanPayload.put("previousFailureFingerprints", replanContext.previousFailureFingerprints());
            replanPayload.put("completedSteps", completedSteps);
            replanPayload.put("requirements", List.of(
                    "只输出剩余未完成步骤，不要重复 completedSteps",
                    "新计划必须能替代 failedStep 之后的剩余执行",
                    "如已完成步骤已经说明信息不足，要优先补充采集、澄清或改用 reply 收束",
                    "如失败目标无法继续，可改用 reply 给出明确失败说明和下一步建议",
                    "如已完成 reply 表示仍需调用 Tool、查询数据或完成其他自主动作，新计划必须加入并执行该动作，禁止只再次给出操作建议",
                    "只有确实需要用户提供外部信息时才使用 clarify；Agent 已具备能力完成的动作不得转交用户",
                    "如输出 reflection 步骤，必须设置 failurePolicy=replan 且 replannable=true"
            ));
        input.put("replanContext", replanPayload);
        }
        String systemPrompt = buildSystemPrompt(executionContext, executionDecision, replanContext);
        String userPrompt = promptContextBudgetService.truncateToChars(objectMapper.writeValueAsString(input), budget.promptChars());
        Map<String, Object> traceMetadata = contextCompactionService.enrichPromptAssemblyMetadata(
                compacted.metadata(),
                systemPrompt,
                userPrompt
        );
        log.info("上下文压缩[planning] taskId={}, traceId={}, compactedChars={}, savedChars={}, summarized={}",
                executionContext.taskContext() == null ? null : executionContext.taskContext().taskId(),
                executionContext.taskContext() == null ? null : executionContext.taskContext().traceId(),
                compacted.metadata().get("compactedChars"),
                compacted.metadata().get("savedChars"),
                compacted.metadata().get("summarized"));
        return new PromptTracePackage(systemPrompt, userPrompt, traceMetadata);
    }

    private String resolvePlanningGoal(ExecutionContext executionContext) {
        if (executionContext.conversationStateContext() != null
                && executionContext.conversationStateContext().goals() != null
                && !executionContext.conversationStateContext().goals().isEmpty()) {
            return String.join("；", executionContext.conversationStateContext().goals());
        }
        return executionContext.userMessage();
    }

    private Map<String, Object> toSelectedTool(ExecutionDecision.SelectedTool selectedTool,
                                               ExecutionContext executionContext) {
        RegistryAgentTool tool = executionContext.availableTools().stream()
                .filter(item -> selectedTool.toolCode().equals(item.toolCode()))
                .findFirst()
                .orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("toolCode", selectedTool.toolCode());
        item.put("toolName", selectedTool.toolName());
        item.put("toolType", tool == null ? "" : nullSafe(tool.toolType()));
        item.put("toolCategory", tool == null ? "" : nullSafe(tool.toolCategory()));
        item.put("providerType", tool == null ? "" : nullSafe(tool.providerType()));
        item.put("transportType", tool == null ? "" : nullSafe(tool.transportType()));
        item.put("description", tool == null ? "" : nullSafe(tool.description()));
        item.put("inputSchema", tool == null || tool.inputSchema() == null ? Map.of() : tool.inputSchema());
        item.put("outputSchema", tool == null || tool.outputSchema() == null ? Map.of() : tool.outputSchema());
        item.put("routing", tool == null || tool.metadata() == null ? Map.of() : tool.metadata().path("routing"));
        return item;
    }

    private Map<String, Object> toSelectedWorkflow(ExecutionDecision.SelectedWorkflow selectedWorkflow,
                                                   ExecutionContext executionContext) {
        RegistryAgentWorkflow workflow = executionContext.availableWorkflows().stream()
                .filter(item -> selectedWorkflow.workflowId().equals(item.workflowId()))
                .findFirst()
                .orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("workflowId", selectedWorkflow.workflowId());
        item.put("workflowName", selectedWorkflow.workflowName());
        item.put("workflowCode", workflow == null ? "" : nullSafe(workflow.workflowCode()));
        item.put("workflowType", workflow == null ? "" : nullSafe(workflow.workflowType()));
        item.put("workflowCategory", workflow == null ? "" : nullSafe(workflow.workflowCategory()));
        item.put("description", workflow == null ? "" : nullSafe(workflow.description()));
        item.put("routing", workflow == null || workflow.metadata() == null ? Map.of() : workflow.metadata().path("routing"));
        return item;
    }

    private String resolveArtifactNamingContent(ExecutionContext executionContext) {
        List<ExecutionContext.ConversationMessageContext> messages = executionContext.recentConversationMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ExecutionContext.ConversationMessageContext message = messages.get(index);
            if (message != null && "assistant".equalsIgnoreCase(message.messageType())
                    && message.content() != null && !message.content().isBlank()) {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(message.content());
            }
        }
        return content.toString();
    }

    private String planningPromptContent(ExecutionContext executionContext) {
        if (executionContext.plannerPromptContext() != null
                && executionContext.plannerPromptContext().promptContent() != null
                && !executionContext.plannerPromptContext().promptContent().isBlank()) {
            return executionContext.plannerPromptContext().promptContent();
        }
        if (executionContext.decisionPromptContext() != null
                && executionContext.decisionPromptContext().promptContent() != null
                && !executionContext.decisionPromptContext().promptContent().isBlank()) {
            return executionContext.decisionPromptContext().promptContent();
        }
        if (executionContext.promptContext() != null
                && executionContext.promptContext().promptContent() != null
                && !executionContext.promptContext().promptContent().isBlank()) {
            return executionContext.promptContext().promptContent();
        }
        return null;
    }

    private String resolvePlanType(ExecutionDecision executionDecision) {
        String decisionType = executionDecision.decisionType() == null
                ? ""
                : executionDecision.decisionType().trim().toLowerCase(Locale.ROOT);
        if ("tool".equals(decisionType) && executionDecision.selectedTools().size() > 1) {
            return "tool_sequence";
        }
        if (decisionType.isBlank()) {
            return "direct_reply";
        }
        return decisionType;
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record PromptTracePackage(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> compactionMetadata
    ) {
    }

    /**
     * 自动重规划输入。
     *
     * @param currentPlan         当前执行计划
     * @param failedStep          触发重规划的步骤
     * @param previousStepResults 已成功的前序步骤结果
     * @param triggerType         触发类型
     * @param triggerReason       触发原因
     * @param replanRound         当前重规划轮次
     * @param previousFailureFingerprints 历史失败指纹
     */
    public record ReplanContext(
            ExecutionPlan currentPlan,
            ExecutionPlan.ExecutionPlanStep failedStep,
            List<RuntimeStepResultContext> previousStepResults,
            String triggerType,
            String triggerReason,
            Integer replanRound,
            List<String> previousFailureFingerprints
    ) {
    }

    public record PlanTemplateHint(
            String planId,
            Integer planVersion,
            String planType,
            String goal,
            Integer qualityScore,
            String summary,
            String finalStatus,
            String errorMessage,
            List<String> stepOutline
    ) {
    }
}

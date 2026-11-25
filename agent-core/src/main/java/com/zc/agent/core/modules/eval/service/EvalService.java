package com.zc.agent.core.modules.eval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.common.exception.BusinessException;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.CreateConversationRequest;
import com.zc.agent.core.modules.conversation.dto.SendMessageRequest;
import com.zc.agent.core.modules.conversation.service.ConversationService;
import com.zc.agent.core.modules.conversation.vo.SendMessageResponse;
import com.zc.agent.core.modules.conversation.vo.TaskPlanResponse;
import com.zc.agent.core.modules.conversation.vo.TaskPlanStepResponse;
import com.zc.agent.core.modules.eval.dto.CreateEvalSuiteRequest;
import com.zc.agent.core.modules.eval.dto.EvalCaseUpsertRequest;
import com.zc.agent.core.modules.eval.dto.ExecuteEvalSuiteRequest;
import com.zc.agent.core.modules.eval.dto.EvalRunPageQueryRequest;
import com.zc.agent.core.modules.eval.dto.EvalSuitePageQueryRequest;
import com.zc.agent.core.modules.eval.dto.UpdateEvalSuiteRequest;
import com.zc.agent.core.modules.eval.model.EvalCaseRecord;
import com.zc.agent.core.modules.eval.model.EvalRunCaseRecord;
import com.zc.agent.core.modules.eval.model.EvalRunRecord;
import com.zc.agent.core.modules.eval.model.EvalSuiteRecord;
import com.zc.agent.core.modules.eval.model.EvalSuiteVersionRecord;
import com.zc.agent.core.modules.eval.repository.EvalRepository;
import com.zc.agent.core.modules.eval.vo.EvalCaseResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunCaseResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunCompareResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunDetailResponse;
import com.zc.agent.core.modules.eval.vo.EvalRunPageItemResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteDetailResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteDiffResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuitePageItemResponse;
import com.zc.agent.core.modules.eval.vo.EvalSuiteVersionResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Eval Harness 服务。
 */
@Service
public class EvalService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int REPLY_KEYWORD_SCORE = 40;
    private static final int FORBIDDEN_KEYWORD_SCORE = 15;
    private static final int TOOL_SCORE = 25;
    private static final int STEP_TYPE_SCORE = 10;
    private static final int FINAL_STATUS_SCORE = 10;

    private final EvalRepository evalRepository;
    private final ConversationService conversationService;
    private final EvalLlmJudgeService evalLlmJudgeService;
    private final ObjectMapper objectMapper;

    public EvalService(EvalRepository evalRepository,
                       ConversationService conversationService,
                       EvalLlmJudgeService evalLlmJudgeService,
                       ObjectMapper objectMapper) {
        this.evalRepository = evalRepository;
        this.conversationService = conversationService;
        this.evalLlmJudgeService = evalLlmJudgeService;
        this.objectMapper = objectMapper;
    }

    public PageResponse<EvalSuitePageItemResponse> pageSuites(EvalSuitePageQueryRequest request) {
        int pageNum = request.pageNum() == null ? DEFAULT_PAGE_NUM : request.pageNum();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        long total = evalRepository.countSuites(request.suiteCode(), request.suiteName(), request.agentId(), request.status());
        List<EvalSuitePageItemResponse> records = evalRepository.pageSuites(
                        request.suiteCode(),
                        request.suiteName(),
                        request.agentId(),
                        request.status(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(item -> new EvalSuitePageItemResponse(
                        item.suiteId(),
                        item.currentVersion(),
                        item.suiteCode(),
                        item.suiteName(),
                        item.agentId(),
                        item.agentName(),
                        item.description(),
                        item.status(),
                        evalRepository.countCasesBySuiteId(item.suiteId()),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public EvalSuiteDetailResponse getSuiteDetail(Long suiteId) {
        EvalSuiteRecord suiteRecord = getSuite(suiteId);
        return toSuiteDetailResponse(suiteRecord, evalRepository.findCasesBySuiteId(suiteId));
    }

    public List<EvalSuiteVersionResponse> listSuiteVersions(Long suiteId) {
        getSuite(suiteId);
        return evalRepository.findSuiteVersions(suiteId).stream()
                .map(this::toSuiteVersionResponse)
                .toList();
    }

    public EvalSuiteDiffResponse getSuiteDiff(Long suiteId, Integer fromVersion, Integer toVersion) {
        if (fromVersion == null || toVersion == null) {
            throw new BusinessException("版本号不能为空");
        }
        EvalSuiteVersionRecord fromRecord = evalRepository.findSuiteVersion(suiteId, fromVersion)
                .orElseThrow(() -> new BusinessException("基准版本不存在"));
        EvalSuiteVersionRecord toRecord = evalRepository.findSuiteVersion(suiteId, toVersion)
                .orElseThrow(() -> new BusinessException("目标版本不存在"));
        List<EvalSuiteDiffResponse.DiffItem> changedItems = buildSuiteDiffItems(fromRecord, toRecord);
        int addedCount = (int) changedItems.stream().filter(item -> "added".equals(item.changeType())).count();
        int removedCount = (int) changedItems.stream().filter(item -> "removed".equals(item.changeType())).count();
        int changedCount = (int) changedItems.stream().filter(item -> "changed".equals(item.changeType())).count();
        return new EvalSuiteDiffResponse(
                suiteId,
                fromVersion,
                toVersion,
                new EvalSuiteDiffResponse.DiffSummary(addedCount, removedCount, changedCount),
                changedItems
        );
    }

    @Transactional
    public EvalSuiteDetailResponse createSuite(CreateEvalSuiteRequest request, SecurityUser securityUser) {
        validateDistinctCaseCodes(request.evalCases());
        Long suiteId = evalRepository.insertSuite(
                request.suiteCode().trim(),
                request.suiteName().trim(),
                request.agentId(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.status().trim(),
                toJsonText(defaultMap(request.metadata())),
                securityUser.userId()
        );
        replaceCases(suiteId, request.evalCases(), securityUser.userId(), false);
        createSuiteVersionSnapshot(suiteId, 1, request, securityUser.userId());
        return getSuiteDetail(suiteId);
    }

    @Transactional
    public EvalSuiteDetailResponse updateSuite(Long suiteId, UpdateEvalSuiteRequest request, SecurityUser securityUser) {
        getSuite(suiteId);
        int nextVersion = getSuite(suiteId).currentVersion() + 1;
        validateDistinctCaseCodes(request.evalCases());
        evalRepository.updateSuite(
                suiteId,
                nextVersion,
                request.suiteCode().trim(),
                request.suiteName().trim(),
                request.agentId(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.status().trim(),
                toJsonText(defaultMap(request.metadata())),
                securityUser.userId()
        );
        replaceCases(suiteId, request.evalCases(), securityUser.userId(), true);
        createSuiteVersionSnapshot(suiteId, nextVersion, request, securityUser.userId());
        return getSuiteDetail(suiteId);
    }

    public PageResponse<EvalRunPageItemResponse> pageRuns(EvalRunPageQueryRequest request) {
        int pageNum = request.pageNum() == null ? DEFAULT_PAGE_NUM : request.pageNum();
        int pageSize = request.pageSize() == null ? DEFAULT_PAGE_SIZE : request.pageSize();
        long total = evalRepository.countRuns(request.suiteId(), request.agentId(), request.runStatus());
        List<EvalRunPageItemResponse> records = evalRepository.pageRuns(
                        request.suiteId(),
                        request.agentId(),
                        request.runStatus(),
                        (pageNum - 1) * pageSize,
                        pageSize
                ).stream()
                .map(this::toRunPageItemResponse)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public EvalRunDetailResponse getRunDetail(Long runId) {
        EvalRunRecord runRecord = evalRepository.findRunById(runId)
                .orElseThrow(() -> new BusinessException("Eval Run 不存在"));
        return toRunDetailResponse(runRecord, evalRepository.findRunCasesByRunId(runId));
    }

    public EvalRunCompareResponse compareRuns(Long fromRunId, Long toRunId) {
        if (fromRunId == null || toRunId == null) {
            throw new BusinessException("对比批次不能为空");
        }
        EvalRunRecord fromRun = evalRepository.findRunById(fromRunId)
                .orElseThrow(() -> new BusinessException("基准批次不存在"));
        EvalRunRecord toRun = evalRepository.findRunById(toRunId)
                .orElseThrow(() -> new BusinessException("目标批次不存在"));
        if (!Objects.equals(fromRun.suiteId(), toRun.suiteId())) {
            throw new BusinessException("仅支持同一个 Suite 下的 Run 对比");
        }
        Map<String, EvalRunCaseRecord> fromCaseMap = toRunCaseMap(evalRepository.findRunCasesByRunId(fromRunId));
        Map<String, EvalRunCaseRecord> toCaseMap = toRunCaseMap(evalRepository.findRunCasesByRunId(toRunId));
        Set<String> caseCodes = new java.util.LinkedHashSet<>();
        caseCodes.addAll(fromCaseMap.keySet());
        caseCodes.addAll(toCaseMap.keySet());
        ArrayList<EvalRunCompareResponse.CompareItem> items = new ArrayList<>();
        int improvedCount = 0;
        int regressedCount = 0;
        int unchangedCount = 0;
        int addedCount = 0;
        int removedCount = 0;
        for (String caseCode : caseCodes) {
            EvalRunCaseRecord fromCase = fromCaseMap.get(caseCode);
            EvalRunCaseRecord toCase = toCaseMap.get(caseCode);
            String changeType;
            BigDecimal scoreDelta = null;
            if (fromCase == null) {
                changeType = "added";
                addedCount++;
            } else if (toCase == null) {
                changeType = "removed";
                removedCount++;
            } else {
                scoreDelta = toCase.score().subtract(fromCase.score()).setScale(2, RoundingMode.HALF_UP);
                int compare = scoreDelta.compareTo(BigDecimal.ZERO);
                if (compare > 0) {
                    changeType = "improved";
                    improvedCount++;
                } else if (compare < 0) {
                    changeType = "regressed";
                    regressedCount++;
                } else {
                    changeType = "unchanged";
                    unchangedCount++;
                }
            }
            items.add(new EvalRunCompareResponse.CompareItem(
                    caseCode,
                    fromCase != null ? fromCase.caseName() : toCase.caseName(),
                    changeType,
                    fromCase == null ? null : fromCase.score(),
                    toCase == null ? null : toCase.score(),
                    scoreDelta,
                    fromCase == null ? null : fromCase.passed(),
                    toCase == null ? null : toCase.passed(),
                    fromCase == null ? null : fromCase.runStatus(),
                    toCase == null ? null : toCase.runStatus(),
                    fromCase == null ? null : fromCase.actualReply(),
                    toCase == null ? null : toCase.actualReply()
            ));
        }
        return new EvalRunCompareResponse(
                fromRun.suiteId(),
                fromRun.runId().intValue(),
                fromRun.runCode(),
                fromRun.suiteVersion(),
                toRun.runId().intValue(),
                toRun.runCode(),
                toRun.suiteVersion(),
                new EvalRunCompareResponse.CompareSummary(
                        improvedCount,
                        regressedCount,
                        unchangedCount,
                        addedCount,
                        removedCount,
                        toRun.averageScore().subtract(fromRun.averageScore()).setScale(2, RoundingMode.HALF_UP),
                        toRun.passedCases() - fromRun.passedCases()
                ),
                items
        );
    }

    @Transactional
    public EvalSuiteDetailResponse markBaselineRun(Long suiteId, Long runId, SecurityUser securityUser) {
        EvalSuiteRecord suiteRecord = getSuite(suiteId);
        EvalRunRecord runRecord = evalRepository.findRunById(runId)
                .orElseThrow(() -> new BusinessException("目标批次不存在"));
        if (!Objects.equals(suiteId, runRecord.suiteId())) {
            throw new BusinessException("仅支持将当前 Suite 下的 Run 设置为基线");
        }
        Map<String, Object> metadata = new HashMap<>(readMap(suiteRecord.metadata()));
        metadata.put("baselineRunId", runRecord.runId());
        metadata.put("baselineRunCode", runRecord.runCode());
        metadata.put("baselineSuiteVersion", runRecord.suiteVersion());
        metadata.put("baselineStrategy", "manual_selected");
        evalRepository.updateSuiteMetadata(suiteId, toJsonText(metadata), securityUser.userId());
        return getSuiteDetail(suiteId);
    }

    public EvalRunDetailResponse executeSuite(Long suiteId,
                                              ExecuteEvalSuiteRequest request,
                                              String authorization,
                                              SecurityUser securityUser) {
        EvalSuiteRecord suiteRecord = getSuite(suiteId);
        if (!"enabled".equalsIgnoreCase(suiteRecord.status())) {
            throw new BusinessException("仅启用状态的 Eval Suite 可以执行");
        }
        Map<String, Object> suiteMetadata = readMap(suiteRecord.metadata());
        List<EvalCaseRecord> caseRecords = evalRepository.findCasesBySuiteId(suiteId).stream()
                .filter(item -> "enabled".equalsIgnoreCase(item.status()))
                .toList();
        List<EvalCaseRecord> selectedCases = filterCasesForExecution(caseRecords, request);
        if (selectedCases.isEmpty()) {
            throw new BusinessException("当前 Eval Suite 没有可执行的启用用例");
        }
        String triggerType = normalizeNullableText(request == null ? null : request.triggerType());
        String effectiveTriggerType = triggerType == null ? "manual" : triggerType;
        Long runId = evalRepository.insertRun(
                suiteId,
                suiteRecord.currentVersion(),
                suiteRecord.agentId(),
                suiteRecord.agentName(),
                "EVAL_RUN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT),
                "running",
                selectedCases.size(),
                effectiveTriggerType,
                toJsonText(buildRunRequestMetadata(suiteRecord, request)),
                LocalDateTime.now(),
                securityUser.userId()
        );
        EvalRunRecord baselineRun = resolveBaselineRun(suiteId, runId, request, suiteMetadata);
        Map<String, EvalRunCaseRecord> baselineCaseMap = new HashMap<>();
        if (baselineRun != null) {
            evalRepository.findRunCasesByRunId(baselineRun.runId()).forEach(item -> baselineCaseMap.put(item.caseCode(), item));
        }
        int passedCases = 0;
        int failedCases = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        ArrayList<EvalCaseExecutionResult> executionResults = new ArrayList<>();
        for (EvalCaseRecord caseRecord : selectedCases) {
            Long runCaseId = evalRepository.insertRunCase(runId, caseRecord.caseId(), "running", LocalDateTime.now(), securityUser.userId());
            EvalCaseExecutionResult result = runSingleCase(
                    suiteRecord,
                    suiteMetadata,
                    caseRecord,
                    baselineCaseMap.get(caseRecord.caseCode()),
                    authorization,
                    securityUser
            );
            totalScore = totalScore.add(result.score());
            if (Boolean.TRUE.equals(result.passed())) {
                passedCases++;
            } else {
                failedCases++;
            }
            executionResults.add(result);
            evalRepository.updateRunCaseResult(
                    runCaseId,
                    result.conversationId(),
                    result.taskId(),
                    result.traceId(),
                    result.runStatus(),
                    result.passed(),
                    result.score(),
                    result.actualReply(),
                    toJsonText(result.actualToolCodes()),
                    toJsonText(result.actualStepTypes()),
                    result.actualFinalStatus(),
                    toJsonText(result.failureReasons()),
                    toJsonText(buildRunCaseMetadata(result)),
                    LocalDateTime.now(),
                    securityUser.userId()
            );
        }
        BigDecimal averageScore = totalScore.divide(BigDecimal.valueOf(selectedCases.size()), 2, RoundingMode.HALF_UP);
        CiGateResult ciGateResult = evaluateCiGate(suiteMetadata, request, averageScore, passedCases, failedCases, selectedCases.size());
        String runStatus = failedCases == 0 ? "success" : "failed";
        if (ciGateResult.enabled() && !ciGateResult.passed()) {
            runStatus = "failed";
        }
        Map<String, Object> runMetadata = buildRunMetadata(
                suiteRecord,
                suiteMetadata,
                baselineRun,
                executionResults,
                averageScore,
                passedCases,
                selectedCases.size(),
                request,
                ciGateResult
        );
        evalRepository.updateRunResult(
                runId,
                runStatus,
                passedCases,
                failedCases,
                averageScore,
                toJsonText(runMetadata),
                LocalDateTime.now(),
                securityUser.userId()
        );
        return getRunDetail(runId);
    }

    private EvalCaseExecutionResult runSingleCase(EvalSuiteRecord suiteRecord,
                                                  Map<String, Object> suiteMetadata,
                                                  EvalCaseRecord caseRecord,
                                                  EvalRunCaseRecord baselineCase,
                                                  String authorization,
                                                  SecurityUser securityUser) {
        try {
            Long conversationId = conversationService.createConversation(
                    new CreateConversationRequest(
                            suiteRecord.agentId(),
                            "[Eval] " + suiteRecord.suiteName() + " / " + caseRecord.caseName(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    ),
                    authorization,
                    securityUser
            ).conversationId();
            SendMessageResponse response = conversationService.sendMessageForEvaluation(
                    conversationId,
                    new SendMessageRequest(caseRecord.userInput()),
                    authorization,
                    securityUser
            );
            return evaluateCase(suiteMetadata, caseRecord, baselineCase, response);
        } catch (Exception exception) {
            return new EvalCaseExecutionResult(
                    null,
                    null,
                    null,
                    "failed",
                    false,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null,
                    null,
                    "",
                    null,
                    List.of(),
                    List.of(),
                    "failed",
                    baselineCase == null ? null : baselineCase.score(),
                    baselineCase == null ? null : baselineCase.passed(),
                    baselineCase == null ? null : BigDecimal.ZERO.subtract(baselineCase.score()).setScale(2, RoundingMode.HALF_UP),
                    List.of("执行异常：" + exception.getMessage()),
                    Map.of("exceptionMessage", exception.getMessage() == null ? "" : exception.getMessage())
            );
        }
    }

    private EvalCaseExecutionResult evaluateCase(Map<String, Object> suiteMetadata,
                                                 EvalCaseRecord caseRecord,
                                                 EvalRunCaseRecord baselineCase,
                                                 SendMessageResponse response) {
        List<String> expectedReplyKeywords = readStringList(caseRecord.expectedReplyKeywords());
        List<String> forbiddenReplyKeywords = readStringList(caseRecord.forbiddenReplyKeywords());
        List<String> expectedToolCodes = readStringList(caseRecord.expectedToolCodes());
        List<String> expectedStepTypes = readStringList(caseRecord.expectedStepTypes());
        String actualReply = response.assistantMessage().content();
        String normalizedReply = actualReply == null ? "" : actualReply.toLowerCase(Locale.ROOT);
        TaskPlanResponse latestPlan = findLatestPlan(response.task().plans());
        List<String> actualToolCodes = latestPlan == null ? List.of() : latestPlan.steps().stream()
                .map(TaskPlanStepResponse::targetToolCode)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        List<String> actualStepTypes = latestPlan == null ? List.of() : latestPlan.steps().stream()
                .map(TaskPlanStepResponse::stepType)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        String actualFinalStatus = response.task().status();
        ArrayList<String> failureReasons = new ArrayList<>();
        BigDecimal ruleScore = BigDecimal.ZERO;
        ruleScore = ruleScore.add(evaluateReplyKeywordScore(expectedReplyKeywords, normalizedReply, failureReasons));
        ruleScore = ruleScore.add(evaluateForbiddenKeywordScore(forbiddenReplyKeywords, normalizedReply, failureReasons));
        ruleScore = ruleScore.add(evaluateExactExpectation("Tool", expectedToolCodes, actualToolCodes, TOOL_SCORE, failureReasons));
        ruleScore = ruleScore.add(evaluateExactExpectation("步骤类型", expectedStepTypes, actualStepTypes, STEP_TYPE_SCORE, failureReasons));
        ruleScore = ruleScore.add(evaluateFinalStatusScore(caseRecord.expectedFinalStatus(), actualFinalStatus, failureReasons));
        BigDecimal normalizedRuleScore = ruleScore.setScale(2, RoundingMode.HALF_UP);
        EvalLlmJudgeService.JudgeResult judgeResult = judgeCaseIfNeeded(
                suiteMetadata,
                caseRecord,
                response,
                expectedReplyKeywords,
                forbiddenReplyKeywords,
                expectedToolCodes,
                actualToolCodes,
                expectedStepTypes,
                actualStepTypes,
                actualFinalStatus
        );
        BigDecimal finalScore = mergeFinalScore(normalizedRuleScore, judgeResult, suiteMetadata);
        if (!judgeResult.skipped() && Boolean.FALSE.equals(judgeResult.passed()) && judgeResult.issues() != null && !judgeResult.issues().isEmpty()) {
            failureReasons.addAll(judgeResult.issues());
        }
        boolean passed = failureReasons.isEmpty()
                && finalScore.compareTo(BigDecimal.valueOf(caseRecord.passThreshold())) >= 0
                && (judgeResult.skipped() || !Boolean.FALSE.equals(judgeResult.passed()));
        if (!passed && failureReasons.isEmpty()) {
            failureReasons.add("总分未达到通过阈值，当前 " + finalScore + "，阈值 " + caseRecord.passThreshold());
        }
        BigDecimal baselineScore = baselineCase == null ? null : baselineCase.score();
        Boolean baselinePassed = baselineCase == null ? null : baselineCase.passed();
        BigDecimal scoreDelta = baselineScore == null ? null : finalScore.subtract(baselineScore).setScale(2, RoundingMode.HALF_UP);
        return new EvalCaseExecutionResult(
                response.conversation().conversationId(),
                response.task().taskId(),
                response.task().traceId(),
                passed ? "success" : "failed",
                passed,
                finalScore,
                normalizedRuleScore,
                judgeResult.score() == null ? null : BigDecimal.valueOf(judgeResult.score()).setScale(2, RoundingMode.HALF_UP),
                judgeResult.passed(),
                judgeResult.skipped() ? judgeResult.skipReason() : judgeResult.rationale(),
                actualReply,
                actualToolCodes,
                actualStepTypes,
                actualFinalStatus,
                baselineScore,
                baselinePassed,
                scoreDelta,
                failureReasons,
                Map.of(
                        "casePassThreshold", caseRecord.passThreshold(),
                        "planVersion", latestPlan == null ? null : latestPlan.planVersion(),
                        "judgeSkipped", judgeResult.skipped()
                )
        );
    }

    private EvalLlmJudgeService.JudgeResult judgeCaseIfNeeded(Map<String, Object> suiteMetadata,
                                                              EvalCaseRecord caseRecord,
                                                              SendMessageResponse response,
                                                              List<String> expectedReplyKeywords,
                                                              List<String> forbiddenReplyKeywords,
                                                              List<String> expectedToolCodes,
                                                              List<String> actualToolCodes,
                                                              List<String> expectedStepTypes,
                                                              List<String> actualStepTypes,
                                                              String actualFinalStatus) {
        String judgeMode = String.valueOf(suiteMetadata.getOrDefault("judgeMode", "rule_only"));
        if (!"rule_and_llm".equals(judgeMode)) {
            return EvalLlmJudgeService.JudgeResult.skipped("当前 Suite 未启用 LLM Judge");
        }
        return evalLlmJudgeService.judge(new EvalLlmJudgeService.EvalJudgePayload(
                caseRecord.userInput(),
                response.assistantMessage().content(),
                expectedReplyKeywords,
                forbiddenReplyKeywords,
                expectedToolCodes,
                actualToolCodes,
                expectedStepTypes,
                actualStepTypes,
                caseRecord.expectedFinalStatus(),
                actualFinalStatus
        ));
    }

    private BigDecimal mergeFinalScore(BigDecimal ruleScore,
                                       EvalLlmJudgeService.JudgeResult judgeResult,
                                       Map<String, Object> suiteMetadata) {
        if (judgeResult == null || judgeResult.skipped() || judgeResult.score() == null) {
            return ruleScore;
        }
        String judgeMode = String.valueOf(suiteMetadata.getOrDefault("judgeMode", "rule_only"));
        if (!"rule_and_llm".equals(judgeMode)) {
            return ruleScore;
        }
        return ruleScore.add(BigDecimal.valueOf(judgeResult.score()))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> buildRunMetadata(EvalSuiteRecord suiteRecord,
                                                 Map<String, Object> suiteMetadata,
                                                 EvalRunRecord baselineRun,
                                                 List<EvalCaseExecutionResult> executionResults,
                                                 BigDecimal averageScore,
                                                 int passedCases,
                                                 int totalCases,
                                                 ExecuteEvalSuiteRequest request,
                                                 CiGateResult ciGateResult) {
        int improvedCaseCount = 0;
        int regressedCaseCount = 0;
        int unchangedCaseCount = 0;
        for (EvalCaseExecutionResult result : executionResults) {
            if (result.scoreDelta() == null) {
                continue;
            }
            int comparison = result.scoreDelta().compareTo(BigDecimal.ZERO);
            if (comparison > 0) {
                improvedCaseCount++;
            } else if (comparison < 0) {
                regressedCaseCount++;
            } else {
                unchangedCaseCount++;
            }
        }
            Map<String, Object> metadata = new HashMap<>();
        metadata.put("suiteCode", suiteRecord.suiteCode());
        metadata.put("isolationMode", true);
        metadata.put("judgeMode", String.valueOf(suiteMetadata.getOrDefault("judgeMode", "rule_only")));
        metadata.put("selectedScenario", normalizeNullableText(request == null ? null : request.scenario()));
        metadata.put("selectedCaseTags", defaultList(request == null ? null : request.caseTags()));
        metadata.put("selectedCaseCount", totalCases);
        if (ciGateResult.enabled()) {
            metadata.put("ciGate", Map.of(
                    "enabled", true,
                    "passed", ciGateResult.passed(),
                    "reason", ciGateResult.reason(),
                    "minimumAverageScore", ciGateResult.minimumAverageScore(),
                    "minimumPassRate", ciGateResult.minimumPassRate(),
                    "maximumFailedCases", ciGateResult.maximumFailedCases()
            ));
        }
        if (baselineRun != null) {
            metadata.put("baselineComparison", Map.of(
                    "baselineRunId", baselineRun.runId(),
                    "baselineRunCode", baselineRun.runCode(),
                    "baselineAverageScore", baselineRun.averageScore(),
                    "scoreDelta", averageScore.subtract(baselineRun.averageScore()).setScale(2, RoundingMode.HALF_UP),
                    "passDelta", passedCases - baselineRun.passedCases(),
                    "improvedCaseCount", improvedCaseCount,
                    "regressedCaseCount", regressedCaseCount,
                    "unchangedCaseCount", unchangedCaseCount
            ));
        }
        return metadata;
    }

    private Map<String, Object> buildRunRequestMetadata(EvalSuiteRecord suiteRecord,
                                                        ExecuteEvalSuiteRequest request) {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("suiteStatus", suiteRecord.status());
        metadata.put("isolationMode", true);
        metadata.put("baselineRunId", request == null ? null : request.baselineRunId());
        metadata.put("scenario", normalizeNullableText(request == null ? null : request.scenario()));
        metadata.put("caseTags", defaultList(request == null ? null : request.caseTags()));
        metadata.put("enforceCiGate", request != null && Boolean.TRUE.equals(request.enforceCiGate()));
        return metadata;
    }

    private Map<String, Object> buildRunCaseMetadata(EvalCaseExecutionResult result) {
        HashMap<String, Object> metadata = new HashMap<>(result.metadata());
        metadata.put("ruleScore", result.ruleScore());
        metadata.put("judgeScore", result.judgeScore());
        metadata.put("judgePassed", result.judgePassed());
        metadata.put("judgeRationale", result.judgeRationale());
        metadata.put("baselineScore", result.baselineScore());
        metadata.put("baselinePassed", result.baselinePassed());
        metadata.put("scoreDelta", result.scoreDelta());
        return metadata;
    }

    private BigDecimal evaluateReplyKeywordScore(List<String> expectedReplyKeywords, String normalizedReply, List<String> failureReasons) {
        if (expectedReplyKeywords.isEmpty()) {
            return BigDecimal.valueOf(REPLY_KEYWORD_SCORE);
        }
        long hitCount = expectedReplyKeywords.stream()
                .filter(item -> normalizedReply.contains(item.toLowerCase(Locale.ROOT)))
                .count();
        if (hitCount != expectedReplyKeywords.size()) {
            List<String> missing = expectedReplyKeywords.stream()
                    .filter(item -> !normalizedReply.contains(item.toLowerCase(Locale.ROOT)))
                    .toList();
            failureReasons.add("回复关键词未全部命中：" + String.join("、", missing));
        }
        return BigDecimal.valueOf(REPLY_KEYWORD_SCORE)
                .multiply(BigDecimal.valueOf(hitCount))
                .divide(BigDecimal.valueOf(expectedReplyKeywords.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal evaluateForbiddenKeywordScore(List<String> forbiddenReplyKeywords, String normalizedReply, List<String> failureReasons) {
        if (forbiddenReplyKeywords.isEmpty()) {
            return BigDecimal.valueOf(FORBIDDEN_KEYWORD_SCORE);
        }
        List<String> matched = forbiddenReplyKeywords.stream()
                .filter(item -> normalizedReply.contains(item.toLowerCase(Locale.ROOT)))
                .toList();
        if (!matched.isEmpty()) {
            failureReasons.add("回复命中禁止关键词：" + String.join("、", matched));
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(FORBIDDEN_KEYWORD_SCORE);
    }

    private BigDecimal evaluateExactExpectation(String label,
                                                List<String> expectedValues,
                                                List<String> actualValues,
                                                int fullScore,
                                                List<String> failureReasons) {
        if (expectedValues.isEmpty()) {
            return BigDecimal.valueOf(fullScore);
        }
        long hitCount = expectedValues.stream().filter(actualValues::contains).count();
        if (hitCount != expectedValues.size()) {
            List<String> missing = expectedValues.stream().filter(item -> !actualValues.contains(item)).toList();
            failureReasons.add(label + " 未全部命中：" + String.join("、", missing));
        }
        return BigDecimal.valueOf(fullScore)
                .multiply(BigDecimal.valueOf(hitCount))
                .divide(BigDecimal.valueOf(expectedValues.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal evaluateFinalStatusScore(String expectedFinalStatus, String actualFinalStatus, List<String> failureReasons) {
        if (expectedFinalStatus == null || expectedFinalStatus.isBlank()) {
            return BigDecimal.valueOf(FINAL_STATUS_SCORE);
        }
        if (expectedFinalStatus.equals(actualFinalStatus)) {
            return BigDecimal.valueOf(FINAL_STATUS_SCORE);
        }
        failureReasons.add("最终状态不匹配，期望 " + expectedFinalStatus + "，实际 " + actualFinalStatus);
        return BigDecimal.ZERO;
    }

    private TaskPlanResponse findLatestPlan(List<TaskPlanResponse> plans) {
        if (plans == null || plans.isEmpty()) {
            return null;
        }
        return plans.stream().max(Comparator.comparing(TaskPlanResponse::planVersion)).orElse(null);
    }

    private List<EvalCaseRecord> filterCasesForExecution(List<EvalCaseRecord> caseRecords,
                                                         ExecuteEvalSuiteRequest request) {
        String scenario = normalizeNullableText(request == null ? null : request.scenario());
        List<String> caseTags = defaultList(request == null ? null : request.caseTags());
        return caseRecords.stream()
                .filter(item -> matchesScenario(item, scenario))
                .filter(item -> matchesTags(item, caseTags))
                .toList();
    }

    private boolean matchesScenario(EvalCaseRecord caseRecord, String scenario) {
        if (scenario == null) {
            return true;
        }
        Object value = readMap(caseRecord.metadata()).get("scenario");
        return scenario.equals(String.valueOf(value == null ? "" : value).trim());
    }

    private boolean matchesTags(EvalCaseRecord caseRecord, List<String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return true;
        }
        List<String> caseTags = readMetadataStringList(readMap(caseRecord.metadata()).get("tags"));
        return requiredTags.stream().allMatch(caseTags::contains);
    }

    private EvalRunRecord resolveBaselineRun(Long suiteId,
                                             Long currentRunId,
                                             ExecuteEvalSuiteRequest request,
                                             Map<String, Object> suiteMetadata) {
        Long explicitRunId = request == null ? null : request.baselineRunId();
        if (explicitRunId != null) {
            EvalRunRecord runRecord = evalRepository.findRunById(explicitRunId)
                    .orElseThrow(() -> new BusinessException("指定的 baseline Run 不存在"));
            if (!Objects.equals(suiteId, runRecord.suiteId())) {
                throw new BusinessException("指定的 baseline Run 不属于当前 Suite");
            }
            return runRecord;
        }
        Long metadataBaselineRunId = toLong(suiteMetadata.get("baselineRunId"));
        if (metadataBaselineRunId != null) {
            EvalRunRecord runRecord = evalRepository.findRunById(metadataBaselineRunId).orElse(null);
            if (runRecord != null && Objects.equals(suiteId, runRecord.suiteId())) {
                return runRecord;
            }
        }
        return evalRepository.findLatestBaselineRun(suiteId, currentRunId).orElse(null);
    }

    private CiGateResult evaluateCiGate(Map<String, Object> suiteMetadata,
                                        ExecuteEvalSuiteRequest request,
                                        BigDecimal averageScore,
                                        int passedCases,
                                        int failedCases,
                                        int totalCases) {
        boolean enforce = request != null && Boolean.TRUE.equals(request.enforceCiGate());
        Map<String, Object> config = readNestedMap(suiteMetadata.get("ciGate"));
        if (!enforce && !Boolean.TRUE.equals(toBoolean(config.get("enabled")))) {
            return CiGateResult.disabled();
        }
        BigDecimal minimumAverageScore = toBigDecimal(config.get("minimumAverageScore"));
        BigDecimal minimumPassRate = toBigDecimal(config.get("minimumPassRate"));
        Integer maximumFailedCases = toInteger(config.get("maximumFailedCases"));
        BigDecimal passRate = BigDecimal.valueOf(passedCases)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCases), 2, RoundingMode.HALF_UP);
        ArrayList<String> reasons = new ArrayList<>();
        if (minimumAverageScore != null && averageScore.compareTo(minimumAverageScore) < 0) {
            reasons.add("平均分低于阈值 " + minimumAverageScore);
        }
        if (minimumPassRate != null && passRate.compareTo(minimumPassRate) < 0) {
            reasons.add("通过率低于阈值 " + minimumPassRate + "%");
        }
        if (maximumFailedCases != null && failedCases > maximumFailedCases) {
            reasons.add("失败用例数超过上限 " + maximumFailedCases);
        }
        return new CiGateResult(true, reasons.isEmpty(), String.join("；", reasons), minimumAverageScore, minimumPassRate, maximumFailedCases);
    }

    private void replaceCases(Long suiteId, List<EvalCaseUpsertRequest> evalCases, Long operatorId, boolean deleteBeforeInsert) {
        if (deleteBeforeInsert) {
            evalRepository.softDeleteCasesBySuiteId(suiteId, operatorId);
        }
        for (EvalCaseUpsertRequest evalCase : evalCases) {
            evalRepository.insertCase(
                    suiteId,
                    evalCase.caseCode().trim(),
                    evalCase.caseName().trim(),
                    evalCase.userInput().trim(),
                    toJsonText(defaultList(evalCase.expectedReplyKeywords())),
                    toJsonText(defaultList(evalCase.forbiddenReplyKeywords())),
                    toJsonText(defaultList(evalCase.expectedToolCodes())),
                    toJsonText(defaultList(evalCase.expectedStepTypes())),
                    normalizeNullableText(evalCase.expectedFinalStatus()),
                    evalCase.passThreshold(),
                    evalCase.orderNo(),
                    evalCase.status().trim(),
                    toJsonText(defaultMap(evalCase.metadata())),
                    operatorId
            );
        }
    }

    private EvalSuiteRecord getSuite(Long suiteId) {
        return evalRepository.findSuiteById(suiteId)
                .orElseThrow(() -> new BusinessException("Eval Suite 不存在"));
    }

    private EvalSuiteDetailResponse toSuiteDetailResponse(EvalSuiteRecord suiteRecord, List<EvalCaseRecord> caseRecords) {
        return new EvalSuiteDetailResponse(
                suiteRecord.suiteId(),
                suiteRecord.currentVersion(),
                suiteRecord.suiteCode(),
                suiteRecord.suiteName(),
                suiteRecord.agentId(),
                suiteRecord.agentName(),
                suiteRecord.description(),
                suiteRecord.status(),
                readMap(suiteRecord.metadata()),
                suiteRecord.createdAt(),
                suiteRecord.updatedAt(),
                caseRecords.stream().map(this::toCaseResponse).toList()
        );
    }

    private EvalCaseResponse toCaseResponse(EvalCaseRecord record) {
        return new EvalCaseResponse(
                record.caseId(),
                record.caseCode(),
                record.caseName(),
                record.userInput(),
                readStringList(record.expectedReplyKeywords()),
                readStringList(record.forbiddenReplyKeywords()),
                readStringList(record.expectedToolCodes()),
                readStringList(record.expectedStepTypes()),
                record.expectedFinalStatus(),
                record.passThreshold(),
                record.orderNo(),
                record.status(),
                readMap(record.metadata()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private EvalRunPageItemResponse toRunPageItemResponse(EvalRunRecord record) {
        return new EvalRunPageItemResponse(
                record.runId(),
                record.suiteId(),
                record.suiteVersion(),
                record.suiteCode(),
                record.suiteName(),
                record.agentId(),
                record.agentName(),
                record.runCode(),
                record.runStatus(),
                record.totalCases(),
                record.passedCases(),
                record.failedCases(),
                record.averageScore(),
                record.triggerType(),
                record.startedAt(),
                record.finishedAt(),
                record.createdAt()
        );
    }

    private EvalRunDetailResponse toRunDetailResponse(EvalRunRecord runRecord, List<EvalRunCaseRecord> runCaseRecords) {
        return new EvalRunDetailResponse(
                runRecord.runId(),
                runRecord.suiteId(),
                runRecord.suiteVersion(),
                runRecord.suiteCode(),
                runRecord.suiteName(),
                runRecord.agentId(),
                runRecord.agentName(),
                runRecord.runCode(),
                runRecord.runStatus(),
                runRecord.totalCases(),
                runRecord.passedCases(),
                runRecord.failedCases(),
                runRecord.averageScore(),
                runRecord.triggerType(),
                readMap(runRecord.metadata()),
                toBaselineComparison(readMap(runRecord.metadata())),
                runRecord.startedAt(),
                runRecord.finishedAt(),
                runRecord.createdAt(),
                runCaseRecords.stream().map(this::toRunCaseResponse).toList()
        );
    }

    private EvalRunCaseResponse toRunCaseResponse(EvalRunCaseRecord record) {
        return new EvalRunCaseResponse(
                record.runCaseId(),
                record.caseId(),
                record.caseCode(),
                record.caseName(),
                record.conversationId(),
                record.taskId(),
                record.traceId(),
                record.runStatus(),
                record.passed(),
                record.score(),
                readBigDecimal(record.metadata(), "ruleScore"),
                readBigDecimal(record.metadata(), "judgeScore"),
                readBoolean(record.metadata(), "judgePassed"),
                readText(record.metadata(), "judgeRationale"),
                record.actualReply(),
                readStringList(record.actualToolCodes()),
                readStringList(record.actualStepTypes()),
                record.actualFinalStatus(),
                record.metadata() == null ? null : readBigDecimal(record.metadata(), "baselineScore"),
                readBoolean(record.metadata(), "baselinePassed"),
                readBigDecimal(record.metadata(), "scoreDelta"),
                readStringList(record.failureReasons()),
                readMap(record.metadata()),
                record.startedAt(),
                record.finishedAt(),
                record.createdAt()
        );
    }

    private Map<String, EvalRunCaseRecord> toRunCaseMap(List<EvalRunCaseRecord> records) {
        LinkedHashMap<String, EvalRunCaseRecord> result = new LinkedHashMap<>();
        for (EvalRunCaseRecord record : records) {
            result.put(record.caseCode(), record);
        }
        return result;
    }

    private EvalSuiteVersionResponse toSuiteVersionResponse(EvalSuiteVersionRecord record) {
        return new EvalSuiteVersionResponse(
                record.versionId(),
                record.suiteId(),
                record.versionNo(),
                record.suiteCode(),
                record.suiteName(),
                record.agentName(),
                record.status(),
                readCaseSnapshots(record.casesJson()).size(),
                record.metadata(),
                record.createdAt(),
                record.createdBy()
        );
    }

    private void createSuiteVersionSnapshot(Long suiteId,
                                            Integer versionNo,
                                            CreateEvalSuiteRequest request,
                                            Long operatorId) {
        evalRepository.insertSuiteVersion(
                suiteId,
                versionNo,
                request.suiteCode().trim(),
                request.suiteName().trim(),
                request.agentId(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.status().trim(),
                toJsonText(defaultMap(request.metadata())),
                toJsonText(request.evalCases()),
                operatorId
        );
    }

    private void createSuiteVersionSnapshot(Long suiteId,
                                            Integer versionNo,
                                            UpdateEvalSuiteRequest request,
                                            Long operatorId) {
        evalRepository.insertSuiteVersion(
                suiteId,
                versionNo,
                request.suiteCode().trim(),
                request.suiteName().trim(),
                request.agentId(),
                request.agentName().trim(),
                normalizeNullableText(request.description()),
                request.status().trim(),
                toJsonText(defaultMap(request.metadata())),
                toJsonText(request.evalCases()),
                operatorId
        );
    }

    private List<EvalSuiteDiffResponse.DiffItem> buildSuiteDiffItems(EvalSuiteVersionRecord fromRecord,
                                                                     EvalSuiteVersionRecord toRecord) {
        ArrayList<EvalSuiteDiffResponse.DiffItem> items = new ArrayList<>();
        appendFieldDiff(items, "suiteName", fromRecord.suiteName(), toRecord.suiteName());
        appendFieldDiff(items, "agentName", fromRecord.agentName(), toRecord.agentName());
        appendFieldDiff(items, "status", fromRecord.status(), toRecord.status());
        appendFieldDiff(items, "description", fromRecord.description(), toRecord.description());
        Map<String, CaseSnapshot> fromCases = mapCaseSnapshots(readCaseSnapshots(fromRecord.casesJson()));
        Map<String, CaseSnapshot> toCases = mapCaseSnapshots(readCaseSnapshots(toRecord.casesJson()));
        LinkedHashSet<String> caseCodes = new LinkedHashSet<>();
        caseCodes.addAll(fromCases.keySet());
        caseCodes.addAll(toCases.keySet());
        for (String caseCode : caseCodes) {
            CaseSnapshot fromCase = fromCases.get(caseCode);
            CaseSnapshot toCase = toCases.get(caseCode);
            if (fromCase == null) {
                items.add(new EvalSuiteDiffResponse.DiffItem("added", "case:" + caseCode, null, caseSummary(toCase)));
                continue;
            }
            if (toCase == null) {
                items.add(new EvalSuiteDiffResponse.DiffItem("removed", "case:" + caseCode, caseSummary(fromCase), null));
                continue;
            }
            if (!Objects.equals(caseSummary(fromCase), caseSummary(toCase))) {
                items.add(new EvalSuiteDiffResponse.DiffItem("changed", "case:" + caseCode, caseSummary(fromCase), caseSummary(toCase)));
            }
        }
        return items;
    }

    private void appendFieldDiff(List<EvalSuiteDiffResponse.DiffItem> items, String key, String beforeText, String afterText) {
        if (!Objects.equals(beforeText, afterText)) {
            items.add(new EvalSuiteDiffResponse.DiffItem("changed", key, beforeText, afterText));
        }
    }

    private Map<String, CaseSnapshot> mapCaseSnapshots(List<CaseSnapshot> caseSnapshots) {
        LinkedHashMap<String, CaseSnapshot> result = new LinkedHashMap<>();
        for (CaseSnapshot caseSnapshot : caseSnapshots) {
            result.put(caseSnapshot.caseCode(), caseSnapshot);
        }
        return result;
    }

    private String caseSummary(CaseSnapshot caseSnapshot) {
        return caseSnapshot.caseName()
                + " | status=" + caseSnapshot.status()
                + " | threshold=" + caseSnapshot.passThreshold()
                + " | tools=" + String.join(",", caseSnapshot.expectedToolCodes())
                + " | steps=" + String.join(",", caseSnapshot.expectedStepTypes())
                + " | input=" + caseSnapshot.userInput();
    }

    private void validateDistinctCaseCodes(List<EvalCaseUpsertRequest> evalCases) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (EvalCaseUpsertRequest evalCase : evalCases) {
            String normalized = evalCase.caseCode().trim();
            if (!codes.add(normalized)) {
                throw new BusinessException("存在重复的 Eval 用例编码: " + normalized);
            }
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> defaultList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(item -> item != null && !item.isBlank()).map(String::trim).toList();
    }

    private Map<String, Object> defaultMap(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    private Map<String, Object> readNestedMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private String toJsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("Eval 数据序列化失败");
        }
    }

    private List<String> readStringList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(jsonText, new TypeReference<List<String>>() {
            });
            return values == null ? List.of() : values.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(jsonText, new TypeReference<Map<String, Object>>() {
            });
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<CaseSnapshot> readCaseSnapshots(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            List<CaseSnapshot> snapshots = objectMapper.readValue(jsonText, new TypeReference<List<CaseSnapshot>>() {
            });
            return snapshots == null ? List.of() : snapshots;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private EvalRunDetailResponse.BaselineComparisonResponse toBaselineComparison(Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get("baselineComparison") instanceof Map<?, ?> comparison)) {
            return null;
        }
        return new EvalRunDetailResponse.BaselineComparisonResponse(
                toLong(comparison.get("baselineRunId")),
                comparison.get("baselineRunCode") == null ? null : String.valueOf(comparison.get("baselineRunCode")),
                toBigDecimal(comparison.get("baselineAverageScore")),
                toBigDecimal(comparison.get("scoreDelta")),
                toInteger(comparison.get("passDelta")),
                toInteger(comparison.get("improvedCaseCount")),
                toInteger(comparison.get("regressedCaseCount")),
                toInteger(comparison.get("unchangedCaseCount"))
        );
    }

    private BigDecimal readBigDecimal(String metadataJson, String key) {
        Map<String, Object> metadata = readMap(metadataJson);
        return toBigDecimal(metadata.get(key));
    }

    private Boolean readBoolean(String metadataJson, String key) {
        Map<String, Object> metadata = readMap(metadataJson);
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private String readText(String metadataJson, String key) {
        Map<String, Object> metadata = readMap(metadataJson);
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private List<String> readMetadataStringList(Object value) {
        if (value instanceof List<?> rawList) {
            return rawList.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        return List.of();
    }

    private record EvalCaseExecutionResult(
            Long conversationId,
            Long taskId,
            String traceId,
            String runStatus,
            Boolean passed,
            BigDecimal score,
            BigDecimal ruleScore,
            BigDecimal judgeScore,
            Boolean judgePassed,
            String judgeRationale,
            String actualReply,
            List<String> actualToolCodes,
            List<String> actualStepTypes,
            String actualFinalStatus,
            BigDecimal baselineScore,
            Boolean baselinePassed,
            BigDecimal scoreDelta,
            List<String> failureReasons,
            Map<String, Object> metadata
    ) {
    }

    private record CaseSnapshot(
            String caseCode,
            String caseName,
            String userInput,
            List<String> expectedReplyKeywords,
            List<String> forbiddenReplyKeywords,
            List<String> expectedToolCodes,
            List<String> expectedStepTypes,
            String expectedFinalStatus,
            Integer passThreshold,
            Integer orderNo,
            String status,
            Map<String, Object> metadata
    ) {
    }

    private record CiGateResult(
            boolean enabled,
            boolean passed,
            String reason,
            BigDecimal minimumAverageScore,
            BigDecimal minimumPassRate,
            Integer maximumFailedCases
    ) {
        private static CiGateResult disabled() {
            return new CiGateResult(false, true, null, null, null, null);
        }
    }
}

package com.zc.agent.core.modules.knowledge.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.knowledge.model.KnowledgeModels;
import com.zc.agent.core.modules.knowledge.service.KnowledgeRetrievalService;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Knowledge 检索调试与评测接口。
 */
@RestController
@RequestMapping("/api/core/knowledge")
public class KnowledgeRuntimeController {

    private final KnowledgeRetrievalService service;

    public KnowledgeRuntimeController(KnowledgeRetrievalService service) {
        this.service = service;
    }

    /** 执行带权限校验的知识检索。 */
    @PostMapping("/search")
    public ApiResponse<KnowledgeModels.SearchResponse> search(@Valid @RequestBody KnowledgeModels.SearchRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(service.search(request, (SecurityUser) authentication.getPrincipal()));
    }

    /** 执行检索评测。 */
    @PostMapping("/eval-runs")
    public ApiResponse<KnowledgeModels.EvalResponse> evaluate(@Valid @RequestBody KnowledgeModels.EvalRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(service.evaluate(request, (SecurityUser) authentication.getPrincipal()));
    }

    /** 查看运行时实际索引分块。 */
    @GetMapping("/chunks")
    public ApiResponse<java.util.List<KnowledgeModels.RuntimeChunk>> chunks(@RequestParam Long agentId,
                                                                            @RequestParam Long knowledgeId,
                                                                            @RequestParam(defaultValue = "1") int pageNum,
                                                                            @RequestParam(defaultValue = "50") int pageSize,
                                                                            Authentication authentication) {
        return ApiResponse.success(service.listChunks(agentId, knowledgeId, pageNum, pageSize,
                (SecurityUser) authentication.getPrincipal()));
    }

    /** 查询历史检索评测运行。 */
    @GetMapping("/eval-runs")
    public ApiResponse<java.util.List<KnowledgeModels.EvalRunSummary>> evalRuns(@RequestParam(required = false) Long agentId) {
        return ApiResponse.success(service.listEvalRuns(agentId));
    }
}

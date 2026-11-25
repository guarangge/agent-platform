package com.zc.agent.registry.modules.workflow.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.workflow.dto.CreateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.dto.UpdateWorkflowRequest;
import com.zc.agent.registry.modules.workflow.dto.WorkflowPageQueryRequest;
import com.zc.agent.registry.modules.workflow.service.WorkflowService;
import com.zc.agent.registry.modules.workflow.service.WorkflowDefinitionValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.zc.agent.registry.modules.workflow.vo.WorkflowDetailResponse;
import com.zc.agent.registry.modules.workflow.vo.WorkflowPageItemResponse;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow 管理接口。
 */
@RestController
@RequestMapping("/api/registry/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * 分页查询 Workflow。
     */
    @GetMapping
    public ApiResponse<PageResponse<WorkflowPageItemResponse>> pageWorkflows(@Valid WorkflowPageQueryRequest request) {
        return ApiResponse.success(workflowService.pageWorkflows(request));
    }

    /**
     * 查询 Workflow 详情。
     */
    @GetMapping("/{workflowId}")
    public ApiResponse<WorkflowDetailResponse> getWorkflowDetail(@PathVariable Long workflowId) {
        return ApiResponse.success(workflowService.getWorkflowDetail(workflowId));
    }

    /**
     * 新增 Workflow。
     */
    @PostMapping
    public ApiResponse<WorkflowDetailResponse> createWorkflow(@Valid @RequestBody CreateWorkflowRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(workflowService.createWorkflow(request, getSecurityUser(authentication)));
    }

    /**
     * 编辑 Workflow。
     */
    @PutMapping("/{workflowId}")
    public ApiResponse<WorkflowDetailResponse> updateWorkflow(@PathVariable Long workflowId,
                                                              @Valid @RequestBody UpdateWorkflowRequest request,
                                                              Authentication authentication) {
        return ApiResponse.success(workflowService.updateWorkflow(workflowId, request, getSecurityUser(authentication)));
    }

    /**
     * 发布 Workflow。
     */
    @PutMapping("/{workflowId}/publish")
    public ApiResponse<Void> publishWorkflow(@PathVariable Long workflowId, Authentication authentication) {
        workflowService.publishWorkflow(workflowId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    /** 发布前校验 Workflow 定义，不修改资源状态。 */
    @PostMapping("/validate")
    public ApiResponse<WorkflowDefinitionValidator.ValidationReport> validateDefinition(@RequestBody JsonNode definitionJson) {
        return ApiResponse.success(workflowService.validateDefinition(definitionJson));
    }

    /**
     * 停用 Workflow。
     */
    @PutMapping("/{workflowId}/disable")
    public ApiResponse<Void> disableWorkflow(@PathVariable Long workflowId, Authentication authentication) {
        workflowService.disableWorkflow(workflowId, getSecurityUser(authentication));
        return ApiResponse.success(null);
    }

    private SecurityUser getSecurityUser(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

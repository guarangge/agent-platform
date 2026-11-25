package com.zc.agent.core.modules.conversation.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.common.response.PageResponse;
import com.zc.agent.core.modules.conversation.dto.TaskOperationsPageQueryRequest;
import com.zc.agent.core.modules.conversation.service.TaskOperationsService;
import com.zc.agent.core.modules.conversation.vo.TaskOperationsPageItemResponse;
import com.zc.agent.core.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 任务运行中心接口。 */
@RestController
@RequestMapping("/api/core/tasks")
public class TaskOperationsController {

    private final TaskOperationsService taskOperationsService;

    public TaskOperationsController(TaskOperationsService taskOperationsService) {
        this.taskOperationsService = taskOperationsService;
    }

    /** 分页查询当前用户的任务运行记录。 */
    @GetMapping
    public ApiResponse<PageResponse<TaskOperationsPageItemResponse>> page(
            @Valid TaskOperationsPageQueryRequest request,
            Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return ApiResponse.success(taskOperationsService.page(request, securityUser));
    }
}

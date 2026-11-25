package com.zc.agent.registry.modules.prompt.controller;
import com.zc.agent.registry.common.response.ApiResponse;import com.zc.agent.registry.modules.prompt.dto.PromptExperimentRequests.*;import com.zc.agent.registry.modules.prompt.service.PromptExperimentService;import com.zc.agent.registry.security.SecurityUser;import jakarta.validation.Valid;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.*;
/** Prompt A/B 实验管理和运行时稳定分流接口。 */
@RestController @RequestMapping("/api/registry/prompt-experiments") public class PromptExperimentController{private final PromptExperimentService service;public PromptExperimentController(PromptExperimentService service){this.service=service;}
 @GetMapping public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.success(service.list());}
 @PostMapping public ApiResponse<Void> create(@Valid @RequestBody ExperimentRequest r,Authentication a){service.create(r,user(a));return ApiResponse.success(null);}
 @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable Long id,@Valid @RequestBody ExperimentRequest r,Authentication a){service.update(id,r,user(a));return ApiResponse.success(null);}
 @PostMapping("/{id}/complete") public ApiResponse<Void> complete(@PathVariable Long id,@Valid @RequestBody CompleteRequest r,Authentication a){service.complete(id,r.winnerPromptId(),user(a));return ApiResponse.success(null);}
 @PostMapping("/resolve") public ApiResponse<Map<String,Object>> resolve(@Valid @RequestBody ResolveRequest r){return ApiResponse.success(service.resolve(r));}
 private SecurityUser user(Authentication a){return(SecurityUser)a.getPrincipal();}}

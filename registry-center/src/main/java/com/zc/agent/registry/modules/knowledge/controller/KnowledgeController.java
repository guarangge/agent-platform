package com.zc.agent.registry.modules.knowledge.controller;

import com.zc.agent.registry.common.response.ApiResponse;
import com.zc.agent.registry.common.response.PageResponse;
import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import com.zc.agent.registry.modules.knowledge.service.KnowledgeService;
import com.zc.agent.registry.modules.knowledge.service.KnowledgeDocumentUploadService;
import com.zc.agent.registry.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * Knowledge 管理与运行时发现接口。
 */
@RestController
@RequestMapping("/api/registry/knowledge-bases")
public class KnowledgeController {

    private final KnowledgeService service;
    private final KnowledgeDocumentUploadService uploadService;

    public KnowledgeController(KnowledgeService service, KnowledgeDocumentUploadService uploadService) {
        this.service = service;
        this.uploadService = uploadService;
    }

    /** 分页查询知识库。 */
    @GetMapping
    public ApiResponse<PageResponse<KnowledgeContracts.KnowledgePageItem>> page(@Valid KnowledgeContracts.PageQuery request) {
        return ApiResponse.success(service.page(request));
    }

    /** 查询知识库详情。 */
    @GetMapping("/{knowledgeId}")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> detail(@PathVariable Long knowledgeId) {
        return ApiResponse.success(service.detail(knowledgeId));
    }

    /** 查询 Agent 和角色绑定候选。 */
    @GetMapping("/binding-options")
    public ApiResponse<KnowledgeContracts.BindingOptions> bindingOptions() {
        return ApiResponse.success(service.bindingOptions());
    }

    /** 创建知识库。 */
    @PostMapping
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> create(@Valid @RequestBody KnowledgeContracts.SaveKnowledgeRequest request,
                                                                  Authentication authentication) {
        return ApiResponse.success(service.create(request, user(authentication)));
    }

    /** 更新知识库。 */
    @PutMapping("/{knowledgeId}")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> update(@PathVariable Long knowledgeId,
                                                                  @Valid @RequestBody KnowledgeContracts.SaveKnowledgeRequest request,
                                                                  Authentication authentication) {
        return ApiResponse.success(service.update(knowledgeId, request, user(authentication)));
    }

    /** 创建知识来源。 */
    @PostMapping("/{knowledgeId}/sources")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> createSource(@PathVariable Long knowledgeId,
                                                                        @Valid @RequestBody KnowledgeContracts.SaveSourceRequest request,
                                                                        Authentication authentication) {
        return ApiResponse.success(service.createSource(knowledgeId, request, user(authentication)));
    }

    /** 更新知识来源。 */
    @PutMapping("/{knowledgeId}/sources/{sourceId}")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> updateSource(@PathVariable Long knowledgeId,
                                                                        @PathVariable Long sourceId,
                                                                        @Valid @RequestBody KnowledgeContracts.SaveSourceRequest request,
                                                                        Authentication authentication) {
        return ApiResponse.success(service.updateSource(knowledgeId, sourceId, request, user(authentication)));
    }

    /** 创建知识文档。 */
    @PostMapping("/{knowledgeId}/documents")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> createDocument(@PathVariable Long knowledgeId,
                                                                          @Valid @RequestBody KnowledgeContracts.SaveDocumentRequest request,
                                                                          Authentication authentication) {
        return ApiResponse.success(service.createDocument(knowledgeId, request, user(authentication)));
    }

    /** 更新知识文档。 */
    @PutMapping("/{knowledgeId}/documents/{documentId}")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> updateDocument(@PathVariable Long knowledgeId,
                                                                          @PathVariable Long documentId,
                                                                          @Valid @RequestBody KnowledgeContracts.SaveDocumentRequest request,
                                                                          Authentication authentication) {
        return ApiResponse.success(service.updateDocument(knowledgeId, documentId, request, user(authentication)));
    }

    /** 上传并解析知识文档。 */
    @PostMapping(path = "/{knowledgeId}/documents/upload", consumes = "multipart/form-data")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> uploadDocument(@PathVariable Long knowledgeId,
            @RequestParam(required = false) Long sourceId, @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.success(uploadService.upload(knowledgeId, sourceId, file, user(authentication)));
    }

    /** 在线读取上传文档的原始文件。 */
    @GetMapping("/{knowledgeId}/documents/{documentId}/preview")
    public ResponseEntity<byte[]> previewDocument(@PathVariable Long knowledgeId, @PathVariable Long documentId) {
        KnowledgeDocumentUploadService.StoredDocument document = uploadService.loadOriginal(knowledgeId, documentId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(document.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(document.content());
    }

    /** 使用原始文件重新解析知识文档。 */
    @PostMapping("/{knowledgeId}/documents/{documentId}/reparse")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> reparseDocument(@PathVariable Long knowledgeId,
            @PathVariable Long documentId, Authentication authentication) {
        return ApiResponse.success(uploadService.reparse(knowledgeId, documentId, user(authentication)));
    }

    /** 更新 Agent 绑定和角色权限。 */
    @PutMapping("/{knowledgeId}/bindings")
    public ApiResponse<KnowledgeContracts.KnowledgeDetail> updateBindings(@PathVariable Long knowledgeId,
                                                                          @RequestBody KnowledgeContracts.UpdateBindingsRequest request,
                                                                          Authentication authentication) {
        return ApiResponse.success(service.updateBindings(knowledgeId, request, user(authentication)));
    }

    /** 创建索引任务。 */
    @PostMapping("/{knowledgeId}/index-tasks")
    public ApiResponse<KnowledgeContracts.IndexTaskItem> createIndexTask(@PathVariable Long knowledgeId,
                                                                         @RequestBody(required = false) KnowledgeContracts.CreateIndexTaskRequest request,
                                                                         Authentication authentication) {
        return ApiResponse.success(service.createIndexTask(knowledgeId, request, user(authentication)));
    }

    /** 运行时查询 Agent 有权限使用的知识快照。 */
    @GetMapping("/runtime/agents/{agentId}")
    public ApiResponse<KnowledgeContracts.RuntimeKnowledgeBundle> runtimeBundle(@PathVariable Long agentId,
                                                                                @RequestParam(required = false) List<String> roleCodes) {
        return ApiResponse.success(service.runtimeBundle(agentId, roleCodes));
    }

    /** 运行时按知识库读取索引快照。 */
    @GetMapping("/runtime/{knowledgeId}")
    public ApiResponse<KnowledgeContracts.RuntimeKnowledge> runtimeKnowledge(@PathVariable Long knowledgeId) {
        return ApiResponse.success(service.runtimeKnowledge(knowledgeId));
    }

    /** 运行时领取待执行索引任务。 */
    @PostMapping("/runtime/index-tasks/claim")
    public ApiResponse<KnowledgeContracts.IndexTaskItem> claimIndexTask() {
        return ApiResponse.success(service.claimIndexTask());
    }

    /** 运行时回写索引任务结果。 */
    @PutMapping("/runtime/index-tasks/{taskId}/complete")
    public ApiResponse<Void> completeIndexTask(@PathVariable Long taskId,
                                               @Valid @RequestBody KnowledgeContracts.CompleteIndexTaskRequest request) {
        service.completeIndexTask(taskId, request);
        return ApiResponse.success(null);
    }

    private SecurityUser user(Authentication authentication) {
        return (SecurityUser) authentication.getPrincipal();
    }
}

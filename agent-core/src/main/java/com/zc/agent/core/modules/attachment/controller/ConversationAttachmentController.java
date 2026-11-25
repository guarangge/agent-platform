package com.zc.agent.core.modules.attachment.controller;

import com.zc.agent.core.common.response.ApiResponse;
import com.zc.agent.core.modules.attachment.model.AttachmentContent;
import com.zc.agent.core.modules.attachment.service.ConversationAttachmentService;
import com.zc.agent.core.modules.attachment.vo.AttachmentResponse;
import com.zc.agent.core.security.SecurityUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/** 会话附件上传与下载接口。 */
@RestController
@RequestMapping("/api/core/conversations/{conversationId}/attachments")
public class ConversationAttachmentController {

    private final ConversationAttachmentService attachmentService;

    public ConversationAttachmentController(ConversationAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** 上传一个图片附件。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> upload(@PathVariable Long conversationId,
                                                  @RequestParam("file") MultipartFile file,
                                                  Authentication authentication) {
        return ApiResponse.success(attachmentService.upload(
                conversationId, file, (SecurityUser) authentication.getPrincipal()));
    }

    /** 下载一个当前用户有权访问的附件。 */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable Long conversationId,
                                           @PathVariable Long attachmentId,
                                           Authentication authentication) {
        AttachmentContent content = attachmentService.download(
                conversationId, attachmentId, (SecurityUser) authentication.getPrincipal());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length)
                .body(content.bytes());
    }

    /** 删除尚未发送的附件。 */
    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable Long conversationId,
                                    @PathVariable Long attachmentId,
                                    Authentication authentication) {
        attachmentService.deleteUnbound(
                conversationId, attachmentId, (SecurityUser) authentication.getPrincipal());
        return ApiResponse.success(null);
    }
}

package com.zc.agent.registry.modules.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import com.zc.agent.registry.modules.knowledge.repository.KnowledgeRepository;
import com.zc.agent.registry.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentUploadServiceTest {

    @TempDir
    Path storageRoot;

    @Test
    void shouldParseUtf8TextAndCreateDocument() throws Exception {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                knowledgeService, mock(KnowledgeRepository.class), new ObjectMapper(),
                mock(KnowledgePdfTextExtractor.class),
                mock(KnowledgeDocxTextExtractor.class),
                storageRoot.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "产品说明.txt", "text/plain",
                "这是解析后的知识内容。".getBytes(StandardCharsets.UTF_8));
        SecurityUser user = new SecurityUser(1L, "tester", "测试用户", List.of("ADMIN"), List.of("knowledge"));

        service.upload(9L, 3L, file, user);

        ArgumentCaptor<KnowledgeContracts.SaveDocumentRequest> captor =
                ArgumentCaptor.forClass(KnowledgeContracts.SaveDocumentRequest.class);
        verify(knowledgeService).createDocument(eq(9L), captor.capture(), eq(user));
        assertThat(captor.getValue().documentName()).isEqualTo("产品说明.txt");
        assertThat(captor.getValue().content()).isEqualTo("这是解析后的知识内容。");
        assertThat(captor.getValue().sourceId()).isEqualTo(3L);
        assertThat(Files.walk(storageRoot).filter(Files::isRegularFile).count()).isEqualTo(1);
    }

    @Test
    void shouldSaveDocxOcrMetadata() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        KnowledgeDocxTextExtractor docxTextExtractor = mock(KnowledgeDocxTextExtractor.class);
        when(docxTextExtractor.extract(any())).thenReturn(new KnowledgeDocxTextExtractor.DocxExtractionResult(
                "Word 正文\n[图片 OCR]\n图片文字", "docx-structured-ocr", 2, 1, true));
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                knowledgeService, mock(KnowledgeRepository.class), new ObjectMapper(),
                mock(KnowledgePdfTextExtractor.class), docxTextExtractor,
                storageRoot.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "图文文档.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{1, 2, 3});
        SecurityUser user = new SecurityUser(1L, "tester", "测试用户", List.of("ADMIN"), List.of("knowledge"));

        service.upload(9L, 3L, file, user);

        ArgumentCaptor<KnowledgeContracts.SaveDocumentRequest> captor =
                ArgumentCaptor.forClass(KnowledgeContracts.SaveDocumentRequest.class);
        verify(knowledgeService).createDocument(eq(9L), captor.capture(), eq(user));
        assertThat(captor.getValue().content()).contains("Word 正文", "图片文字");
        assertThat(captor.getValue().metadata().path("parser").asText()).isEqualTo("docx-structured-ocr");
        assertThat(captor.getValue().metadata().path("embeddedImageCount").asInt()).isEqualTo(2);
        assertThat(captor.getValue().metadata().path("ocrImageCount").asInt()).isEqualTo(1);
        assertThat(captor.getValue().metadata().path("ocrApplied").asBoolean()).isTrue();
    }

    @Test
    void shouldRejectUnsupportedFileWithoutWriting() {
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                mock(KnowledgeService.class), mock(KnowledgeRepository.class), new ObjectMapper(),
                mock(KnowledgePdfTextExtractor.class),
                mock(KnowledgeDocxTextExtractor.class),
                storageRoot.toString(), 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "program.exe", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(9L, null, file, mock(SecurityUser.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支持 PDF、DOCX、Markdown 和 TXT 文件");
        assertThat(storageRoot.toFile().listFiles()).isEmpty();
    }

    @Test
    void shouldLoadOriginalDocumentWithinKnowledgeBoundary() throws Exception {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        Path storedFile = storageRoot.resolve("9/document.pdf");
        Files.createDirectories(storedFile.getParent());
        Files.write(storedFile, "%PDF-preview".getBytes(StandardCharsets.US_ASCII));
        when(repository.findDocument(9L, 7L)).thenReturn(Optional.of(new KnowledgeRepository.DocumentRecord(
                7L, null, "DOC_7", "预览.pdf", "application/pdf", "local://9/document.pdf", "text", "hash",
                1, "enabled", "{\"storageKey\":\"9/document.pdf\",\"originalFilename\":\"预览.pdf\"}", null, null)));
        KnowledgeDocumentUploadService service = new KnowledgeDocumentUploadService(
                mock(KnowledgeService.class), repository, new ObjectMapper(), mock(KnowledgePdfTextExtractor.class),
                mock(KnowledgeDocxTextExtractor.class),
                storageRoot.toString(), 1024 * 1024);

        KnowledgeDocumentUploadService.StoredDocument result = service.loadOriginal(9L, 7L);

        assertThat(result.filename()).isEqualTo("预览.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).isEqualTo("%PDF-preview".getBytes(StandardCharsets.US_ASCII));
        verify(repository).findDocument(9L, 7L);
    }
}

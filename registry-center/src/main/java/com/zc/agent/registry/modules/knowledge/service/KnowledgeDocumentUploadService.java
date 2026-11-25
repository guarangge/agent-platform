package com.zc.agent.registry.modules.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zc.agent.registry.common.exception.BusinessException;
import com.zc.agent.registry.modules.knowledge.dto.KnowledgeContracts;
import com.zc.agent.registry.modules.knowledge.repository.KnowledgeRepository;
import com.zc.agent.registry.security.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

/** 知识文档原始文件存储与正文解析服务。 */
@Service
public class KnowledgeDocumentUploadService {
    private final KnowledgeService knowledgeService;
    private final KnowledgeRepository repository;
    private final ObjectMapper objectMapper;
    private final KnowledgePdfTextExtractor pdfTextExtractor;
    private final KnowledgeDocxTextExtractor docxTextExtractor;
    private final Path storageRoot;
    private final long maxFileBytes;

    public KnowledgeDocumentUploadService(KnowledgeService knowledgeService, KnowledgeRepository repository,
            ObjectMapper objectMapper, KnowledgePdfTextExtractor pdfTextExtractor,
            KnowledgeDocxTextExtractor docxTextExtractor,
            @Value("${registry.knowledge.storage-root:${java.io.tmpdir}/agent-knowledge}") String storageRoot,
            @Value("${registry.knowledge.max-file-bytes:20971520}") long maxFileBytes) {
        this.knowledgeService = knowledgeService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.pdfTextExtractor = pdfTextExtractor;
        this.docxTextExtractor = docxTextExtractor;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes;
    }

    /** 上传原始文件，解析成功后创建待索引文档。 */
    public KnowledgeContracts.KnowledgeDetail upload(Long knowledgeId, Long sourceId, MultipartFile file,
                                                       SecurityUser user) {
        byte[] bytes = read(file);
        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        ParsedFile parsed = parse(extension, bytes);
        String storageKey = knowledgeId + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolveSafely(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            ObjectNode metadata = JsonNodeFactory.instance.objectNode();
            metadata.put("originalFilename", filename);
            metadata.put("storageProvider", "local");
            metadata.put("storageKey", storageKey);
            metadata.put("fileSize", bytes.length);
            metadata.put("parser", parsed.parser());
            if (parsed.pageCount() != null) metadata.put("pageCount", parsed.pageCount());
            metadata.put("ocrApplied", parsed.ocrApplied());
            if (parsed.embeddedImageCount() != null) metadata.put("embeddedImageCount", parsed.embeddedImageCount());
            if (parsed.ocrImageCount() != null) metadata.put("ocrImageCount", parsed.ocrImageCount());
            var request = new KnowledgeContracts.SaveDocumentRequest(sourceId,
                    "DOC_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT),
                    filename, parsed.contentType(), "local://" + storageKey, parsed.content(), "enabled", metadata);
            return knowledgeService.createDocument(knowledgeId, request, user);
        } catch (IOException exception) {
            throw new BusinessException("知识文档文件存储失败");
        } catch (RuntimeException exception) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { /* 尽力补偿。 */ }
            throw exception;
        }
    }

    /** 读取上传文档的原始文件，用于受鉴权的页面预览。 */
    public StoredDocument loadOriginal(Long knowledgeId, Long documentId) {
        KnowledgeRepository.DocumentRecord document = repository.findDocument(knowledgeId, documentId)
                .orElseThrow(() -> new BusinessException("知识文档不存在"));
        try {
            JsonNode metadata = objectMapper.readTree(document.metadata());
            String storageKey = metadata.path("storageKey").asText("");
            String filename = metadata.path("originalFilename").asText(document.name());
            if (storageKey.isBlank()) throw new BusinessException("该文档没有可预览的原始文件");
            Path target = resolveSafely(storageKey);
            if (!Files.isRegularFile(target)) throw new BusinessException("文档原始文件不存在");
            long size = Files.size(target);
            if (size > maxFileBytes) throw new BusinessException("文档原始文件超过预览限制");
            return new StoredDocument(filename, document.contentType(), Files.readAllBytes(target));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("读取文档原始文件失败");
        }
    }

    /** 使用已保存的原始文件重新解析文档，并使旧索引失效。 */
    public KnowledgeContracts.KnowledgeDetail reparse(Long knowledgeId, Long documentId, SecurityUser user) {
        KnowledgeRepository.DocumentRecord document = repository.findDocument(knowledgeId, documentId)
                .orElseThrow(() -> new BusinessException("知识文档不存在"));
        StoredDocument original = loadOriginal(knowledgeId, documentId);
        ParsedFile parsed = parse(extension(original.filename()), original.content());
        try {
            JsonNode currentMetadata = objectMapper.readTree(document.metadata());
            ObjectNode metadata = currentMetadata != null && currentMetadata.isObject()
                    ? (ObjectNode) currentMetadata.deepCopy() : JsonNodeFactory.instance.objectNode();
            metadata.put("parser", parsed.parser());
            if (parsed.pageCount() != null) metadata.put("pageCount", parsed.pageCount());
            metadata.put("ocrApplied", parsed.ocrApplied());
            if (parsed.embeddedImageCount() != null) metadata.put("embeddedImageCount", parsed.embeddedImageCount());
            if (parsed.ocrImageCount() != null) metadata.put("ocrImageCount", parsed.ocrImageCount());
            KnowledgeContracts.SaveDocumentRequest request = new KnowledgeContracts.SaveDocumentRequest(
                    document.sourceId(), document.code(), document.name(), parsed.contentType(), document.sourceUri(),
                    parsed.content(), document.status(), metadata);
            return knowledgeService.updateDocument(knowledgeId, documentId, request, user);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("更新重新解析结果失败");
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("上传文件不能为空");
        if (file.getSize() > maxFileBytes) throw new BusinessException("上传文件不能超过 " + maxFileBytes / 1048576 + "MB");
        try { return file.getBytes(); } catch (IOException exception) { throw new BusinessException("读取上传文件失败"); }
    }

    private ParsedFile parse(String extension, byte[] bytes) {
        try {
            KnowledgePdfTextExtractor.PdfExtractionResult pdf = "pdf".equals(extension)
                    ? pdfTextExtractor.extract(bytes) : null;
            KnowledgeDocxTextExtractor.DocxExtractionResult docx = "docx".equals(extension)
                    ? docxTextExtractor.extract(bytes) : null;
            String content = switch (extension) {
                case "pdf" -> pdf.content();
                case "docx" -> docx.content();
                case "md", "txt" -> StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                default -> throw new BusinessException("仅支持 PDF、DOCX、Markdown 和 TXT 文件");
            };
            content = content.replace("\u0000", "").trim();
            if (content.isBlank()) throw new BusinessException("文档未解析出可索引文本");
            if (content.length() > 5_000_000) throw new BusinessException("文档解析文本不能超过 500 万字符");
            String contentType = switch (extension) {
                case "pdf" -> "application/pdf";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "md" -> "text/markdown";
                default -> "text/plain";
            };
            String parser = pdf != null ? pdf.parser() : docx != null ? docx.parser() : extension;
            return new ParsedFile(content, contentType, parser, pdf == null ? null : pdf.pageCount(),
                    pdf != null ? pdf.ocrApplied() : docx != null && docx.ocrApplied(),
                    docx == null ? null : docx.embeddedImageCount(), docx == null ? null : docx.ocrImageCount());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("文档解析失败，请确认文件未损坏或加密");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        String value = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank() || value.length() > 255) throw new BusinessException("文件名不能为空且不能超过 255 个字符");
        return value;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private Path resolveSafely(String storageKey) {
        Path target = storageRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storageRoot)) throw new BusinessException("知识文档存储路径不安全");
        return target;
    }

    private record ParsedFile(String content, String contentType, String parser, Integer pageCount,
                              boolean ocrApplied, Integer embeddedImageCount, Integer ocrImageCount) { }

    /** 原始文档预览内容。 */
    public record StoredDocument(String filename, String contentType, byte[] content) { }
}

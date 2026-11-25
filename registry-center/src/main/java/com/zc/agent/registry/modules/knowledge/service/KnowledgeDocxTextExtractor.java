package com.zc.agent.registry.modules.knowledge.service;

import com.zc.agent.registry.common.exception.BusinessException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** DOCX 正文、表格与内嵌图片 OCR 提取。 */
@Service
public class KnowledgeDocxTextExtractor {

    private static final Set<String> OCR_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "tif", "tiff");
    private final boolean ocrEnabled;
    private final String ocrCommand;
    private final String ocrLanguages;
    private final Duration imageTimeout;
    private final int maxImages;

    public KnowledgeDocxTextExtractor(
            @Value("${registry.knowledge.docx-ocr.enabled:true}") boolean ocrEnabled,
            @Value("${registry.knowledge.docx-ocr.command:tesseract}") String ocrCommand,
            @Value("${registry.knowledge.docx-ocr.languages:chi_sim+eng}") String ocrLanguages,
            @Value("${registry.knowledge.docx-ocr.image-timeout-seconds:60}") int imageTimeoutSeconds,
            @Value("${registry.knowledge.docx-ocr.max-images:100}") int maxImages) {
        this.ocrEnabled = ocrEnabled;
        this.ocrCommand = ocrCommand;
        this.ocrLanguages = ocrLanguages;
        this.imageTimeout = Duration.ofSeconds(Math.max(10, Math.min(180, imageTimeoutSeconds)));
        this.maxImages = Math.max(1, Math.min(500, maxImages));
    }

    /** 按 DOCX 正文顺序提取段落、表格和内嵌图片文字。 */
    public DocxExtractionResult extract(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            ExtractionState state = new ExtractionState();
            String content = extractBodyElements(document.getBodyElements(), state).trim();
            String parser = state.ocrImageCount > 0 ? "docx-structured-ocr" : "docx-structured";
            return new DocxExtractionResult(content, parser, state.embeddedImageCount,
                    state.ocrImageCount, state.ocrImageCount > 0);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Word 文档解析失败，请确认文件未损坏或加密");
        }
    }

    private String extractBodyElements(List<IBodyElement> elements, ExtractionState state) {
        StringBuilder result = new StringBuilder();
        for (IBodyElement element : elements) {
            String content = "";
            if (element instanceof XWPFParagraph paragraph) content = extractParagraph(paragraph, state);
            else if (element instanceof XWPFTable table) content = extractTable(table, state);
            appendBlock(result, content);
        }
        return result.toString();
    }

    private String extractParagraph(XWPFParagraph paragraph, ExtractionState state) {
        StringBuilder result = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = normalize(run.text());
            if (!text.isBlank()) result.append(text);
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                String imageText = extractPicture(picture.getPictureData(), state);
                if (!imageText.isBlank()) {
                    if (!result.isEmpty()) result.append('\n');
                    result.append("[图片 OCR]\n").append(imageText);
                }
            }
        }
        if (result.isEmpty()) return normalize(paragraph.getText());
        return normalize(result.toString());
    }

    private String extractTable(XWPFTable table, ExtractionState state) {
        StringBuilder result = new StringBuilder("[表格]\n");
        for (XWPFTableRow row : table.getRows()) {
            result.append("| ");
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = extractBodyElements(cell.getBodyElements(), state).replace("\n", " ").trim();
                result.append(cellText.replace("|", "\\|")).append(" | ");
            }
            result.append('\n');
        }
        return result.append("[/表格]").toString();
    }

    private String extractPicture(XWPFPictureData pictureData, ExtractionState state) {
        state.embeddedImageCount++;
        if (!ocrEnabled || pictureData == null) return "";
        String extension = normalizeExtension(pictureData.suggestFileExtension());
        byte[] data = pictureData.getData();
        if (!OCR_IMAGE_EXTENSIONS.contains(extension) || !hasUsefulDimensions(data)) return "";
        String hash = sha256(data);
        String cached = state.imageTextCache.get(hash);
        if (cached != null) {
            if (!cached.isBlank()) state.ocrImageCount++;
            return cached;
        }
        if (state.processedImageCount >= maxImages) return "";
        if (!state.ocrCommandChecked) {
            assertOcrAvailable();
            state.ocrCommandChecked = true;
        }
        state.processedImageCount++;
        String extracted = runOcr(data, extension, state.processedImageCount);
        state.imageTextCache.put(hash, extracted);
        if (!extracted.isBlank()) state.ocrImageCount++;
        return extracted;
    }

    private String runOcr(byte[] data, String extension, int imageIndex) {
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("knowledge-docx-ocr-");
            Path imagePath = tempDirectory.resolve("image." + extension);
            Path outputBase = tempDirectory.resolve("result");
            Path errorPath = tempDirectory.resolve("ocr.err");
            Files.write(imagePath, data);
            Process process = new ProcessBuilder(ocrCommand, imagePath.toString(), outputBase.toString(),
                    "-l", ocrLanguages, "--psm", "3").redirectError(errorPath.toFile()).start();
            boolean finished = process.waitFor(imageTimeout.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("Word 第 " + imageIndex + " 张图片 OCR 超时");
            }
            if (process.exitValue() != 0) {
                String error = Files.exists(errorPath) ? Files.readString(errorPath, StandardCharsets.UTF_8).trim() : "";
                throw new BusinessException("Word 第 " + imageIndex + " 张图片 OCR 失败" +
                        (error.isBlank() ? "" : "：" + abbreviate(error)));
            }
            Path textPath = Path.of(outputBase + ".txt");
            String text = Files.exists(textPath) ? normalize(Files.readString(textPath, StandardCharsets.UTF_8)) : "";
            long meaningful = text.codePoints().filter(Character::isLetterOrDigit).count();
            return meaningful >= 4 ? text : "";
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Word 图片 OCR 处理被中断");
        } catch (Exception exception) {
            throw new BusinessException("Word 图片 OCR 处理失败，请检查 OCR 运行环境");
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private boolean hasUsefulDimensions(byte[] data) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(data));
            return image != null && image.getWidth() >= 120 && image.getHeight() >= 60;
        } catch (IOException exception) {
            return false;
        }
    }

    private void assertOcrAvailable() {
        try {
            Process process = new ProcessBuilder(ocrCommand, "--version").redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new BusinessException("Word 图片 OCR 命令不可用，请检查 tesseract 安装");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("Word 图片 OCR 命令不可用，请检查 tesseract 安装");
        }
    }

    private String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private void appendBlock(StringBuilder target, String content) {
        if (content == null || content.isBlank()) return;
        if (!target.isEmpty()) target.append("\n\n");
        target.append(content.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\u0000", "").replace("\r\n", "\n")
                .replace('\r', '\n').replaceAll("[ \\t]+(?=\\n)", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String normalizeExtension(String value) {
        String extension = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 尽力清理临时文件。 */ }
            });
        } catch (IOException ignored) {
            // 临时目录由操作系统继续清理，不影响解析结果。
        }
    }

    private static final class ExtractionState {
        private final Map<String, String> imageTextCache = new HashMap<>();
        private int embeddedImageCount;
        private int processedImageCount;
        private int ocrImageCount;
        private boolean ocrCommandChecked;
    }

    /** DOCX 文本提取结果与图片 OCR 统计。 */
    public record DocxExtractionResult(String content, String parser, int embeddedImageCount,
                                       int ocrImageCount, boolean ocrApplied) { }
}

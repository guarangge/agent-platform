package com.zc.agent.registry.modules.knowledge.service;

import com.zc.agent.registry.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** PDF 文本质量检测与图片型 PDF OCR 提取。 */
@Service
public class KnowledgePdfTextExtractor {

    private static final List<String> WATERMARKS = List.of("住房城乡建设部信息公开", "浏览专用");
    private final boolean ocrEnabled;
    private final String renderCommand;
    private final String ocrCommand;
    private final String ocrLanguages;
    private final int ocrDpi;
    private final Duration pageTimeout;

    public KnowledgePdfTextExtractor(
            @Value("${registry.knowledge.pdf-ocr.enabled:true}") boolean ocrEnabled,
            @Value("${registry.knowledge.pdf-ocr.render-command:pdftoppm}") String renderCommand,
            @Value("${registry.knowledge.pdf-ocr.command:tesseract}") String ocrCommand,
            @Value("${registry.knowledge.pdf-ocr.languages:chi_sim+eng}") String ocrLanguages,
            @Value("${registry.knowledge.pdf-ocr.dpi:240}") int ocrDpi,
            @Value("${registry.knowledge.pdf-ocr.page-timeout-seconds:60}") int pageTimeoutSeconds) {
        this.ocrEnabled = ocrEnabled;
        this.renderCommand = renderCommand;
        this.ocrCommand = ocrCommand;
        this.ocrLanguages = ocrLanguages;
        this.ocrDpi = Math.max(120, Math.min(300, ocrDpi));
        this.pageTimeout = Duration.ofSeconds(Math.max(10, Math.min(180, pageTimeoutSeconds)));
    }

    /** 优先读取 PDF 文本层，质量不足时逐页 OCR。 */
    public PdfExtractionResult extract(byte[] bytes) {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new BusinessException("文件内容不是有效 PDF");
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new BusinessException("暂不支持加密 PDF");
            int pageCount = document.getNumberOfPages();
            String directText = normalize(new PDFTextStripper().getText(document));
            if (!requiresOcr(directText, pageCount)) {
                return new PdfExtractionResult(directText, "pdfbox", pageCount, false);
            }
            if (!ocrEnabled) {
                throw new BusinessException("PDF 正文为图片或文本层质量过低，请启用 OCR 后重新解析");
            }
            String ocrText = ocr(document, bytes);
            if (requiresOcr(ocrText, pageCount)) {
                throw new BusinessException("PDF OCR 未提取到足够的有效正文，请检查文件清晰度或 OCR 配置");
            }
            return new PdfExtractionResult(ocrText, "pdf-ocr-tesseract", pageCount, true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException("PDF 文档解析失败，请确认文件未损坏");
        }
    }

    boolean requiresOcr(String content, int pageCount) {
        String text = normalize(content);
        if (text.isBlank()) return true;
        long meaningful = text.codePoints().filter(value -> Character.isLetterOrDigit(value)).count();
        int pages = Math.max(1, pageCount);
        if (meaningful < Math.max(80L, pages * 120L)) return true;
        String compact = compact(text);
        for (String watermark : WATERMARKS) {
            if (occurrences(compact, watermark) >= Math.max(3, pages / 4)) return true;
        }
        return false;
    }

    String cleanOcrPages(List<String> pageTexts) {
        Map<String, Integer> pageFrequency = new HashMap<>();
        List<List<String>> pages = new ArrayList<>();
        for (String pageText : pageTexts) {
            List<String> lines = normalize(pageText).lines().map(String::trim).filter(line -> !line.isBlank()).toList();
            pages.add(lines);
            Set<String> seen = new HashSet<>();
            for (String line : lines) {
                String normalized = compact(line);
                if (!normalized.isBlank() && seen.add(normalized)) pageFrequency.merge(normalized, 1, Integer::sum);
            }
        }
        int repeatedThreshold = Math.max(3, (int) Math.ceil(pageTexts.size() * 0.4D));
        StringBuilder result = new StringBuilder();
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            if (!result.isEmpty()) result.append("\n\n");
            result.append("[第 ").append(pageIndex + 1).append(" 页]\n");
            for (String line : pages.get(pageIndex)) {
                String normalized = compact(line);
                if (isWatermark(normalized) || normalized.matches("[-—_]*\\d{1,4}[-—_]*")) continue;
                if (normalized.length() <= 50 && pageFrequency.getOrDefault(normalized, 0) >= repeatedThreshold) continue;
                result.append(line).append('\n');
            }
        }
        return normalize(result.toString());
    }

    private String ocr(PDDocument document, byte[] sourceBytes) {
        assertCommandAvailable(renderCommand, "-v", "PDF OCR 渲染命令不可用，请检查 pdftoppm 安装");
        assertOcrAvailable();
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("knowledge-pdf-ocr-");
            Path sourcePath = tempDirectory.resolve("source.pdf");
            Path renderPrefix = tempDirectory.resolve("page");
            Path renderErrorPath = tempDirectory.resolve("render.err");
            Files.write(sourcePath, sourceBytes);
            Process renderProcess = new ProcessBuilder(renderCommand, "-png", "-r", String.valueOf(ocrDpi),
                    sourcePath.toString(), renderPrefix.toString()).redirectError(renderErrorPath.toFile()).start();
            boolean rendered = renderProcess.waitFor(Math.max(120L, document.getNumberOfPages() * 5L), TimeUnit.SECONDS);
            if (!rendered) {
                renderProcess.destroyForcibly();
                throw new BusinessException("PDF OCR 页面渲染超时");
            }
            if (renderProcess.exitValue() != 0) {
                String error = Files.exists(renderErrorPath)
                        ? Files.readString(renderErrorPath, StandardCharsets.UTF_8).trim() : "";
                throw new BusinessException("PDF OCR 页面渲染失败" + (error.isBlank() ? "" : "：" + abbreviate(error)));
            }
            List<Path> renderedPages;
            try (var files = Files.list(tempDirectory)) {
                renderedPages = files.filter(path -> path.getFileName().toString().matches("page-\\d+\\.png"))
                        .sorted().toList();
            }
            if (renderedPages.size() != document.getNumberOfPages()) {
                throw new BusinessException("PDF OCR 页面渲染数量不完整");
            }
            List<String> pageTexts = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < renderedPages.size(); pageIndex++) {
                Path imagePath = renderedPages.get(pageIndex);
                Path outputBase = tempDirectory.resolve("page-" + (pageIndex + 1));
                Path errorPath = tempDirectory.resolve("page-" + (pageIndex + 1) + ".err");
                Process process = new ProcessBuilder(ocrCommand, imagePath.toString(), outputBase.toString(),
                        "-l", ocrLanguages, "--psm", "3")
                        .redirectError(errorPath.toFile()).start();
                boolean finished = process.waitFor(pageTimeout.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new BusinessException("PDF OCR 第 " + (pageIndex + 1) + " 页处理超时");
                }
                if (process.exitValue() != 0) {
                    String error = Files.exists(errorPath) ? Files.readString(errorPath, StandardCharsets.UTF_8).trim() : "";
                    throw new BusinessException("PDF OCR 第 " + (pageIndex + 1) + " 页处理失败" +
                            (error.isBlank() ? "" : "：" + abbreviate(error)));
                }
                Path textPath = Path.of(outputBase + ".txt");
                pageTexts.add(Files.exists(textPath) ? Files.readString(textPath, StandardCharsets.UTF_8) : "");
                Files.deleteIfExists(imagePath);
                Files.deleteIfExists(textPath);
                Files.deleteIfExists(errorPath);
            }
            return cleanOcrPages(pageTexts);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PDF OCR 处理被中断");
        } catch (Exception exception) {
            throw new BusinessException("PDF OCR 处理失败，请检查 OCR 运行环境");
        } finally {
            deleteDirectory(tempDirectory);
        }
    }

    private void assertOcrAvailable() {
        assertCommandAvailable(ocrCommand, "--version", "PDF OCR 命令不可用，请检查 tesseract 安装");
    }

    private void assertCommandAvailable(String command, String versionArgument, String errorMessage) {
        try {
            Process process = new ProcessBuilder(command, versionArgument).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new BusinessException(errorMessage);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(errorMessage);
        }
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 尽力清理临时文件。 */ }
            });
        } catch (IOException ignored) {
            // 临时目录由操作系统继续清理，不影响解析结果。
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\u0000", "").replace("\r\n", "\n")
                .replace('\r', '\n').replaceAll("[ \\t]+(?=\\n)", "").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private boolean isWatermark(String value) {
        return WATERMARKS.stream().anyMatch(watermark -> value.contains(watermark));
    }

    private int occurrences(String text, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }

    private String abbreviate(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    /** PDF 文本提取结果与质量来源。 */
    public record PdfExtractionResult(String content, String parser, int pageCount, boolean ocrApplied) { }
}

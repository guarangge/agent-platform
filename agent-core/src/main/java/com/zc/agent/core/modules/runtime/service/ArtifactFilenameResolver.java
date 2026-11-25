package com.zc.agent.core.modules.runtime.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据用户意图和正文主题生成安全、可读的产物文件名。
 */
public final class ArtifactFilenameResolver {

    private static final String DEFAULT_PDF_FILENAME = "Agent生成文档.pdf";
    private static final int MAX_BASENAME_CODE_POINTS = 48;
    private static final Pattern EXPLICIT_PDF_NAME_PATTERN = Pattern.compile(
            "(?:命名为|文件名(?:为|叫)?|保存为|另存为|叫做)\\s*[《【\\[\\(（\\\"'`]?([^\\n\\r》】\\]\\)）\\\"'`]{1,64}?\\.pdf)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_TITLE_PATTERN = Pattern.compile("^#{1,6}\\s+(.+?)\\s*$");
    private static final Pattern PDF_TOPIC_PATTERN = Pattern.compile(
            "(?:请)?(?:把|将)?(?:前面|上面|上述|以上|当前)?(?:的)?\\s*(.{2,40}?)(?:生成|制作|创建|导出|转换|转成)(?:一个|一份|为)?\\s*pdf",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> GENERIC_TITLES = List.of(
            "最终回复", "当前进度汇报", "任务执行结果", "可下载产物", "pdf文件生成请求"
    );

    private ArtifactFilenameResolver() {
    }

    /**
     * 生成 PDF 文件名，优先尊重用户显式命名，其次使用正文标题和请求主题。
     *
     * @param userMessage 用户当前请求
     * @param sourceContent PDF 正文
     * @return UTF-8 安全文件名
     */
    public static String resolvePdfFilename(String userMessage, String sourceContent) {
        String explicitName = extractExplicitPdfName(userMessage);
        if (explicitName != null) {
            return sanitizePdfFilename(explicitName);
        }
        String contentTitle = extractContentTitle(sourceContent);
        if (contentTitle != null) {
            return sanitizePdfFilename(contentTitle + ".pdf");
        }
        String requestTopic = extractRequestTopic(userMessage);
        if (requestTopic != null) {
            return sanitizePdfFilename(requestTopic + ".pdf");
        }
        return DEFAULT_PDF_FILENAME;
    }

    /** 判断当前请求是否明确要求生成 PDF。 */
    public static boolean isPdfGenerationRequest(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("pdf") && (normalized.contains("生成")
                || normalized.contains("创建")
                || normalized.contains("制作")
                || normalized.contains("导出")
                || normalized.contains("转换")
                || normalized.contains("转成")
                || normalized.contains("generate")
                || normalized.contains("create")
                || normalized.contains("export")
                || normalized.contains("convert"));
    }

    private static String extractExplicitPdfName(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        Matcher matcher = EXPLICIT_PDF_NAME_PATTERN.matcher(userMessage);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String extractContentTitle(String sourceContent) {
        if (sourceContent == null || sourceContent.isBlank()) {
            return null;
        }
        for (String line : sourceContent.lines().limit(40).toList()) {
            Matcher matcher = MARKDOWN_TITLE_PATTERN.matcher(line.trim());
            if (!matcher.matches()) {
                continue;
            }
            String title = cleanTitle(matcher.group(1));
            if (!title.isBlank() && GENERIC_TITLES.stream().noneMatch(title::equalsIgnoreCase)) {
                return title;
            }
        }
        return null;
    }

    private static String extractRequestTopic(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        Matcher matcher = PDF_TOPIC_PATTERN.matcher(userMessage);
        if (!matcher.find()) {
            return null;
        }
        String topic = cleanTitle(matcher.group(1))
                .replaceFirst("^(?:一份|一个|这个|该)", "")
                .trim();
        return topic.isBlank() || "文件".equals(topic) ? null : topic;
    }

    private static String sanitizePdfFilename(String candidate) {
        String basename = candidate == null ? "" : candidate.trim();
        basename = basename.replaceFirst("(?i)\\.pdf$", "");
        basename = cleanTitle(basename)
                .replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "-")
                .replaceAll("\\s+", "")
                .replaceAll("[-.]{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (basename.isBlank()) {
            return DEFAULT_PDF_FILENAME;
        }
        int codePointCount = basename.codePointCount(0, basename.length());
        if (codePointCount > MAX_BASENAME_CODE_POINTS) {
            basename = basename.substring(0, basename.offsetByCodePoints(0, MAX_BASENAME_CODE_POINTS));
        }
        return basename + ".pdf";
    }

    private static String cleanTitle(String title) {
        return title == null ? "" : title
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("^[\\s#>•·:：—-]+|[\\s#>•·:：—-]+$", "")
                .trim();
    }
}

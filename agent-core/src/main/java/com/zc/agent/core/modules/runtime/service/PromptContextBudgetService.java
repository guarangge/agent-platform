package com.zc.agent.core.modules.runtime.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import org.springframework.stereotype.Service;

/**
 * 统一 Prompt 上下文预算服务。
 * 基于模型配置的上下文窗口与预留 Token，给不同执行阶段分配可用预算。
 */
@Service
public class PromptContextBudgetService {

    private static final int DEFAULT_CONTEXT_WINDOW = 32000;
    private static final int DEFAULT_RESERVED_OUTPUT_TOKENS = 2048;
    private static final int DEFAULT_RESERVED_SYSTEM_TOKENS = 2048;
    private static final double DEFAULT_COMPACTION_TRIGGER_RATIO = 0.8D;
    private static final int DEFAULT_CHARS_PER_TOKEN = 4;
    private static final int MIN_STAGE_TOKENS = 1024;
    private static final int DEFAULT_SAFETY_MARGIN_TOKENS = 512;
    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
    private static final double DEFAULT_TOKEN_CHARS_PER_TOKEN = 4D;
    private static final double CL100K_CHARS_PER_TOKEN = 3.4D;
    private static final double O200K_CHARS_PER_TOKEN = 3.8D;
    private static final double SENTENCEPIECE_CHARS_PER_TOKEN = 2.8D;

    public StageBudget resolveBudget(ExecutionContext.ModelContext modelContext, PromptStage stage) {
        int contextWindow = positiveOrDefault(modelContext == null ? null : modelContext.contextWindow(), DEFAULT_CONTEXT_WINDOW);
        int reservedOutputTokens = positiveOrDefault(modelContext == null ? null : modelContext.reservedOutputTokens(),
                positiveOrDefault(modelContext == null ? null : modelContext.maxTokens(), DEFAULT_RESERVED_OUTPUT_TOKENS));
        int reservedSystemTokens = positiveOrDefault(modelContext == null ? null : modelContext.reservedSystemTokens(), DEFAULT_RESERVED_SYSTEM_TOKENS);
        double triggerRatio = ratioOrDefault(modelContext == null ? null : modelContext.compactionTriggerRatio(), DEFAULT_COMPACTION_TRIGGER_RATIO);
        String tokenizerFamily = modelContext == null ? null : modelContext.tokenizerFamily();
        int triggerTokens = (int) Math.floor(contextWindow * triggerRatio);
        int usablePromptTokens = triggerTokens - reservedOutputTokens - reservedSystemTokens - resolveSafetyMarginTokens(contextWindow);
        usablePromptTokens = Math.max(MIN_STAGE_TOKENS, usablePromptTokens);
        int promptChars = estimateCharsByTokens(usablePromptTokens, tokenizerFamily);
        return switch (stage) {
            case DECISION -> new StageBudget(
                    stage,
                    contextWindow,
                    tokenizerFamily,
                    usablePromptTokens,
                    promptChars,
                    allocate(promptChars, 0.18D, 1200, 3200),
                    allocate(promptChars, 0.12D, 800, 2200),
                    allocate(promptChars, 0.20D, 1200, 3600),
                    allocate(promptChars, 0.12D, 600, 1800),
                    allocate(promptChars, 0.10D, 500, 1600),
                    allocate(promptChars, 0.08D, 180, 420),
                    allocate(promptChars, 0.10D, 200, 1200)
            );
            case PLANNING -> new StageBudget(
                    stage,
                    contextWindow,
                    tokenizerFamily,
                    usablePromptTokens,
                    promptChars,
                    allocate(promptChars, 0.16D, 1200, 3000),
                    allocate(promptChars, 0.14D, 800, 2200),
                    allocate(promptChars, 0.22D, 1400, 4200),
                    allocate(promptChars, 0.12D, 800, 2200),
                    allocate(promptChars, 0.12D, 600, 2000),
                    allocate(promptChars, 0.08D, 180, 420),
                    allocate(promptChars, 0.10D, 300, 1500)
            );
            case EXECUTION_INPUT -> new StageBudget(
                    stage,
                    contextWindow,
                    tokenizerFamily,
                    usablePromptTokens,
                    promptChars,
                    allocate(promptChars, 0.24D, 800, 2400),
                    allocate(promptChars, 0.10D, 400, 1600),
                    allocate(promptChars, 0.06D, 300, 900),
                    allocate(promptChars, 0.06D, 300, 900),
                    allocate(promptChars, 0.20D, 480, 2200),
                    allocate(promptChars, 0.08D, 140, 320),
                    allocate(promptChars, 0.18D, 240, 1200)
            );
            case REPLY -> new StageBudget(
                    stage,
                    contextWindow,
                    tokenizerFamily,
                    usablePromptTokens,
                    promptChars,
                    allocate(promptChars, 0.18D, 1000, 3000),
                    allocate(promptChars, 0.12D, 600, 1800),
                    allocate(promptChars, 0.22D, 1200, 4200),
                    allocate(promptChars, 0.12D, 600, 1800),
                    allocate(promptChars, 0.16D, 800, 2200),
                    allocate(promptChars, 0.08D, 180, 420),
                    allocate(promptChars, 0.14D, 400, 1800)
            );
            case REFLECTION -> new StageBudget(
                    stage,
                    contextWindow,
                    tokenizerFamily,
                    usablePromptTokens,
                    promptChars,
                    allocate(promptChars, 0.16D, 800, 2400),
                    allocate(promptChars, 0.10D, 500, 1600),
                    allocate(promptChars, 0.18D, 1000, 3200),
                    allocate(promptChars, 0.10D, 500, 1600),
                    allocate(promptChars, 0.14D, 600, 1800),
                    allocate(promptChars, 0.08D, 160, 360),
                    allocate(promptChars, 0.18D, 400, 1800)
            );
        };
    }

    public int estimateTokens(String text) {
        return estimateTokens(text, null);
    }

    public int estimateTokens(String text, String tokenizerFamily) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Integer exactTokens = tryExactTokenEstimate(text, tokenizerFamily);
        if (exactTokens != null) {
            return exactTokens;
        }
        int chineseChars = 0;
        int punctuationChars = 0;
        int asciiWordChars = 0;
        int asciiWords = 0;
        boolean inAsciiWord = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                inAsciiWord = false;
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(current);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                chineseChars++;
                inAsciiWord = false;
                continue;
            }
            if (Character.isLetterOrDigit(current)) {
                asciiWordChars++;
                if (!inAsciiWord) {
                    asciiWords++;
                    inAsciiWord = true;
                }
                continue;
            }
            punctuationChars++;
            inAsciiWord = false;
        }
        double charsPerToken = resolveCharsPerToken(tokenizerFamily);
        double tokenEstimate = chineseChars;
        tokenEstimate += asciiWords * 0.35D;
        tokenEstimate += asciiWordChars / charsPerToken;
        tokenEstimate += punctuationChars * 0.4D;
        return Math.max(1, (int) Math.ceil(tokenEstimate));
    }

    public String truncateToTokenBudget(String text, int maxTokens) {
        return truncateToTokenBudget(text, maxTokens, null);
    }

    public String truncateToTokenBudget(String text, int maxTokens, String tokenizerFamily) {
        if (text == null || text.isBlank() || maxTokens <= 0) {
            return maxTokens <= 0 ? "" : text;
        }
        if (estimateTokens(text, tokenizerFamily) <= maxTokens) {
            return text;
        }
        int low = 0;
        int high = text.length();
        String best = "";
        while (low <= high) {
            int middle = (low + high) >>> 1;
            String candidate = truncateToChars(text, middle);
            int candidateTokens = estimateTokens(candidate, tokenizerFamily);
            if (candidateTokens <= maxTokens) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return best.isBlank() ? truncateToChars(text, estimateCharsByTokens(maxTokens, tokenizerFamily)) : best;
    }

    public String truncateToChars(String text, int maxChars) {
        if (text == null || text.isBlank() || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    public int estimateBudgetTokensForChars(int charCount, String tokenizerFamily) {
        if (charCount <= 0) {
            return 0;
        }
        return (int) Math.ceil(charCount / resolveCharsPerToken(tokenizerFamily));
    }

    public int estimateCharsByTokens(int tokens, String tokenizerFamily) {
        if (tokens <= 0) {
            return 0;
        }
        return (int) Math.ceil(tokens * resolveCharsPerToken(tokenizerFamily));
    }

    public String estimateMode(String tokenizerFamily) {
        return resolveEncodingType(tokenizerFamily) == null ? "heuristic" : "exact_jtokkit";
    }

    private int resolveSafetyMarginTokens(int contextWindow) {
        return Math.max(DEFAULT_SAFETY_MARGIN_TOKENS, contextWindow / 50);
    }

    private int allocate(int promptChars, double ratio, int minChars, int maxChars) {
        int allocated = (int) Math.floor(promptChars * ratio);
        allocated = Math.max(minChars, allocated);
        return Math.min(allocated, maxChars);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private double ratioOrDefault(Double value, double defaultValue) {
        if (value == null || value <= 0D || value > 1D) {
            return defaultValue;
        }
        return value;
    }

    private double resolveCharsPerToken(String tokenizerFamily) {
        String normalized = tokenizerFamily == null ? "" : tokenizerFamily.trim().toLowerCase();
        if (normalized.contains("o200k")) {
            return O200K_CHARS_PER_TOKEN;
        }
        if (normalized.contains("cl100k")) {
            return CL100K_CHARS_PER_TOKEN;
        }
        if (normalized.contains("sentencepiece") || normalized.contains("spm")) {
            return SENTENCEPIECE_CHARS_PER_TOKEN;
        }
        return DEFAULT_TOKEN_CHARS_PER_TOKEN;
    }

    private Integer tryExactTokenEstimate(String text, String tokenizerFamily) {
        EncodingType encodingType = resolveEncodingType(tokenizerFamily);
        if (encodingType == null) {
            return null;
        }
        try {
            Encoding encoding = encodingRegistry.getEncoding(encodingType);
            return encoding.encode(text).size();
        } catch (Exception exception) {
            return null;
        }
    }

    private EncodingType resolveEncodingType(String tokenizerFamily) {
        String normalized = tokenizerFamily == null ? "" : tokenizerFamily.trim().toLowerCase();
        return switch (normalized) {
            case "cl100k", "cl100k_base" -> EncodingType.CL100K_BASE;
            case "o200k", "o200k_base" -> EncodingType.O200K_BASE;
            case "p50k", "p50k_base" -> EncodingType.P50K_BASE;
            case "r50k", "r50k_base", "gpt2" -> EncodingType.R50K_BASE;
            default -> null;
        };
    }

    public enum PromptStage {
        DECISION,
        PLANNING,
        EXECUTION_INPUT,
        REPLY,
        REFLECTION
    }

    public record StageBudget(
            PromptStage stage,
            int contextWindow,
            String tokenizerFamily,
            int usablePromptTokens,
            int promptChars,
            int userMessageChars,
            int conversationStateChars,
            int conversationMemoryChars,
            int recentTaskMemoryChars,
            int previousSummaryChars,
            int historyMessageChars,
            int factChars
    ) {
    }
}

package com.zc.agent.core.modules.tool.service;

import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.runtime.service.RuntimeLlmGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档检索无命中时的 LLM 回退服务。
 */
@Service
public class DocSearchLlmFallbackService {

    private static final Logger log = LoggerFactory.getLogger(DocSearchLlmFallbackService.class);

    private final RuntimeLlmGatewayService runtimeLlmGatewayService;

    public DocSearchLlmFallbackService(RuntimeLlmGatewayService runtimeLlmGatewayService) {
        this.runtimeLlmGatewayService = runtimeLlmGatewayService;
    }

    /**
     * 当本地文档与知识库均无命中时，使用运行时模型给出保守补充说明。
     *
     * @param executionContext 运行时上下文
     * @param userMessage      用户原始问题
     * @param gaokaoQuery      是否高考场景
     * @return LLM 文本；失败时返回 null
     */
    public String answer(ExecutionContext executionContext, String userMessage, boolean gaokaoQuery) {
        if (executionContext == null || !executionContext.hasRunnableModel()) {
            return null;
        }
        try {
            return runtimeLlmGatewayService.chatCompletion(
                    buildSystemPrompt(gaokaoQuery),
                    buildUserPrompt(userMessage, gaokaoQuery),
                    executionContext.modelContext()
            ).trim();
        } catch (Exception exception) {
            com.zc.agent.core.modules.cost.service.BudgetExceededException.propagate(exception);
            log.warn("文档检索 LLM 回退失败，继续返回本地检索结果：{}", exception.getMessage());
            return null;
        }
    }

    private String buildSystemPrompt(boolean gaokaoQuery) {
        if (gaokaoQuery) {
            return """
                    你是高考志愿填报辅助助手。
                    当前本地高考知识库和文档目录没有命中足够内容，你只能基于通用知识做谨慎补充。
                    约束：
                    1. 不得编造具体院校录取分、位次、招生计划、当年实时政策。
                    2. 优先给出概念解释、通用规则、风险提醒和需要补充的信息。
                    3. 明确提醒用户最终以目标省份考试院和院校官方文件为准。
                    4. 直接输出自然语言答案，不要 Markdown 标题，不要 JSON。
                    """;
        }
        return """
                你是项目知识检索补充助手。
                当前本地文档未命中足够内容，请基于用户问题给出保守说明。
                约束：
                1. 不要编造项目中不存在的接口、类、表或配置。
                2. 如果缺乏确定依据，要明确说明“需要结合实际代码/配置再确认”。
                3. 直接输出自然语言答案，不要 Markdown 标题，不要 JSON。
                """;
    }

    private String buildUserPrompt(String userMessage, boolean gaokaoQuery) {
        if (gaokaoQuery) {
            return """
                    用户问题：
                    %s

                    请给出：
                    1. 一个保守的直接回答；
                    2. 2到4条风险或注意事项；
                    3. 建议用户补充哪些信息，以便后续继续检索。
                    """.formatted(userMessage == null ? "" : userMessage);
        }
        return """
                用户问题：
                %s

                请在没有本地文档命中的前提下，给出保守解释，并指出还需要结合哪些代码或配置确认。
                """.formatted(userMessage == null ? "" : userMessage);
    }
}

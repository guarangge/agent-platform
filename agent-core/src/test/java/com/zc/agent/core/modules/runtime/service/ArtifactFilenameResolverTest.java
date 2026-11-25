package com.zc.agent.core.modules.runtime.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactFilenameResolverTest {

    @Test
    void shouldPreferExplicitFilenameFromUserMessage() {
        String filename = ArtifactFilenameResolver.resolvePdfFilename(
                "请把上述内容生成 PDF，文件名为《研究生AI转型路线.pdf》",
                "# AI 自学路线\n正文"
        );

        assertThat(filename).isEqualTo("研究生AI转型路线.pdf");
    }

    @Test
    void shouldUseMarkdownTitleWhenFilenameIsNotSpecified() {
        String filename = ArtifactFilenameResolver.resolvePdfFilename(
                "请把前面的计划生成一个 PDF 文件",
                "# AI 自学路线（计算机科班研究生转 AI 方向）\n\n## 第一阶段"
        );

        assertThat(filename).isEqualTo("AI自学路线（计算机科班研究生转AI方向）.pdf");
    }

    @Test
    void shouldUseRequestTopicWhenContentHasNoTitle() {
        String filename = ArtifactFilenameResolver.resolvePdfFilename(
                "请将项目实施方案导出为 PDF",
                "这里是正文内容，没有 Markdown 标题。"
        );

        assertThat(filename).isEqualTo("项目实施方案.pdf");
    }

    @Test
    void shouldRemoveUnsafeCharactersAndLimitLongTitle() {
        String filename = ArtifactFilenameResolver.resolvePdfFilename(
                "生成 PDF",
                "# 2026 / AI：学习*路线?与<Agent>工程实践的超长超长超长超长超长超长超长超长标题\n正文"
        );

        assertThat(filename)
                .endsWith(".pdf")
                .doesNotContain("/", ":", "*", "?", "<", ">")
                .matches(value -> value.codePointCount(0, value.length()) <= 52);
    }

    @Test
    void shouldFallbackToGenericNameWhenNoTopicCanBeResolved() {
        assertThat(ArtifactFilenameResolver.resolvePdfFilename("请生成一个 PDF 文件", "普通正文"))
                .isEqualTo("Agent生成文档.pdf");
    }
}

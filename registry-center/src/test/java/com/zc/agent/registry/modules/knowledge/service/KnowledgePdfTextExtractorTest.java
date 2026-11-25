package com.zc.agent.registry.modules.knowledge.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgePdfTextExtractorTest {

    private final KnowledgePdfTextExtractor extractor =
            new KnowledgePdfTextExtractor(false, "pdftoppm", "tesseract", "chi_sim+eng", 240, 60);

    @Test
    void shouldRejectRepeatedWatermarkTextLayer() {
        String content = "住房城乡建设部信息公开\n浏览专用\n".repeat(82);

        assertThat(extractor.requiresOcr(content, 82)).isTrue();
    }

    @Test
    void shouldAcceptMeaningfulTextLayer() {
        String content = "建筑节能与可再生能源利用规范正文，围护结构应符合设计要求。".repeat(400);

        assertThat(extractor.requiresOcr(content, 20)).isFalse();
    }

    @Test
    void shouldRemoveRepeatedHeadersWatermarksAndPageNumbers() {
        String result = extractor.cleanOcrPages(List.of(
                "建筑规范\n住房城乡建设部信息公开\n1.0.1 第一页正文\n1",
                "建筑规范\n浏览专用\n2.0.1 第二页正文\n2",
                "建筑规范\n住房城乡建设部信息公开\n3.0.1 第三页正文\n3"
        ));

        assertThat(result).contains("[第 1 页]", "1.0.1 第一页正文", "[第 3 页]", "3.0.1 第三页正文")
                .doesNotContain("建筑规范", "住房城乡建设部信息公开", "浏览专用");
    }
}

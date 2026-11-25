package com.zc.agent.registry.modules.knowledge.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocxTextExtractorTest {

    private final KnowledgeDocxTextExtractor extractor =
            new KnowledgeDocxTextExtractor(false, "tesseract", "chi_sim+eng", 60, 100);

    @Test
    void shouldKeepParagraphAndTableOrder() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("第一段正文");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("表头A");
            table.getRow(0).getCell(1).setText("表头B");
            table.getRow(1).getCell(0).setText("内容A");
            table.getRow(1).getCell(1).setText("内容B");
            document.createParagraph().createRun().setText("第二段正文");
            document.write(output);
            bytes = output.toByteArray();
        }

        KnowledgeDocxTextExtractor.DocxExtractionResult result = extractor.extract(bytes);

        assertThat(result.parser()).isEqualTo("docx-structured");
        assertThat(result.content()).contains("[表格]", "| 表头A | 表头B |", "[/表格]");
        assertThat(result.content().indexOf("第一段正文"))
                .isLessThan(result.content().indexOf("表头A"));
        assertThat(result.content().indexOf("表头A"))
                .isLessThan(result.content().indexOf("第二段正文"));
    }

    @Test
    void shouldCountEmbeddedImageWhenOcrDisabled() throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nH0AAAAASUVORK5CYII=");
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("图片前文字");
            document.createParagraph().createRun().addPicture(new ByteArrayInputStream(png),
                    XWPFDocument.PICTURE_TYPE_PNG, "sample.png", Units.toEMU(20), Units.toEMU(20));
            document.write(output);
            bytes = output.toByteArray();
        }

        KnowledgeDocxTextExtractor.DocxExtractionResult result = extractor.extract(bytes);

        assertThat(result.embeddedImageCount()).isEqualTo(1);
        assertThat(result.ocrImageCount()).isZero();
        assertThat(result.ocrApplied()).isFalse();
    }
}

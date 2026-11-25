package com.zc.agent.core.modules.tool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.agent.core.modules.attachment.model.AttachmentContent;
import com.zc.agent.core.modules.runtime.model.ExecutionContext;
import com.zc.agent.core.modules.tool.model.RegistryAgentTool;
import com.zc.agent.core.modules.tool.model.ToolExecutionResult;
import com.zc.agent.core.modules.tool.model.ToolInvocationRequest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageLayoutAnalyzeToolExecutorTest {

    @Test
    void shouldAnalyzePngWithoutPersistingContent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ImageToolAttachmentResolver resolver = mock(ImageToolAttachmentResolver.class);
        RegistryAgentTool tool = mock(RegistryAgentTool.class);
        when(tool.toolName()).thenReturn("图片版面分析");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        ExecutionContext.MessageAttachmentContext reference = new ExecutionContext.MessageAttachmentContext(
                12L, "ATT_12", "sample.png", "image/png", (long) png.length, "sha256-value");
        when(resolver.resolve(any(), any())).thenReturn(new ImageToolAttachmentResolver.ResolvedImage(
                reference, new AttachmentContent("sample.png", "image/png", png)));

        ToolExecutionResult result = new ImageLayoutAnalyzeToolExecutor(resolver, objectMapper)
                .execute(null, tool, new ToolInvocationRequest("", Map.of("attachmentId", 12L)));

        JsonNode snapshot = objectMapper.readTree(result.outputSnapshot());
        assertThat(snapshot.path("width").asInt()).isEqualTo(2);
        assertThat(snapshot.path("height").asInt()).isEqualTo(1);
        assertThat(snapshot.path("orientation").asText()).isEqualTo("landscape");
        assertThat(result.outputSnapshot()).doesNotContain("iVBOR");
    }
}

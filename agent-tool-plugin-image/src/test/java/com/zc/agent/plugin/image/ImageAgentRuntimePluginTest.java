package com.zc.agent.plugin.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageAgentRuntimePluginTest {

    @Test
    void shouldDeclareImageTools() {
        assertThat(new ImageAgentRuntimePlugin().manifest().components().get("tools"))
                .containsExactly("IMAGE_OCR", "IMAGE_LAYOUT_ANALYZE");
    }
}

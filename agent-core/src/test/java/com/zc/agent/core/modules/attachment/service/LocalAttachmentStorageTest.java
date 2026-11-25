package com.zc.agent.core.modules.attachment.service;

import com.zc.agent.core.modules.attachment.config.AttachmentProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalAttachmentStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void shouldStoreReadAndDeleteContentWithinRoot() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setStorageRoot(storageRoot);
        LocalAttachmentStorage storage = new LocalAttachmentStorage(properties);
        byte[] content = new byte[]{1, 2, 3};

        storage.store("1/1/10/ATT_TEST.png", content);

        assertThat(storage.read("1/1/10/ATT_TEST.png")).isEqualTo(content);
        storage.delete("1/1/10/ATT_TEST.png");
        assertThat(storageRoot.resolve("1/1/10/ATT_TEST.png")).doesNotExist();
    }

    @Test
    void shouldRejectStorageKeyEscapingRoot() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setStorageRoot(storageRoot);
        LocalAttachmentStorage storage = new LocalAttachmentStorage(properties);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> storage.read("../../outside.png"))
                .withMessageContaining("不安全");
    }
}

